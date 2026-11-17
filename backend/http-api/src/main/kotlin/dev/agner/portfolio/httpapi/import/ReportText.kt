package dev.agner.portfolio.httpapi.import

import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import java.io.IOException
import java.math.BigDecimal

// Shared text handling for the text-PDF layouts (the XP research reports and the Rico broker
// notes). PDFBox's stripper — the same dependency PdfConverter uses — yields one page at a time, so
// every parser anchors on a header line inside the text rather than on a page number.

// A PDF PDFBox cannot open is not this layout: null lets each parser answer "not mine" instead of
// throwing out of a file claim.
internal fun pdfPagesOrNull(content: ByteArray): List<String>? =
    try {
        Loader.loadPDF(content).use { document ->
            val stripper = PDFTextStripper().apply { sortByPosition = true }
            (1..document.numberOfPages).map { page ->
                stripper.startPage = page
                stripper.endPage = page
                stripper.getText(document)
            }
        }
    } catch (exception: IOException) {
        null
    }

// A PT-BR number as the reports print it: a comma decimal separator, dots as thousands separators
// and an optional trailing percent sign. "12,5%" -> 12.5, "1.234,56" -> 1234.56.
internal fun parsePtBrDecimal(raw: String): BigDecimal =
    BigDecimal(raw.trim().removeSuffix("%").replace(".", "").replace(",", "."))
