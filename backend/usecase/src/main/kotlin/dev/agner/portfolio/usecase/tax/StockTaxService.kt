package dev.agner.portfolio.usecase.tax

import dev.agner.portfolio.usecase.catalog.CatalogService
import dev.agner.portfolio.usecase.catalog.model.AssetKind
import dev.agner.portfolio.usecase.corporateaction.repository.ICorporateActionRepository
import dev.agner.portfolio.usecase.listedasset.model.Gap
import dev.agner.portfolio.usecase.listedasset.repository.IGapRepository
import dev.agner.portfolio.usecase.listedasset.repository.ITradeRepository
import dev.agner.portfolio.usecase.portfolio.MonthService
import dev.agner.portfolio.usecase.tax.model.CarrySeed
import dev.agner.portfolio.usecase.tax.model.StockTaxReport
import dev.agner.portfolio.usecase.tax.model.TaxPendingGap
import dev.agner.portfolio.usecase.tax.model.TickerLedger
import dev.agner.portfolio.usecase.tax.repository.ICarryBucketRepository
import kotlinx.datetime.YearMonth
import kotlinx.datetime.yearMonth
import org.springframework.stereotype.Service

// The period's tax read: it gathers each ticker's ledger — every trade, imported or manual, plus
// the applied corporate actions — and hands the whole book to StockTaxCalculator, which replays it
// as of the period's end. The only stored input is each carry bucket's adoption opening balance; the
// meter, the realized results, the bucket rolls and the average prices are a pure function of the
// ledger, the catalog and those seeds.
@Service
class StockTaxService(
    private val months: MonthService,
    private val trades: ITradeRepository,
    private val corporateActions: ICorporateActionRepository,
    private val catalog: CatalogService,
    private val carryBuckets: ICarryBucketRepository,
    private val gaps: IGapRepository,
) {

    // The period-parameterised read. Inside the month window it never refuses: a pending missing-sale
    // gap and an unclassified ticker are reported, not guessed at.
    suspend fun fetch(period: YearMonth): StockTaxReport {
        val range = months.fetchPeriodRange()
        if (!range.contains(period)) throw TaxPeriodOutsideWindowException(period = period, range = range)
        return report(period)
    }

    // The open month's report — an internal helper for the close and the harvest writes, which always
    // read the month that is currently open.
    suspend fun fetchCurrent(): StockTaxReport? {
        val current = months.fetchCurrent() ?: return null
        return report(current.month.period)
    }

    private suspend fun report(period: YearMonth): StockTaxReport {
        val kinds = catalog.fetchAll().associateBy { it.ticker }
        val actions = corporateActions.fetchApplied().groupBy { it.ticker }
        val ledgers = trades.fetchAll()
            .groupBy { it.ticker }
            .map { (ticker, ledger) ->
                TickerLedger(
                    ticker = ticker,
                    // An uncatalogued ticker is UNKNOWN, not a stock: the meter must not guess.
                    assetKind = kinds[ticker]?.assetKind ?: AssetKind.UNKNOWN,
                    trades = ledger,
                    corporateActions = actions[ticker].orEmpty(),
                )
            }

        return StockTaxCalculator.compute(
            period = period,
            ledgers = ledgers,
            carrySeed = CarrySeed.from(carryBuckets.fetchAll()),
        ).copy(pendingGaps = pendingGapsOf(period))
    }

    // A missing sale's proceeds are unknown until the owner prices it; the month's result is pending
    // rather than guessed, and the GAP never feeds the tax.
    private suspend fun pendingGapsOf(period: YearMonth): List<TaxPendingGap> = gaps.fetchAll()
        .filterIsInstance<Gap.Sell>()
        .filter { it.realizedPending && it.date.yearMonth == period }
        .map { TaxPendingGap(id = it.id, ticker = it.ticker, date = it.date, quantity = it.quantity) }
        .sortedWith(compareBy({ it.date }, { it.ticker }))
}
