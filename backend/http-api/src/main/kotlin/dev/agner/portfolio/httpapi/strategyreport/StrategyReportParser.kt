package dev.agner.portfolio.httpapi.strategyreport

import dev.agner.portfolio.usecase.strategy.parser.ParsedStrategyReport

/**
 * One broker's model-portfolio report layout.
 *
 * Every broker lays its report out differently, and even inside one broker the layout varies by
 * strategy — the reports are built by different teams. So format detection is the parser's own
 * job: [StrategyReportParserResolver] extracts the document once and asks each registered parser
 * whether it recognises it. Adding support for a new layout means adding a `@Component` that
 * implements this interface; nothing else changes.
 */
interface StrategyReportParser {

    /** Whether this parser recognises [document] as its own layout. Must not throw. */
    fun shouldExecute(document: StrategyReportDocument): Boolean

    /** Parses a document [shouldExecute] returned true for, or throws [dev.agner.portfolio.usecase.strategy.parser.StrategyReportParseException]. */
    fun parse(document: StrategyReportDocument): ParsedStrategyReport
}

/** A report's text, extracted once so every candidate parser inspects the same thing. */
data class StrategyReportDocument(
    val text: String,
) {
    val lines: List<String> = text.lines()

    fun containsAnyIgnoringCase(vararg needles: String) = needles.any { text.contains(it, ignoreCase = true) }
}
