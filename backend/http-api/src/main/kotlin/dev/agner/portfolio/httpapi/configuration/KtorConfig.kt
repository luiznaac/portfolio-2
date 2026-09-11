package dev.agner.portfolio.httpapi.configuration

import com.fasterxml.jackson.databind.ObjectMapper
import dev.agner.portfolio.httpapi.controller.ControllerTemplate
import dev.agner.portfolio.usecase.commons.defaultScale
import dev.agner.portfolio.usecase.commons.disgustingLocalDateFormat
import dev.agner.portfolio.usecase.commons.logger
import dev.agner.portfolio.usecase.strategy.StrategyEditionAlreadyExistsException
import dev.agner.portfolio.usecase.strategy.StrategyNotFoundException
import dev.agner.portfolio.usecase.strategy.parser.StrategyReportParseException
import dev.agner.portfolio.usecase.upload.model.UploadOrder
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.jackson.JacksonConverter
import io.ktor.server.application.install
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.engine.stop
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.plugins.statuspages.exception
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import kotlinx.datetime.LocalDate
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration

@Configuration
class KtorConfig(
    private val routes: Set<ControllerTemplate>,
    private val mapper: ObjectMapper,
    @Value("\${ktor.wait}") wait: Boolean,
    @Value("\${ktor.port}") port: Int,
) {

    private val server: EmbeddedServer<*, *>

    init {
        val log = logger()

        server = embeddedServer(Netty, port = port) {
            routes.onEach {
                log.info("Initializing route: ${it::class.java.simpleName}")
                routing(it.routes())
            }

            install(ContentNegotiation) {
                register(ContentType.Application.Json, JacksonConverter(mapper))
                register(ContentType.Application.Xlsx, XlsxConverter(mapper)) {
                    register<UploadOrder>(
                        DataDef("Data", "date") { LocalDate.parse(this!!, disgustingLocalDateFormat) },
                        DataDef("Descrição", "action") { UploadOrder.Action.fromValue(this!!) },
                        DataDef("Preço"),
                        DataDef("Quantidade"),
                        DataDef("Valor", "amount") { this!!.sanitizeCurrency().toBigDecimal().defaultScale() },
                        DataDef("Taxas"),
                        DataDef("Quantidade acumulada"),
                        DataDef("Preço Médio"),
                    )
                }
                register(ContentType.Application.Pdf, PdfConverter())
            }

            install(StatusPages) {
                exception<StrategyReportParseException> { call, cause ->
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiError(
                            error = "strategy-report-invalid",
                            message = "The strategy report is invalid",
                            detail = cause.message ?: "The report could not be parsed or validated",
                        ),
                    )
                }
                exception<StrategyNotFoundException> { call, cause ->
                    call.respond(
                        HttpStatusCode.NotFound,
                        ApiError(
                            error = "strategy-not-found",
                            message = "Strategy not found",
                            detail = cause.message ?: "The requested strategy does not exist",
                        ),
                    )
                }
                exception<StrategyEditionAlreadyExistsException> { call, cause ->
                    call.respond(
                        HttpStatusCode.Conflict,
                        ApiError(
                            error = "strategy-edition-duplicate",
                            message = "Strategy edition already exists",
                            detail = cause.message ?: "An edition for this strategy and reference date already exists",
                        ),
                    )
                }
            }

            install(CORS) {
                allowMethod(HttpMethod.Options)
                allowMethod(HttpMethod.Put)
                allowMethod(HttpMethod.Delete)
                allowMethod(HttpMethod.Patch)
                allowHeader(HttpHeaders.Authorization)
                allowHeader(HttpHeaders.ContentType)
                anyHost()
            }
        }.start(wait = wait)
    }

    fun stop() = server.stop(0, 0)
}

private data class ApiError(
    val error: String,
    val message: String,
    val detail: String,
)

private fun String.sanitizeCurrency() = replace(".", "").replace(",", ".")
