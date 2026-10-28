package dev.agner.portfolio.usecase.fixedincome.model

import dev.agner.portfolio.usecase.index.model.IndexId

// How a fixed-income product's value accrues: a contracted fixed rate, or one that floats with an
// index. A variant type rather than a "rate kind plus a nullable index" — so a fixed rate cannot
// carry an index and a floating rate cannot be left without one (backend/AGENTS.md, model variants).
sealed interface FixedIncomeRate {

    // The sub-class follows from the rate itself, which is the only fact it depends on.
    val subClass: FixedIncomeSubClass

    data object Fixed : FixedIncomeRate {
        override val subClass = FixedIncomeSubClass.FIXED_RATE
    }

    data class Floating(val indexId: IndexId) : FixedIncomeRate {
        override val subClass = when (indexId) {
            IndexId.IPCA -> FixedIncomeSubClass.INFLATION_LINKED
            IndexId.CDI, IndexId.SELIC -> FixedIncomeSubClass.FLOATING_RATE
        }
    }
}
