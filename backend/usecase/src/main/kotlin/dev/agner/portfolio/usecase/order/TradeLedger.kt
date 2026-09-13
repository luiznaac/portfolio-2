package dev.agner.portfolio.usecase.order

import dev.agner.portfolio.usecase.commons.defaultScale
import dev.agner.portfolio.usecase.listedasset.model.AssetKind
import dev.agner.portfolio.usecase.listedasset.model.ListedAsset
import dev.agner.portfolio.usecase.order.model.Order
import dev.agner.portfolio.usecase.order.model.SaleCeiling
import dev.agner.portfolio.usecase.tax.TaxRules
import dev.agner.portfolio.usecase.trade.model.Trade
import dev.agner.portfolio.usecase.trade.repository.ITradeRepository
import kotlinx.datetime.LocalDate
import org.springframework.stereotype.Component
import java.math.BigDecimal

/**
 * The trade ledger as order-plan assembly sees it: whether the opposite side of a planned order
 * already traded today (a day trade), and how much stock has been sold this month against the
 * sale-exemption ceiling.
 *
 * Both reads take `today` as an argument rather than reaching for a clock, so the caller decides
 * which date the run is for.
 */
@Component
class TradeLedger(
    private val tradeRepository: ITradeRepository,
) {

    suspend fun hasOppositeTradeToday(assetId: Int, today: LocalDate, isSale: Boolean): Boolean =
        tradeRepository.fetchByAssetId(assetId).any { trade ->
            trade.date == today && if (isSale) trade is Trade.Buy else trade is Trade.Sell
        }

    suspend fun saleCeiling(
        today: LocalDate,
        orders: List<Order>,
        allAssets: List<ListedAsset>,
    ): SaleCeiling {
        val monthStart = LocalDate(today.year, today.month, 1)
        val kindByAssetId = allAssets.associate { it.id to it.kind }

        // Day trades never consume the exemption: a sell paired with a buy on the same day is
        // dropped from the ledger side, and a planned order already flagged as a day-trade risk is
        // dropped from the planned side. Only FIIs are excluded from the ledger side — a sale of an
        // asset the app does not know about still counts toward the ceiling, which can only
        // understate headroom, never overstate it.
        val trades = tradeRepository.fetchByDateRange(monthStart, today)
        val boughtOn = trades.filterIsInstance<Trade.Buy>()
            .mapTo(mutableSetOf()) { it.assetId to it.date }
        val settledStockSales = trades
            .filterIsInstance<Trade.Sell>()
            .filter { kindByAssetId[it.assetId] != AssetKind.FII }
            .filter { (it.assetId to it.date) !in boughtOn }
            .sumOf { it.notional }

        val plannedStockSales = orders
            .filter { !it.isFii && it.kind.isSale }
            .filter { !it.dayTradeRisk }
            .sumOf { it.notional }

        val monthSold = (settledStockSales + plannedStockSales).defaultScale()
        val limit = TaxRules.MONTHLY_STOCK_SALE_EXEMPTION

        return SaleCeiling(
            monthSold = monthSold,
            limit = limit,
            remaining = (limit - monthSold).max(BigDecimal.ZERO),
        )
    }
}
