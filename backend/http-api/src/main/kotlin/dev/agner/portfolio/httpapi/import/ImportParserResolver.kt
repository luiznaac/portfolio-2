package dev.agner.portfolio.httpapi.import

import dev.agner.portfolio.usecase.import.UnsupportedImportFileException
import dev.agner.portfolio.usecase.import.model.ImportDestinationObject
import dev.agner.portfolio.usecase.import.model.ImportRowPayload
import org.springframework.stereotype.Component

// Picks the layout parser for a staged file. Only the parsers that serve the upload's destination
// are asked, and the first one that claims the file wins; a file no layout recognises is rejected
// loudly — the resolver raises UnsupportedImportFileException so the caller answers with a 4xx
// canonical error body and nothing is staged, rather than silently recording an empty import.
@Component
class ImportParserResolver(
    private val parsers: List<IImportParser>,
) {

    fun resolve(
        fileName: String,
        content: ByteArray,
        destination: ImportDestinationObject,
    ): List<ImportRowPayload> =
        parsers
            .filter { it.destination == destination }
            .firstOrNull { it.shouldExecute(fileName, content) }
            ?.parse(fileName, content)
            ?: throw UnsupportedImportFileException(fileName)
}
