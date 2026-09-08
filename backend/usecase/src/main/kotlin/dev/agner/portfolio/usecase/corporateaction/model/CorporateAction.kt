package dev.agner.portfolio.usecase.corporateaction.model

import kotlinx.datetime.LocalDate
import java.math.BigDecimal

/**
 * A fact about a listed asset that changes quantity and/or cost basis without being a trade.
 * See the plan's "Eventos corporativos" section for why position must be replayed from these
 * plus trades instead of stored as a running balance — this is what makes a late-discovered event
 * correctable by simply inserting it with the right date.
 */
sealed class CorporateAction(
    open val id: Int,
    open val assetId: Int,
    open val date: LocalDate,
) {
    /** Each share becomes [ratio] shares; cost basis unchanged (average price divides by ratio). */
    data class Split(
        override val id: Int,
        override val assetId: Int,
        override val date: LocalDate,
        val ratio: BigDecimal,
    ) : CorporateAction(id, assetId, date)

    /** Every [ratio] shares become 1; cost basis unchanged (average price multiplies by ratio). */
    data class ReverseSplit(
        override val id: Int,
        override val assetId: Int,
        override val date: LocalDate,
        val ratio: BigDecimal,
    ) : CorporateAction(id, assetId, date)

    /**
     * [ratio] new shares granted per existing share, at [valuePerNewShare] declared by the company —
     * this is the one event that *raises* cost basis (see plan section 05).
     */
    data class Bonus(
        override val id: Int,
        override val assetId: Int,
        override val date: LocalDate,
        val ratio: BigDecimal,
        val valuePerNewShare: BigDecimal,
    ) : CorporateAction(id, assetId, date)

    /** A pure renaming (e.g. CPLE6 -> CPLE3): no effect on quantity or cost basis. */
    data class TickerChange(
        override val id: Int,
        override val assetId: Int,
        override val date: LocalDate,
        val newTicker: String,
    ) : CorporateAction(id, assetId, date)
}
