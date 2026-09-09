package dev.agner.portfolio.usecase.allocation.model

// The second allocation level inside RENDA_FIXA. Derived from each product's existing IndexId
// rather than a separately tagged field: no index -> fixed rate -> PRE_FIXADO; CDI/SELIC ->
// POS_FIXADO; IPCA -> INFLACAO. See AllocationService.
enum class FixedIncomeSubClass {
    POS_FIXADO,
    PRE_FIXADO,
    INFLACAO,
}
