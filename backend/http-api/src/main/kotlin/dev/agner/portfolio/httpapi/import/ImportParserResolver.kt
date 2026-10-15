package dev.agner.portfolio.httpapi.import

import dev.agner.portfolio.usecase.import.model.ImportRowPayload
import org.springframework.stereotype.Component

// Picks the layout parser for a staged file. The first parser that claims the file wins; a file no
// layout recognises stages with no rows rather than being dropped, so the raw upload is recorded.
@Component
class ImportParserResolver(
    private val parsers: List<IImportParser>,
) {

    fun resolve(fileName: String, content: ByteArray): List<ImportRowPayload> =
        parsers.firstOrNull { it.shouldExecute(fileName, content) }?.parse(fileName, content).orEmpty()
}
