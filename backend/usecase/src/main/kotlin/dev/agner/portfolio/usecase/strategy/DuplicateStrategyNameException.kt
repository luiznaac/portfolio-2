package dev.agner.portfolio.usecase.strategy

import dev.agner.portfolio.usecase.commons.DomainException

// A strategy's name is its natural key (the XP report names the strategy it belongs to), so a second
// bucket under one name is a caller error — never a duplicate row, and never a raw constraint 500.
class DuplicateStrategyNameException(name: String) : DomainException(
    error = "duplicate-strategy-name",
    userMessage = "A strategy with this name already exists",
    detail = "A strategy named '$name' already exists",
)
