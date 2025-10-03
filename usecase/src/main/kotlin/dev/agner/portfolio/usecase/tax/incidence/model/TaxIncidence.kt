package dev.agner.portfolio.usecase.tax.incidence.model

import java.math.BigDecimal

sealed class TaxIncidence(open val rate: BigDecimal) {
    data class Renda(override val rate: BigDecimal) : TaxIncidence(rate)
    data class IOF(override val rate: BigDecimal) : TaxIncidence(rate)
}
