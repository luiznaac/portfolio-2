package dev.agner.portfolio.httpapi.strategyreport

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import java.io.ByteArrayOutputStream

/**
 * A one-page PDF with each string on its own line. That is all the line-based parsers work from,
 * and pdfbox extracts the real reports into roughly this shape. Helvetica's WinAnsi encoding
 * covers Portuguese accents, so the fixtures can quote the reports' own headers verbatim.
 */
fun pdfOf(vararg lines: String): ByteArray {
    val document = PDDocument()
    val page = PDPage()
    document.addPage(page)

    PDPageContentStream(document, page).use { stream ->
        stream.setFont(PDType1Font(Standard14Fonts.FontName.HELVETICA), 10f)
        stream.beginText()
        stream.newLineAtOffset(40f, 780f)
        lines.forEach { line ->
            stream.showText(line)
            stream.newLineAtOffset(0f, -13f)
        }
        stream.endText()
    }

    val output = ByteArrayOutputStream()
    document.save(output)
    document.close()
    return output.toByteArray()
}
