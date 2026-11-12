package dev.agner.portfolio.httpapi.controller

import dev.agner.portfolio.httpapi.import.ImportParserResolver
import dev.agner.portfolio.usecase.import.ImportService
import dev.agner.portfolio.usecase.import.model.Import
import dev.agner.portfolio.usecase.import.model.ImportApplyResult
import dev.agner.portfolio.usecase.import.model.ImportDestination
import dev.agner.portfolio.usecase.import.model.ImportDestinationObject
import dev.agner.portfolio.usecase.import.model.ImportSource
import dev.agner.portfolio.usecase.import.model.ImportStatus
import dev.agner.portfolio.usecase.import.model.StagingResult
import dev.agner.portfolio.usecase.import.model.sha256Hex
import io.ktor.http.ContentDisposition
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
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
                call.respond(
                    HttpStatusCode.OK,
                    service.fetchAll(
                        status = call.optionalEnumParameter<ImportStatus>("status"),
                        source = call.optionalEnumParameter<ImportSource>("source"),
                    ),
                )
            }

            post {
                val staged = call.stageFile()
                call.respond(if (staged.created) HttpStatusCode.Created else HttpStatusCode.OK, staged.record)
            }

            get("/{import_id}") {
                call.respondImport(service.fetchById(call.requiredInt("import_id")))
            }

            // Provenance: the retained original beside the record's file name and hash prefix.
            get("/{import_id}/file") {
                call.respondOriginal(call.requiredInt("import_id"))
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
                call.respondApply(service.apply(call.requiredInt("import_id")))
            }
        }
    }

    // Reads the uploaded file, its source and its typed destination, then hands the pipeline the
    // parsed rows. The hash is computed here so the service can recognise a re-import without ever
    // touching the bytes, and the bytes are retained beside the record for provenance.
    private suspend fun ApplicationCall.stageFile(): StagingResult {
        val upload = receiveUpload()
        val source = upload.formItems["source"]
            ?.let { value -> runCatching { ImportSource.valueOf(value) }.getOrNull() }
            ?: throw InvalidParameterException("source", upload.formItems["source"])
        val destinationObject = upload.formItems["destination_object"]
            ?.let { value -> runCatching { ImportDestinationObject.valueOf(value) }.getOrNull() }
            ?: throw InvalidParameterException("destination_object", upload.formItems["destination_object"])
        val destination = ImportDestination(destinationObject, upload.formItems.destinationEntityId())
        return service.stage(
            source,
            upload.fileName,
            upload.content.sha256Hex(),
            parsers.resolve(upload.fileName, upload.content),
            destination,
            upload.content,
        )
    }

    private suspend fun ApplicationCall.respondImport(import: Import?) =
        if (import == null) respond(HttpStatusCode.NotFound) else respond(HttpStatusCode.OK, import)

    private suspend fun ApplicationCall.respondApply(result: ImportApplyResult?) =
        if (result == null) respond(HttpStatusCode.NotFound) else respond(HttpStatusCode.OK, result)

    private suspend fun ApplicationCall.respondOriginal(id: Int) {
        val record = service.fetchById(id)
        val original = service.fetchOriginal(id)
        if (record == null || original == null) {
            respond(HttpStatusCode.NotFound)
        } else {
            response.header(
                HttpHeaders.ContentDisposition,
                ContentDisposition.Attachment
                    .withParameter(ContentDisposition.Parameters.FileName, record.fileName)
                    .toString(),
            )
            respondBytes(original)
        }
    }
}

// A destination entity id is optional; when present it must be numeric, so a malformed value is a
// 400 rather than a parked session that silently loses its detail.
private fun Map<String, String>.destinationEntityId(): Int? {
    val raw = this["destination_entity_id"]?.takeIf { it.isNotBlank() } ?: return null
    return raw.toIntOrNull() ?: throw InvalidParameterException("destination_entity_id", raw)
}
