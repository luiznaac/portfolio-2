package dev.agner.portfolio.usecase.corporateaction.model

import dev.agner.portfolio.usecase.catalog.model.normalizedTicker
import kotlinx.datetime.LocalDate
import java.math.BigDecimal

// A corporate action as the log stores it: the holder-neutral template plus the engine's decision
// (APPLIED or NEEDS_REVIEW). The ticker is normalized at the boundary; `ratio` keeps the event's
// own direction — DESDOBRAMENTO multiplies by it, GRUPAMENTO divides by it — because that is how
// the issuer states the event and re-deriving a "new per old" fraction would make the stored number
// disagree with the source. `ordinal` is the event's occurrence within its (ticker, kind, exDate)
// tuple, so two same-day events of one kind stay distinct and replay in a deterministic order.
//
// `exDate` is nullable only for a parked row: a brapi event that carries no ex-date waits for a
// decision rather than guessing a date across a trade, and an applied row always has one.
//
// `targetTicker` is the event's other code: the new code of a TICKER_CHANGE, or the underlying a
// RIGHTS_ALLOTMENT was issued from. A rights allotment is keyed on the rights ticker it creates, so
// its replay (and the allocation exclusion) sees it on the paper it belongs to; `grantedQuantity` is
// the holder's computed grant — the underlying position at the ex-date times the factor — and is
// what the position replay adds at zero cost.
data class CorporateAction(
    val id: Int,
    val ticker: String,
    val kind: CorporateActionKind,
    val exDate: LocalDate?,
    val approvalDate: LocalDate?,
    val ratio: BigDecimal?,
    val declaredValuePerShare: BigDecimal?,
    val targetTicker: String?,
    val source: CorporateActionSource,
    val processNumber: String?,
    val rawType: String?,
    val ordinal: Int,
    val fileHash: String? = null,
    val status: CorporateActionStatus,
    val grantedQuantity: BigDecimal? = null,
) {
    val isApplied: Boolean get() = status == CorporateActionStatus.APPLIED

    fun naturalKey() = CorporateActionKey(ticker, kind, exDate, ordinal)
}

// The log's dedup key: an event already applied must never be applied again, even when a later
// export overlaps the period that carried it. Ordinal is the occurrence within a (ticker, kind,
// exDate) tuple, which is how two same-day events of one kind stay distinct. The file hash is a
// provenance column and a same-file no-op guard, not part of the identity — folding it in would let
// an overlapping re-export double a split's quantity and cash.
data class CorporateActionKey(
    val ticker: String,
    val kind: CorporateActionKind,
    val exDate: LocalDate?,
    val ordinal: Int,
)

// One holder-neutral event before persistence. It carries no quantity or total amount: the engine
// derives both from the holder's current Position, so the same template applies to any holder.
data class CorporateActionCreation(
    val ticker: String,
    val kind: CorporateActionKind,
    val exDate: LocalDate?,
    val approvalDate: LocalDate? = null,
    val ratio: BigDecimal? = null,
    val declaredValuePerShare: BigDecimal? = null,
    val targetTicker: String? = null,
    val source: CorporateActionSource = CorporateActionSource.B3_MOVEMENTS,
    val processNumber: String? = null,
    val rawType: String? = null,
    val ordinal: Int = 1,
    val fileHash: String? = null,
    // The holder's computed grant. The engine fills it when it settles a RIGHTS_ALLOTMENT, and the
    // derived fallback carries the sold quantity directly. Null on every other kind.
    val grantedQuantity: BigDecimal? = null,
) {
    fun normalized() = copy(
        ticker = ticker.normalizedTicker(),
        targetTicker = targetTicker?.normalizedTicker(),
    )

    fun naturalKey() = CorporateActionKey(ticker.normalizedTicker(), kind, exDate, ordinal)

    // A quantity event without its ratio cannot be applied; parking it is the honest outcome.
    // A review kind is parked regardless of the columns the row did carry. A cash-only event with
    // no declared per-share value has no amount to pay, a ticker change with no target code has
    // nothing to remap, an event with no ex-date has no day to land on, and a rights allotment needs
    // both its underlying (target code) and its factor to compute the grant — all wait for a human
    // rather than guessing.
    fun needsReview(): Boolean =
        exDate == null ||
            kind.requiresReview ||
            (kind.isQuantityEvent && !ratio.isPositive()) ||
            (kind == CorporateActionKind.BONUS_SHARES && !declaredValuePerShare.isPositive()) ||
            (kind.isCashOnly && !declaredValuePerShare.isPositive()) ||
            (kind == CorporateActionKind.TICKER_CHANGE && targetTicker == null) ||
            (kind == CorporateActionKind.RIGHTS_ALLOTMENT && (targetTicker == null || !ratio.isPositive()))
}

// A factor or per-share amount must be a real positive number of the event's own direction; zero or
// a negative is a malformed row, not an event the engine can apply.
private fun BigDecimal?.isPositive(): Boolean = this != null && signum() > 0
