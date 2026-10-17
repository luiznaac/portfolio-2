package dev.agner.portfolio.usecase.portfolio.model

// The four rungs of the target chain: Capital → class → strategy (or RF sub-class) → ticker. Stored
// as the discriminator column of `target`; the repository keys each row's meaning on it.
enum class TargetLevel {
    CLASS,
    STRATEGY,
    SUB_CLASS,
    TICKER,
}
