package dev.agner.portfolio.httpapi.configuration

import dev.agner.portfolio.usecase.commons.DomainException
import dev.agner.portfolio.usecase.configuration.JsonMapper
import dev.agner.portfolio.usecase.strategy.StrategyNotFoundException
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.jackson.JacksonConverter
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import java.io.File

// The golden error body is committed once at contracts/error-response.json and also asserted by
// the frontend's src/api/contract.test.ts. Changing the shape on the backend breaks this test;
// changing it in the fixture breaks the frontend's typecheck.
class DomainExceptionContractTest : DescribeSpec({

    describe("the golden error-response contract") {

        it("serialises a domain exception to exactly the committed fixture") {
            val expected = JsonMapper.mapper.readTree(contractsDir().resolve("error-response.json"))

            testApplication {
                application { installThrowingRoute(StrategyNotFoundException(42)) }

                val response = client.get("/boom")
                val actual = JsonMapper.mapper.readTree(response.bodyAsText())

                response.status shouldBe HttpStatusCode.NotFound
                actual shouldBe expected
            }
        }
    }
})

private fun contractsDir(): File =
    File(
        System.getProperty("contractsDir")
            ?: error("contractsDir system property is not set; see backend/build.gradle.kts"),
    )

private fun Application.installThrowingRoute(exception: DomainException) {
    install(ContentNegotiation) {
        register(ContentType.Application.Json, JacksonConverter(JsonMapper.mapper))
    }

    installDomainExceptionHandler(DefaultDomainExceptionStatusMapper())

    routing {
        get("/boom") {
            throw exception
        }
    }
}
