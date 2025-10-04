package dev.agner.portfolio.usecase.bond.model

import dev.agner.portfolio.usecase.index.model.IndexId
import java.math.BigDecimal

sealed class BondCreation(open val name: String, open val value: BigDecimal) {
    data class FixedRateBondCreation(
        override val name: String,
        override val value: BigDecimal,
    ) : BondCreation(name, value)

    data class FloatingRateBondCreation(
        override val name: String,
        override val value: BigDecimal,
        val indexId: IndexId,
    ) : BondCreation(name, value)
}
