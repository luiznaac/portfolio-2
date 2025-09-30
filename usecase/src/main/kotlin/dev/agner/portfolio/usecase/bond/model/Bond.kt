package dev.agner.portfolio.usecase.bond.model

import dev.agner.portfolio.usecase.index.model.IndexId

// TODO(): Make it sealed
interface Bond {
    val id: Int
    val name: String
    val value: Double
}

data class FixedRateBond(
    override val id: Int,
    override val name: String,
    override val value: Double,
) : Bond

data class FloatingRateBond(
    override val id: Int,
    override val name: String,
    override val value: Double,
    val indexId: IndexId,
) : Bond
