package dev.agner.portfolio.httpapi.import.strategy

import dev.agner.portfolio.usecase.strategy.model.ParsedStrategyReport

// A strategy-report layout parser, the same Strategy shape as the trade-import parsers
// (backend/AGENTS.md): `claims` recognises a layout from its already-extracted page text,
// `parse` turns the report into the rows the service stages as an Edition. Parsing gets page text,
// never bytes, so each layout is unit-testable against a sample's extracted pages.
interface IStrategyReportParser {

    // The layout's display name (the reference prototype's "Parser" column).
    val name: String

    // Resolvers try parsers in ascending order, so the generic fallback is always last.
    val order: Int

    fun claims(pages: List<String>): Boolean

    fun parse(fileName: String, pages: List<String>): ParsedStrategyReport
}
