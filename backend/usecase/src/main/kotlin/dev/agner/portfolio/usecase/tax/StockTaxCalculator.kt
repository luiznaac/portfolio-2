package dev.agner.portfolio.usecase.tax

import dev.agner.portfolio.usecase.catalog.model.AssetKind
import dev.agner.portfolio.usecase.commons.defaultScale
import dev.agner.portfolio.usecase.commons.percentOf
import dev.agner.portfolio.usecase.listedasset.PositionCalculator
import dev.agner.portfolio.usecase.listedasset.model.Position
import dev.agner.portfolio.usecase.listedasset.model.Trade
import dev.agner.portfolio.usecase.tax.model.CarrySeed
import dev.agner.portfolio.usecase.tax.model.ExemptionMeter
import dev.agner.portfolio.usecase.tax.model.RealizedKind
import dev.agner.portfolio.usecase.tax.model.RealizedResult
import dev.agner.portfolio.usecase.tax.model.StockTaxReport
import dev.agner.portfolio.usecase.tax.model.TickerAveragePrice
import dev.agner.portfolio.usecase.tax.model.TickerLedger
import kotlinx.datetime.YearMonth
import kotlinx.datetime.yearMonth
import java.math.BigDecimal

// The stock-sale tax arithmetic: the monthly R$20k exemption meter, the fiscal average price and the
// realized result per tax kind. Pure and side-effect free — the service only gathers each ticker's
// ledger and the adoption seed, so the rules are unit-tested with fixtures instead of a database.
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

    fun compute(period: YearMonth, ledgers: List<TickerLedger>, carrySeed: CarrySeed): StockTaxReport {
        val replays = ledgers.map { replay(it) }
        val months = replays
            .flatMap { it.sales }
            .groupBy { it.month }
            .mapValues { (month, sales) -> monthOf(month, sales) }
        val current = months[period] ?: monthOf(period, emptyList())
        val prior = months.filterKeys { it < period }.toSortedMap().values.toList()
        val carry = CarryRoller.roll(carrySeed, prior, current)
        val swingCarry = carry.first { it.kind == RealizedKind.SWING }

        return StockTaxReport(
            period = period,
            meter = meterOf(current.swing, current.swingExceeded, swingCarry.lossApplied),
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
            realized = current.results,
            carry = carry,
            dayTrades = ledgers
                .flatMap { DayTradeCalculator.warnings(it.ticker, it.trades, period) }
                .sortedWith(compareBy({ it.date }, { it.ticker }, { it.institutionId })),
        )
    }

    private fun meterOf(swing: RealizedResult, exceeded: Boolean, carryApplied: BigDecimal): ExemptionMeter {
        val taxableGain = if (exceeded && swing.result.signum() > 0) {
            swing.result.subtract(carryApplied).defaultScale()
        } else {
            BigDecimal.ZERO.defaultScale()
        }

        return ExemptionMeter(
            grossSales = swing.grossSales,
            exemptionLimit = EXEMPTION_LIMIT,
            exceeded = exceeded,
            realizedGain = swing.result,
            taxableGain = taxableGain,
            rate = STOCK_GAIN_RATE,
            tax = taxableGain.percentOf(STOCK_GAIN_RATE),
        )
    }

    private fun monthOf(period: YearMonth, sales: List<Sale>): MonthRealized {
        val results = realizedOf(sales)
        val swing = results.first { it.kind == RealizedKind.SWING }
        return MonthRealized(
            period = period,
            results = results,
            swing = swing,
            swingExceeded = swing.grossSales > EXEMPTION_LIMIT,
        )
    }

    // Three rows always, in the enum's order, so a kind with no sale still reports a zero result
    // instead of vanishing from the ledger the UI renders.
    private fun realizedOf(sales: List<Sale>): List<RealizedResult> =
        RealizedKind.entries.map { kind ->
            val ofKind = sales.filter { it.kind == kind }
            val gains = ofKind.map { it.gain }.filter { it.signum() > 0 }.sumOf { it }.defaultScale()
            val losses = ofKind.map { it.gain }.filter { it.signum() < 0 }.sumOf { it.negate() }.defaultScale()

            RealizedResult(
                kind = kind,
                grossSales = ofKind.sumOf { it.gross }.defaultScale(),
                gains = gains,
                losses = losses,
                result = gains.subtract(losses).defaultScale(),
            )
        }

    private fun replay(ledger: TickerLedger) = LedgerReplay(
        ticker = ledger.ticker,
        position = PositionCalculator.replay(ledger.ticker, ledger.trades, ledger.corporateActions),
        sales = salesOf(ledger),
    )

    private fun salesOf(ledger: TickerLedger): List<Sale> = when (ledger.assetKind) {
        AssetKind.STOCK -> stockSales(ledger)
        AssetKind.FII -> fiiSales(ledger)
        else -> emptyList()
    }

    // A STOCK sale splits at the matched day-trade quantity: the part a same-day same-institution buy
    // covers is a day trade, the remainder is swing. Both legs keep the sale's own moving-average
    // basis, which is the formula the tax rules use for either species.
    private fun stockSales(ledger: TickerLedger): List<Sale> {
        val dayTraded = DayTradeCalculator.quantitiesBySale(ledger.trades)
        return ledger.trades
            .filterIsInstance<Trade.Sell>()
            .flatMap { sale ->
                val dayTradeQuantity = dayTraded[sale.id] ?: BigDecimal.ZERO
                val swingQuantity = sale.quantity.subtract(dayTradeQuantity)
                listOfNotNull(
                    saleOf(ledger, sale, swingQuantity, RealizedKind.SWING),
                    saleOf(ledger, sale, dayTradeQuantity, RealizedKind.DAY_TRADE),
                )
            }
    }

    private fun fiiSales(ledger: TickerLedger): List<Sale> =
        ledger.trades
            .filterIsInstance<Trade.Sell>()
            .mapNotNull { sale -> saleOf(ledger, sale, sale.quantity, RealizedKind.FII) }

    private fun saleOf(ledger: TickerLedger, sale: Trade.Sell, quantity: BigDecimal, kind: RealizedKind): Sale? {
        if (quantity.signum() <= 0) return null
        val basis = PositionCalculator.positionBefore(
            ledger.ticker,
            ledger.trades,
            ledger.corporateActions,
            sale,
        )
        // Gross prices on both legs: a B3 import carries no fees, and the trade's stored fee stays
        // for later exactness rather than shifting the basis now.
        val gross = quantity.multiply(sale.unitPrice)
        return Sale(
            month = sale.date.yearMonth,
            kind = kind,
            gross = gross,
            gain = gross.subtract(quantity.multiply(basis.averagePrice)),
        )
    }

    private data class LedgerReplay(
        val ticker: String,
        val position: Position,
        val sales: List<Sale>,
    )

    private data class Sale(
        val month: YearMonth,
        val kind: RealizedKind,
        val gross: BigDecimal,
        val gain: BigDecimal,
    )
}
