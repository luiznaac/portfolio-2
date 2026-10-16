package dev.agner.portfolio.httpapi.controller

import com.fasterxml.jackson.databind.ObjectMapper
import dev.agner.portfolio.httpapi.import.IImportParser
import dev.agner.portfolio.httpapi.import.ImportParserResolver
import dev.agner.portfolio.usecase.import.ImportService
import dev.agner.portfolio.usecase.import.model.Import
import dev.agner.portfolio.usecase.import.model.ImportRow
import dev.agner.portfolio.usecase.import.model.ImportRowPayload
import dev.agner.portfolio.usecase.import.model.ImportSource
import dev.agner.portfolio.usecase.import.model.ImportStatus
import dev.agner.portfolio.usecase.listedasset.model.TradeSide
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import java.math.BigDecimal

private const val ABC_HASH = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"

class ImportControllerTest : DescribeSpec({

    describe("listing imports") {

        it("returns 200 with the imports") {
            val service = mockk<ImportService>()
            coEvery { service.fetchAll() } returns listOf(stagedImport)

            testApplication {
                application { installRoutes(ImportController(service, ImportParserResolver(emptyList()))) }

                val response = client.get("/imports")

                response.status shouldBe HttpStatusCode.OK
                ObjectMapper().readTree(response.bodyAsText()).get(0).get("file_name").asText() shouldBe "neg.xlsx"
            }
        }
    }

    describe("staging a file") {

        it("returns 201 with the staged import and passes the source, name and content hash") {
            val service = mockk<ImportService>()
            val captured = slot<List<ImportRowPayload>>()
            coEvery {
                service.stage(ImportSource.B3_TRADES, "neg.xlsx", ABC_HASH, capture(captured))
            } returns stagedImport

            testApplication {
                application {
                    installRoutes(
                        ImportController(service, ImportParserResolver(listOf(StubParser(claims = true)))),
                    )
                }

                val response = client.post("/imports") {
                    setBody(
                        MultiPartFormDataContent(
                            formData {
                                append("source", "B3_TRADES")
                                append(
                                    "file",
                                    "abc".toByteArray(),
                                    Headers.build {
                                        append(HttpHeaders.ContentDisposition, "filename=\"neg.xlsx\"")
                                    },
                                )
                            },
                        ),
                    )
                }

                response.status shouldBe HttpStatusCode.Created
                ObjectMapper().readTree(response.bodyAsText()).get("status").asText() shouldBe "STAGED"
                captured.captured shouldBe emptyList()
            }
        }

        it("returns 400 with the canonical error body when no parser recognises the file") {
            val service = mockk<ImportService>()

            testApplication {
                application { installRoutes(ImportController(service, ImportParserResolver(emptyList()))) }

                val response = client.post("/imports") {
                    setBody(
                        MultiPartFormDataContent(
                            formData {
                                append("source", "B3_TRADES")
                                append(
                                    "file",
                                    "abc".toByteArray(),
                                    Headers.build {
                                        append(HttpHeaders.ContentDisposition, "filename=\"unknown.xlsx\"")
                                    },
                                )
                            },
                        ),
                    )
                }

                response.status shouldBe HttpStatusCode.BadRequest
                ObjectMapper().readTree(response.bodyAsText()).get("error").asText() shouldBe
                    "unsupported-import-file"
                coVerify(exactly = 0) { service.stage(any(), any(), any(), any()) }
            }
        }

        it("stages the rows a parser produced for the file") {
            val service = mockk<ImportService>()
            val parser = mockk<dev.agner.portfolio.httpapi.import.IImportParser>()
            coEvery { parser.shouldExecute("neg.xlsx", any()) } returns true
            coEvery { parser.parse("neg.xlsx", any()) } returns listOf(payload)
            coEvery { service.stage(any(), any(), any(), any()) } returns stagedImport

            testApplication {
                application { installRoutes(ImportController(service, ImportParserResolver(listOf(parser)))) }

                client.post("/imports") {
                    setBody(
                        MultiPartFormDataContent(
                            formData {
                                append("source", "B3_TRADES")
                                append(
                                    "file",
                                    "abc".toByteArray(),
                                    Headers.build {
                                        append(HttpHeaders.ContentDisposition, "filename=\"neg.xlsx\"")
                                    },
                                )
                            },
                        ),
                    )
                }

                coVerify(exactly = 1) {
                    service.stage(ImportSource.B3_TRADES, "neg.xlsx", ABC_HASH, listOf(payload))
                }
            }
        }
    }

    describe("fetching one import") {

        it("returns 200 with the import") {
            val service = mockk<ImportService>()
            coEvery { service.fetchById(1) } returns stagedImport

            testApplication {
                application { installRoutes(ImportController(service, ImportParserResolver(emptyList()))) }

                client.get("/imports/1").status shouldBe HttpStatusCode.OK
            }
        }

        it("returns 404 when the import does not exist") {
            val service = mockk<ImportService>()
            coEvery { service.fetchById(99) } returns null

            testApplication {
                application { installRoutes(ImportController(service, ImportParserResolver(emptyList()))) }

                client.get("/imports/99").status shouldBe HttpStatusCode.NotFound
            }
        }
    }

    describe("listing an import's rows") {

        it("returns 200 with the rows") {
            val service = mockk<ImportService>()
            coEvery { service.fetchRows(1) } returns listOf(row)

            testApplication {
                application { installRoutes(ImportController(service, ImportParserResolver(emptyList()))) }

                val response = client.get("/imports/1/rows")

                response.status shouldBe HttpStatusCode.OK
                ObjectMapper().readTree(response.bodyAsText()).get(0).get("ticker").asText() shouldBe "PETR4"
            }
        }

        it("returns 404 when the import does not exist") {
            val service = mockk<ImportService>()
            coEvery { service.fetchRows(99) } returns null

            testApplication {
                application { installRoutes(ImportController(service, ImportParserResolver(emptyList()))) }

                client.get("/imports/99/rows").status shouldBe HttpStatusCode.NotFound
            }
        }
    }

    describe("reviewing an import") {

        it("returns 200 with the reviewed import") {
            val service = mockk<ImportService>()
            coEvery { service.review(1) } returns reviewedImport

            testApplication {
                application { installRoutes(ImportController(service, ImportParserResolver(emptyList()))) }

                val response = client.post("/imports/1/review")

                response.status shouldBe HttpStatusCode.OK
                ObjectMapper().readTree(response.bodyAsText()).get("status").asText() shouldBe "REVIEWED"
            }
        }
    }

    describe("applying an import") {

        it("returns 200 with the applied import") {
            val service = mockk<ImportService>()
            coEvery { service.apply(1) } returns appliedImport

            testApplication {
                application { installRoutes(ImportController(service, ImportParserResolver(emptyList()))) }

                val response = client.post("/imports/1/apply")

                response.status shouldBe HttpStatusCode.OK
                ObjectMapper().readTree(response.bodyAsText()).get("status").asText() shouldBe "APPLIED"
            }
        }
    }
})

