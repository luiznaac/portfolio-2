package dev.agner.portfolio.usecase.tax.capitalgains

import dev.agner.portfolio.usecase.corporateaction.repository.ICorporateActionRepository
import dev.agner.portfolio.usecase.listedasset.model.AssetKind
import dev.agner.portfolio.usecase.listedasset.repository.IListedAssetRepository
import dev.agner.portfolio.usecase.tax.capitalgains.model.MonthlyCapitalGain
import dev.agner.portfolio.usecase.trade.AveragePriceCalculator
import dev.agner.portfolio.usecase.trade.repository.ITradeRepository
import org.springframework.stereotype.Service

/**
 * Orchestrates [CapitalGainsCalculator]: replays every asset's trades + corporate actions into
 * realized sales (same [AveragePriceCalculator] the custody/position screens use), tags each sale
 * stock-or-FII, and hands the flattened list to the pure calculator.
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

            gains.map { gain ->
                TaxableSale(
                    date = gain.date,
                    isFii = asset.kind == AssetKind.FII,
                    proceeds = gain.proceeds,
                    costBasis = gain.costBasis,
                )
            }
        }

        return calculator.calculate(sales)
    }
}
