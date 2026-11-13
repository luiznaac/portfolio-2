package dev.agner.portfolio.httpapi.controller

import com.fasterxml.jackson.databind.ObjectMapper
import dev.agner.portfolio.httpapi.import.IImportParser
import dev.agner.portfolio.httpapi.import.ImportParserResolver
import dev.agner.portfolio.usecase.import.ImportService
import dev.agner.portfolio.usecase.import.model.Import
import dev.agner.portfolio.usecase.import.model.ImportApplyResult
import dev.agner.portfolio.usecase.import.model.ImportAttempt
import dev.agner.portfolio.usecase.import.model.ImportDestination
import dev.agner.portfolio.usecase.import.model.ImportDestinationObject
import dev.agner.portfolio.usecase.import.model.ImportRow
import dev.agner.portfolio.usecase.import.model.ImportRowPayload
import dev.agner.portfolio.usecase.import.model.ImportSource
import dev.agner.portfolio.usecase.import.model.ImportSourceStatus
import dev.agner.portfolio.usecase.import.model.ImportStatus
import dev.agner.portfolio.usecase.import.model.StagingResult
import dev.agner.portfolio.usecase.listedasset.model.TradeSide
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
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
private val TRADES = ImportDestination(ImportDestinationObject.TRADES)

class ImportControllerTest : DescribeSpec({

    describe("listing imports") {

        it("returns 200 with the imports") {
            val service = mockk<ImportService>()
            coEvery { service.fetchAll(null, null) } returns listOf(stagedImport)

            testApplication {
                application { installRoutes(ImportController(service, ImportParserResolver(emptyList()))) }

                val response = client.get("/imports")

                response.status shouldBe HttpStatusCode.OK
                ObjectMapper().readTree(response.bodyAsText()).get(0).get("file_name").asText() shouldBe "neg.xlsx"
            }
        }

        it("forwards the additive status and source filters") {
            val service = mockk<ImportService>()
            coEvery { service.fetchAll(ImportStatus.STAGED, ImportSource.B3_TRADES) } returns
                listOf(stagedImport)

            testApplication {
                application { installRoutes(ImportController(service, ImportParserResolver(emptyList()))) }

                client.get("/imports?status=STAGED&source=B3_TRADES").status shouldBe HttpStatusCode.OK
            }
        }

        it("rejects an unknown filter value with the canonical 400") {
            val service = mockk<ImportService>()

            testApplication {
                application { installRoutes(ImportController(service, ImportParserResolver(emptyList()))) }

                val response = client.get("/imports?status=NOPE")

                response.status shouldBe HttpStatusCode.BadRequest
                ObjectMapper().readTree(response.bodyAsText()).get("error").asText() shouldBe
                    "invalid-parameter"
                coVerify(exactly = 0) { service.fetchAll(any(), any()) }
            }
        }
    }

    describe("reading the per-source status") {

        it("returns the latest attempt, last success, pending count and failure per source") {
            val service = mockk<ImportService>()
            coEvery { service.fetchSourceStatus() } returns listOf(
                ImportSourceStatus(
                    source = ImportSource.B3_TRADES,
                    lastAttempt = ImportAttempt(
                        4,
                        "negocios-falho.xlsx",
                        ImportStatus.FAILED,
                        LocalDateTime.parse("2026-10-20T09:00:00"),
                    ),
                    lastSuccess = ImportAttempt(
                        1,
                        "neg.xlsx",
                        ImportStatus.APPLIED,
                        LocalDateTime.parse("2026-10-14T10:00:00"),
                    ),
                    pending = 2,
                    failure = ImportAttempt(
                        4,
                        "negocios-falho.xlsx",
                        ImportStatus.FAILED,
                        LocalDateTime.parse("2026-10-20T09:00:00"),
                    ),
                ),
            )

            testApplication {
                application { installRoutes(ImportController(service, ImportParserResolver(emptyList()))) }

                val response = client.get("/imports/sources")

                response.status shouldBe HttpStatusCode.OK
                val body = ObjectMapper().readTree(response.bodyAsText()).get(0)
                body.get("source").asText() shouldBe "B3_TRADES"
                body.get("pending").asInt() shouldBe 2
                body.get("last_attempt").get("import_id").asInt() shouldBe 4
                body.get("last_attempt").get("file_name").asText() shouldBe "negocios-falho.xlsx"
                body.get("last_attempt").get("status").asText() shouldBe "FAILED"
                body.get("last_attempt").get("created_at").asText() shouldBe "2026-10-20T09:00"
                body.get("last_success").get("import_id").asInt() shouldBe 1
                body.get("failure").get("import_id").asInt() shouldBe 4
            }
        }

        it("omits the failure and last success when the source has neither") {
            val service = mockk<ImportService>()
            coEvery { service.fetchSourceStatus() } returns listOf(
                ImportSourceStatus(
                    source = ImportSource.B3_POSITION,
                    lastAttempt = ImportAttempt(
                        7,
                        "posicao.xlsx",
                        ImportStatus.STAGED,
                        LocalDateTime.parse("2026-10-21T09:00:00"),
                    ),
                    lastSuccess = null,
                    pending = 1,
                    failure = null,
                ),
            )

            testApplication {
                application { installRoutes(ImportController(service, ImportParserResolver(emptyList()))) }

                val body = ObjectMapper()
                    .readTree(client.get("/imports/sources").bodyAsText())
                    .get(0)

                body.has("failure") shouldBe false
                body.has("last_success") shouldBe false
                body.get("pending").asInt() shouldBe 1
            }
        }
    }

    describe("staging a file") {

        it("returns 201 with the staged import and passes the source, name, hash and destination") {
            val service = mockk<ImportService>()
            val parser = mockk<IImportParser>()
            val captured = slot<List<ImportRowPayload>>()
            coEvery {
                service.stage(
                    ImportSource.B3_TRADES,
                    "neg.xlsx",
                    ABC_HASH,
                    capture(captured),
                    TRADES,
                    any(),
                )
            } returns StagingResult(stagedImport, created = true)
            coEvery { parser.shouldExecute("neg.xlsx", any()) } returns true
            coEvery { parser.parse("neg.xlsx", any()) } returns emptyList()

            testApplication {
                application { installRoutes(ImportController(service, ImportParserResolver(listOf(parser)))) }

                val response = client.post("/imports") {
                    setBody(uploadBody())
                }

                response.status shouldBe HttpStatusCode.Created
                ObjectMapper().readTree(response.bodyAsText()).get("status").asText() shouldBe "STAGED"
                captured.captured shouldBe emptyList()
            }
        }

        it("returns 200 with the existing import untouched when the hash is already known") {
            val service = mockk<ImportService>()
            val parser = mockk<IImportParser>()
            coEvery { service.stage(any(), any(), any(), any(), any(), any()) } returns
                StagingResult(appliedImport, created = false)
            coEvery { parser.shouldExecute("neg.xlsx", any()) } returns true
            coEvery { parser.parse("neg.xlsx", any()) } returns emptyList()

            testApplication {
                application { installRoutes(ImportController(service, ImportParserResolver(listOf(parser)))) }

                val response = client.post("/imports") {
                    setBody(uploadBody())
                }

                response.status shouldBe HttpStatusCode.OK
                ObjectMapper().readTree(response.bodyAsText()).get("status").asText() shouldBe "APPLIED"
            }
        }

        it("returns 400 with the canonical error body when no parser recognises the file") {
            val service = mockk<ImportService>()

            testApplication {
                application { installRoutes(ImportController(service, ImportParserResolver(emptyList()))) }

                val response = client.post("/imports") {
                    setBody(uploadBody(fileName = "unknown.xlsx"))
                }

                response.status shouldBe HttpStatusCode.BadRequest
                ObjectMapper().readTree(response.bodyAsText()).get("error").asText() shouldBe
                    "unsupported-import-file"
                coVerify(exactly = 0) { service.stage(any(), any(), any(), any(), any(), any()) }
            }
        }

        it("returns 400 when the destination is missing") {
            val service = mockk<ImportService>()
            val parser = mockk<IImportParser>()
            coEvery { parser.shouldExecute("neg.xlsx", any()) } returns true
            coEvery { parser.parse("neg.xlsx", any()) } returns emptyList()

            testApplication {
                application { installRoutes(ImportController(service, ImportParserResolver(listOf(parser)))) }

                val response = client.post("/imports") {
                    setBody(uploadBody(destination = null))
                }

                response.status shouldBe HttpStatusCode.BadRequest
                ObjectMapper().readTree(response.bodyAsText()).get("error").asText() shouldBe
                    "invalid-parameter"
                coVerify(exactly = 0) { service.stage(any(), any(), any(), any(), any(), any()) }
            }
        }

        it("stages the rows a parser produced for the file") {
            val service = mockk<ImportService>()
            val parser = mockk<IImportParser>()
            coEvery { parser.shouldExecute("neg.xlsx", any()) } returns true
            coEvery { parser.parse("neg.xlsx", any()) } returns listOf(payload)
            coEvery { service.stage(any(), any(), any(), any(), any(), any()) } returns
                StagingResult(stagedImport, created = true)

            testApplication {
                application { installRoutes(ImportController(service, ImportParserResolver(listOf(parser)))) }

                client.post("/imports") {
                    setBody(uploadBody())
                }

                coVerify(exactly = 1) {
                    service.stage(
                        ImportSource.B3_TRADES,
                        "neg.xlsx",
                        ABC_HASH,
                        listOf(payload),
                        TRADES,
                        "abc".toByteArray(),
                    )
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

    describe("downloading an import's retained original") {

        it("returns the bytes with the file name as an attachment") {
            val service = mockk<ImportService>()
            coEvery { service.fetchById(1) } returns stagedImport
            coEvery { service.fetchOriginal(1) } returns "workbook".toByteArray()

            testApplication {
                application { installRoutes(ImportController(service, ImportParserResolver(emptyList()))) }

                val response = client.get("/imports/1/file")

                response.status shouldBe HttpStatusCode.OK
                response.bodyAsBytes().decodeToString() shouldBe "workbook"
                response.headers[HttpHeaders.ContentDisposition] shouldBe
                    "attachment; filename=neg.xlsx"
            }
        }

        it("returns 404 when the import was staged without an original") {
            val service = mockk<ImportService>()
            coEvery { service.fetchById(1) } returns stagedImport.copy(hasOriginal = false)
            coEvery { service.fetchOriginal(1) } returns null

            testApplication {
                application { installRoutes(ImportController(service, ImportParserResolver(emptyList()))) }

                client.get("/imports/1/file").status shouldBe HttpStatusCode.NotFound
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

        it("returns 200 with the applied record and its per-row counts") {
            val service = mockk<ImportService>()
            coEvery { service.apply(1) } returns
                ImportApplyResult(appliedImport, applied = 2, rejected = 1, skippedDuplicate = 1)

            testApplication {
                application { installRoutes(ImportController(service, ImportParserResolver(emptyList()))) }

                val response = client.post("/imports/1/apply")

                response.status shouldBe HttpStatusCode.OK
                val body = ObjectMapper().readTree(response.bodyAsText())
                body.get("record").get("status").asText() shouldBe "APPLIED"
                body.get("applied").asInt() shouldBe 2
                body.get("rejected").asInt() shouldBe 1
                body.get("skipped_duplicate").asInt() shouldBe 1
            }
        }

        it("returns 404 when the import does not exist") {
            val service = mockk<ImportService>()
            coEvery { service.apply(99) } returns null

            testApplication {
                application { installRoutes(ImportController(service, ImportParserResolver(emptyList()))) }

                client.post("/imports/99/apply").status shouldBe HttpStatusCode.NotFound
            }
        }
    }
})

private fun uploadBody(
    fileName: String = "neg.xlsx",
    destination: String? = "TRADES",
) = MultiPartFormDataContent(
    formData {
        append("source", "B3_TRADES")
        if (destination != null) append("destination_object", destination)
        append(
            "file",
            "abc".toByteArray(),
            Headers.build { append(HttpHeaders.ContentDisposition, "filename=\"$fileName\"") },
        )
    },
)

private val stagedImport = Import(
    id = 1,
    source = ImportSource.B3_TRADES,
    fileName = "neg.xlsx",
    fileHash = ABC_HASH,
    status = ImportStatus.STAGED,
    destination = TRADES,
    hasOriginal = true,
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
