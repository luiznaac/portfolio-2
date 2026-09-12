package dev.agner.portfolio.usecase.strategy

import dev.agner.portfolio.usecase.commons.DomainException

class StrategyNotFoundException(strategyId: Int) :
    DomainException(
        error = "strategy-not-found",
        userMessage = "Strategy not found",
        detail = "Strategy with ID $strategyId not found",
    )

class StrategyEditionAlreadyExistsException(strategyId: Int, referenceDate: String) :
    DomainException(
        error = "strategy-edition-duplicate",
        userMessage = "Strategy edition already exists",
        detail = "Strategy $strategyId already has an edition for $referenceDate",
    )

class InvalidStrategyIdException(received: String?) :
    DomainException(
        error = "invalid-strategy-id",
        userMessage = "Invalid strategy id",
        detail = "Received strategy id: ${received ?: "<missing>"}",
    )
