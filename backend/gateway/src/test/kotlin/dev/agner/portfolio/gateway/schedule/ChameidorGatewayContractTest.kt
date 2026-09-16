package dev.agner.portfolio.gateway.schedule

import dev.agner.portfolio.usecase.configuration.JsonMapper
import dev.agner.portfolio.usecase.schedule.model.ScheduleContext
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.jackson.JacksonConverter
import java.io.File

// The contract is chameidor's HTTP surface; this test pins its own copy of chameidor's
// contracts/tasks-one-time-request.json and asserts the payload ChameidorGateway really
// sends against the pin, so drift on either side fails here (chameidor repo, contracts/).
class ChameidorGatewayContractTest : StringSpec({

    "sends exactly the pinned one-time task request" {
        val fixture = contractsDir().resolve("chameidor/tasks-one-time-request.json")

        val request = captureRequest { gateway ->
            gateway.scheduleOneTimeJob(ScheduleContext(host = CALLBACK_HOST, endpoint = CALLBACK_ENDPOINT))
        }

        request.method shouldBe HttpMethod.Post
        request.url.toString() shouldBe "$CHAMEIDOR_HOST/tasks/one-time"
        request.headers[HttpHeaders.Authorization] shouldBe "Bearer $CHAMEIDOR_TOKEN"
        request.headers["X-External-System"] shouldBe null
        request.body.contentType?.match(ContentType.Application.Json) shouldBe true
        JsonMapper.mapper.readTree(request.bodyText()) shouldBe JsonMapper.mapper.readTree(fixture)
    }
})

private const val CHAMEIDOR_HOST = "http://localhost:3000"
private const val CHAMEIDOR_TOKEN = "dev-portfolio-fixture-token"
private const val CALLBACK_HOST = "portfolio:8080"
private const val CALLBACK_ENDPOINT = "/consolidations/BOND/1"

private suspend fun captureRequest(block: suspend (ChameidorGateway) -> Unit): HttpRequestData {
    lateinit var captured: HttpRequestData
    val engine = MockEngine { request ->
        captured = request
        respond("{}", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
    }
    val client = HttpClient(engine) {
        install(ContentNegotiation) {
            register(ContentType.Application.Json, JacksonConverter(JsonMapper.mapper))
        }
    }

    block(ChameidorGateway(client, CHAMEIDOR_HOST, CHAMEIDOR_TOKEN))
    return captured
}

private suspend fun HttpRequestData.bodyText(): String = body.toByteArray().decodeToString()

private fun contractsDir(): File =
    File(
        System.getProperty("contractsDir")
            ?: error("contractsDir system property is not set; see backend/build.gradle.kts"),
    )
