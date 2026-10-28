package dev.agner.portfolio.usecase.strategy.model

import java.math.BigDecimal

// The one home of the weight invariant an Edition must satisfy: a table with rows, no ticker twice,
// and weights summing to 100 ± 0.01. It returns the reason the table is invalid, or null when it is
// valid, so each caller names its own rejection — the parser throws a file-named
// InvalidStrategyReportException, the service an edition-named InvalidEditionException. A report
// whose weights are wrong is a caller error, never a silently accepted edition (issue #93).
fun editionWeightProblem(entries: List<EditionEntry>): String? {
    if (entries.isEmpty()) return "the portfolio table has no rows"

    val duplicated = entries.groupingBy { it.ticker }.eachCount().filterValues { it > 1 }.keys
    if (duplicated.isNotEmpty()) return "the portfolio table repeats $duplicated"

    val total = entries.fold(BigDecimal.ZERO) { sum, entry -> sum + entry.weight }
    return if (total.subtract(HUNDRED).abs() > WEIGHT_TOLERANCE) {
        "weights sum to $total, expected 100"
    } else {
        null
    }
}

private val HUNDRED = BigDecimal("100")
private val WEIGHT_TOLERANCE = BigDecimal("0.01")
