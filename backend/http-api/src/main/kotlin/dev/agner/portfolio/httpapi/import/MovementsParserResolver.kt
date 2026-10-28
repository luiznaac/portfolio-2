package dev.agner.portfolio.httpapi.import

import dev.agner.portfolio.usecase.import.UnsupportedImportFileException
import dev.agner.portfolio.usecase.import.model.MovementsRouting
import org.springframework.stereotype.Component

// Picks the Movimentação layout parser for an uploaded file. The first parser that claims the file
// wins; a file no layout recognises is rejected loudly with the same canonical 4xx the trade import
// path uses, rather than recorded as an empty import.
@Component
class MovementsParserResolver(
    private val parsers: List<IMovementsParser>,
) {

    fun parse(fileName: String, content: ByteArray): MovementsRouting =
        parsers.firstOrNull { it.shouldExecute(fileName, content) }?.parse(fileName, content)
            ?: throw UnsupportedImportFileException(fileName)
}
