package dev.agner.portfolio.usecase.import

import dev.agner.portfolio.usecase.configuration.ITransactionTemplate
import dev.agner.portfolio.usecase.corporateaction.CorporateActionService
import dev.agner.portfolio.usecase.import.model.ImportSource
import dev.agner.portfolio.usecase.import.model.ImportStatus
import dev.agner.portfolio.usecase.import.model.MovementsImportResult
import dev.agner.portfolio.usecase.import.model.MovementsRouting
import dev.agner.portfolio.usecase.income.IncomeService
import org.springframework.stereotype.Service

// The Movimentações import orchestrator: the parser has already split the rows by destination, so
// this only dispatches them. Trades ride the existing staged/reviewed/applied pipeline (institution
// creation, catalog upsert and natural-key dedup come with it); cash proventos reconcile the income
// ledger; corporate actions go through their engine. One transaction wraps the whole file, so a
// failure cannot leave trades applied beside unprocessed events.
@Service
class MovementsImportService(
    private val imports: ImportService,
    private val incomeService: IncomeService,
    private val corporateActionService: CorporateActionService,
    private val transaction: ITransactionTemplate,
) {

    suspend fun import(
        fileName: String,
        fileHash: String,
        routing: MovementsRouting,
    ): MovementsImportResult = transaction.execute {
        val trades = applyTrades(fileName, fileHash, routing)
        val incomes = incomeService.reconcile(routing.incomes)
        val actions = corporateActionService.importEvents(
            routing.corporateActions.map { it.copy(fileHash = fileHash) },
        )
        MovementsImportResult(trades = trades, incomes = incomes.size, corporateActions = actions)
    }

    // A re-import of the same file finds its own Import by hash. APPLIED means its trades are
    // already in the ledger; STAGED/REVIEWED are walked forward; FAILED is terminal for the file.
    private suspend fun applyTrades(
        fileName: String,
        fileHash: String,
        routing: MovementsRouting,
    ): Int {
        if (routing.trades.isEmpty()) return 0
        // Malformed rows stage but apply skips them, so they do not count as absorbed trades.
        val validTrades = routing.trades.count { it.error == null }
        val staged = imports.stage(ImportSource.B3_MOVEMENTS, fileName, fileHash, routing.trades)
        return when (staged.status) {
            ImportStatus.APPLIED -> 0
            ImportStatus.FAILED -> 0
            ImportStatus.STAGED -> {
                imports.review(staged.id)
                imports.apply(staged.id)
                validTrades
            }
            ImportStatus.REVIEWED -> {
                imports.apply(staged.id)
                validTrades
            }
        }
    }
}
