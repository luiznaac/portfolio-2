package dev.agner.portfolio.httpapi.controller

import com.fasterxml.jackson.databind.ObjectMapper
import dev.agner.portfolio.usecase.institution.InstitutionService
import dev.agner.portfolio.usecase.institution.model.Institution
import dev.agner.portfolio.usecase.institution.model.InstitutionCreation
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk

class InstitutionControllerTest : DescribeSpec({

    describe("listing institutions") {

        it("returns 200 with the institutions") {
            val service = mockk<InstitutionService>()
            coEvery { service.fetchAll() } returns listOf(xp)

            testApplication {
                application { installRoutes(InstitutionController(service)) }

                val response = client.get("/institutions")

                response.status shouldBe HttpStatusCode.OK
                ObjectMapper().readTree(response.bodyAsText()).get(0).get("name").asText() shouldBe "XP INVESTIMENTOS"
            }
        }
    }

    describe("creating an institution") {

        it("returns 201 with the created institution") {
            val service = mockk<InstitutionService>()
            coEvery { service.create(InstitutionCreation("XP INVESTIMENTOS")) } returns xp

            testApplication {
                application { installRoutes(InstitutionController(service)) }

                val response = client.post("/institutions") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"name":"XP INVESTIMENTOS"}""")
                }

                response.status shouldBe HttpStatusCode.Created
                coVerify(exactly = 1) { service.create(InstitutionCreation("XP INVESTIMENTOS")) }
            }
        }
    }

    describe("renaming an institution") {

        it("returns 200 with the surviving institution") {
            val service = mockk<InstitutionService>()
            coEvery { service.rename(9, "NOVA CORRETORA") } returns Institution(9, "NOVA CORRETORA")

            testApplication {
                application { installRoutes(InstitutionController(service)) }

                val response = client.put("/institutions/9") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"name":"NOVA CORRETORA"}""")
                }

                response.status shouldBe HttpStatusCode.OK
            }
        }

        it("returns 404 when the institution does not exist") {
            val service = mockk<InstitutionService>()
            coEvery { service.rename(9, any()) } returns null

            testApplication {
                application { installRoutes(InstitutionController(service)) }

                val response = client.put("/institutions/9") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"name":"NOVA CORRETORA"}""")
                }

                response.status shouldBe HttpStatusCode.NotFound
            }
        }
    }

    describe("deleting an institution") {

        it("returns 204 and deletes the institution") {
            val service = mockk<InstitutionService>()
            coEvery { service.delete(9) } returns Unit

            testApplication {
                application { installRoutes(InstitutionController(service)) }

                val response = client.delete("/institutions/9")

                response.status shouldBe HttpStatusCode.NoContent
                coVerify(exactly = 1) { service.delete(9) }
            }
        }
    }
})

private val xp = Institution(id = 1, name = "XP INVESTIMENTOS")
