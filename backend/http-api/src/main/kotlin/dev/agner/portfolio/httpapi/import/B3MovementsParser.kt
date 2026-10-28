package dev.agner.portfolio.httpapi.import

import dev.agner.portfolio.usecase.catalog.model.normalizedTicker
import dev.agner.portfolio.usecase.corporateaction.model.CorporateActionCreation
import dev.agner.portfolio.usecase.corporateaction.model.CorporateActionKind
import dev.agner.portfolio.usecase.corporateaction.model.CorporateActionSource
import dev.agner.portfolio.usecase.import.model.ImportRowPayload
import dev.agner.portfolio.usecase.import.model.MovementsRouting
import dev.agner.portfolio.usecase.income.model.IncomeCreation
import dev.agner.portfolio.usecase.income.model.IncomeType
import dev.agner.portfolio.usecase.income.model.Provenance
import dev.agner.portfolio.usecase.listedasset.model.TradeSide
import kotlinx.datetime.LocalDate
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.Normalizer
import java.util.Locale

// The columns the B3 "Movimentação" export carries, by their verbatim headers. The column set is
// wider than the Negociação export (it also carries events and RF movements), so a column is
// located by any of its known aliases and the optional ones are simply absent from older exports.
private enum class MovementsColumn(val aliases: Set<String>) {
    DATE(setOf("Data", "Data do Negócio")),
    TYPE(setOf("Tipo de Movimentação")),
    TICKER(setOf("Código de Negociação", "Ticker")),
    INSTITUTION(setOf("Instituição")),
    QUANTITY(setOf("Quantidade")),
    UNIT_PRICE(setOf("Preço Unitário", "Preço")),
    GROSS(setOf("Valor")),
    MARKET(setOf("Mercado")),
    FACTOR(setOf("Fator", "Fator de Bonificação", "Proporção", "Ratio")),
    NEW_TICKER(setOf("Novo Código de Negociação", "Novo Ticker")),
    PROCESS(setOf("Número do Processo", "Processo")),
    APPROVAL_DATE(setOf("Data de Aprovação")),
}

// Parses the B3 "Movimentação" export (the broader dataset: trades, proventos, corporate events and
// RF movements) and routes each row by its "Tipo de Movimentação":
//
// - Compra/Venda join the negotiation ledger.
// - Dividendo/JCP/Rendimento reconcile the income ledger.
// - Desdobramento/Grupamento/Bonificação/Ticker change become corporate actions.
// - Subscrição/cessão/incorporação/fusão/cisão, RF rows and anything unrecognised become review
//   queued corporate actions — a movement row is never silently dropped.
//
// The layout is recognised by its header row, never by the file name. The export's event encoding
// is not publicly documented, so a quantity event without a factor column is queued for review
// rather than guessed; the same applies to a cash event with no per-share amount.
@Component
class B3MovementsParser : IMovementsParser {

    override fun shouldExecute(fileName: String, content: ByteArray): Boolean = runCatching {
        WorkbookFactory.create(content.inputStream()).use { workbook ->
            isRecognised(columnHeaders(workbook.getSheetAt(0).getRow(0)))
        }
    }.getOrDefault(false)

    override fun parse(fileName: String, content: ByteArray): MovementsRouting =
        WorkbookFactory.create(content.inputStream()).use { workbook ->
            val sheet = workbook.getSheetAt(0)
            val headers = columnHeaders(sheet.getRow(0))
            require(isRecognised(headers)) {
                "B3 movimentações XLSX unrecognised layout in $fileName: found headers ${headers.keys}"
            }
            route(sheet, headers)
        }

    private fun isRecognised(headers: ColumnIndex): Boolean =
        listOf(MovementsColumn.DATE, MovementsColumn.TYPE, MovementsColumn.TICKER)
            .all { column -> column.aliases.any { it in headers } }

    private fun route(sheet: Sheet, headers: ColumnIndex): MovementsRouting {
        // POI's DataFormatter is not thread-safe, so it is per-parse rather than shared on this
        // singleton component; a concurrent upload must not reuse it.
        val formatter = DataFormatter(Locale.US)
        val rows = (1..sheet.lastRowNum).mapNotNull { rowIndex ->
            sheet.getRow(rowIndex)
                ?.takeIf { it.firstCellNum >= 0 }
                ?.let { parseRow(it, headers, formatter) }
        }
        return MovementsRouting(
            trades = rows.filterIsInstance<RoutedRow.Trade>().map { it.payload },
            incomes = rows.filterIsInstance<RoutedRow.Income>().map { it.creation },
            corporateActions = withOrdinals(rows.filterIsInstance<RoutedRow.Action>().map { it.creation }),
        )
    }

