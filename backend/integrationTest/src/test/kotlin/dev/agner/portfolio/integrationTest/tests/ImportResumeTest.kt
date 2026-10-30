package dev.agner.portfolio.integrationTest.tests

import com.fasterxml.jackson.databind.ObjectMapper
import dev.agner.portfolio.integrationTest.config.ClockMock
import dev.agner.portfolio.integrationTest.config.IntegrationTest
import dev.agner.portfolio.integrationTest.helpers.getBean
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
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.ByteArrayOutputStream
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
        val file = negotiation(listOf(petr4))
        val first = upload("neg.xlsx", file)
        first.status shouldBe HttpStatusCode.Created
        val id = json(first).get("id").asInt()

        reupload(file, id, status = "STAGED")
        review(id)
        reupload(file, id, status = "REVIEWED")
        apply(id)
        // The request source is ignored on a hash match: the file identity wins.
        reupload(file, id, status = "APPLIED", source = "B3_POSITION")

        rows(id).map { it.get("situation").asText() } shouldBe listOf("duplicate_in_ledger")
    }

    "the rows carry the four server situations and apply writes the new suffix" {
        val seeded = upload("neg.xlsx", negotiation(listOf(petr4)))
        reviewAndApply(json(seeded).get("id").asInt())

        val second = upload("neg.xlsx", negotiation(listOf(petr4, vale3, vale3, missingDate)))
        second.status shouldBe HttpStatusCode.Created
        val secondId = json(second).get("id").asInt()

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

private suspend fun reupload(
    file: ByteArray,
    id: Int,
    status: String,
    source: String = "B3_TRADES",
) {
    val again = upload("neg.xlsx", file, source)
    again.status shouldBe HttpStatusCode.OK
    json(again).also {
        it.get("id").asInt() shouldBe id
        it.get("status").asText() shouldBe status
        it.get("source").asText() shouldBe "B3_TRADES"
    }
}

private const val INSTITUTION = "XP INVESTIMENTOS"

private val petr4 = listOf("2026-10-14", "C", "VISTA", "PETR4", 100, 48.92, 4892.00, INSTITUTION)
private val vale3 = listOf("2026-10-15", "C", "VISTA", "VALE3", 10, 60.00, 600.00, INSTITUTION)
private val missingDate = listOf("", "C", "VISTA", "ITUB4", 5, 30.00, 150.00, INSTITUTION)

private val HEADERS = listOf(
    "Data",
    "Tipo de Movimentação",
    "Mercado",
    "Ticker",
    "Quantidade",
    "Preço Unitário",
    "Valor Bruto",
    "Instituição",
)

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

// A minimal B3 Negociação workbook: the header set the parser claims plus the rows under test.
private fun negotiation(rows: List<List<Any?>>): ByteArray {
    XSSFWorkbook().use { workbook ->
        val sheet = workbook.createSheet("Sheet1")
        val header = sheet.createRow(0)
        HEADERS.forEachIndexed { index, name -> header.createCell(index).setCellValue(name) }
        rows.forEachIndexed { rowIndex, values ->
            val row = sheet.createRow(rowIndex + 1)
            values.forEachIndexed { column, value ->
                when (value) {
                    null -> Unit
                    is String -> row.createCell(column).setCellValue(value)
                    is Int -> row.createCell(column).setCellValue(value.toDouble())
                    is Double -> row.createCell(column).setCellValue(value)
                    else -> error("Unsupported fixture cell: $value")
                }
            }
        }
        return ByteArrayOutputStream().use { out ->
            workbook.write(out)
            out.toByteArray()
        }
    }
}
