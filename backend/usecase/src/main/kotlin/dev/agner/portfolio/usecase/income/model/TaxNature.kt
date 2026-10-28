package dev.agner.portfolio.usecase.income.model

// The tax character of a cash movement. Proventos are INCOME; the other natures describe events
// that reduce a position's cost basis or return capital, and are routed away from the Income reads
// (which select `tax_nature = INCOME`).
enum class TaxNature {
    INCOME,
    COST_REDUCTION,
    RETURN_OF_CAPITAL,
    OTHER,
}
