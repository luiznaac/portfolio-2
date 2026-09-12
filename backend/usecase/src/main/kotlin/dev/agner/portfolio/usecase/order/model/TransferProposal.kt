package dev.agner.portfolio.usecase.order.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import java.math.BigDecimal

/**
 * `PENDING -> APPLIED | REJECTED`. Approving and applying are one action: unlike a real trade, a
 * transfer has no separate execution step, so an APPROVED-but-not-yet-APPLIED state would carry
 * no behaviour of its own.
 */
enum class TransferProposalStatus {
    PENDING,
    APPLIED,
    REJECTED,
}

/**
 * A suggestion to move shares of one ticker between two strategies, scoped to one [month].
 * Rejecting one only holds for that month — next month the engine proposes afresh, since a
 * rejection three months running is a signal the target is wrong rather than a missing permanent
 * blacklist.
 */
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
 * Transfers whose notional falls at or below this threshold apply themselves without asking;
 * anything above it always goes to the user. Starts at zero, i.e. everything is asked.
 */
data class TransferSettings(
    val autoApprovalThreshold: BigDecimal,
)