private val stagedImport = Import(
    id = 1,
    source = ImportSource.B3_TRADES,
    fileName = "neg.xlsx",
    fileHash = ABC_HASH,
    status = ImportStatus.STAGED,
    createdAt = LocalDateTime.parse("2026-10-14T10:00:00"),
)

private val reviewedImport = stagedImport.copy(status = ImportStatus.REVIEWED)
private val appliedImport = stagedImport.copy(status = ImportStatus.APPLIED)

private val row = ImportRow(
    id = 10,
    importId = 1,
    date = LocalDate.parse("2026-10-14"),
    side = TradeSide.BUY,
    venue = "VISTA",
    ticker = "PETR4",
    quantity = BigDecimal("100"),
    unitPrice = BigDecimal("48.92"),
    institutionName = "XP INVESTIMENTOS",
    error = null,
)

private val payload = ImportRowPayload(
    date = LocalDate.parse("2026-10-14"),
    side = TradeSide.BUY,
    venue = "VISTA",
    ticker = "PETR4",
    quantity = BigDecimal("100"),
    unitPrice = BigDecimal("48.92"),
    institutionName = "XP INVESTIMENTOS",
)

private class StubParser(
    private val claims: Boolean,
    private val rows: List<ImportRowPayload> = emptyList(),
) : IImportParser {
    override fun shouldExecute(fileName: String, content: ByteArray) = claims

    override fun parse(fileName: String, content: ByteArray) = rows
}
