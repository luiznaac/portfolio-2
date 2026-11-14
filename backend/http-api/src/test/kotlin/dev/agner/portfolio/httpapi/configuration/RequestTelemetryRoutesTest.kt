package dev.agner.portfolio.httpapi.configuration

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import dev.agner.portfolio.httpapi.controller.ControllerTemplate
import dev.agner.portfolio.httpapi.controller.installRoutes
import dev.agner.portfolio.usecase.commons.MetricCatalog
import dev.agner.portfolio.usecase.commons.TelemetryAttributes
import dev.agner.portfolio.usecase.monthlyclose.PendingTransfersException
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.metrics.SdkMeterProvider
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader
import org.slf4j.LoggerFactory

class RequestTelemetryRoutesTest : DescribeSpec({

    lateinit var events: ListAppender<ILoggingEvent>
    lateinit var logger: Logger

    beforeTest {
        val context = LoggerFactory.getILoggerFactory() as LoggerContext
        logger = context.getLogger(RequestTelemetry::class.java)
        events = ListAppender()
        events.context = context
        events.start()
        logger.addAppender(events)
    }

    afterTest {
        logger.detachAppender(events)
    }

    describe("the request telemetry over the real controller test support") {

        it("keeps the framework route template on a known parameterized route") {
            val reader = InMemoryMetricReader.create()

            testApplication {
                app(reader) {
                    get("/bonds/{id}") { call.respond(HttpStatusCode.OK) }
                }

                client.get("/bonds/42").status shouldBe HttpStatusCode.OK

                val duration = reader.collectAllMetrics().single { it.name == MetricCatalog.HTTP_SERVER_DURATION }
                val point = duration.histogramData.points.single()
                point.attributes.get(AttributeKey.stringKey(TelemetryAttributes.HTTP_ROUTE)) shouldBe "/bonds/{id}"
                point.attributes.get(AttributeKey.stringKey(TelemetryAttributes.URL_PATH)) shouldBe null
            }
        }

        it("keeps a framework-rejected request a WARN 4xx through the real handler") {
            val reader = InMemoryMetricReader.create()

            testApplication {
                app(reader) {
                    get("/trades") { throw BadRequestException("malformed body") }
                }

                val response = client.get("/trades")

                response.status shouldBe HttpStatusCode.BadRequest
                val event = events.list.single()
                event.level shouldBe Level.WARN
                event.message shouldBe "http.request.failed"
                event.throwableProxy.shouldBeNull()
                event.mdcPropertyMap[TelemetryAttributes.ERROR_TYPE] shouldBe TelemetryAttributes.INVALID_ARGUMENT
                event.mdcPropertyMap[TelemetryAttributes.HTTP_RESPONSE_STATUS_CODE] shouldBe "400"
            }
        }

        it("emits one ERROR with exception semconv for an unexpected failure through the real handler") {
            val reader = InMemoryMetricReader.create()

            testApplication {
                app(reader) {
                    get("/trades") { throw IllegalStateException("boom") }
                }

                val response = client.get("/trades")

                response.status shouldBe HttpStatusCode.InternalServerError
                val event = events.list.single()
                event.level shouldBe Level.ERROR
                event.message shouldBe "http.request.failed"
                event.throwableProxy.shouldNotBeNull().className shouldBe "java.lang.IllegalStateException"
                event.mdcPropertyMap[TelemetryAttributes.ERROR_TYPE] shouldBe "IllegalStateException"
                event.mdcPropertyMap[TelemetryAttributes.HTTP_RESPONSE_STATUS_CODE] shouldBe "500"
            }
        }

        it("emits the conflict response as one WARN with the domain code and no stacktrace") {
            val reader = InMemoryMetricReader.create()

            testApplication {
                app(reader) {
                    get("/months/close") { throw PendingTransfersException(emptyList()) }
                }

                val response = client.get("/months/close") {
                    header(TelemetryAttributes.REQUEST_ID_HEADER, "close-request-1")
                }

                response.status shouldBe HttpStatusCode.Conflict
                response.headers[TelemetryAttributes.REQUEST_ID_HEADER] shouldBe "close-request-1"
                val event = events.list.single()
                event.level shouldBe Level.WARN
                event.message shouldBe "http.request.failed"
                event.throwableProxy.shouldBeNull()
                event.mdcPropertyMap[TelemetryAttributes.ERROR_TYPE] shouldBe "pending-transfers"
                event.mdcPropertyMap[TelemetryAttributes.HTTP_RESPONSE_STATUS_CODE] shouldBe "409"
                event.mdcPropertyMap[TelemetryAttributes.HTTP_ROUTE] shouldBe "/months/close"
                event.mdcPropertyMap[TelemetryAttributes.REQUEST_ID] shouldBe "close-request-1"
            }
        }
    }
})

private fun ApplicationTestBuilder.app(reader: InMemoryMetricReader, routes: Routing.() -> Unit) {
    val sdk = OpenTelemetrySdk.builder()
        .setMeterProvider(SdkMeterProvider.builder().registerMetricReader(reader).build())
        .build()
    val telemetry = RequestTelemetry(
        openTelemetry = sdk,
        meter = sdk.meterBuilder("test-service").build(),
        statusMapper = DefaultDomainExceptionStatusMapper(),
    )
    val controller = object : ControllerTemplate {
        override fun routes(): Routing.() -> Unit = routes
    }
    application {
        telemetry.install(this)
        installRoutes(controller)
    }
}
