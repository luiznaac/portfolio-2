package dev.agner.portfolio.usecase.order

import dev.agner.portfolio.usecase.allocation.AllocationService
import dev.agner.portfolio.usecase.attribution.AttributionService
import dev.agner.portfolio.usecase.attribution.model.AttributionMovementCreation
import dev.agner.portfolio.usecase.attribution.model.AttributionReason.TRANSFERENCIA
import dev.agner.portfolio.usecase.commons.isZero
import dev.agner.portfolio.usecase.commons.now
import dev.agner.portfolio.usecase.commons.today
import dev.agner.portfolio.usecase.listedasset.gateway.IQuoteGateway
import dev.agner.portfolio.usecase.listedasset.model.AssetKind
import dev.agner.portfolio.usecase.listedasset.model.ListedAsset
import dev.agner.portfolio.usecase.listedasset.repository.IListedAssetRepository
import dev.agner.portfolio.usecase.order.model.Order
import dev.agner.portfolio.usecase.order.model.OrderKind
import dev.agner.portfolio.usecase.order.model.OrderPlan
import dev.agner.portfolio.usecase.order.model.SaleCeiling
import dev.agner.portfolio.usecase.order.model.StrategyDelta
import dev.agner.portfolio.usecase.order.model.TransferProposal
import dev.agner.portfolio.usecase.order.model.TransferProposalCreation
import dev.agner.portfolio.usecase.order.model.TransferProposalStatus.APLICADA
import dev.agner.portfolio.usecase.order.model.TransferProposalStatus.PENDENTE
import dev.agner.portfolio.usecase.order.model.TransferProposalStatus.REJEITADA
import dev.agner.portfolio.usecase.order.repository.ITransferProposalRepository
import dev.agner.portfolio.usecase.order.repository.ITransferSettingsRepository
import dev.agner.portfolio.usecase.strategy.StrategyEditionService
import dev.agner.portfolio.usecase.strategy.StrategyService
import dev.agner.portfolio.usecase.strategy.repository.IStrategyWeightRepository
import dev.agner.portfolio.usecase.trade.repository.ITradeRepository
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock

/**
 * Assembles the month's executable order list: per strategy per ticker, ideal quantity (from the
 * strategy's own share of its AssetClass's ideal capital — see [dev.agner.portfolio.usecase.strategy.model.StrategyWeight] —
 * times its latest [dev.agner.portfolio.usecase.strategy.model.StrategyEdition]'s target weight)
 * against current attributed quantity, netted per ticker across strategies into one order, with
 * same-ticker excess/shortage matched by [TransferMatcher] first and reconciled here against
 * persisted [TransferProposal]s — the plan's full "proposta -> aprovada/rejeitada" lifecycle
 * (Fases §3), not the fresh-every-request shortcut this service started with.
 */
