package dev.agner.portfolio.usecase.bond.consolidation.model

import dev.agner.portfolio.usecase.tax.incidence.model.TaxIncidence
import java.math.BigDecimal

data class BondCalculationContext(
    val actualData: ActualData,
    val processingData: ProcessingData,
) {
    constructor(
        principal: BigDecimal,
        startingYield: BigDecimal,
        yieldPercentage: BigDecimal = BigDecimal("0.00"),
        sellAmount: BigDecimal = BigDecimal("0.00"),
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
        val principal: BigDecimal,
        val yieldAmount: BigDecimal,
    )

    data class ProcessingData(
        val yieldPercentage: BigDecimal,
        val redeemedAmount: BigDecimal,
        val taxes: Set<TaxIncidence> = emptySet(),
    )
}
