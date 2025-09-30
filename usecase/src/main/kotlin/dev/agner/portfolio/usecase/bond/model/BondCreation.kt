package dev.agner.portfolio.usecase.bond.model

import dev.agner.portfolio.usecase.index.model.IndexId

sealed class BondCreation(open val name: String, open val value: Double) {
    data class FixedRateBondCreation(
        override val name: String,
        override val value: Double,
    ) : BondCreation(name, value)

    data class FloatingRateBondCreation(
        override val name: String,
        override val value: Double,
        val indexId: IndexId,
    ) : BondCreation(name, value)
}
