package dev.agner.portfolio.httpapi.import

import dev.agner.portfolio.usecase.commons.brazilianLocalDateFormat
import dev.agner.portfolio.usecase.import.UnsupportedImportFileException
import dev.agner.portfolio.usecase.import.model.ImportDestinationObject
import dev.agner.portfolio.usecase.import.model.ImportRowPayload
import dev.agner.portfolio.usecase.listedasset.model.TradeSide
import kotlinx.datetime.LocalDate
import org.springframework.stereotype.Component

// The Rico Investimentos "nota de corretagem" layout: one text PDF may carry several notes (one per
// page), each with a trade table and a fee summary. The note prints an asset *description* instead
// of a ticker, so the parser keeps the raw description and leaves the ticker for the Broker Asset
// Alias lookup at staging. Note-level clearing, emolumentos, ISS, corretagem and IRRF are ignored:
// trades keep the gross price and no fee, matching the B3 import's behaviour.
@Component
class RicoNotaParser : IImportParser {

    override val destination = ImportDestinationObject.TRADES

    // A page is claimed by the Rico header, never by the filename. A PDF that cannot be opened, or
    // that is another broker's report, is simply not ours — no throw.
    override fun shouldExecute(fileName: String, content: ByteArray): Boolean =
        pdfPagesOrNull(content)?.any(::isRicoPage) ?: false

    override fun parse(fileName: String, content: ByteArray): List<ImportRowPayload> {
        val pages = pdfPagesOrNull(content) ?: throw UnsupportedImportFileException(fileName)
        // A page with no trade rows (a cover or summary-only page) is skipped, so it contributes no
        // empty note to the staged record.
        return pages.filter(::isRicoPage).flatMap(::parsePage)
    }

    private fun isRicoPage(page: String): Boolean =
        page.contains("RICO", ignoreCase = true) && page.contains("Nota de Corretagem", ignoreCase = true)

    private fun parsePage(page: String): List<ImportRowPayload> {
        val noteNumber = NOTE_NUMBER.find(page)?.groupValues?.get(1)
        val noteDate = NOTE_DATE.find(page)
            ?.groupValues
            ?.get(1)
            ?.let { raw -> runCatching { LocalDate.parse(raw, brazilianLocalDateFormat) }.getOrNull() }

        return page.lineSequence()
            .mapNotNull { line -> TRADE_ROW.find(line)?.toPayload(noteNumber, noteDate) }
            .toList()
    }

    private fun MatchResult.toPayload(noteNumber: String?, noteDate: LocalDate?): ImportRowPayload {
        val type = groupValues[1].uppercase()
        val venue = venueFrom(groupValues[2])
        val rawDescription = groupValues[3].trim()
        val quantity = parsePtBrDecimal(groupValues[4])
        val unitPrice = parsePtBrDecimal(groupValues[5])
        val gross = parsePtBrDecimal(groupValues[6])
        val direction = groupValues[7].uppercase()
        val side = if (direction == "D") TradeSide.BUY else TradeSide.SELL

        // Every row-level problem stages the row with an `error`; none blocks the file. The D/C
        // column is the note's cash direction, so a LISTADOC/LISTADOV that names the other side is a
        // misread line, not a trade to write.
        val error = when {
            noteDate == null -> "Missing note date"
            rawDescription.isEmpty() -> "Missing asset description"
            quantity.signum() <= 0 -> "Quantity must be positive"
            typeSide(type) != side -> "$type disagrees with $direction"
            else -> grossMismatch(gross, quantity, unitPrice)
        }

        return ImportRowPayload(
            date = noteDate,
            side = side,
            venue = venue,
            ticker = null,
            quantity = quantity,
            unitPrice = unitPrice,
            institutionName = INSTITUTION,
            error = error,
            rawDescription = rawDescription,
            noteNumber = noteNumber,
        )
    }

    private fun venueFrom(raw: String): String {
        val venue = raw.uppercase()
        return if (venue == "FRACIONÁRIO" || venue == "FRACIONARIO") "FRACIONARIO" else venue
    }

    private fun typeSide(type: String): TradeSide =
        if (type == "LISTADOC") TradeSide.BUY else TradeSide.SELL

    private companion object {
        const val INSTITUTION = "RICO INVESTIMENTOS"

        val NOTE_NUMBER = Regex("(?i)n[uú]mero da nota\\s*:?\\s*(\\S+)")
        val NOTE_DATE = Regex("(?i)data (?:do )?preg[aã]o\\s*:?\\s*(\\d{2}/\\d{2}/\\d{4})")

        // Q | Tipo | Mercado | Especificação do título | Quantidade | Preço | Valor | D/C. The
        // description is lazy so a description that itself ends in a number (a rights row prints
        // "DM 88,27") still leaves the three numeric columns and the direction at the line's tail.
        val TRADE_ROW = Regex(
            "^\\s*\\d+\\s+(LISTADOC|LISTADOV)\\s+([A-Za-zÀ-ÿ]+)\\s+(.+?)\\s+" +
                "(\\d+(?:[.,]\\d+)?)\\s+([\\d.,]+)\\s+([\\d.,]+)\\s+([DC])\\s*$",
            RegexOption.IGNORE_CASE,
        )
    }
}
