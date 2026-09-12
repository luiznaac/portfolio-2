package dev.agner.portfolio.httpapi.configuration

import dev.agner.portfolio.usecase.income.model.ReceivedIncome
import dev.agner.portfolio.usecase.income.parser.IIncomeStatementParser
import dev.agner.portfolio.usecase.income.parser.IncomeStatementParseException
import dev.agner.portfolio.usecase.listedasset.model.DividendType
import org.springframework.stereotype.Component

/**
 * Reads the movements sheet of the same B3 statement export [ApachePoiBrokerageNoteParser] reads
 * for trades. No sample export was available when this was written — the column names below are a
 * best-effort reading of B3's own documentation, not verified against a real file.
 *
 * Rows whose movement type isn't a cash distribution (splits, bonus shares, ...) are skipped:
 * detecting corporate actions from this sheet is left for a later pass.
 */
@Component
class ApachePoiIncomeStatementParser : IIncomeStatementParser {

    override fun parse(xlsxBytes: ByteArray): List<ReceivedIncome> =
        XlsxSheetReader.open(xlsxBytes, REQUIRED_COLUMNS, ::fail).use { reader ->
            with(reader) {
                dataRows().mapNotNull { row ->
                    val type = row.text(COLUMN_MOVEMENT).orEmpty().toDividendTypeOrNull() ?: return@mapNotNull null

                    ReceivedIncome(
                        date = row.requiredDate(COLUMN_DATE),
                        ticker = row.requiredText(COLUMN_TICKER),
                        type = type,
                        amount = row.requiredDecimal(COLUMN_AMOUNT),
                    )
                }
            }
        }

    private fun String.toDividendTypeOrNull(): DividendType? = when {
        contains("JRS", ignoreCase = true) || contains("JUROS", ignoreCase = true) -> DividendType.JCP
        contains("DIVIDENDO", ignoreCase = true) -> DividendType.DIVIDEND
        contains("RENDIMENTO", ignoreCase = true) -> DividendType.FUND_INCOME
        else -> null
    }

    private companion object {
        const val COLUMN_DATE = "Data"
        const val COLUMN_MOVEMENT = "Movimentação"
        const val COLUMN_TICKER = "Produto"
        const val COLUMN_AMOUNT = "Valor da Operação"
        val REQUIRED_COLUMNS = listOf(COLUMN_DATE, COLUMN_MOVEMENT, COLUMN_TICKER, COLUMN_AMOUNT)

        fun fail(message: String): Nothing = throw IncomeStatementParseException(message)
    }
}
