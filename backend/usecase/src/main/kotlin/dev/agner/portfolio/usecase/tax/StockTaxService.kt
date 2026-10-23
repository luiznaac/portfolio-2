package dev.agner.portfolio.usecase.tax

import dev.agner.portfolio.usecase.catalog.CatalogService
import dev.agner.portfolio.usecase.catalog.model.AssetKind
import dev.agner.portfolio.usecase.corporateaction.repository.ICorporateActionRepository
import dev.agner.portfolio.usecase.listedasset.repository.ITradeRepository
import dev.agner.portfolio.usecase.portfolio.MonthService
import dev.agner.portfolio.usecase.tax.model.StockTaxReport
import dev.agner.portfolio.usecase.tax.model.TickerLedger
import org.springframework.stereotype.Service

// The open month's tax read: it gathers each ticker's ledger — every trade, imported or manual, plus
// the applied corporate actions — and hands the whole book to StockTaxCalculator. Nothing is
// persisted: the meter and the average prices are a pure function of the ledger and the catalog.
@Service
class StockTaxService(
    private val months: MonthService,
    private val trades: ITradeRepository,
    private val corporateActions: ICorporateActionRepository,
    private val catalog: CatalogService,
) {

    // Null when no month is open — the edge maps that to the 404 the current-month reads promise.
    suspend fun fetchCurrent(): StockTaxReport? {
        val current = months.fetchCurrent() ?: return null
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

        return StockTaxCalculator.compute(current.month.period, ledgers)
    }
}
