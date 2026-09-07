package dev.agner.portfolio.usecase.bond.model

import dev.agner.portfolio.usecase.index.model.IndexId
import kotlinx.datetime.LocalDate
import java.math.BigDecimal

sealed class Bond(
    open val id: Int,
    open val name: String,
    open val value: BigDecimal,
    open val maturityDate: LocalDate,
) {
    data class FixedRateBond(
        override val id: Int,
        override val name: String,
        override val value: BigDecimal,
        override val maturityDate: LocalDate,
    ) : Bond(id, name, value, maturityDate)

    data class FloatingRateBond(
        override val id: Int,
        override val name: String,
        override val value: BigDecimal,
        override val maturityDate: LocalDate,
        val indexId: IndexId,
    ) : Bond(id, name, value, maturityDate)
}