    private fun parseRow(row: Row, headers: ColumnIndex, formatter: DataFormatter): RoutedRow? {
        val parsed = MovementRow(
            date = readDate(cell(row, headers, MovementsColumn.DATE), formatter) ?: return null,
            ticker = readText(cell(row, headers, MovementsColumn.TICKER), formatter)
                ?.normalizedTicker()
                ?.takeIf { it.isNotEmpty() } ?: return null,
            type = readText(cell(row, headers, MovementsColumn.TYPE), formatter) ?: MISSING_TYPE,
            institution = readText(cell(row, headers, MovementsColumn.INSTITUTION), formatter).orEmpty(),
            quantity = readNumber(cell(row, headers, MovementsColumn.QUANTITY), formatter),
            unitPrice = readNumber(cell(row, headers, MovementsColumn.UNIT_PRICE), formatter),
            value = readNumber(cell(row, headers, MovementsColumn.GROSS), formatter),
            market = readText(cell(row, headers, MovementsColumn.MARKET), formatter).orEmpty(),
            factor = readNumber(cell(row, headers, MovementsColumn.FACTOR), formatter),
            targetTicker = readText(cell(row, headers, MovementsColumn.NEW_TICKER), formatter),
            processNumber = readText(cell(row, headers, MovementsColumn.PROCESS), formatter),
            approvalDate = readDate(cell(row, headers, MovementsColumn.APPROVAL_DATE), formatter),
        )

        return when (normalized(parsed.type)) {
            "COMPRA" -> trade(parsed, TradeSide.BUY)
            "VENDA" -> trade(parsed, TradeSide.SELL)
            "DIVIDENDO", "JCP", "JUROS SOBRE CAPITAL PROPRIO", "RENDIMENTO" -> income(parsed)
            else -> RoutedRow.Action(corporateAction(parsed))
        }
    }

    private fun trade(row: MovementRow, side: TradeSide): RoutedRow.Trade {
        val error = when {
            row.quantity == null -> "Invalid quantity"
            row.unitPrice == null -> "Invalid unit price"
            row.quantity.signum() <= 0 -> "Quantity must be positive"
            else -> grossError(row.value, row.quantity, row.unitPrice)
        }
        return RoutedRow.Trade(
            ImportRowPayload(
                date = row.date,
                side = side,
                venue = row.market,
                ticker = row.ticker,
                quantity = row.quantity ?: BigDecimal.ZERO,
                unitPrice = row.unitPrice ?: BigDecimal.ZERO,
                institutionName = row.institution,
                error = error,
            ),
        )
    }

    private fun income(row: MovementRow): RoutedRow {
        val derived = row.quantity?.times(row.unitPrice ?: BigDecimal.ZERO)
        val gross = derived ?: row.value
        if (gross == null) {
            return RoutedRow.Action(
                CorporateActionCreation(
                    ticker = row.ticker,
                    kind = CorporateActionKind.OTHER,
                    exDate = row.date,
                    rawType = row.type,
                    source = CorporateActionSource.B3_MOVEMENTS,
                ),
            )
        }
        val net = row.value ?: gross
        return RoutedRow.Income(
            IncomeCreation(
                ticker = row.ticker,
                date = row.date,
                type = incomeType(row.type),
                grossAmount = gross.max(net),
                netAmount = net,
                provenance = Provenance.B3,
            ),
        )
    }

    private fun corporateAction(row: MovementRow): CorporateActionCreation {
        val perShare = when {
            row.quantity != null && row.quantity.signum() > 0 && row.value != null ->
                row.value.divide(row.quantity, PER_SHARE_SCALE, RoundingMode.HALF_UP)
            else -> row.unitPrice
        }
        return CorporateActionCreation(
            ticker = row.ticker,
            kind = kind(row.type),
            exDate = row.date,
            approvalDate = row.approvalDate,
            ratio = row.factor,
            declaredValuePerShare = perShare,
            targetTicker = row.targetTicker,
            source = CorporateActionSource.B3_MOVEMENTS,
            processNumber = row.processNumber,
            rawType = row.type,
        )
    }

