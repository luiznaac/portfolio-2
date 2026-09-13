package dev.agner.portfolio.usecase.order

import dev.agner.portfolio.usecase.allocation.AllocationService
import dev.agner.portfolio.usecase.allocation.model.AssetClass.STOCKS
import dev.agner.portfolio.usecase.attribution.AttributionService
import dev.agner.portfolio.usecase.configuration.ITransactionTemplate
import dev.agner.portfolio.usecase.listedasset.gateway.IQuoteGateway
import dev.agner.portfolio.usecase.listedasset.model.AssetKind.STOCK
import dev.agner.portfolio.usecase.listedasset.model.ListedAsset
import dev.agner.portfolio.usecase.listedasset.repository.IListedAssetRepository
import dev.agner.portfolio.usecase.order.model.TransferProposal
import dev.agner.portfolio.usecase.order.model.TransferProposalStatus
import dev.agner.portfolio.usecase.order.model.TransferProposalStatus.PENDING
import dev.agner.portfolio.usecase.order.model.TransferSettings
import dev.agner.portfolio.usecase.order.repository.ITransferProposalRepository
import dev.agner.portfolio.usecase.order.repository.ITransferSettingsRepository
import dev.agner.portfolio.usecase.strategy.StrategyEditionService
import dev.agner.portfolio.usecase.strategy.StrategyService
import dev.agner.portfolio.usecase.strategy.model.Strategy
import dev.agner.portfolio.usecase.strategy.model.StrategyEdition
import dev.agner.portfolio.usecase.strategy.model.StrategyEditionWithDiff
import dev.agner.portfolio.usecase.strategy.model.StrategyTarget
import dev.agner.portfolio.usecase.strategy.repository.IStrategyWeightRepository
import dev.agner.portfolio.usecase.trade.repository.ITradeRepository
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.datetime.LocalDate
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

val top = Strategy(id = 1, name = "Top", assetClass = STOCKS)
val petr4 = ListedAsset(id = 10, ticker = "PETR4", kind = STOCK, name = "Petrobras", b3Identifier = "PETROBRAS")
val vale3 = ListedAsset(id = 11, ticker = "VALE3", kind = STOCK, name = "Vale", b3Identifier = "VALE")
val today = LocalDate.parse("2026-09-15")

fun edition(strategyId: Int, targets: List<StrategyTarget>) = StrategyEditionWithDiff(
    edition = StrategyEdition(1, strategyId, LocalDate.parse("2026-09-01"), null, targets),
    diff = null,
)

fun proposal(
    toStrategyId: Int,
    toStrategyName: String,
    quantity: String,
    status: TransferProposalStatus = PENDING,
) = TransferProposal(
    id = toStrategyId,
    month = LocalDate.parse("2026-09-01"),
    listedAssetId = 10,
    ticker = "PETR4",
    fromStrategyId = 1,
    fromStrategyName = "Top",
    toStrategyId = toStrategyId,
    toStrategyName = toStrategyName,
    proposedQuantity = BigDecimal(quantity),
    appliedQuantity = null,
    status = status,
    decidedAt = null,
)

data class OrderPlanTestEnv(
    val strategyService: StrategyService,
    val strategyWeightRepository: IStrategyWeightRepository,
    val strategyEditionService: StrategyEditionService,
    val allocationService: AllocationService,
    val attributionService: AttributionService,
    val listedAssetRepository: IListedAssetRepository,
    val tradeRepository: ITradeRepository,
    val quoteGateway: IQuoteGateway,
    val transferProposalRepository: ITransferProposalRepository,
    val transferSettingsRepository: ITransferSettingsRepository,
    val clock: Clock,
) {
    // Runs the block verbatim, like the real TransactionService: a failure inside the block
    // propagates out, so the tests can assert what ran before the rollback.
    val transaction = object : ITransactionTemplate {
        override suspend fun <T> execute(block: suspend () -> T): T = block()
    }

    val service = OrderPlanService(
        OrderPlanAssembler(
            StrategyIdealProvider(
                strategyService,
                strategyWeightRepository,
                strategyEditionService,
                allocationService,
                clock,
            ),
            attributionService,
            listedAssetRepository,
            quoteGateway,
            TransferMatcher(),
            TradeLedger(tradeRepository),
        ),
        transferProposalRepository,
        transferSettingsRepository,
        attributionService,
        transaction,
        clock,
    )

    fun stubDefaults() {
        clearAllMocks()
        every { clock.instant() } returns Instant.parse("2026-09-15T12:00:00Z")
        every { clock.zone } returns ZoneOffset.UTC
        coEvery { tradeRepository.fetchByAssetId(any()) } returns emptyList()
        coEvery { tradeRepository.fetchByDateRange(any(), any()) } returns emptyList()
        coEvery { transferSettingsRepository.fetch() } returns TransferSettings(BigDecimal.ZERO)
        // Only refreshPlan() reconciles the month's existing proposals; tests that care stub a
        // non-empty month on top of this default.
        coEvery { transferProposalRepository.fetchByMonth(any()) } returns emptyList()
    }
}

fun orderPlanTestEnv() = OrderPlanTestEnv(
    strategyService = mockk<StrategyService>(),
    strategyWeightRepository = mockk<IStrategyWeightRepository>(),
    strategyEditionService = mockk<StrategyEditionService>(),
    allocationService = mockk<AllocationService>(),
    attributionService = mockk<AttributionService>(),
    listedAssetRepository = mockk<IListedAssetRepository>(),
    tradeRepository = mockk<ITradeRepository>(),
    quoteGateway = mockk<IQuoteGateway>(),
    transferProposalRepository = mockk<ITransferProposalRepository>(),
    transferSettingsRepository = mockk<ITransferSettingsRepository>(),
    clock = mockk<Clock>(),
)
