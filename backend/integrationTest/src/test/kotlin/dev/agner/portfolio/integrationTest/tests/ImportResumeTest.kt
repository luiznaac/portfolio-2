package dev.agner.portfolio.integrationTest.tests

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import dev.agner.portfolio.integrationTest.config.ClockMock
import dev.agner.portfolio.integrationTest.config.IntegrationTest
import dev.agner.portfolio.integrationTest.config.appBaseUrl
import dev.agner.portfolio.integrationTest.helpers.b3NegociacaoFixture
import dev.agner.portfolio.integrationTest.helpers.getBean
import dev.agner.portfolio.usecase.import.ImportService
import dev.agner.portfolio.usecase.import.model.ImportDestination
import dev.agner.portfolio.usecase.import.model.ImportDestinationObject
import dev.agner.portfolio.usecase.import.model.ImportRowPayload
import dev.agner.portfolio.usecase.import.model.ImportSource
import dev.agner.portfolio.usecase.listedasset.model.TradeSide
import dev.agner.portfolio.usecase.listedasset.repository.ITradeRepository
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.mockk.every
import kotlinx.datetime.LocalDate
import java.math.BigDecimal
import java.time.Instant

// Re-uploading a file is an identity check, not a second import: the hash decides whether the
// request creates an Import (201) or resumes the one already there (200), whatever its status. The
// staged record is the parked session — the status filter finds it, the typed destination says
// where an apply lands, and the retained original proves provenance. The row situations and
// outcomes then have to be the review's contract — computed against a real ledger, so only a MySQL
// round-trip can prove the institution name resolves the same way apply resolves it.
@IntegrationTest
class ImportResumeTest : StringSpec({

    beforeTest {
        every { ClockMock.clock.instant() } returns Instant.parse("2026-10-14T10:00:00Z")
    }

    "an identical re-upload answers 200 with the same untouched import at every status" {
        // The same bytes twice: a hash comparison is only meaningful when the file is the same file.
        val file = b3NegociacaoFixture()
        val first = upload("b3-negociacao.xlsx", file)
        first.status shouldBe HttpStatusCode.Created
        val body = json(first)
        val id = body.get("id").asInt()
        body.get("destination").get("object_type").asText() shouldBe "TRADES"
        body.get("has_original").asBoolean() shouldBe true

        reupload(file, id, status = "STAGED")
        review(id)
        reupload(file, id, status = "REVIEWED")
        apply(id)
        // The request source is ignored on a hash match: the file identity wins.
        reupload(file, id, status = "APPLIED", source = "B3_POSITION")

        rows(id).map { it.get("situation").asText() } shouldBe
            listOf("duplicate_in_ledger", "duplicate_in_ledger")
    }

    "a parked session is found by the status and source filters until it is applied" {
        val staged = upload("parked.xlsx", b3NegociacaoFixture())
        val stagedId = json(staged).get("id").asInt()
        val applied = stageTyped("applied.xlsx", "applied-hash", listOf(petr4()))
        reviewAndApply(applied)

        imports("status=STAGED&source=B3_TRADES").map { it.get("id").asInt() } shouldBe listOf(stagedId)

        imports("source=B3_TRADES").map { it.get("id").asInt() }.contains(stagedId) shouldBe true
        imports("source=B3_POSITION").map { it.get("id").asInt() } shouldBe emptyList()

        getBean<HttpClient>().get("${appBaseUrl()}/imports?status=NOPE").status shouldBe
            HttpStatusCode.BadRequest
    }

    "the applied record retains the original file beside its hash" {
        val file = b3NegociacaoFixture()
        val id = json(upload("neg.xlsx", file)).get("id").asInt()
        reviewAndApply(id)

        val record = json(getBean<HttpClient>().get("${appBaseUrl()}/imports/$id"))
        record.get("status").asText() shouldBe "APPLIED"
        record.get("destination").get("object_type").asText() shouldBe "TRADES"
        record.get("has_original").asBoolean() shouldBe true

        val original = getBean<HttpClient>().get("${appBaseUrl()}/imports/$id/file")
        original.status shouldBe HttpStatusCode.OK
        original.bodyAsBytes().toList() shouldBe file.toList()
    }

    "the rows carry the four server situations and apply writes the new suffix" {
        val seeded = stageTyped("seed.xlsx", "seed-hash", listOf(petr4()))
        reviewAndApply(seeded)

        val second = stageTyped("neg.xlsx", "second-hash", listOf(petr4(), vale3(), vale3(), missingDate()))

        rows(second).map { it.get("situation").asText() } shouldBe
            listOf("duplicate_in_ledger", "new", "duplicate_in_file", "error")

        // Apply's occurrence walk writes the new suffix (new + the in-file repeat, which the ordinal
        // model keeps as a real execution) and skips the ledger-covered and error rows.
        val result = reviewAndApply(second)
        val trades = getBean<ITradeRepository>().fetchAll()
        trades.size shouldBe 3
        trades.count { it.ticker == "PETR4" } shouldBe 1
        trades.count { it.ticker == "VALE3" } shouldBe 2

        result.get("record").get("destination").get("object_type").asText() shouldBe "TRADES"
        result.get("applied").asInt() shouldBe 2
        result.get("rejected").asInt() shouldBe 1
        result.get("skipped_duplicate").asInt() shouldBe 1
    }

    "apply persists each row's outcome so rejected rows stay as the retry batch" {
        val seeded = stageTyped("seed.xlsx", "outcome-seed-hash", listOf(petr4()))
        reviewAndApply(seeded)

        val id = stageTyped("outcome.xlsx", "outcome-hash", listOf(petr4(), vale3(), vale3(), missingDate()))
        reviewAndApply(id)

        rows(id).map { it.get("outcome").asText() } shouldBe
            listOf("skipped_duplicate", "applied", "applied", "rejected")
    }
})