    // The export's type vocabulary, accent- and case-insensitive. RF rows and unknown labels land
    // in OTHER so they queue for review; the raw label stays on the row for the reviewer.
    private fun kind(type: String): CorporateActionKind = when (normalized(type)) {
        "DESDOBRAMENTO" -> CorporateActionKind.SPLIT
        "GRUPAMENTO" -> CorporateActionKind.REVERSE_SPLIT
        "BONIFICACAO EM ACOES", "BONIFICACAO" -> CorporateActionKind.BONUS_SHARES
        "BONIFICACAO EM DINHEIRO" -> CorporateActionKind.CASH_BONUS
        "RESTITUICAO", "RESTITUICAO DE CAPITAL" -> CorporateActionKind.CAPITAL_RETURN
        "AMORTIZACAO" -> CorporateActionKind.AMORTIZATION
        "SUBSCRICAO" -> CorporateActionKind.RIGHTS_ISSUE
        "CESSAO" -> CorporateActionKind.RIGHTS_ASSIGNMENT
        "INCORPORACAO" -> CorporateActionKind.INCORPORATION
        "FUSAO" -> CorporateActionKind.MERGER
        "CISAO" -> CorporateActionKind.SPIN_OFF
        "ALTERACAO DE CODIGO DE NEGOCIACAO", "TICKER CHANGE" -> CorporateActionKind.TICKER_CHANGE
        else -> CorporateActionKind.OTHER
    }

    private fun incomeType(type: String): IncomeType = when (normalized(type)) {
        "JCP", "JUROS SOBRE CAPITAL PROPRIO" -> IncomeType.JCP
        "RENDIMENTO" -> IncomeType.FUND_INCOME
        else -> IncomeType.DIVIDEND
    }

    private fun grossError(gross: BigDecimal?, quantity: BigDecimal, unitPrice: BigDecimal): String? {
        if (gross == null) return null
        val expected = quantity.multiply(unitPrice).setScale(GROSS_SCALE, RoundingMode.HALF_UP)
        return if (gross.setScale(GROSS_SCALE, RoundingMode.HALF_UP).compareTo(expected) == 0) {
            null
        } else {
            "Gross $gross does not match quantity x price ($expected)"
        }
    }

    // The dedup key carries an occurrence ordinal, so two same-day events of one kind stay
    // distinct while a re-import of the same row resolves to the same key.
    private fun withOrdinals(creations: List<CorporateActionCreation>): List<CorporateActionCreation> {
        val ordinalByIndex = creations.withIndex()
            .groupBy { (_, creation) -> Triple(creation.ticker, creation.kind, creation.exDate) }
            .values
            .flatMap { group -> group.mapIndexed { ordinal, (index, _) -> index to ordinal + 1 } }
            .toMap()
        return creations.mapIndexed { index, creation -> creation.copy(ordinal = ordinalByIndex.getValue(index)) }
    }

    private fun cell(row: Row, headers: ColumnIndex, column: MovementsColumn): Cell? =
        column.aliases.firstNotNullOfOrNull { headers[it] }
            ?.let { row.getCell(it, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL) }

    private fun normalized(text: String): String =
        Normalizer.normalize(text.trim().uppercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(DIACRITICS, "")
            .trim()

    private data class MovementRow(
        val date: LocalDate,
        val ticker: String,
        val type: String,
        val institution: String,
        val quantity: BigDecimal?,
        val unitPrice: BigDecimal?,
        val value: BigDecimal?,
        val market: String,
        val factor: BigDecimal?,
        val targetTicker: String?,
        val processNumber: String?,
        val approvalDate: LocalDate?,
    )

    private sealed class RoutedRow {
        data class Trade(val payload: ImportRowPayload) : RoutedRow()
        data class Income(val creation: IncomeCreation) : RoutedRow()
        data class Action(val creation: CorporateActionCreation) : RoutedRow()
    }

    private companion object {
        const val GROSS_SCALE = 2
        const val MISSING_TYPE = "(sem tipo)"
        const val PER_SHARE_SCALE = 6
        val DIACRITICS = Regex("\\p{M}+")
    }
}
