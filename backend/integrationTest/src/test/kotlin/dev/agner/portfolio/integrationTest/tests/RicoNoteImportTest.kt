package dev.agner.portfolio.integrationTest.tests

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import dev.agner.portfolio.integrationTest.config.ClockMock
import dev.agner.portfolio.integrationTest.config.IntegrationTest
import dev.agner.portfolio.integrationTest.config.appBaseUrl
import dev.agner.portfolio.integrationTest.helpers.getBean
import dev.agner.portfolio.integrationTest.helpers.nonRicoPdfFixture
import dev.agner.portfolio.integrationTest.helpers.ricoNotaFixture
import dev.agner.portfolio.usecase.broker.repository.IBrokerAssetAliasRepository
import dev.agner.portfolio.usecase.listedasset.model.TradeSide
import dev.agner.portfolio.usecase.listedasset.repository.ITradeRepository
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.mockk.every
import java.time.Instant

// The Rico nota's end-to-end path: the PDF is claimed by its header, one import record carries
// every note's rows, the seeded Broker Asset Alias resolves the descriptions, and apply writes the
// valid rows as gross-priced trades while the unresolved and misread rows stay rejected.
@IntegrationTest
class RicoNoteImportTest : StringSpec({

    beforeTest {
        every { ClockMock.clock.instant() } returns Instant.parse("2026-10-14T10:00:00Z")
    }

    "the Rico nota stages one record, resolves the seeded descriptions and applies the valid rows" {
        val uploaded = upload("nota-de-corretagem.pdf", ricoNotaFixture())
        uploaded.status shouldBe HttpStatusCode.Created
        val record = json(uploaded)
        record.get("source").asText() shouldBe "RICO_NOTES"
        record.get("destination").get("object_type").asText() shouldBe "TRADES"
        val id = record.get("id").asInt()

        val rows = rows(id)
        rows.size() shouldBe 8
        rows.map { it.get("note_number").asText() } shouldBe
            listOf("123456", "123456", "123456", "123457", "123457", "123457", "123457", "123457")
        rows.map { it.get("institution_name").asText() }.toSet() shouldBe setOf("RICO INVESTIMENTOS")
        rows.map { it.get("raw_description").asText() } shouldBe listOf(
            "FII BCFF11 CI",
            "FII KNRI11 ER",
            "FII VISC11 NM",
            "FII RBR PROP DM 88,27",
            "ITAUUNIBANCO PN N1",
            "ATIVO INEXISTENTE XPTO",
            "ENGIE BRASIL ON NM",
            "FLEURY ON NM",
        )
        rows.map { it.get("ticker")?.takeIf { ticker -> !ticker.isNull }?.asText() } shouldBe
            listOf("BCFF11", "KNRI11", "VISC11", "RBRP12", "ITSA4", null, "EGIE3", "FLRY3")
        rows.map { it.get("situation").asText() } shouldBe
            listOf("new", "new", "new", "new", "new", "error", "error", "error")
        rows[5].get("error").asText() shouldBe
            "Description 'ATIVO INEXISTENTE XPTO' did not resolve to a ticker"

        review(id)
        val applied = json(apply(id))
        applied.get("applied").asInt() shouldBe 5
        applied.get("rejected").asInt() shouldBe 3

        val trades = getBean<ITradeRepository>().fetchAll().sortedBy { it.ticker }
        trades.map { it.ticker to it.quantity.stripTrailingZeros().toPlainString() } shouldBe listOf(
            "BCFF11" to "100",
            "ITSA4" to "100",
            "KNRI11" to "7",
            "RBRP12" to "10",
            "VISC11" to "50",
        )
        trades.single { it.ticker == "VISC11" }.side shouldBe TradeSide.SELL
        trades.single { it.ticker == "RBRP12" }.side shouldBe TradeSide.SELL
    }

    "the owner confirms an unresolved description and its row then applies" {
        val id = json(upload("nota-de-corretagem.pdf", ricoNotaFixture())).get("id").asInt()
        val unresolved = rows(id).single {
            it.get("raw_description").asText() == "ATIVO INEXISTENTE XPTO"
        }
        unresolved.get("ticker") shouldBe null

        val confirmed = confirmTicker(
            importId = id,
            rowId = unresolved.get("id").asInt(),
            ticker = "XPTO3",
        )

        confirmed.status shouldBe HttpStatusCode.OK
        val confirmedRow = json(confirmed)
        confirmedRow.get("ticker").asText() shouldBe "XPTO3"
        confirmedRow.get("error") shouldBe null
        confirmedRow.get("situation").asText() shouldBe "new"
        rows(id).single { it.get("raw_description").asText() == "ATIVO INEXISTENTE XPTO" }
            .get("ticker").asText() shouldBe "XPTO3"

        // The answer is the broker's own alias, not a second global: the next note resolves through it.
        val alias = getBean<IBrokerAssetAliasRepository>()
            .fetchByDescription("ATIVO INEXISTENTE XPTO")
            .single()
        alias.ticker shouldBe "XPTO3"
        alias.institutionId shouldNotBe null

        review(id)
        val applied = json(apply(id))
        applied.get("applied").asInt() shouldBe 6
        applied.get("rejected").asInt() shouldBe 2

        val trade = getBean<ITradeRepository>().fetchAll().single { it.ticker == "XPTO3" }
        trade.quantity.stripTrailingZeros().toPlainString() shouldBe "5"
        trade.side shouldBe TradeSide.BUY
    }

    "an unrecognised PDF is rejected with the canonical 4xx body and stages nothing" {
        val before = imports()
        val rejected = upload("relatorio.pdf", nonRicoPdfFixture(), source = "RICO_NOTES")

        rejected.status shouldBe HttpStatusCode.BadRequest
        json(rejected).get("error").asText() shouldBe "unsupported-import-file"
        imports() shouldBe before
    }
})

private suspend fun upload(
    fileName: String,
    content: ByteArray,
    source: String = "RICO_NOTES",
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

private suspend fun confirmTicker(importId: Int, rowId: Int, ticker: String): HttpResponse =
    getBean<HttpClient>().post("${appBaseUrl()}/imports/$importId/rows/$rowId/ticker") {
        contentType(ContentType.Application.Json)
        setBody(mapOf("ticker" to ticker))
    }

private suspend fun review(importId: Int) {
    getBean<HttpClient>().post("${appBaseUrl()}/imports/$importId/review").status shouldBe
        HttpStatusCode.OK
}

private suspend fun apply(importId: Int): HttpResponse =
    getBean<HttpClient>().post("${appBaseUrl()}/imports/$importId/apply")

private suspend fun rows(importId: Int): JsonNode =
    json(getBean<HttpClient>().get("${appBaseUrl()}/imports/$importId/rows"))

private suspend fun imports(): JsonNode =
    json(getBean<HttpClient>().get("${appBaseUrl()}/imports"))

private suspend fun json(response: HttpResponse): JsonNode =
    ObjectMapper().readTree(response.bodyAsText())
