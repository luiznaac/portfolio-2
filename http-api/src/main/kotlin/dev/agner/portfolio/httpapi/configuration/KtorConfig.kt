package dev.agner.portfolio.httpapi.configuration

import com.fasterxml.jackson.databind.ObjectMapper
import dev.agner.portfolio.httpapi.controller.ControllerTemplate
import dev.agner.portfolio.usecase.commons.defaultScale
import dev.agner.portfolio.usecase.commons.disgustingLocalDateFormat
import dev.agner.portfolio.usecase.commons.logger
import dev.agner.portfolio.usecase.upload.model.UploadOrder
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.serialization.jackson.JacksonConverter
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.routing.routing
import kotlinx.datetime.LocalDate
import org.springframework.stereotype.Component

@Component
class KtorConfig(
    private val routes: Set<ControllerTemplate>,
    private val mapper: ObjectMapper,
) {

    private val log = logger()

    fun start() {
        log.info("Starting ktor")

        embeddedServer(Netty, port = 8080) {
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

            install(CORS) {
                allowMethod(HttpMethod.Options)
                allowMethod(HttpMethod.Put)
                allowMethod(HttpMethod.Delete)
                allowMethod(HttpMethod.Patch)
                allowHeader(HttpHeaders.Authorization)
                allowHeader(HttpHeaders.ContentType)
                anyHost()
            }
        }.start(wait = true)
    }
}

private fun String.sanitizeCurrency() = replace(".", "").replace(",", ".")
