package dev.agner.portfolio.gateway.cotahist

import dev.agner.portfolio.usecase.catalog.model.CotahistRow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import java.math.BigDecimal

// The COTAHIST layout is a 245-char fixed-width record. Offsets below are 1-based, matching B3's
// published layout (SeriesHistoricas_Layout.pdf): TIPREG 1-2, DATPRE 3-10, CODBDI 11-12,
// CODNEG 13-24, TPMERC 25-27, NOMRES 28-39, PREULT 109-121 (2 implied decimals), CODISI 225-236.
class CotahistParserTest : StringSpec({
    val parser = CotahistParser()

    "parses an à-vista stock record" {
        parser.parseLine(petr4Record()) shouldBe CotahistRow(
            ticker = "PETR4",
            shortName = "PETROBRAS",
            bdiType = "02",
            isin = "BRPETRACNPR6",
            date = LocalDate.parse("2026-10-14"),
            closePrice = BigDecimal("48.92"),
        )
    }

    "strips the fractional-market suffix from the ticker" {
        val line = cotahistLine(
            1 to "01",
            3 to "20261014",
            11 to "02",
            13 to "ALUP11F",
            25 to "010",
            109 to "0000000004892",
        )

        parser.parseLine(line)?.ticker shouldBe "ALUP11"
    }

    "truncates the short name to the 12-char NOMRES field" {
        val line = cotahistLine(
            1 to "01",
            3 to "20261014",
            11 to "12",
            13 to "MXRF11",
            25 to "010",
            28 to "FII MAXI RENDA",
            109 to "0000000009100",
        )

        parser.parseLine(line)?.shortName shouldBe "FII MAXI REN"
    }

    "treats a blank ISIN as null" {
        val line = cotahistLine(
            1 to "01",
            3 to "20261014",
            11 to "02",
            13 to "PETR4",
            25 to "010",
            109 to "0000000004892",
            225 to "            ",
        )

        parser.parseLine(line)?.isin shouldBe null
    }

    "drops a row whose price is malformed" {
        val line = cotahistLine(
            1 to "01",
            3 to "20261014",
            11 to "02",
            13 to "PETR4",
            25 to "010",
            109 to "not-a-price",
        )

        parser.parseLine(line) shouldBe null
    }

    "filters out option markets" {
        val line = cotahistLine(1 to "01", 3 to "20261014", 11 to "78", 13 to "PETRG100", 25 to "070")

        parser.parseLine(line) shouldBe null
    }

    "filters out the fractional market" {
        val line = cotahistLine(1 to "01", 3 to "20261014", 11 to "96", 13 to "ENJU3F", 25 to "020")

        parser.parseLine(line) shouldBe null
    }

    "filters out the trailer record" {
        val line = cotahistLine(1 to "99", 3 to "20261014")

        parser.parseLine(line) shouldBe null
    }

    "returns null for a truncated line" {
        parser.parseLine("01 20261014 02 PETR4") shouldBe null
    }
})
