package dev.agner.portfolio.usecase.portfolio

import dev.agner.portfolio.usecase.bond.BondService
import dev.agner.portfolio.usecase.bond.model.Bond
import dev.agner.portfolio.usecase.bond.position.BondPositionService
import dev.agner.portfolio.usecase.catalog.CatalogService
import dev.agner.portfolio.usecase.commons.mapAsync
import dev.agner.portfolio.usecase.listedasset.PositionService
import dev.agner.portfolio.usecase.listedasset.model.Position
import dev.agner.portfolio.usecase.listedasset.repository.IAttributionRepository
import dev.agner.portfolio.usecase.portfolio.model.Allocation
import dev.agner.portfolio.usecase.portfolio.model.Targets
import dev.agner.portfolio.usecase.quotes.QuoteService
import dev.agner.portfolio.usecase.strategy.model.AttributionLedger
import dev.agner.portfolio.usecase.strategy.model.StrategyKind
import dev.agner.portfolio.usecase.strategy.model.StrategyTicker
import dev.agner.portfolio.usecase.strategy.repository.IStrategyRepository
import kotlinx.coroutines.awaitAll
import org.springframework.stereotype.Service
import java.math.BigDecimal

// Composes the open month's allocation: the target chain from MonthService, the listed book
// (Position × current quote) and the RF book (the consolidation engine's latest principal + yield per
// product). The arithmetic itself lives in AllocationCalculator; this class only gathers its inputs.
@Service
class AllocationService(
    private val months: MonthService,
    private val positions: PositionService,
    private val catalog: CatalogService,
    private val quotes: QuoteService,
    private val bonds: BondService,
    private val bondPositions: BondPositionService,
    private val strategies: IStrategyRepository,
    private val attributions: IAttributionRepository,
) {

    // The read is month-free: without an open month it still returns the tree, with `period` absent
    // and Actual only, so the bucket and outside rows stay visible before the ritual starts.
    suspend fun fetchCurrent(): Allocation {
        val current = months.fetchCurrent()

        val listed = positions.fetchAll().mapAsync { toListedHolding(it) }.awaitAll()
        // Every RF product is a bond, including the ones a checking account creates on each deposit
        // (BondRepository.save(checkingAccountId, …)). Counting the bonds counts each product once;
        // summing the accounts too would count the account-held bonds a second time.
        val fixedIncome = bonds.fetchAll().mapNotNull { toFixedIncomeHolding(it) }

        val strategyList = strategies.fetchAll()
        val strategyById = strategyList.associateBy { it.id }
        // The ledger is keyed by strategy name because that is how the Target chain names its
        // strategies; the id resolves the name and the kind in one read.
        val ledger: AttributionLedger = attributions.fetchAll()
            .mapNotNull { row ->
                strategyById[row.strategyId]?.let { strategy ->
                    StrategyTicker(strategy.name, row.ticker) to row.quantity
                }
            }
            .toMap()
        val manual = strategyList
            .filter { it.kind == StrategyKind.MANUAL }
            .map { ManualStrategyHolding(it.name, it.assetClass) }

        return AllocationCalculator.compute(
            period = current?.month?.period,
            capital = current?.month?.capital ?: BigDecimal.ZERO,
            floor = current?.month?.floor ?: BigDecimal.ZERO,
            targets = current?.targets ?: EMPTY_TARGETS,
            listed = listed,
            fixedIncome = fixedIncome,
            attributions = ledger,
            manualStrategies = manual,
        )
    }

    // A position is valued at the current quote; a ticker with no close yet values at zero. Its class
    // is the catalog's.
    private suspend fun toListedHolding(position: Position) = ListedHolding(
        ticker = position.ticker,
        assetClass = catalog.fetchBy(position.ticker)?.assetClass,
        quantity = position.quantity,
        price = quotes.latestClose(position.ticker) ?: BigDecimal.ZERO,
    )

    // A product with no consolidated position yet contributes nothing.
    private suspend fun toFixedIncomeHolding(bond: Bond): FixedIncomeHolding? =
        bondPositions.getByBondId(bond.id).lastOrNull()?.let { position ->
            FixedIncomeHolding(bond.subClass, position.principal + position.yield)
        }
}

// The chain a month-free read scales: nothing. The calculator still lays out every class and RF
// sub-class so their Actuals show, with no target rung above them.
private val EMPTY_TARGETS = Targets(
    classes = emptyList(),
    strategies = emptyList(),
    subClasses = emptyList(),
    tickers = emptyList(),
)
