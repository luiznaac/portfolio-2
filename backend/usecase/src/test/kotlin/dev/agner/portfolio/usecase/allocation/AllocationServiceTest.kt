package dev.agner.portfolio.usecase.allocation

import dev.agner.portfolio.usecase.allocation.classification.model.ProductClassification
import dev.agner.portfolio.usecase.allocation.classification.repository.IProductClassificationRepository
import dev.agner.portfolio.usecase.allocation.model.AllocationPlan
import dev.agner.portfolio.usecase.allocation.model.AssetClass
import dev.agner.portfolio.usecase.allocation.model.AssetClassTarget
import dev.agner.portfolio.usecase.allocation.model.CapitalSnapshot
import dev.agner.portfolio.usecase.allocation.model.FixedIncomeSubClass
import dev.agner.portfolio.usecase.allocation.model.FixedIncomeSubClassTarget
import dev.agner.portfolio.usecase.allocation.repository.IAssetClassTargetRepository
import dev.agner.portfolio.usecase.allocation.repository.ICapitalSnapshotRepository
import dev.agner.portfolio.usecase.allocation.repository.IFixedIncomeSubClassTargetRepository
import dev.agner.portfolio.usecase.bond.model.Bond
import dev.agner.portfolio.usecase.bond.position.BondPositionService
import dev.agner.portfolio.usecase.bond.position.model.BondPosition
import dev.agner.portfolio.usecase.bond.repository.IBondRepository
import dev.agner.portfolio.usecase.checkingaccount.model.CheckingAccount
import dev.agner.portfolio.usecase.checkingaccount.position.CheckingAccountPosition
import dev.agner.portfolio.usecase.checkingaccount.repository.ICheckingAccountRepository
import dev.agner.portfolio.usecase.consolidation.ProductType
import dev.agner.portfolio.usecase.index.model.IndexId
import dev.agner.portfolio.usecase.listedasset.model.AssetKind
import dev.agner.portfolio.usecase.listedasset.model.ListedAsset
import dev.agner.portfolio.usecase.listedasset.position.ListedAssetPositionService
import dev.agner.portfolio.usecase.listedasset.position.model.ListedAssetPosition
import dev.agner.portfolio.usecase.listedasset.repository.IListedAssetRepository
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class AllocationServiceTest : StringSpec({

    val capitalSnapshotRepository = mockk<ICapitalSnapshotRepository>()
    val classTargetRepository = mockk<IAssetClassTargetRepository>()
    val subClassTargetRepository = mockk<IFixedIncomeSubClassTargetRepository>()
    val classificationRepository = mockk<IProductClassificationRepository>()
    val bondRepository = mockk<IBondRepository>()
    val checkingAccountRepository = mockk<ICheckingAccountRepository>()
    val listedAssetRepository = mockk<IListedAssetRepository>()
    val bondPositionService = mockk<BondPositionService>()
    val listedAssetPositionService = mockk<ListedAssetPositionService>()
    val clock = Clock.fixed(Instant.parse("2026-09-15T12:00:00Z"), ZoneOffset.UTC)

    val service = AllocationService(
        capitalSnapshotRepository,
        AllocationTargetsProvider(classTargetRepository, subClassTargetRepository, classificationRepository),
        PositionProvider(
            classificationRepository,
            bondRepository,
            checkingAccountRepository,
            listedAssetRepository,
            bondPositionService,
            listedAssetPositionService,
        ),
        RebalanceCalculator(),
        clock,
    )

    val today = LocalDate.parse("2026-09-15")

    fun capital(total: String) = CapitalSnapshot(
        id = 1,
        date = today,
        externalBalance = BigDecimal(total),
        plannedContribution = BigDecimal.ZERO,
    )

    fun classTarget(assetClass: AssetClass, weight: String) = AssetClassTarget(
        id = 1,
        assetClass = assetClass,
        weight = BigDecimal(weight),
        effectiveFrom = today,
    )

    fun subClassTarget(subClass: FixedIncomeSubClass, weight: String) = FixedIncomeSubClassTarget(
        id = 1,
        subClass = subClass,
        weight = BigDecimal(weight),
        effectiveFrom = today,
    )

    fun bondPosition(principal: String, yield: String) = BondPosition(
        date = today,
        principal = BigDecimal(principal),
        yield = BigDecimal(yield),
        taxes = BigDecimal.ZERO,
    )

    fun checkingPosition(principal: String, yield: String) = CheckingAccountPosition(
        date = today,
        principal = BigDecimal(principal),
        yield = BigDecimal(yield),
        taxes = BigDecimal.ZERO,
    )

    fun listedPosition(principal: String, yield: String) = ListedAssetPosition(
        date = today,
        principal = BigDecimal(principal),
        yield = BigDecimal(yield),
        taxes = BigDecimal.ZERO,
    )

    fun fixedBond(id: Int) =
        Bond.FixedRateBond(id = id, name = "Fixed $id", value = BigDecimal.ZERO, maturityDate = today)

    fun floatingBond(id: Int, indexId: IndexId) = Bond.FloatingRateBond(
        id = id,
        name = "Floating $id",
        value = BigDecimal.ZERO,
        maturityDate = today,
        indexId = indexId,
    )

    fun checkingAccount(id: Int, indexId: IndexId) = CheckingAccount(
        id = id,
        name = "Account $id",
        value = BigDecimal.ZERO,
        indexId = indexId,
        maturityDuration = DatePeriod(months = 1),
    )

    fun listedAsset(id: Int, kind: AssetKind) = ListedAsset(
        id = id,
        ticker = "TICKER$id",
        kind = kind,
        name = "Asset $id",
        b3Identifier = "ASSET$id",
    )

    fun stubEmptyPortfolio() {
        coEvery { capitalSnapshotRepository.fetchLast() } returns capital("1000.00")
        coEvery { classTargetRepository.fetchCurrent(today) } returns emptyList()
        coEvery { subClassTargetRepository.fetchCurrent(today) } returns emptyList()
        coEvery { classificationRepository.fetchAll() } returns emptyList()
        coEvery { bondRepository.fetchAll() } returns emptySet()
        coEvery { checkingAccountRepository.fetchAll() } returns emptySet()
        coEvery { listedAssetRepository.fetchAll() } returns emptyList()
    }

    beforeEach {
        clearAllMocks()
        stubEmptyPortfolio()
    }

    fun AllocationPlan.classNode(assetClass: AssetClass) = classes.first { it.assetClass == assetClass }

    fun AllocationPlan.subNode(subClass: FixedIncomeSubClass) =
        classNode(AssetClass.FIXED_INCOME).subClasses.first { it.subClass == subClass }

    "should compute each class ideal as weight times capital and default missing targets to zero" {
        coEvery { classTargetRepository.fetchCurrent(today) } returns listOf(classTarget(AssetClass.STOCKS, "0.5"))

        val plan = service.currentPlan()

        plan.capital shouldBe BigDecimal("1000.00")
        plan.classNode(AssetClass.STOCKS).idealWeight shouldBe BigDecimal("0.5")
        plan.classNode(AssetClass.STOCKS).ideal shouldBe BigDecimal("500.00")
        plan.classNode(AssetClass.FIXED_INCOME).ideal shouldBe BigDecimal("0.00")
        plan.classNode(AssetClass.ALTERNATIVES).current shouldBe BigDecimal("0.00")
        plan.classNode(AssetClass.CASH).idealWeight shouldBe BigDecimal.ZERO
    }

    "should bucket positions by class and by fixed-income sub-class derived from their index" {
        coEvery { classTargetRepository.fetchCurrent(today) } returns listOf(
            classTarget(AssetClass.FIXED_INCOME, "1"),
        )
        coEvery { subClassTargetRepository.fetchCurrent(today) } returns listOf(
            subClassTarget(FixedIncomeSubClass.FLOATING_RATE, "0.6"),
            subClassTarget(FixedIncomeSubClass.FIXED_RATE, "0.4"),
        )
        coEvery { bondRepository.fetchAll() } returns setOf(fixedBond(1), floatingBond(2, IndexId.CDI))
        coEvery { bondPositionService.getByBondId(1) } returns listOf(bondPosition("200.00", "50.00"))
        coEvery { bondPositionService.getByBondId(2) } returns listOf(bondPosition("100.00", "0.00"))
        coEvery { checkingAccountRepository.fetchAll() } returns setOf(checkingAccount(3, IndexId.SELIC))
        coEvery { bondPositionService.getByCheckingAccountId(3) } returns listOf(checkingPosition("300.00", "0.00"))

        val plan = service.currentPlan()

        plan.classNode(AssetClass.FIXED_INCOME).current shouldBe BigDecimal("650.00")
        plan.classNode(AssetClass.FIXED_INCOME).ideal shouldBe BigDecimal("1000.00")
        plan.subNode(FixedIncomeSubClass.FLOATING_RATE).current shouldBe BigDecimal("400.00")
        plan.subNode(FixedIncomeSubClass.FLOATING_RATE).ideal shouldBe BigDecimal("600.00")
        plan.subNode(FixedIncomeSubClass.FIXED_RATE).current shouldBe BigDecimal("250.00")
        plan.subNode(FixedIncomeSubClass.FIXED_RATE).ideal shouldBe BigDecimal("400.00")
        plan.subNode(FixedIncomeSubClass.INFLATION_LINKED).current shouldBe BigDecimal("0.00")
    }

    "should map listed asset kinds to their default asset class" {
        coEvery { listedAssetRepository.fetchAll() } returns listOf(
            listedAsset(1, AssetKind.FII),
            listedAsset(2, AssetKind.STOCK),
            listedAsset(3, AssetKind.ETF),
            listedAsset(4, AssetKind.BDR),
        )
        coEvery { listedAssetPositionService.getByAssetId(1) } returns listOf(listedPosition("400.00", "0.00"))
        coEvery { listedAssetPositionService.getByAssetId(2) } returns listOf(listedPosition("100.00", "0.00"))
        coEvery { listedAssetPositionService.getByAssetId(3) } returns listOf(listedPosition("200.00", "0.00"))
        coEvery { listedAssetPositionService.getByAssetId(4) } returns listOf(listedPosition("300.00", "0.00"))

        val plan = service.currentPlan()

        plan.classNode(AssetClass.REAL_ESTATE).current shouldBe BigDecimal("400.00")
        plan.classNode(AssetClass.STOCKS).current shouldBe BigDecimal("600.00")
    }

    "should let an explicit classification override the product type's default class" {
        coEvery { classificationRepository.fetchAll() } returns listOf(
            ProductClassification(ProductType.BOND, 1, AssetClass.ALTERNATIVES),
            ProductClassification(ProductType.LISTED_ASSET, 2, AssetClass.FIXED_INCOME),
        )
        coEvery { bondRepository.fetchAll() } returns setOf(fixedBond(1))
        coEvery { bondPositionService.getByBondId(1) } returns listOf(bondPosition("100.00", "0.00"))
        coEvery { listedAssetRepository.fetchAll() } returns listOf(listedAsset(2, AssetKind.FII))
        coEvery { listedAssetPositionService.getByAssetId(2) } returns listOf(listedPosition("200.00", "0.00"))

        val plan = service.currentPlan()

        plan.classNode(AssetClass.ALTERNATIVES).current shouldBe BigDecimal("100.00")
        plan.classNode(AssetClass.REAL_ESTATE).current shouldBe BigDecimal("0.00")
        plan.classNode(AssetClass.FIXED_INCOME).current shouldBe BigDecimal("200.00")
        // No index to derive a sub-class from a reclassified listed asset: class total only.
        plan.subNode(FixedIncomeSubClass.FIXED_RATE).current shouldBe BigDecimal("0.00")
        plan.subNode(FixedIncomeSubClass.FLOATING_RATE).current shouldBe BigDecimal("0.00")
    }

    "should derive the fixed-income sub-class from CDI, SELIC, IPCA and fixed rate products" {
        coEvery { bondRepository.fetchAll() } returns setOf(
            floatingBond(1, IndexId.CDI),
            floatingBond(2, IndexId.SELIC),
            floatingBond(3, IndexId.IPCA),
            fixedBond(4),
        )
        coEvery { bondPositionService.getByBondId(1) } returns listOf(bondPosition("100.00", "0.00"))
        coEvery { bondPositionService.getByBondId(2) } returns listOf(bondPosition("200.00", "0.00"))
        coEvery { bondPositionService.getByBondId(3) } returns listOf(bondPosition("300.00", "0.00"))
        coEvery { bondPositionService.getByBondId(4) } returns listOf(bondPosition("400.00", "0.00"))

        val plan = service.currentPlan()

        plan.subNode(FixedIncomeSubClass.FLOATING_RATE).current shouldBe BigDecimal("300.00")
        plan.subNode(FixedIncomeSubClass.INFLATION_LINKED).current shouldBe BigDecimal("300.00")
        plan.subNode(FixedIncomeSubClass.FIXED_RATE).current shouldBe BigDecimal("400.00")
    }

    "should default capital to zero when there is no snapshot and skip products without a position" {
        coEvery { capitalSnapshotRepository.fetchLast() } returns null
        coEvery { bondRepository.fetchAll() } returns setOf(fixedBond(1))
        coEvery { bondPositionService.getByBondId(1) } returns emptyList()
        coEvery { checkingAccountRepository.fetchAll() } returns setOf(checkingAccount(2, IndexId.CDI))
        coEvery { bondPositionService.getByCheckingAccountId(2) } returns emptyList()
        coEvery { listedAssetRepository.fetchAll() } returns listOf(listedAsset(3, AssetKind.STOCK))
        coEvery { listedAssetPositionService.getByAssetId(3) } returns emptyList()

        val plan = service.currentPlan()

        plan.capital shouldBe BigDecimal("0.00")
        plan.classes.all { it.current == BigDecimal("0.00") } shouldBe true
        plan.classes.all { it.ideal == BigDecimal("0.00") } shouldBe true
    }
})
