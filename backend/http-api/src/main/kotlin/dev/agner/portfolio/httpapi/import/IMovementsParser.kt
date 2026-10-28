package dev.agner.portfolio.httpapi.import

import dev.agner.portfolio.usecase.import.model.MovementsRouting

// A "Movimentação" layout parser. The broader export routes each row to a destination, so it cannot
// reuse IImportParser's flat row list; the strategy is the same — a new layout is a new class, and
// the resolver picks the first that claims the file.
interface IMovementsParser {

    fun shouldExecute(fileName: String, content: ByteArray): Boolean

    fun parse(fileName: String, content: ByteArray): MovementsRouting
}
