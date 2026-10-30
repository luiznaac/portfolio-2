package dev.agner.portfolio.integrationTest.tests

import com.fasterxml.jackson.databind.ObjectMapper
import dev.agner.portfolio.integrationTest.config.ClockMock
import dev.agner.portfolio.integrationTest.config.IntegrationTest
import dev.agner.portfolio.integrationTest.helpers.getBean
import dev.agner.portfolio.usecase.import.ImportService
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
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.mockk.every
import kotlinx.datetime.LocalDate
import java.io.File
import java.math.BigDecimal
import java.time.Instant

// Re-uploading a file is an identity check, not a second import: the hash decides whether the
// request creates an Import (201) or resumes the one already there (200), whatever its status. The
// row situations then have to be the review's contract — computed against a real ledger, so only a
// MySQL round-trip can prove the institution name resolves the same way apply resolves it.
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
        val id = json(first).get("id").asInt()

        reupload(file, id, status = "STAGED")
        review(id)
        reupload(file, id, status = "REVIEWED")
        apply(id)
        // The request source is ignored on a hash match: the file identity wins.
        reupload(file, id, status = "APPLIED", source = "B3_POSITION")

        rows(id).map { it.get("situation").asText() } shouldBe
            listOf("duplicate_in_ledger", "duplicate_in_ledger")
    }

    "the rows carry the four server situations and apply writes the new suffix" {
        val imports = getBean<ImportService>()
        val seeded = imports.stage(
            ImportSource.B3_TRADES,
            "seed.xlsx",
            "seed-hash",
            listOf(petr4()),
        )
        imports.review(seeded.record.id)
        imports.apply(seeded.record.id)

        val second = imports.stage(
            ImportSource.B3_TRADES,
            "neg.xlsx",
            "second-hash",
            listOf(petr4(), vale3(), vale3(), missingDate()),
        )
        val secondId = second.record.id

        rows(secondId).map { it.get("situation").asText() } shouldBe
            listOf("duplicate_in_ledger", "new", "duplicate_in_file", "error")

        // Apply's occurrence walk writes the new suffix (new + the in-file repeat, which the ordinal
        // model keeps as a real execution) and skips the ledger-covered and error rows.
        reviewAndApply(secondId)
        val trades = getBean<ITradeRepository>().fetchAll()
        trades.size shouldBe 3
        trades.count { it.ticker == "PETR4" } shouldBe 1
        trades.count { it.ticker == "VALE3" } shouldBe 2
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

// The committed two-row B3 Negociação fixture the parser tests pin. Gradle runs this module's tests
// from `backend/integrationTest`, so the sibling test resource resolves one directory up.
private fun b3NegociacaoFixture(): ByteArray {
    val fixture = File("../http-api/src/test/resources/b3-negociacao.xlsx")
    check(fixture.isFile) { "B3 fixture not found at ${fixture.absolutePath}" }
    return fixture.readBytes()
}

private suspend fun upload(
    fileName: String,
    content: ByteArray,
    source: String = "B3_TRADES",
): HttpResponse = getBean<HttpClient>().post("http://localhost:8080/imports") {
    setBody(
        MultiPartFormDataContent(
            formData {
                append("source", source)
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

private suspend fun review(importId: Int) {
    getBean<HttpClient>().post("http://localhost:8080/imports/$importId/review").status shouldBe
        HttpStatusCode.OK
}

private suspend fun apply(importId: Int) {
    getBean<HttpClient>().post("http://localhost:8080/imports/$importId/apply").status shouldBe
        HttpStatusCode.OK
}

private suspend fun reviewAndApply(importId: Int) {
    review(importId)
    apply(importId)
}

private suspend fun rows(importId: Int) =
    json(getBean<HttpClient>().get("http://localhost:8080/imports/$importId/rows"))

private suspend fun json(response: HttpResponse) = ObjectMapper().readTree(response.bodyAsText())
