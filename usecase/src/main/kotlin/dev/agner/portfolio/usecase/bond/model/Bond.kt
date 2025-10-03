package dev.agner.portfolio.usecase.bond.model

import dev.agner.portfolio.usecase.index.model.IndexId
import java.math.BigDecimal

sealed class Bond(open val id: Int, open val name: String, open val value: BigDecimal) {
    data class FixedRateBond(
        override val id: Int,
        override val name: String,
        override val value: BigDecimal,
    ) : Bond(id, name, value)

    data class FloatingRateBond(
        override val id: Int,
        override val name: String,
        override val value: BigDecimal,
        val indexId: IndexId,
    ) : Bond(id, name, value)
}
