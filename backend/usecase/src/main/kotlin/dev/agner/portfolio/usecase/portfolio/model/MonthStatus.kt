package dev.agner.portfolio.usecase.portfolio.model

// A month is worked on while OPEN and locked once closed. At most one month is OPEN at a time —
// opening a new one closes the previous (see IMonthRepository.open).
enum class MonthStatus {
    OPEN,
    CLOSED,
}
