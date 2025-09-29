package dev.agner.portfolio.usecase.bond.consolidation.model

import dev.agner.portfolio.usecase.tax.incidence.model.TaxIncidence

data class BondCalculationContext(
    val actualData: ActualData,
    val processingData: ProcessingData,
) {
    constructor(
        principal: Double,
        startingYield: Double,
        yieldPercentage: Double,
        sellAmount: Double,
        taxes: Set<TaxIncidence>,
    ) : this(
        ActualData(
            principal = principal,
            yieldAmount = startingYield,
        ),
        ProcessingData(
            yieldPercentage = yieldPercentage,
            redeemedAmount = sellAmount,
            taxes = taxes,
        ),
    )

    data class ActualData(
        val principal: Double,
        val yieldAmount: Double,
    )

    data class ProcessingData(
        val yieldPercentage: Double,
        val redeemedAmount: Double,
        val taxes: Set<TaxIncidence> = emptySet(),
    )
}
