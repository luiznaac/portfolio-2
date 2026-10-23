package dev.agner.portfolio.usecase

import dev.agner.portfolio.usecase.tax.incidence.model.TaxIncidence
import dev.agner.portfolio.usecase.tax.model.HarvestLeg
import dev.agner.portfolio.usecase.tax.model.TickerAveragePrice
import kotlinx.datetime.LocalDate
import java.math.BigDecimal

fun iofIncidence() = TaxIncidence.IOF(bigDecimal())

fun rendaIncidence() = TaxIncidence.Renda(bigDecimal())

// A held position at the tax/harvest seam: the ticker plus its fiscal quantity and average price.
fun tickerAveragePrice(
    ticker: String = "PETR4",
    quantity: String = "300",
    averagePrice: String = "12",
) = TickerAveragePrice(
    ticker = ticker,
    quantity = BigDecimal(quantity),
    averagePrice = BigDecimal(averagePrice),
)

// One harvest leg; `amount` is always quantity × price, never hand-set, so a fixture cannot carry an
// amount that disagrees with its legs.
fun harvestLeg(
    date: String,
    quantity: String = "300",
    price: String = "10",
) = HarvestLeg(
    date = LocalDate.parse(date),
    quantity = BigDecimal(quantity),
    price = BigDecimal(price),
    amount = BigDecimal(quantity).multiply(BigDecimal(price)),
)