@Service
class OrderPlanService(
    private val strategyService: StrategyService,
    private val strategyWeightRepository: IStrategyWeightRepository,
    private val strategyEditionService: StrategyEditionService,
    private val allocationService: AllocationService,
    private val attributionService: AttributionService,
    private val listedAssetRepository: IListedAssetRepository,
    private val tradeRepository: ITradeRepository,
    private val quoteGateway: IQuoteGateway,
    private val transferMatcher: TransferMatcher,
    private val transferProposalRepository: ITransferProposalRepository,
    private val transferSettingsRepository: ITransferSettingsRepository,
    private val clock: Clock,
) {

    suspend fun computePlan(): OrderPlan {
        val today = LocalDate.today(clock)
        val month = monthOf(today)
        val strategies = strategyService.fetchAll()
        val strategyById = strategies.associateBy { it.id }
        val strategyNames = strategies.associate { it.id to it.name }

        val weightByStrategy = strategyWeightRepository.fetchCurrent(today).associate { it.strategyId to it.weight }
        val classIdealByClass = allocationService.currentPlan().classes.associate { it.assetClass to it.ideal }
        val idealCapitalByStrategy = strategies.associate { s ->
            val weight = weightByStrategy[s.id] ?: BigDecimal.ZERO
            val classIdeal = classIdealByClass[s.assetClass] ?: BigDecimal.ZERO
            s.id to (weight * classIdeal)
        }

        // latest edition's targets per strategy, keyed by ticker for O(1) lookup
        val latestTargetsByStrategy = strategies.associate { s ->
            val latest = strategyEditionService.fetchEditions(s.id).lastOrNull()?.edition
            s.id to (latest?.targets.orEmpty().associate { it.ticker to it.weight })
        }

        val targetedTickers = latestTargetsByStrategy.values.flatMap { it.keys }.toSet()
        val relevantAssets = relevantAssets(targetedTickers, today)

        val orders = mutableListOf<Order>()
        val pendingProposals = mutableListOf<TransferProposal>()
        val threshold = transferSettingsRepository.fetch().autoApprovalThreshold

        for (asset in relevantAssets) {
            val summary = attributionService.summarize(asset.id)
            val quote = quoteGateway.getQuote(asset)
            val currentByStrategy = summary.balances.associate { it.strategyId to it.quantity }

            val targetingStrategyIds = strategyById.keys.filter {
                asset.ticker in latestTargetsByStrategy[it].orEmpty()
            }
            val involvedStrategyIds = (currentByStrategy.keys + targetingStrategyIds).toSet()

            val idealByStrategy = involvedStrategyIds.associateWith { strategyId ->
                val targetWeight = latestTargetsByStrategy[strategyId]?.get(asset.ticker) ?: BigDecimal.ZERO
                val idealCapital = idealCapitalByStrategy[strategyId] ?: BigDecimal.ZERO
                if (quote == null || quote.price <= BigDecimal.ZERO || targetWeight.isZero()) {
                    BigDecimal.ZERO
                } else {
                    (idealCapital * targetWeight).divide(quote.price, 0, RoundingMode.DOWN)
                }
            }
            val deltaByStrategy = involvedStrategyIds.associateWith { strategyId ->
                (currentByStrategy[strategyId] ?: BigDecimal.ZERO) - (idealByStrategy[strategyId] ?: BigDecimal.ZERO)
            }

            if (deltaByStrategy.values.any { !it.isZero() }) {
                val matches = transferMatcher.match(asset.id, asset.ticker, deltaByStrategy, strategyNames)
                for (match in matches) {
                    reconcileProposal(month, match, quote?.price, threshold)?.let { pendingProposals += it }
                }
            }

            val totalIdeal = idealByStrategy.values.sumOf { it }
            val netDelta = summary.custodyQuantity - totalIdeal
            if (netDelta.isZero() || quote == null) continue

            val kind = when {
                totalIdeal.isZero() && summary.custodyQuantity > BigDecimal.ZERO -> OrderKind.ZERAR
                summary.custodyQuantity.isZero() && totalIdeal > BigDecimal.ZERO -> OrderKind.ENTRADA_NOVA
                netDelta > BigDecimal.ZERO -> OrderKind.VENDER
                else -> OrderKind.COMPRAR
            }
            val quantity = netDelta.abs()

            orders += Order(
                listedAssetId = asset.id,
                ticker = asset.ticker,
                isFii = asset.kind == AssetKind.FII,
                kind = kind,
                quantity = quantity,
                notional = (quantity * quote.price).setScale(2, RoundingMode.HALF_EVEN),
                contributions = deltaByStrategy.filterValues { !it.isZero() }.map {
                    StrategyDelta(strategyId = it.key, strategyName = strategyNames[it.key].orEmpty(), delta = it.value)
                },
                dayTradeRisk = hasOppositeTradeToday(
                    asset.id,
                    today,
                    sell = kind == OrderKind.VENDER || kind == OrderKind.ZERAR,
                ),
            )
        }

        return OrderPlan(
            orders = orders,
            transferProposals = pendingProposals,
            saleCeiling = saleCeiling(today, orders),
        )
    }

    suspend fun transfersForMonth(): List<TransferProposal> = transferProposalRepository.fetchByMonth(
        monthOf(LocalDate.today(clock)),
    )

    suspend fun approveTransfer(id: Int, quantity: BigDecimal?): TransferProposal {
        val proposal = requireProposal(id)
        require(proposal.status == PENDENTE) { "Transfer proposal $id is not pending" }

        val approvedQuantity = quantity ?: proposal.proposedQuantity
        require(approvedQuantity > BigDecimal.ZERO && approvedQuantity <= proposal.proposedQuantity) {
            "Approved quantity must be between 0 and ${proposal.proposedQuantity}"
        }

        applyTransfer(proposal, approvedQuantity)
        return transferProposalRepository.decide(id, APLICADA, approvedQuantity, LocalDateTime.now(clock))
    }

    suspend fun rejectTransfer(id: Int): TransferProposal {
        val proposal = requireProposal(id)
        require(proposal.status == PENDENTE) { "Transfer proposal $id is not pending" }

        return transferProposalRepository.decide(id, REJEITADA, null, LocalDateTime.now(clock))
    }

    suspend fun transferSettings() = transferSettingsRepository.fetch()

    suspend fun setTransferSettings(autoApprovalThreshold: BigDecimal) =
        transferSettingsRepository.save(autoApprovalThreshold)

    private suspend fun reconcileProposal(
        month: LocalDate,
        match: TransferMatch,
        price: BigDecimal?,
        threshold: BigDecimal,
    ): TransferProposal? {
        val existing = transferProposalRepository.find(
            month,
            match.listedAssetId,
            match.fromStrategyId,
            match.toStrategyId,
        )

        val current = when {
            existing == null -> transferProposalRepository.save(
                TransferProposalCreation(
                    month = month,
                    listedAssetId = match.listedAssetId,
                    ticker = match.ticker,
                    fromStrategyId = match.fromStrategyId,
                    fromStrategyName = match.fromStrategyName,
                    toStrategyId = match.toStrategyId,
                    toStrategyName = match.toStrategyName,
                    proposedQuantity = match.quantity,
                ),
            )

            existing.status == PENDENTE && existing.proposedQuantity != match.quantity ->
                transferProposalRepository.updateProposedQuantity(existing.id, match.quantity)

            else -> existing
        }

        // A rejection or an already-applied transfer stands for the whole month — never
        // recreated or re-surfaced until the next competência.
        if (current.status != PENDENTE) return null

        val notional = price?.let { current.proposedQuantity * it }
        if (notional != null && notional <= threshold) {
            applyTransfer(current, current.proposedQuantity)
            transferProposalRepository.decide(current.id, APLICADA, current.proposedQuantity, LocalDateTime.now(clock))
            return null
        }

        return current
    }

    private suspend fun applyTransfer(proposal: TransferProposal, quantity: BigDecimal) {
        val today = LocalDate.today(clock)

        attributionService.recordMovement(
            AttributionMovementCreation(
                listedAssetId = proposal.listedAssetId,
                strategyId = proposal.fromStrategyId,
                date = today,
                quantity = quantity.negate(),
                reason = TRANSFERENCIA,
                note = "Transferência para ${proposal.toStrategyName} (proposta #${proposal.id})",
            ),
        )
        attributionService.recordMovement(
            AttributionMovementCreation(
                listedAssetId = proposal.listedAssetId,
                strategyId = proposal.toStrategyId,
                date = today,
                quantity = quantity,
                reason = TRANSFERENCIA,
                note = "Transferência de ${proposal.fromStrategyName} (proposta #${proposal.id})",
            ),
        )
    }

    private suspend fun requireProposal(id: Int): TransferProposal =
        transferProposalRepository.fetchByMonth(monthOf(LocalDate.today(clock))).find { it.id == id }
            ?: error("Transfer proposal $id not found for the current month")

    private fun monthOf(date: LocalDate) = LocalDate(date.year, date.month, 1)

    private suspend fun relevantAssets(targetedTickers: Set<String>, today: LocalDate): List<ListedAsset> {
        val allAssets = listedAssetRepository.fetchAll()
        val byTicker = allAssets.associateBy { it.ticker }

        val heldOrTargeted = allAssets.filter { asset ->
            asset.ticker in targetedTickers || !attributionService.summarize(asset.id).custodyQuantity.isZero()
        }

        // A targeted ticker with no registered ListedAsset yet (e.g. a new entrant not imported
        // into the app) simply can't be planned for — it has no id/quote to hang an order off of.
        // Left as a known gap rather than silently guessing an asset.
        return (heldOrTargeted.map { it.ticker } + targetedTickers.filter { it in byTicker })
            .toSet()
            .mapNotNull { byTicker[it] }
    }

    private suspend fun hasOppositeTradeToday(assetId: Int, today: LocalDate, sell: Boolean): Boolean =
        tradeRepository.fetchByAssetId(assetId).any { trade ->
            trade.date == today && if (sell) trade.quantity > BigDecimal.ZERO else trade.quantity < BigDecimal.ZERO
        }

    private suspend fun saleCeiling(today: LocalDate, orders: List<Order>): SaleCeiling {
        val monthStart = LocalDate(today.year, today.month, 1)
        val kindByAssetId = listedAssetRepository.fetchAll().associate { it.id to it.kind }

        val executedStockSales = tradeRepository.fetchAll()
            .filter { it.date in monthStart..today && it.quantity < BigDecimal.ZERO }
            .filter { kindByAssetId[it.assetId] == AssetKind.STOCK }
            .sumOf { it.quantity.abs() * it.price }

        val plannedStockSales = orders
            .filter { !it.isFii && (it.kind == OrderKind.VENDER || it.kind == OrderKind.ZERAR) }
            .sumOf { it.notional }

        val monthSold = executedStockSales + plannedStockSales
        val limit = BigDecimal("20000.00")
        val remaining = (limit - monthSold).max(BigDecimal.ZERO)

        return SaleCeiling(monthSold = monthSold, limit = limit, remaining = remaining, exceeded = monthSold > limit)
    }
}
