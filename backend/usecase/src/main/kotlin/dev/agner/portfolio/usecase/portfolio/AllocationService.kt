package dev.agner.portfolio.usecase.portfolio

import dev.agner.portfolio.usecase.bond.BondService
import dev.agner.portfolio.usecase.bond.model.Bond
import dev.agner.portfolio.usecase.bond.position.BondPositionService
import dev.agner.portfolio.usecase.catalog.CatalogService
import dev.agner.portfolio.usecase.commons.mapAsync
import dev.agner.portfolio.usecase.listedasset.PositionService
import dev.agner.portfolio.usecase.listedasset.model.Position
import dev.agner.portfolio.usecase.portfolio.model.Allocation
import dev.agner.portfolio.usecase.quotes.QuoteService
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
) {

    // Null when no month is open — the edge maps that to the 404 the current-month reads promise.
    suspend fun fetchCurrent(): Allocation? {
        val current = months.fetchCurrent() ?: return null

        val listed = positions.fetchAll().mapAsync { toListedHolding(it) }.awaitAll()
        // Every RF product is a bond, including the ones a checking account creates on each deposit
        // (BondRepository.save(checkingAccountId, …)). Counting the bonds counts each product once;
        // summing the accounts too would count the account-held bonds a second time.
        val fixedIncome = bonds.fetchAll().mapNotNull { toFixedIncomeHolding(it) }

        return AllocationCalculator.compute(
            period = current.month.period,
            capital = current.month.capital,
            floor = current.month.floor,
            targets = current.targets,
            listed = listed,
            fixedIncome = fixedIncome,
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
