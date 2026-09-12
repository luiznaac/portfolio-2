package dev.agner.portfolio.usecase.income

import dev.agner.portfolio.usecase.commons.defaultScale
import dev.agner.portfolio.usecase.commons.isZero
import dev.agner.portfolio.usecase.corporateaction.model.CorporateAction
import dev.agner.portfolio.usecase.corporateaction.repository.ICorporateActionRepository
import dev.agner.portfolio.usecase.income.model.AssetIncomeSummary
import dev.agner.portfolio.usecase.income.model.IncomeEvent
import dev.agner.portfolio.usecase.income.model.IncomeReconciliation
import dev.agner.portfolio.usecase.income.model.ReceivedIncome
import dev.agner.portfolio.usecase.listedasset.gateway.IDividendGateway
import dev.agner.portfolio.usecase.listedasset.model.DividendDeclaration
import dev.agner.portfolio.usecase.listedasset.model.DividendType
import dev.agner.portfolio.usecase.listedasset.model.DividendType.JCP
import dev.agner.portfolio.usecase.listedasset.model.ListedAsset
import dev.agner.portfolio.usecase.listedasset.repository.IListedAssetRepository
import dev.agner.portfolio.usecase.tax.TaxRules
import dev.agner.portfolio.usecase.trade.AveragePriceCalculator
import dev.agner.portfolio.usecase.trade.model.Trade
import dev.agner.portfolio.usecase.trade.repository.ITradeRepository
import kotlinx.datetime.LocalDate
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Turns [dev.agner.portfolio.usecase.listedasset.model.DividendDeclaration]s (declared, gross, per
 * share) into money for the position actually held on each ex-date, and reconciles that expected
 * amount against what the monthly statement says actually landed.
 */
@Service
class IncomeService(
    private val listedAssetRepository: IListedAssetRepository,
    private val tradeRepository: ITradeRepository,
    private val corporateActionRepository: ICorporateActionRepository,
    private val dividendGateway: IDividendGateway,
    private val averagePriceCalculator: AveragePriceCalculator,
) {

    suspend fun eventsForAsset(assetId: Int): List<IncomeEvent> {
        val asset = listedAssetRepository.fetchById(assetId) ?: return emptyList()
        return eventsFor(asset)
    }

    suspend fun summary(): List<AssetIncomeSummary> =
        listedAssetRepository.fetchAll().map { asset ->
            // Fetch each input once and share it between the events and the cost basis.
            val declarations = dividendGateway.getDividends(asset)
            val trades = tradeRepository.fetchByAssetId(asset.id)
            val corporateActions = corporateActionRepository.fetchByAssetId(asset.id)
            val events = if (declarations.isEmpty()) {
                emptyList()
            } else {
                events(asset, declarations, trades, corporateActions)
            }
            val costBasis = averagePriceCalculator.calculate(trades, corporateActions).position.totalCost

            val totalNet = events.sumOf { it.netAmount }
            AssetIncomeSummary(
                listedAssetId = asset.id,
                ticker = asset.ticker,
                totalNet = totalNet.defaultScale(),
                costBasis = costBasis,
                yieldOnCost = if (costBasis.isZero()) {
                    BigDecimal.ZERO
                } else {
                    totalNet.divide(costBasis, 4, RoundingMode.HALF_EVEN)
                },
            )
        }.filter { !it.totalNet.isZero() || !it.costBasis.isZero() }

    suspend fun reconcile(received: List<ReceivedIncome>): List<IncomeReconciliation> {
        val expectedByAsset = listedAssetRepository.fetchAll().associateWith { eventsFor(it) }

        val expectedByKey = expectedByAsset.values.flatten()
            .groupBy { ReconciliationKey(it.ticker, monthOf(it.paymentDate ?: it.exDate), it.type) }
            .mapValues { (_, group) -> group.sumOf { it.netAmount } }

        val receivedByKey = received
            .groupBy { ReconciliationKey(it.ticker, monthOf(it.date), it.type) }
            .mapValues { (_, group) -> group.sumOf { it.amount } }

        return (expectedByKey.keys + receivedByKey.keys).map { key ->
            IncomeReconciliation(
                ticker = key.ticker,
                month = key.month,
                type = key.type,
                expected = (expectedByKey[key] ?: BigDecimal.ZERO).defaultScale(),
                received = (receivedByKey[key] ?: BigDecimal.ZERO).defaultScale(),
            )
        }.sortedWith(compareBy({ it.month }, { it.ticker }))
    }

    private suspend fun eventsFor(asset: ListedAsset): List<IncomeEvent> {
        val declarations = dividendGateway.getDividends(asset)
        if (declarations.isEmpty()) return emptyList()

        return events(
            asset,
            declarations,
            tradeRepository.fetchByAssetId(asset.id),
            corporateActionRepository.fetchByAssetId(asset.id),
        )
    }

    private fun events(
        asset: ListedAsset,
        declarations: List<DividendDeclaration>,
        trades: List<Trade>,
        corporateActions: List<CorporateAction>,
    ): List<IncomeEvent> {
        return declarations.map { declaration ->
            val quantityHeld = averagePriceCalculator.calculate(
                trades.filter { it.date <= declaration.exDate },
                corporateActions.filter { it.date <= declaration.exDate },
            ).position.quantity.coerceAtLeast(BigDecimal.ZERO)

            val gross = (quantityHeld * declaration.valuePerShare).defaultScale()
            val retained = when (declaration.type) {
                JCP -> (gross * TaxRules.JCP_WITHHOLDING_RATE).defaultScale()
                DividendType.DIVIDEND, DividendType.FUND_INCOME -> BigDecimal.ZERO.defaultScale()
            }

            IncomeEvent(
                listedAssetId = asset.id,
                ticker = asset.ticker,
                type = declaration.type,
                exDate = declaration.exDate,
                paymentDate = declaration.paymentDate,
                quantityHeld = quantityHeld,
                grossAmount = gross,
                retainedTax = retained,
                netAmount = gross - retained,
            )
        }.filter { !it.quantityHeld.isZero() }
    }

    private fun monthOf(date: LocalDate) = LocalDate(date.year, date.month, 1)

    private data class ReconciliationKey(val ticker: String, val month: LocalDate, val type: DividendType)
}
