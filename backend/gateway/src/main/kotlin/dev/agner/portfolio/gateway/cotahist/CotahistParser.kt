package dev.agner.portfolio.gateway.cotahist

import dev.agner.portfolio.usecase.catalog.model.CotahistRow
import dev.agner.portfolio.usecase.catalog.model.normalizedTicker
import kotlinx.datetime.LocalDate
import kotlinx.datetime.format.FormatStringsInDatetimeFormats
import kotlinx.datetime.format.byUnicodePattern
import org.springframework.stereotype.Component

@OptIn(FormatStringsInDatetimeFormats::class)
private val cotahistDateFormat = LocalDate.Format { byUnicodePattern("yyyyMMdd") }

// B3's COTAHIST fixed-width layout (SeriesHistoricas_Layout.pdf). Records are 245 chars; the
// positions below are the 0-based half-open ranges of the 1-based layout. Only the à-vista market
// (TPMERC 010) becomes a catalog/quote row — options (070/080) and the fractional market (020) are
// dropped, since a fractional ticker is the same paper as its round-lot form anyway.
@Component
class CotahistParser {

    fun parseLine(line: String): CotahistRow? {
        if (!isSpotQuote(line)) return null

        // A malformed price drops the row instead of aborting the whole file's parse.
        return line.substring(PREULT).trim().toBigDecimalOrNull()?.let { close ->
            CotahistRow(
                ticker = line.substring(CODNEG).normalizedTicker(),
                shortName = line.substring(NOMRES).trim(),
                bdiType = line.substring(CODBDI),
                isin = line.substring(CODISI).trim().ifBlank { null },
                date = cotahistDateFormat.parse(line.substring(DATPRE)),
                closePrice = close.movePointLeft(2),
                specification = line.substring(ESPECI).trim().ifBlank { null },
            )
        }
    }

    private fun isSpotQuote(line: String) = line.length >= MIN_RECORD_LENGTH &&
        line.substring(TIPREG) == RECORD_TYPE_QUOTE &&
        line.substring(TPMERC) == SPOT_MARKET

    private companion object {
        const val RECORD_TYPE_QUOTE = "01"
        const val SPOT_MARKET = "010"
        const val MIN_RECORD_LENGTH = 242
        val TIPREG = 0 until 2
        val DATPRE = 2 until 10
        val CODBDI = 10 until 12
        val CODNEG = 12 until 24
        val TPMERC = 24 until 27
        val NOMRES = 27 until 39
        val ESPECI = 39 until 49
        val PREULT = 108 until 121
        val CODISI = 230 until 242
    }
}
