package dev.agner.portfolio.httpapi.controller

import dev.agner.portfolio.httpapi.configuration.DefaultDomainExceptionStatusMapper
import dev.agner.portfolio.usecase.commons.DomainException
import dev.agner.portfolio.usecase.configuration.JsonMapper
import dev.agner.portfolio.usecase.index.IndexValueService
import dev.agner.portfolio.usecase.index.model.IndexId
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.jackson.JacksonConverter
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.plugins.statuspages.exception
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk

class IndexValueControllerTest : DescribeSpec({

    describe("listing index values") {

        it("returns 200 with the values for a valid index id") {
            val service = mockk<IndexValueService>()
            coEvery { service.fetchAllBy(IndexId.CDI) } returns emptyList()

            testApplication {
                application { installController(IndexValueController(service)) }

                val response = client.get("/indexes/CDI/values")

                response.status shouldBe HttpStatusCode.OK
                response.bodyAsText() shouldBe "[]"
                coVerify(exactly = 1) { service.fetchAllBy(IndexId.CDI) }
            }
        }
    }

    describe("an unknown index id") {

        it("returns 500 on GET and never reaches the service") {
            val service = mockk<IndexValueService>()

            testApplication {
                application { installController(IndexValueController(service)) }

                val response = client.get("/indexes/XPTO/values")

                // IndexId.valueOf throws an IllegalArgumentException that nothing maps yet, so the
                // request dies in the pipeline. This pins today's accidental 500; a later fix
                // should turn it into a 400 with error "invalid-parameter", like requiredInt.
                response.status shouldBe HttpStatusCode.InternalServerError
                coVerify(exactly = 0) { service.fetchAllBy(any()) }
            }
        }

        it("returns 500 on hydrate and never reaches the service") {
            val service = mockk<IndexValueService>()

            testApplication {
                application { installController(IndexValueController(service)) }

                val response = client.post("/indexes/XPTO/values/hydrate")

                // Same unmapped IllegalArgumentException as the GET above; expected to become a
                // 400 "invalid-parameter" once that mapping is added.
                response.status shouldBe HttpStatusCode.InternalServerError
                coVerify(exactly = 0) { service.hydrateIndexValues(any()) }
            }
        }
    }
})

private data class IndexValueTestApiError(val error: String, val message: String, val detail: String)

private fun Application.installController(controller: IndexValueController) {
    install(ContentNegotiation) {
        register(ContentType.Application.Json, JacksonConverter(JsonMapper.mapper))
    }

    install(StatusPages) {
        exception<DomainException> { call, cause ->
            call.respond(
                DefaultDomainExceptionStatusMapper().statusFor(cause),
                IndexValueTestApiError(
                    error = cause.error,
                    message = cause.userMessage,
                    detail = cause.detail,
                ),
            )
        }
    }

    routing(controller.routes())
}
