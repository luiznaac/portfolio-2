package dev.agner.portfolio.usecase.allocation.model

/**
 * The second allocation level inside [AssetClass.FIXED_INCOME]. Derived from each product's
 * existing index rather than a separately tagged field: no index means a fixed rate, so
 * [FIXED_RATE]; CDI or SELIC means [FLOATING_RATE]; IPCA means [INFLATION_LINKED]. See
 * `AllocationService`.
 */
enum class FixedIncomeSubClass {
    FLOATING_RATE,
    FIXED_RATE,
    INFLATION_LINKED,
}
