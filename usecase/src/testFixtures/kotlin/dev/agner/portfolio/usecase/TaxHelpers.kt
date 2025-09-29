package dev.agner.portfolio.usecase

import dev.agner.portfolio.usecase.tax.incidence.model.TaxIncidence

fun iofIncidence() = TaxIncidence.IOF(double())

fun rendaIncidence() = TaxIncidence.Renda(double())
