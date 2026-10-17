package dev.agner.portfolio.httpapi.controller

import dev.agner.portfolio.httpapi.import.ImportParserResolver
import dev.agner.portfolio.usecase.import.ImportService
import dev.agner.portfolio.usecase.import.model.Import
import dev.agner.portfolio.usecase.import.model.ImportSource
import dev.agner.portfolio.usecase.import.model.sha256Hex
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.springframework.stereotype.Component

@Component
class ImportController(
    private val service: ImportService,
    private val parsers: ImportParserResolver,
) : ControllerTemplate {

    override fun routes(): RouteDefinition = {
        route("/imports") {
            get {
                call.respond(HttpStatusCode.OK, service.fetchAll())
            }

            post {
                call.respond(HttpStatusCode.Created, call.stageFile())
            }

            get("/{import_id}") {
                call.respondImport(service.fetchById(call.requiredInt("import_id")))
            }

            get("/{import_id}/rows") {
                val rows = service.fetchRows(call.requiredInt("import_id"))
                when (rows) {
                    null -> call.respond(HttpStatusCode.NotFound)
                    else -> call.respond(HttpStatusCode.OK, rows)
                }
            }

            post("/{import_id}/review") {
                call.respondImport(service.review(call.requiredInt("import_id")))
            }

            post("/{import_id}/apply") {
                call.respondImport(service.apply(call.requiredInt("import_id")))
            }
        }
    }

    // Reads the uploaded file and its source, then hands the pipeline the parsed rows. The hash is
    // computed here so the service can recognise a re-import without ever touching the bytes.
    private suspend fun ApplicationCall.stageFile(): Import {
        val upload = receiveUpload()
        val source = upload.formItems["source"]?.let { ImportSource.valueOf(it) }
            ?: throw InvalidParameterException("source", null)
        return service.stage(
            source,
            upload.fileName,
            upload.content.sha256Hex(),
            parsers.resolve(upload.fileName, upload.content),
        )
    }

    private suspend fun ApplicationCall.respondImport(import: Import?) = when (import) {
        null -> respond(HttpStatusCode.NotFound)
        else -> respond(HttpStatusCode.OK, import)
    }
}