private const val INSTITUTION = "XP INVESTIMENTOS"

private fun petr4() = row("PETR4", quantity = 100, unitPrice = "48.92")
private fun vale3() = row("VALE3", quantity = 10, unitPrice = "60.00")
private fun missingDate() = row("ITUB4", quantity = 5, unitPrice = "30.00", date = null, error = "Missing date")

private fun row(
    ticker: String,
    quantity: Int,
    unitPrice: String,
    date: LocalDate? = LocalDate.parse("2026-10-14"),
    error: String? = null,
) = ImportRowPayload(
    date = date,
    side = TradeSide.BUY,
    venue = "VISTA",
    ticker = ticker,
    quantity = BigDecimal(quantity),
    unitPrice = BigDecimal(unitPrice),
    institutionName = INSTITUTION,
    error = error,
)

private suspend fun upload(
    fileName: String,
    content: ByteArray,
    source: String = "B3_TRADES",
): HttpResponse = getBean<HttpClient>().post("${appBaseUrl()}/imports") {
    setBody(
        MultiPartFormDataContent(
            formData {
                append("source", source)
                append("destination_object", "TRADES")
                append(
                    "file",
                    content,
                    Headers.build { append(HttpHeaders.ContentDisposition, "filename=\"$fileName\"") },
                )
            },
        ),
    )
}

private suspend fun reupload(
    file: ByteArray,
    id: Int,
    status: String,
    source: String = "B3_TRADES",
) {
    val again = upload("b3-negociacao.xlsx", file, source)
    again.status shouldBe HttpStatusCode.OK
    json(again).also {
        it.get("id").asInt() shouldBe id
        it.get("status").asText() shouldBe status
        it.get("source").asText() shouldBe "B3_TRADES"
    }
}

private suspend fun stageTyped(fileName: String, hash: String, rows: List<ImportRowPayload>): Int =
    getBean<ImportService>().stage(
        ImportSource.B3_TRADES,
        fileName,
        hash,
        rows,
        ImportDestination(ImportDestinationObject.TRADES),
    ).record.id

private suspend fun review(importId: Int) {
    getBean<HttpClient>().post("${appBaseUrl()}/imports/$importId/review").status shouldBe
        HttpStatusCode.OK
}

private suspend fun apply(importId: Int): JsonNode =
    json(getBean<HttpClient>().post("${appBaseUrl()}/imports/$importId/apply"))

private suspend fun reviewAndApply(importId: Int): JsonNode {
    review(importId)
    return apply(importId)
}

private suspend fun rows(importId: Int) =
    json(getBean<HttpClient>().get("${appBaseUrl()}/imports/$importId/rows"))

private suspend fun imports(query: String) =
    json(getBean<HttpClient>().get("${appBaseUrl()}/imports?$query"))

private suspend fun json(response: HttpResponse) = ObjectMapper().readTree(response.bodyAsText())
