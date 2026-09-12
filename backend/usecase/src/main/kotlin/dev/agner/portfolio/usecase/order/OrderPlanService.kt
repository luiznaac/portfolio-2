package dev.agner.portfolio.usecase.order

import dev.agner.portfolio.usecase.allocation.AllocationService
import dev.agner.portfolio.usecase.attribution.AttributionService
import dev.agner.portfolio.usecase.attribution.model.AttributionMovementCreation
import dev.agner.portfolio.usecase.attribution.model.AttributionReason.TRANSFER
import dev.agner.portfolio.usecase.attribution.model.AttributionSummary
import dev.agner.portfolio.usecase.commons.defaultScale
import dev.agner.portfolio.usecase.commons.isZero
import dev.agner.portfolio.usecase.commons.now
import dev.agner.portfolio.usecase.commons.today
import dev.agner.portfolio.usecase.configuration.ITransactionTemplate
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
import dev.agner.portfolio.usecase.order.model.TransferProposalStatus.APPLIED
import dev.agner.portfolio.usecase.order.model.TransferProposalStatus.PENDING
import dev.agner.portfolio.usecase.order.model.TransferProposalStatus.REJECTED
import dev.agner.portfolio.usecase.order.repository.ITransferProposalRepository
import dev.agner.portfolio.usecase.order.repository.ITransferSettingsRepository
import dev.agner.portfolio.usecase.strategy.StrategyEditionService
import dev.agner.portfolio.usecase.strategy.StrategyService
import dev.agner.portfolio.usecase.strategy.repository.IStrategyWeightRepository
import dev.agner.portfolio.usecase.tax.TaxRules
import dev.agner.portfolio.usecase.trade.model.Trade
import dev.agner.portfolio.usecase.trade.repository.ITradeRepository
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock

