package dev.agner.portfolio.usecase.order.model

import dev.agner.portfolio.usecase.commons.DomainException

// Raised for a `?phase=` value the engine does not know. The supported phases grow with the
// rebalancing slices; the error names the received value so the caller can correct the query.
class InvalidOrderPlanPhaseException(phase: String) : DomainException(
    error = "invalid-order-plan-phase",
    userMessage = "Invalid order plan phase",
    detail = "Unknown order plan phase '$phase'; supported phases are: ${OrderPlanPhase.supportedPhases}",
)
