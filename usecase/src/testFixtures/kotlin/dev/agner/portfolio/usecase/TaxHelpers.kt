package dev.agner.portfolio.usecase

import dev.agner.portfolio.usecase.tax.incidence.model.TaxIncidence

fun iofIncidence() = TaxIncidence.IOF(bigDecimal())

fun rendaIncidence() = TaxIncidence.Renda(bigDecimal())
