package dev.agner.portfolio.usecase.bond.model

import dev.agner.portfolio.usecase.index.model.IndexId

interface BondCreation {
    val name: String
    val value: Double
}

data class FixedRateBondCreation(
    override val name: String,
    override val value: Double,
) : BondCreation

data class FloatingRateBondCreation(
    override val name: String,
    override val value: Double,
    val indexId: IndexId,
) : BondCreation
