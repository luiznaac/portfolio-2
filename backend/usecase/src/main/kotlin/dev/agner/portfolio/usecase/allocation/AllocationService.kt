package dev.agner.portfolio.usecase.allocation

import dev.agner.portfolio.usecase.allocation.classification.model.ProductClassification
import dev.agner.portfolio.usecase.allocation.classification.repository.IProductClassificationRepository
import dev.agner.portfolio.usecase.allocation.model.AllocationPlan
import dev.agner.portfolio.usecase.allocation.model.AssetClass
import dev.agner.portfolio.usecase.allocation.model.AssetClassTargetCreation
import dev.agner.portfolio.usecase.allocation.model.CapitalSnapshotCreation
import dev.agner.portfolio.usecase.allocation.model.FixedIncomeSubClass
import dev.agner.portfolio.usecase.allocation.model.FixedIncomeSubClassTargetCreation
import dev.agner.portfolio.usecase.allocation.repository.IAssetClassTargetRepository
import dev.agner.portfolio.usecase.allocation.repository.ICapitalSnapshotRepository
import dev.agner.portfolio.usecase.allocation.repository.IFixedIncomeSubClassTargetRepository
import dev.agner.portfolio.usecase.bond.model.Bond
import dev.agner.portfolio.usecase.bond.model.Bond.FloatingRateBond
import dev.agner.portfolio.usecase.bond.position.BondPositionService
import dev.agner.portfolio.usecase.bond.repository.IBondRepository
import dev.agner.portfolio.usecase.checkingaccount.repository.ICheckingAccountRepository
import dev.agner.portfolio.usecase.commons.today
import dev.agner.portfolio.usecase.consolidation.ProductType
import dev.agner.portfolio.usecase.consolidation.ProductType.BOND
import dev.agner.portfolio.usecase.consolidation.ProductType.CHECKING_ACCOUNT
import dev.agner.portfolio.usecase.consolidation.ProductType.LISTED_ASSET
import dev.agner.portfolio.usecase.index.model.IndexId
import dev.agner.portfolio.usecase.listedasset.model.AssetKind
import dev.agner.portfolio.usecase.listedasset.position.ListedAssetPositionService
import dev.agner.portfolio.usecase.listedasset.repository.IListedAssetRepository
import kotlinx.datetime.LocalDate
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.Clock

/**
 * Orchestrates the pieces RebalanceCalculator needs: latest capital, current targets, and each
 * tracked product's current value bucketed by AssetClass (and, for FIXED_INCOME, by
 * FixedIncomeSubClass). The calculator itself stays pure and untouched by any of this fetching.
 */
@Service
class AllocationService(
    private val capitalSnapshotRepository: ICapitalSnapshotRepository,
    private val classTargetRepository: IAssetClassTargetRepository,
    private val subClassTargetRepository: IFixedIncomeSubClassTargetRepository,
    private val classificationRepository: IProductClassificationRepository,
    private val bondRepository: IBondRepository,
    private val checkingAccountRepository: ICheckingAccountRepository,
    private val listedAssetRepository: IListedAssetRepository,
    private val bondPositionService: BondPositionService,
    private val listedAssetPositionService: ListedAssetPositionService,
    private val calculator: RebalanceCalculator,
    private val clock: Clock,
) {

    suspend fun recordCapitalSnapshot(creation: CapitalSnapshotCreation) =
        capitalSnapshotRepository.save(creation)

    suspend fun fetchCapitalHistory() = capitalSnapshotRepository.fetchAll()

    suspend fun setClassTarget(creation: AssetClassTargetCreation) = classTargetRepository.save(creation)

    suspend fun fetchClassTargetHistory() = classTargetRepository.fetchAll()

    suspend fun setFixedIncomeSubClassTarget(creation: FixedIncomeSubClassTargetCreation) =
        subClassTargetRepository.save(creation)

    suspend fun fetchFixedIncomeSubClassTargetHistory() = subClassTargetRepository.fetchAll()

    suspend fun classify(classification: ProductClassification) = classificationRepository.save(classification)

    suspend fun fetchClassifications() = classificationRepository.fetchAll()

    suspend fun currentPlan(): AllocationPlan {
        val today = LocalDate.today(clock)
        val capital = capitalSnapshotRepository.fetchLast()?.total ?: BigDecimal.ZERO
        val classTargets = classTargetRepository.fetchCurrent(today)
        val subClassTargets = subClassTargetRepository.fetchCurrent(today)
        val overrides = classificationRepository.fetchAll()
            .associate { (it.productType to it.productId) to it.assetClass }

        val currentByClass = mutableMapOf<AssetClass, BigDecimal>()
        val currentBySubClass = mutableMapOf<FixedIncomeSubClass, BigDecimal>()

        fun add(
            productType: ProductType,
            productId: Int,
            default: AssetClass,
            value: BigDecimal,
            subClass: FixedIncomeSubClass?,
        ) {
            val assetClass = overrides[productType to productId] ?: default
            currentByClass.merge(assetClass, value, BigDecimal::add)
            if (assetClass == AssetClass.FIXED_INCOME && subClass != null) {
                currentBySubClass.merge(subClass, value, BigDecimal::add)
            }
        }

        for (bond in bondRepository.fetchAll()) {
            val value = bondPositionService.getByBondId(bond.id).lastOrNull()?.let { it.principal + it.yield }
                ?: continue
            add(BOND, bond.id, AssetClass.FIXED_INCOME, value, subClassOf(bond))
        }

        for (account in checkingAccountRepository.fetchAll()) {
            val value = bondPositionService.getByCheckingAccountId(account.id).lastOrNull()
                ?.let { it.principal + it.yield } ?: continue
            add(CHECKING_ACCOUNT, account.id, AssetClass.FIXED_INCOME, value, subClassOf(account.indexId))
        }

        for (asset in listedAssetRepository.fetchAll()) {
            val value = listedAssetPositionService.getByAssetId(asset.id).lastOrNull()
                ?.let { it.principal + it.yield } ?: continue
            // No IndexId to derive a fixed-income subclass from if a listed asset gets
            // reclassified into FIXED_INCOME — contributes to the class total, not a sub-bucket.
            add(LISTED_ASSET, asset.id, defaultClassOf(asset.kind), value, null)
        }

        return calculator.calculate(capital, classTargets, subClassTargets, currentByClass, currentBySubClass)
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
