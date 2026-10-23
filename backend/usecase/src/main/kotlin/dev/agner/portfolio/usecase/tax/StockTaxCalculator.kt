package dev.agner.portfolio.usecase.tax

import dev.agner.portfolio.usecase.catalog.model.AssetKind
import dev.agner.portfolio.usecase.commons.defaultScale
import dev.agner.portfolio.usecase.listedasset.PositionCalculator
import dev.agner.portfolio.usecase.listedasset.model.Position
import dev.agner.portfolio.usecase.listedasset.model.Trade
import dev.agner.portfolio.usecase.tax.model.ExemptionMeter
import dev.agner.portfolio.usecase.tax.model.StockTaxReport
import dev.agner.portfolio.usecase.tax.model.TickerAveragePrice
import dev.agner.portfolio.usecase.tax.model.TickerLedger
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import java.math.BigDecimal

// The stock-sale tax arithmetic: the monthly R$20k exemption meter and the fiscal average price.
// Pure and side-effect free — the service only gathers each ticker's ledger, so the rules are
// unit-tested with fixtures instead of a database.
//
// The meter counts gross proceeds of STOCK sales only. Units, FIIs, BDRs, ETFs and day trades are
// outside it (SC Cosit 145/2021), and so are an unclassified ticker's sales: the catalog has not
// said it is a stock. Both import and manual sources are in the same ledger, so the meter sees
// both.
object StockTaxCalculator {

    // Lei 11.033/2004 art. 3º, I: R$20k of gross stock sales in the calendar month keeps the
    // month's gain exempt. The limit is on gross sales, not on the gain.
    val EXEMPTION_LIMIT: BigDecimal = BigDecimal("20000.00")

    // Lei 11.033/2004 art. 59: a month whose gross stock sales cross the limit loses the exemption
    // for the whole month — the 15% applies to the month's net gain, not to the excess only.
    val STOCK_GAIN_RATE: BigDecimal = BigDecimal("15.00")

    private val HUNDRED = BigDecimal("100")

    fun compute(period: YearMonth, ledgers: List<TickerLedger>): StockTaxReport {
        val replays = ledgers.map { replay(it, period) }
        val sales = replays.flatMap { it.sales }

        val grossSales = sales.sumOf { it.gross }.defaultScale()
        val realizedGain = sales.sumOf { it.gain }.defaultScale()
        val exceeded = grossSales > EXEMPTION_LIMIT
        val taxableGain = if (exceeded && realizedGain.signum() > 0) realizedGain else BigDecimal.ZERO.defaultScale()

        return StockTaxReport(
            period = period,
            meter = ExemptionMeter(
                grossSales = grossSales,
                exemptionLimit = EXEMPTION_LIMIT,
                exceeded = exceeded,
                realizedGain = realizedGain,
                taxableGain = taxableGain,
                rate = STOCK_GAIN_RATE,
                tax = taxableGain.multiply(STOCK_GAIN_RATE).divide(HUNDRED).defaultScale(),
            ),
            averagePrices = replays
                .filter { it.position.quantity.signum() > 0 }
                .map {
                    TickerAveragePrice(
                        ticker = it.ticker,
                        quantity = it.position.quantity,
                        averagePrice = it.position.averagePrice,
                    )
                }
                .sortedBy { it.ticker },
        )
    }

    private fun replay(ledger: TickerLedger, period: YearMonth) = LedgerReplay(
        ticker = ledger.ticker,
        position = PositionCalculator.replay(ledger.ticker, ledger.trades, ledger.corporateActions),
        sales = if (ledger.assetKind == AssetKind.STOCK) {
            val swing = swingQuantities(ledger.trades)
            ledger.trades
                .filterIsInstance<Trade.Sell>()
                .filter { it.date.year == period.year && it.date.month == period.month }
                .mapNotNull { sale ->
                    swing[sale.id]
                        ?.takeIf { it.signum() > 0 }
                        ?.let { quantity -> saleOf(ledger, sale, quantity) }
                }
        } else {
            emptyList()
        },
    )

    private fun saleOf(ledger: TickerLedger, sale: Trade.Sell, quantity: BigDecimal): Sale {
        val basis = PositionCalculator.positionBefore(
            ledger.ticker,
            ledger.trades,
            ledger.corporateActions,
            sale,
        )
        // Gross prices on both legs: a B3 import carries no fees, and the trade's stored fee stays
        // for later exactness rather than shifting the basis now.
        val gross = quantity.multiply(sale.unitPrice)
        return Sale(gross = gross, gain = gross.subtract(quantity.multiply(basis.averagePrice)))
    }

    // A day trade is the offsetting of the same asset at the same institution on the same day, so
    // the swing quantity of a sale is what a same-day buy at that institution does not cover. The
    // matched part is taxed under the day-trade rule (20%) and stays out of this swing meter; the
    // unmatched part keeps the normal moving-average basis. Quoting sells in ledger order keeps the
    // allocation deterministic.
    private fun swingQuantities(trades: List<Trade>): Map<Int, BigDecimal> {
        val openQuantity = trades
            .filterIsInstance<Trade.Buy>()
            .groupBy { DayTradeKey(it.date, it.institutionId) }
            .mapValues { (_, buys) -> buys.sumOf { it.quantity } }
            .toMutableMap()

        return trades
            .filterIsInstance<Trade.Sell>()
            .sortedWith(compareBy({ it.date }, { it.ordinal }, { it.id }))
            .associate { sale ->
                val key = DayTradeKey(sale.date, sale.institutionId)
                val open = openQuantity[key] ?: BigDecimal.ZERO
                val dayTraded = minOf(open, sale.quantity)
                openQuantity[key] = open.subtract(dayTraded)
                sale.id to sale.quantity.subtract(dayTraded)
            }
    }

    private data class DayTradeKey(
        val date: LocalDate,
        val institutionId: Int,
    )

    private data class LedgerReplay(
        val ticker: String,
        val position: Position,
        val sales: List<Sale>,
    )

    private data class Sale(
        val gross: BigDecimal,
        val gain: BigDecimal,
    )
}
