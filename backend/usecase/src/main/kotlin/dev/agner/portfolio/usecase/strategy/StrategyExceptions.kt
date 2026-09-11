package dev.agner.portfolio.usecase.strategy

class StrategyNotFoundException(strategyId: Int) : RuntimeException("Strategy with ID $strategyId not found")

class StrategyEditionAlreadyExistsException(strategyId: Int, referenceDate: String) :
    RuntimeException("Strategy $strategyId already has an edition for $referenceDate")
