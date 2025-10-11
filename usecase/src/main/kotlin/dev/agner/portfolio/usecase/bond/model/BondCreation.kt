package dev.agner.portfolio.usecase.bond.model

import dev.agner.portfolio.usecase.index.model.IndexId
import kotlinx.datetime.LocalDate
import java.math.BigDecimal

sealed class BondCreation(open val name: String, open val value: BigDecimal, open val maturityDate: LocalDate) {
    data class FixedRateBondCreation(
        override val name: String,
        override val value: BigDecimal,
        override val maturityDate: LocalDate,
    ) : BondCreation(name, value, maturityDate)

    data class FloatingRateBondCreation(
        override val name: String,
        override val value: BigDecimal,
        override val maturityDate: LocalDate,
        val indexId: IndexId,
    ) : BondCreation(name, value, maturityDate)
}
