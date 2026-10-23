package dev.agner.portfolio.usecase.income

import dev.agner.portfolio.usecase.catalog.model.normalizedTicker
import dev.agner.portfolio.usecase.commons.mapToSet
import dev.agner.portfolio.usecase.commons.today
import dev.agner.portfolio.usecase.income.model.Income
import dev.agner.portfolio.usecase.income.model.IncomeCreation
import dev.agner.portfolio.usecase.income.model.IncomeStatus
import dev.agner.portfolio.usecase.income.model.IncomeType
import dev.agner.portfolio.usecase.income.repository.IIncomeRepository
import kotlinx.datetime.LocalDate
import org.springframework.stereotype.Service
import java.time.Clock

@Service
class IncomeService(
    private val repository: IIncomeRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
) {

    // A read derives each movement's stage from its payable date; the one stage a stored row owns
    // (RECONCILED) wins, so the lifecycle never walks a settled movement backwards. A GET never
    // writes (backend/AGENTS.md -> separate commands from queries).
    suspend fun fetchAll(): List<Income> = derive(repository.fetchAll())

    // "Upcoming" is everything not yet paid: announced and payable movements. A received (or
    // reconciled) one has already landed, so it is not a forecast.
    suspend fun fetchUpcoming(): List<Income> = derive(repository.fetchAll())
        .filter { it.status == IncomeStatus.ANNOUNCED || it.status == IncomeStatus.PAYABLE }

    // Imports parsed movements, skipping the ones the ledger already holds — income is deduped by
    // its natural key (ticker, payable date, type), so a re-export of content an earlier import
    // applied is a no-op. The ticker is normalized at this boundary so ALUP11F and ALUP11 are one
    // paper; the batch's own duplicates collapse before the ledger is touched.
    suspend fun importEvents(creations: List<IncomeCreation>): List<Income> {
        if (creations.isEmpty()) return emptyList()
        val existing = repository.fetchAll().mapToSet { it.naturalKey() }
        val fresh = creations
            .map { it.normalized() }
            .distinctBy { it.naturalKey() }
            .filter { it.naturalKey() !in existing }
        return derive(repository.saveAll(fresh))
    }

    private fun derive(incomes: List<Income>): List<Income> {
        val today = LocalDate.today(clock)
        return incomes.map { it.copy(status = IncomeLifecycle.statusAt(it.status, it.date, today)) }
    }

    // Reconciles a Movimentações cash row against the announced ledger. A row matches an announced,
    // payable or already-reconciled movement of the same ticker and type in the same month — the
    // export's payment date can differ from the forecast. An unconcluded match is marked RECONCILED;
    // a row that matches a settled movement is a re-import and is skipped (never saved again, which
    // would duplicate the natural key). A row no forecast matches is a payment announced elsewhere,
    // so it is imported as usual.
    suspend fun reconcile(creations: List<IncomeCreation>): List<Income> {
        if (creations.isEmpty()) return emptyList()
        val ledger = repository.fetchAll()
        val fresh = creations.map { it.normalized() }
            .distinctBy { IncomeNaturalKey(it.ticker, it.date, it.type) }
        val pairs = fresh.mapNotNull { creation ->
            ledger.firstOrNull { it.matches(creation) }?.let { creation to it }
        }
        val newlyReconciled = pairs.filter { (_, income) -> income.status != IncomeStatus.RECONCILED }
        if (newlyReconciled.isNotEmpty()) {
            repository.markReconciled(newlyReconciled.map { (_, income) -> income.id }.distinct())
        }
        val matched = pairs.map { (creation, _) -> creation }.toSet()
        val unmatched = fresh - matched
        val saved = if (unmatched.isEmpty()) emptyList() else repository.saveAll(unmatched)
        return derive(newlyReconciled.map { (_, income) -> income.copy(status = IncomeStatus.RECONCILED) } + saved)
    }
}

private fun Income.matches(creation: IncomeCreation): Boolean =
    ticker == creation.ticker &&
        type == creation.type &&
        date.year == creation.date.year &&
        date.month == creation.date.month

private fun IncomeCreation.normalized() = copy(ticker = ticker.normalizedTicker())

private fun Income.naturalKey() = IncomeNaturalKey(ticker, date, type)

private fun IncomeCreation.naturalKey() = IncomeNaturalKey(ticker, date, type)

// The ledger's dedup key: income is per ticker (whole Position), so the tuple alone identifies a
// payment. The payment's type separates a dividend and a JCP paid the same day on one ticker.
private data class IncomeNaturalKey(val ticker: String, val date: LocalDate, val type: IncomeType)
