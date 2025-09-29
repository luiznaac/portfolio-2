package dev.agner.portfolio.usecase.tax.incidence.model

sealed class TaxIncidence(open val rate: Double) {
    data class Renda(override val rate: Double) : TaxIncidence(rate)
    data class IOF(override val rate: Double) : TaxIncidence(rate)
}
