package dev.agner.portfolio.usecase

import dev.agner.portfolio.usecase.monthlyclose.model.CloseState
import dev.agner.portfolio.usecase.monthlyclose.model.IncomeTotals
import dev.agner.portfolio.usecase.monthlyclose.model.OrdersSnapshot
import dev.agner.portfolio.usecase.monthlyclose.model.TaxCloseSnapshot
import dev.agner.portfolio.usecase.order.model.TransferProposal
import dev.agner.portfolio.usecase.order.model.TransferStatus
import dev.agner.portfolio.usecase.portfolio.model.Month
import dev.agner.portfolio.usecase.portfolio.model.MonthStatus
import dev.agner.portfolio.usecase.tax.model.ExemptionMeter
import kotlinx.datetime.YearMonth
import java.math.BigDecimal

// Shared fixtures for the monthly-close tests: the same month and the same transfer proposal are
// observed through the service, the mapper and the controller, so the builder lives in one place.
fun monthFixture(status: MonthStatus = MonthStatus.OPEN) = Month(
    id = 7,
    period = YearMonth.parse("2026-10"),
    capital = BigDecimal("245000.00"),
    floor = BigDecimal("20000.00"),
    status = status,
)

// The blocked close's proposal: 50 units at R$10 is exactly the R$500 threshold, so the inbox
// leaves it for a human.
fun transferProposal(
    id: Int = 7,
    status: TransferStatus = TransferStatus.PENDING,
) = TransferProposal(
    id = id,
    ticker = "ITUB4",
    fromStrategyId = 2,
    fromStrategy = "Small Caps",
    toStrategyId = 1,
    toStrategy = "Top Dividendos",
    quantity = BigDecimal("50"),
    amount = BigDecimal("500"),
    autoApproved = false,
    status = status,
)

// An empty-book close state; the close tests fill in the rungs they assert on through the service's
// collaborators, so only the envelope values need a shared builder.
fun closeStateFixture() = CloseState(
    period = YearMonth.parse("2026-10"),
    capital = BigDecimal("245000.00"),
    floor = BigDecimal("20000.00"),
    caixa = BigDecimal("45000.00"),
    holdings = emptyList(),
    attributions = emptyList(),
    classes = emptyList(),
    income = IncomeTotals(gross = BigDecimal.ZERO, retained = BigDecimal.ZERO, net = BigDecimal.ZERO),
    orders = OrdersSnapshot(orders = emptyList(), transfers = emptyList(), unplannedTrades = emptyList()),
    tax = TaxCloseSnapshot(
        carry = emptyList(),
        meter = ExemptionMeter(
            grossSales = BigDecimal.ZERO,
            exemptionLimit = BigDecimal("20000.00"),
            exceeded = false,
            realizedGain = BigDecimal.ZERO,
            taxableGain = BigDecimal.ZERO,
            rate = BigDecimal("15.00"),
            tax = BigDecimal.ZERO,
        ),
    ),
)
