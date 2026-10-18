package dev.agner.portfolio.usecase.strategy.model

import kotlinx.datetime.YearMonth

// A parsed XP report, before it is persisted as an Edition: the month its header names and the rows
// its portfolio table carried. Lives in usecase (like ImportRowPayload) so the parser in http-api and
// the service share one shape while the domain never sees PDF bytes.
data class ParsedStrategyReport(
    val referenceMonth: YearMonth,
    val entries: List<EditionEntry>,
)
