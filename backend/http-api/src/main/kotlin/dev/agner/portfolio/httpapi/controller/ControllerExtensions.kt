package dev.agner.portfolio.httpapi.controller

import dev.agner.portfolio.usecase.commons.DomainException
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveMultipart
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray

// Client-driven path parsing: an absent or non-numeric segment must surface as a domain error
// (mapped to 400) instead of a NumberFormatException/KotlinNullPointerException 500. Unlike the
// field-specific exceptions it replaced, the detail names the field that failed, so every route
// that parses a path parameter gets the same contract for free.
class InvalidParameterException(fieldName: String, received: String?) :
    DomainException(
        error = "invalid-parameter",
        userMessage = "Invalid parameter",
        detail = "Invalid value for field $fieldName: ${received ?: "<missing>"}",
    )

internal fun ApplicationCall.requiredInt(name: String): Int =
    parameters[name]?.toIntOrNull() ?: throw InvalidParameterException(name, parameters[name])

internal fun ApplicationCall.requiredString(name: String): String =
    parameters[name] ?: throw InvalidParameterException(name, null)

// One uploaded part: the file the browser sent plus every form field beside it (the import
// pipeline reads `source`, the position cross-check reads no field). Reading it is the one place
// the multipart layout is understood, so both controllers share this instead of each parsing parts.
internal data class UploadedFile(
    val fileName: String,
    val content: ByteArray,
    val formItems: Map<String, String>,
)

internal suspend fun ApplicationCall.receiveUpload(): UploadedFile {
    var fileName = ""
    var content = ByteArray(0)
    val formItems = mutableMapOf<String, String>()

    receiveMultipart().forEachPart { part ->
        when (part) {
            is PartData.FormItem -> formItems[part.name.orEmpty()] = part.value
            is PartData.FileItem -> {
                fileName = part.originalFileName.orEmpty()
                content = part.provider().readRemaining().readByteArray()
            }
            else -> Unit
        }
        part.dispose()
    }

    return UploadedFile(fileName, content, formItems)
}
