package dev.agner.portfolio.usecase.bond.consolidation.model

data class BondCalculationContext(
    val actualData: ActualData,
    val processingData: ProcessingData,
) {
    constructor(principal: Double, startingYield: Double, yieldPercentage: Double, sellAmount: Double) : this(
        ActualData(
            principal = principal,
            yieldAmount = startingYield,
        ),
        ProcessingData(
            yieldPercentage = yieldPercentage,
            redeemedAmount = sellAmount,
        ),
    )

    data class ActualData(
        val principal: Double,
        val yieldAmount: Double,
    )

    data class ProcessingData(
        val yieldPercentage: Double,
        val redeemedAmount: Double,
    )
}
