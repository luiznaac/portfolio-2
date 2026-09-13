package dev.agner.portfolio.usecase.tax.capitalgains

import dev.agner.portfolio.usecase.corporateaction.repository.ICorporateActionRepository
import dev.agner.portfolio.usecase.listedasset.model.AssetKind
import dev.agner.portfolio.usecase.listedasset.repository.IListedAssetRepository
import dev.agner.portfolio.usecase.tax.capitalgains.model.MonthlyCapitalGain
import dev.agner.portfolio.usecase.trade.AveragePriceCalculator
import dev.agner.portfolio.usecase.trade.model.Trade
import dev.agner.portfolio.usecase.trade.repository.ITradeRepository
import org.springframework.stereotype.Service

/**
 * Orchestrates [CapitalGainsCalculator]: replays every asset's trades + corporate actions into
 * realized sales (same [AveragePriceCalculator] the custody/position screens use), tags each sale
 * with its asset kind — the FII bucket and whether it is exemption-eligible (only STOCK is) — and
 * flags same-day (day-trade) sales, and hands the flattened list to the pure calculator.
 */
@Service
class CapitalGainsService(
    private val listedAssetRepository: IListedAssetRepository,
    private val tradeRepository: ITradeRepository,
    private val corporateActionRepository: ICorporateActionRepository,
    private val averagePriceCalculator: AveragePriceCalculator,
    private val calculator: CapitalGainsCalculator,
) {

    suspend fun monthlyReport(): List<MonthlyCapitalGain> {
        val assets = listedAssetRepository.fetchAll()

        val sales = assets.flatMap { asset ->
            val trades = tradeRepository.fetchByAssetId(asset.id)
            val corporateActions = corporateActionRepository.fetchByAssetId(asset.id)
            val gains = averagePriceCalculator.calculate(trades, corporateActions).realizedGains

            // A buy and a sell of the same ticker on the same day is a day trade — the same
            // definition the sale-ceiling meter applies on the ledger side (OrderPlanService).
            val boughtDates = trades.filterIsInstance<Trade.Buy>().map { it.date }.toSet()

            gains.map { gain ->
                TaxableSale(
                    date = gain.date,
                    isFii = asset.kind == AssetKind.FII,
                    isDayTrade = gain.date in boughtDates,
                    exemptible = asset.kind == AssetKind.STOCK,
                    proceeds = gain.proceeds,
                    costBasis = gain.costBasis,
                )
            }
        }

        return calculator.calculate(sales)
    }
}
