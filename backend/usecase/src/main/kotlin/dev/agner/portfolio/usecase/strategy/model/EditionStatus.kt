package dev.agner.portfolio.usecase.strategy.model

// The edition lifecycle: an imported edition stages for review, then goes active on confirmation.
// Older active editions stay as history; the strategy's current edition is its latest active one.
enum class EditionStatus {
    STAGED,
    ACTIVE,
}
