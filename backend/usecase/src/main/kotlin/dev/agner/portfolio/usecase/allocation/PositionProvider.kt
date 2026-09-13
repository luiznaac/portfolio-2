package dev.agner.portfolio.usecase.allocation

import dev.agner.portfolio.usecase.allocation.classification.repository.IProductClassificationRepository
import dev.agner.portfolio.usecase.allocation.model.AssetClass
import dev.agner.portfolio.usecase.allocation.model.FixedIncomeSubClass
import dev.agner.portfolio.usecase.bond.model.Bond
import dev.agner.portfolio.usecase.bond.model.Bond.FloatingRateBond
import dev.agner.portfolio.usecase.bond.position.BondPositionService
import dev.agner.portfolio.usecase.bond.repository.IBondRepository
import dev.agner.portfolio.usecase.checkingaccount.repository.ICheckingAccountRepository
import dev.agner.portfolio.usecase.consolidation.ProductType
import dev.agner.portfolio.usecase.consolidation.ProductType.BOND
import dev.agner.portfolio.usecase.consolidation.ProductType.CHECKING_ACCOUNT
import dev.agner.portfolio.usecase.consolidation.ProductType.LISTED_ASSET
import dev.agner.portfolio.usecase.index.model.IndexId
import dev.agner.portfolio.usecase.listedasset.model.AssetKind
import dev.agner.portfolio.usecase.listedasset.position.ListedAssetPositionService
import dev.agner.portfolio.usecase.listedasset.repository.IListedAssetRepository
import org.springframework.stereotype.Component
import java.math.BigDecimal

/**
 * Values every tracked product at its latest position and buckets it by [AssetClass] (and, for
 * fixed income, by [FixedIncomeSubClass]) for [RebalanceCalculator]. An explicit classification
 * overrides the default class of the product's type.
 */
@Component
class PositionProvider(
    private val classificationRepository: IProductClassificationRepository,
    private val bondRepository: IBondRepository,
    private val checkingAccountRepository: ICheckingAccountRepository,
    private val listedAssetRepository: IListedAssetRepository,
    private val bondPositionService: BondPositionService,
    private val listedAssetPositionService: ListedAssetPositionService,
) {

    suspend fun fetchCurrentPositions(): CurrentPositions {
        val overrides = classificationRepository.fetchAll()
            .associate { (it.productType to it.productId) to it.assetClass }
        val positions = CurrentPositions(overrides)

        for (bond in bondRepository.fetchAll()) {
            val value = bondPositionService.getByBondId(bond.id).lastOrNull()?.let { it.principal + it.yield }
                ?: continue
            positions.add(BOND, bond.id, AssetClass.FIXED_INCOME, value, subClassOf(bond))
        }

        for (account in checkingAccountRepository.fetchAll()) {
            val value = bondPositionService.getByCheckingAccountId(account.id).lastOrNull()
                ?.let { it.principal + it.yield } ?: continue
            positions.add(CHECKING_ACCOUNT, account.id, AssetClass.FIXED_INCOME, value, subClassOf(account.indexId))
        }

        for (asset in listedAssetRepository.fetchAll()) {
            val value = listedAssetPositionService.getByAssetId(asset.id).lastOrNull()
                ?.let { it.principal + it.yield } ?: continue
            // No IndexId to derive a fixed-income subclass from if a listed asset gets
            // reclassified into FIXED_INCOME — contributes to the class total, not a sub-bucket.
            positions.add(LISTED_ASSET, asset.id, defaultClassOf(asset.kind), value, null)
        }

        return positions
    }

    private fun defaultClassOf(kind: AssetKind): AssetClass = when (kind) {
        AssetKind.FII -> AssetClass.REAL_ESTATE
        AssetKind.STOCK, AssetKind.ETF, AssetKind.BDR -> AssetClass.STOCKS
    }

    private fun subClassOf(bond: Bond): FixedIncomeSubClass =
        if (bond is FloatingRateBond) subClassOf(bond.indexId) else FixedIncomeSubClass.FIXED_RATE

    private fun subClassOf(indexId: IndexId): FixedIncomeSubClass = when (indexId) {
        IndexId.CDI, IndexId.SELIC -> FixedIncomeSubClass.FLOATING_RATE
        IndexId.IPCA -> FixedIncomeSubClass.INFLATION_LINKED
    }
}

/**
 * Accumulated position totals, mutable while [PositionProvider] assembles them and read by
 * [RebalanceCalculator] once complete.
 */
class CurrentPositions(
    private val overrides: Map<Pair<ProductType, Int>, AssetClass>,
) {
    val byClass = mutableMapOf<AssetClass, BigDecimal>()
    val bySubClass = mutableMapOf<FixedIncomeSubClass, BigDecimal>()

    fun add(
        productType: ProductType,
        productId: Int,
        default: AssetClass,
        value: BigDecimal,
        subClass: FixedIncomeSubClass?,
    ) {
        val assetClass = overrides[productType to productId] ?: default
        byClass.merge(assetClass, value, BigDecimal::add)
        if (assetClass == AssetClass.FIXED_INCOME && subClass != null) {
            bySubClass.merge(subClass, value, BigDecimal::add)
        }
    }
}
