package dev.agner.portfolio.usecase.order

import dev.agner.portfolio.usecase.attribution.AttributionService
import dev.agner.portfolio.usecase.attribution.model.AttributionMovementCreation
import dev.agner.portfolio.usecase.attribution.model.AttributionReason.TRANSFER
import dev.agner.portfolio.usecase.commons.now
import dev.agner.portfolio.usecase.commons.today
import dev.agner.portfolio.usecase.configuration.ITransactionTemplate
import dev.agner.portfolio.usecase.order.model.OrderPlan
import dev.agner.portfolio.usecase.order.model.TransferProposal
import dev.agner.portfolio.usecase.order.model.TransferProposalCreation
import dev.agner.portfolio.usecase.order.model.TransferProposalStatus.APPLIED
import dev.agner.portfolio.usecase.order.model.TransferProposalStatus.PENDING
import dev.agner.portfolio.usecase.order.model.TransferProposalStatus.REJECTED
import dev.agner.portfolio.usecase.order.repository.ITransferProposalRepository
import dev.agner.portfolio.usecase.order.repository.ITransferSettingsRepository
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.Clock

/**
 * The order plan's facade and transfer-proposal lifecycle: it delegates the month's order assembly
 * to [OrderPlanAssembler], then owns what happens to the matched transfers — reconciliation against
 * what is stored, auto-apply below the threshold, expiry of stranded proposals, and the
 * approve/reject decisions.
 *
 * Reading and writing are deliberately separate. [computePlan] is pure: it never touches the
 * database, so callers that only want to look — the brokerage-note preview, the step-up planner,
 * a plain `GET` — cannot cause a transfer to be created or auto-applied as a side effect.
 * [refreshPlan] is the command that persists newly matched proposals, refreshes the quantity on
 * still-pending ones, and auto-applies anything under the configured threshold.
 */
@Service
class OrderPlanService(
    private val assembler: OrderPlanAssembler,
    private val transferProposalRepository: ITransferProposalRepository,
    private val transferSettingsRepository: ITransferSettingsRepository,
    private val attributionService: AttributionService,
    private val transaction: ITransactionTemplate,
    private val clock: Clock,
) {

    /** Read-only. Transfer proposals come back exactly as they are stored, never created here. */
    suspend fun computePlan(): OrderPlan {
        val assembled = assembler.assemble(LocalDate.today(clock))
        val stored = transferProposalRepository.fetchByMonth(currentMonth).filter { it.status == PENDING }

        return OrderPlan(orders = assembled.orders, transferProposals = stored, saleCeiling = assembled.saleCeiling)
    }

    /** The write side: reconciles freshly matched transfers against what is already stored. */
    suspend fun refreshPlan(): OrderPlan {
        val assembled = assembler.assemble(LocalDate.today(clock))
        val threshold = transferSettingsRepository.fetch().autoApprovalThreshold
        val month = currentMonth

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

    suspend fun transfersForMonth(): List<TransferProposal> = transferProposalRepository.fetchByMonth(currentMonth)

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
        // or re-surfaced until the next one. A still-pending proposal under the auto-approval
        // threshold is applied on the spot instead of being returned.
        val notional = priced.price?.let { current.proposedQuantity * it }
        return when {
            current.status != PENDING -> null
            notional != null && notional <= threshold -> {
                applyAndDecide(current, current.proposedQuantity)
                null
            }

            else -> current
        }
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

    // Recomputed on every access: the clock, not a captured field, decides the current month.
    private val currentMonth: LocalDate
        get() = LocalDate.today(clock).let { LocalDate(it.year, it.month, 1) }
}
