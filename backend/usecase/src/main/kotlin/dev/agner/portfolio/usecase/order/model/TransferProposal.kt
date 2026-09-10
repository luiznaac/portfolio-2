package dev.agner.portfolio.usecase.order.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import java.math.BigDecimal

/**
 * The plan's fuller design (see the PR description that once simplified this away): a transfer
 * suggestion with a real lifecycle — `PENDENTE -> APLICADA` or `REJEITADA` — scoped to one
 * [month] (competência). Rejecting one is only valid for that month: next month the engine
 * proposes fresh, since a rejection three months running is a signal the target is wrong, not
 * that a permanent blacklist is missing. Approving and applying are collapsed into one action
 * here (see [OrderPlanService]/[TransferProposalRepository][dev.agner.portfolio.usecase.order.repository.ITransferProposalRepository]) —
 * there's no separate execution step for a transfer the way there is for a real trade, so a
 * distinct APROVADA-but-not-yet-APLICADA state would carry no behavior of its own.
 */
enum class TransferProposalStatus {
    PENDENTE,
    APLICADA,
    REJEITADA,
}

data class TransferProposal(
    val id: Int,
    val month: LocalDate,
    val listedAssetId: Int,
    val ticker: String,
    val fromStrategyId: Int,
    val fromStrategyName: String,
    val toStrategyId: Int,
    val toStrategyName: String,
    val proposedQuantity: BigDecimal,
    val appliedQuantity: BigDecimal?,
    val status: TransferProposalStatus,
    val decidedAt: LocalDateTime?,
)

data class TransferProposalCreation(
    val month: LocalDate,
    val listedAssetId: Int,
    val ticker: String,
    val fromStrategyId: Int,
    val fromStrategyName: String,
    val toStrategyId: Int,
    val toStrategyName: String,
    val proposedQuantity: BigDecimal,
)

/**
 * Transfers under this notional apply themselves without asking — "um valor configurável:
 * transferências abaixo dele são aplicadas sozinhas, acima sempre perguntam" from the plan.
 * Starts at zero (everything goes through the user) until raised.
 */
data class TransferSettings(
    val autoApprovalThreshold: BigDecimal,
)
