package dev.agner.portfolio.usecase.fixedincome.model

// The target bucket a fixed-income product allocates to: FIXED_RATE ("pré-fixado"),
// FLOATING_RATE ("pós-fixado" on CDI/SELIC) and INFLATION_LINKED ("inflação" on IPCA). Like a
// listed asset's class it is derived on read and never stored (see FixedIncomeRate) — the
// consolidation engine already holds the facts, so a cached copy could only drift from them.
enum class FixedIncomeSubClass {
    FIXED_RATE,
    FLOATING_RATE,
    INFLATION_LINKED,
}