/**
 * Assembles the month's executable order list: per strategy per ticker, an ideal quantity (the
 * strategy's share of its asset class's ideal capital, times its latest edition's target weight
 * for that ticker) against the currently attributed quantity, netted per ticker across strategies
 * into one order, with same-ticker excess and shortage matched by [TransferMatcher] first so the
 * residual trade is as small as possible.
 *
 * Reading and writing are deliberately separate. [computePlan] is pure: it never touches the
 * database, so callers that only want to look — the brokerage-note preview, the step-up planner,
 * a plain `GET` — cannot cause a transfer to be created or auto-applied as a side effect.
 * [refreshPlan] is the command that persists newly matched proposals, refreshes the quantity on
 * still-pending ones, and auto-applies anything under the configured threshold.
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
    private val transaction: ITransactionTemplate,
    private val clock: Clock,
) {

    /** Read-only. Transfer proposals come back exactly as they are stored, never created here. */
    suspend fun computePlan(): OrderPlan {
        val assembled = assemble()
        val stored = transferProposalRepository.fetchByMonth(currentMonth()).filter { it.status == PENDING }

        return OrderPlan(orders = assembled.orders, transferProposals = stored, saleCeiling = assembled.saleCeiling)
    }

    /** The write side: reconciles freshly matched transfers against what is already stored. */
    suspend fun refreshPlan(): OrderPlan {
        val assembled = assemble()
        val threshold = transferSettingsRepository.fetch().autoApprovalThreshold
        val month = currentMonth()

        val pending = assembled.matches.mapNotNull {
            reconcileProposal(month, it, assembled.strategyNames, threshold)
        }

        // A PENDING proposal the matcher did not return this run is stranded: its pairing
        // disappeared (a price or capital change, or a manual attribution movement), so there is
        // no path to approve or reject it any more. Expire it now — a rejection stands for the
        // month — so it stops coming back from transfersForMonth() and blocking the close.
        val liveIds = pending.mapTo(mutableSetOf()) { it.id }
        val stranded = transferProposalRepository.fetchByMonth(month)
            .filter { it.status == PENDING && it.id !in liveIds }
        for (proposal in stranded) {
            transferProposalRepository.decide(proposal.id, REJECTED, null, LocalDateTime.now(clock))
        }

        return OrderPlan(orders = assembled.orders, transferProposals = pending, saleCeiling = assembled.saleCeiling)
    }

    suspend fun transfersForMonth(): List<TransferProposal> = transferProposalRepository.fetchByMonth(currentMonth())

    suspend fun approveTransfer(id: Int, quantity: BigDecimal?): TransferProposal {
        val proposal = requireProposal(id)

        val approvedQuantity = quantity ?: proposal.proposedQuantity
        if (approvedQuantity <= BigDecimal.ZERO || approvedQuantity > proposal.proposedQuantity) {
            throw InvalidTransferQuantityException(approvedQuantity, proposal.proposedQuantity)
        }

        return applyAndDecide(proposal, approvedQuantity)
    }

    suspend fun rejectTransfer(id: Int): TransferProposal {
        val proposal = requireProposal(id)

        return transferProposalRepository.decide(proposal.id, REJECTED, null, LocalDateTime.now(clock))
    }

    suspend fun transferSettings() = transferSettingsRepository.fetch()

    suspend fun setTransferSettings(autoApprovalThreshold: BigDecimal) =
        transferSettingsRepository.save(autoApprovalThreshold)

    private suspend fun assemble(): AssembledPlan {
        val today = LocalDate.today(clock)
        val strategies = strategyService.fetchAll()
        val strategyNames = strategies.associate { it.id to it.name }

        val weightByStrategy = strategyWeightRepository.fetchCurrent(today).associate { it.strategyId to it.weight }
        val classIdealByClass = allocationService.currentPlan().classes.associate { it.assetClass to it.ideal }
        val idealCapitalByStrategy = strategies.associate { strategy ->
            val weight = weightByStrategy[strategy.id] ?: BigDecimal.ZERO
            val classIdeal = classIdealByClass[strategy.assetClass] ?: BigDecimal.ZERO
            strategy.id to (weight * classIdeal)
        }

        val targetsByStrategy = strategies.associate { strategy ->
            val latest = strategyEditionService.fetchEditions(strategy.id).lastOrNull()?.edition
            strategy.id to latest?.targets.orEmpty().associate { it.ticker to it.weight }
        }
        val targetedTickers = targetsByStrategy.values.flatMap { it.keys }.toSet()

        // Every asset fetched once — the per-asset loop below would otherwise issue one query per
        // asset for the attribution replay.
        val allAssets = listedAssetRepository.fetchAll()
        val summaryByAsset = allAssets.associate { it.id to attributionService.summarize(it.id) }

        val relevant = allAssets.filter {
            it.ticker in targetedTickers || summaryByAsset.getValue(it.id).custodyQuantity.isZero().not()
        }

        val orders = mutableListOf<Order>()
        val matches = mutableListOf<PricedMatch>()

        for (asset in relevant) {
            val summary = summaryByAsset.getValue(asset.id)
            val quote = quoteGateway.getQuote(asset)
            val price = quote?.price

            val idealByStrategy = idealByStrategy(asset, summary, targetsByStrategy, idealCapitalByStrategy, price)
            val currentByStrategy = summary.balances.associate { it.strategyId to it.quantity }
            val deltaByStrategy = idealByStrategy.keys.associateWith {
                (currentByStrategy[it] ?: BigDecimal.ZERO) - (idealByStrategy[it] ?: BigDecimal.ZERO)
            }

            if (deltaByStrategy.values.any { !it.isZero() }) {
                matches += transferMatcher.match(asset.id, asset.ticker, deltaByStrategy)
                    .map { PricedMatch(it, price) }
            }

            buildOrder(asset, summary, idealByStrategy, deltaByStrategy, strategyNames, price, today)
                ?.let { orders += it }
        }

        return AssembledPlan(orders, matches, strategyNames, saleCeiling(today, orders, allAssets))
    }

    private fun idealByStrategy(
        asset: ListedAsset,
        summary: AttributionSummary,
        targetsByStrategy: Map<Int, Map<String, BigDecimal>>,
        idealCapitalByStrategy: Map<Int, BigDecimal>,
        price: BigDecimal?,
    ): Map<Int, BigDecimal> {
        val holding = summary.balances.map { it.strategyId }
        val targeting = targetsByStrategy.filterValues { asset.ticker in it }.keys

        return (holding + targeting).toSet().associateWith { strategyId ->
            val targetWeight = targetsByStrategy[strategyId]?.get(asset.ticker) ?: BigDecimal.ZERO
            val idealCapital = idealCapitalByStrategy[strategyId] ?: BigDecimal.ZERO

            if (price == null || price <= BigDecimal.ZERO || targetWeight.isZero()) {
                BigDecimal.ZERO
            } else {
                (idealCapital * targetWeight).divide(price, 0, RoundingMode.DOWN)
            }
        }
    }

    private suspend fun buildOrder(
        asset: ListedAsset,
        summary: AttributionSummary,
        idealByStrategy: Map<Int, BigDecimal>,
        deltaByStrategy: Map<Int, BigDecimal>,
        strategyNames: Map<Int, String>,
        price: BigDecimal?,
        today: LocalDate,
    ): Order? {
        val totalIdeal = idealByStrategy.values.sumOf { it }
        val netDelta = summary.custodyQuantity - totalIdeal
        if (netDelta.isZero() || price == null) return null

        val kind = when {
            totalIdeal.isZero() && summary.custodyQuantity > BigDecimal.ZERO -> OrderKind.FULL_EXIT
            summary.custodyQuantity.isZero() && totalIdeal > BigDecimal.ZERO -> OrderKind.NEW_ENTRY
            netDelta > BigDecimal.ZERO -> OrderKind.SELL
            else -> OrderKind.BUY
        }
        val quantity = netDelta.abs()

        return Order(
            listedAssetId = asset.id,
            ticker = asset.ticker,
            isFii = asset.kind == AssetKind.FII,
            kind = kind,
            quantity = quantity,
            notional = (quantity * price).defaultScale(),
            contributions = deltaByStrategy.filterValues { !it.isZero() }.map {
                StrategyDelta(strategyId = it.key, strategyName = strategyNames[it.key].orEmpty(), delta = it.value)
            },
            dayTradeRisk = hasOppositeTradeToday(asset.id, today, isSale = kind.isSale),
        )
    }

    private suspend fun reconcileProposal(
        month: LocalDate,
        priced: PricedMatch,
        strategyNames: Map<Int, String>,
        threshold: BigDecimal,
    ): TransferProposal? {
        val match = priced.match
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
                    fromStrategyName = strategyNames[match.fromStrategyId].orEmpty(),
                    toStrategyId = match.toStrategyId,
                    toStrategyName = strategyNames[match.toStrategyId].orEmpty(),
                    proposedQuantity = match.quantity,
                ),
            )

            // compareTo, not !=: the stored value comes back at the column's scale (18,8) while
            // the freshly matched one is scale 0, so `!=` would report a change on every call.
            existing.status == PENDING && existing.proposedQuantity.compareTo(match.quantity) != 0 ->
                transferProposalRepository.updateProposedQuantity(existing.id, match.quantity)

            else -> existing
        }

        // A rejection or an already-applied transfer stands for the whole month — never recreated
        // or re-surfaced until the next one.
        if (current.status != PENDING) return null

        val notional = priced.price?.let { current.proposedQuantity * it }
        if (notional != null && notional <= threshold) {
            applyAndDecide(current, current.proposedQuantity)
            return null
        }

        return current
    }

    /**
     * The attribution movements and the status change are one unit of work: applying the transfer
     * but failing to record the decision would leave the proposal PENDING and apply it a second
     * time on the next refresh.
     */
    private suspend fun applyAndDecide(proposal: TransferProposal, quantity: BigDecimal): TransferProposal =
        transaction.execute {
            val today = LocalDate.today(clock)

            attributionService.recordMovement(
                proposal.listedAssetId,
                AttributionMovementCreation(
                    strategyId = proposal.fromStrategyId,
                    date = today,
                    quantity = quantity.negate(),
                    reason = TRANSFER,
                    note = "Transfer to ${proposal.toStrategyName} (proposal #${proposal.id})",
                ),
            )
            attributionService.recordMovement(
                proposal.listedAssetId,
                AttributionMovementCreation(
                    strategyId = proposal.toStrategyId,
                    date = today,
                    quantity = quantity,
                    reason = TRANSFER,
                    note = "Transfer from ${proposal.fromStrategyName} (proposal #${proposal.id})",
                ),
            )

            transferProposalRepository.decide(proposal.id, APPLIED, quantity, LocalDateTime.now(clock))
        }

    private suspend fun requireProposal(id: Int): TransferProposal {
        val proposal = transferProposalRepository.fetchById(id)
            ?: throw TransferProposalNotFoundException(id)
        if (proposal.status != PENDING) throw TransferProposalNotPendingException(id, proposal.status)

        return proposal
    }

    private fun currentMonth() = LocalDate.today(clock).let { LocalDate(it.year, it.month, 1) }

    private suspend fun hasOppositeTradeToday(assetId: Int, today: LocalDate, isSale: Boolean): Boolean =
        tradeRepository.fetchByAssetId(assetId).any { trade ->
            trade.date == today && if (isSale) trade is Trade.Buy else trade is Trade.Sell
        }

    private suspend fun saleCeiling(
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

    private data class PricedMatch(val match: TransferMatch, val price: BigDecimal?)

    private data class AssembledPlan(
        val orders: List<Order>,
        val matches: List<PricedMatch>,
        val strategyNames: Map<Int, String>,
        val saleCeiling: SaleCeiling,
    )
}
