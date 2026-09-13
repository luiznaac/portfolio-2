package dev.agner.portfolio.httpapi.configuration

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.InputStream

private class HeaderParseException(message: String) : RuntimeException(message)

/**
 * Mocks [WorkbookFactory] on purpose: a real in-memory workbook can't prove its `close()` was
 * called, and the finding here is exactly that the failure paths must release it.
 */
class XlsxSheetReaderTest : DescribeSpec({

    beforeEach { mockkStatic(WorkbookFactory::class) }
    afterEach { unmockkStatic(WorkbookFactory::class) }

    fun open(xlsxBytes: ByteArray, requiredColumns: List<String>) =
        XlsxSheetReader.open(xlsxBytes, requiredColumns) { throw HeaderParseException(it) }

    describe("opening a statement with a bad header") {

        it("closes the workbook when the sheet has no header row") {
            val workbook = mockk<Workbook>()
            val sheet = mockk<Sheet>()
            every { WorkbookFactory.create(any<InputStream>()) } returns workbook
            every { workbook.close() } just Runs
            every { workbook.getSheetAt(0) } returns sheet
            every { sheet.getRow(0) } returns null

            shouldThrow<HeaderParseException> { open(byteArrayOf(1), emptyList()) }

            verify { workbook.close() }
        }

        it("closes the workbook when a required column is missing") {
            val workbook = mockk<Workbook>()
            val sheet = mockk<Sheet>()
            val headerRow = mockk<Row>()
            val header = mockk<Cell>()
            every { WorkbookFactory.create(any<InputStream>()) } returns workbook
            every { workbook.close() } just Runs
            every { workbook.getSheetAt(0) } returns sheet
            every { sheet.getRow(0) } returns headerRow
            every { headerRow.iterator() } returns mutableListOf(header).iterator()
            every { header.cellType } returns CellType.STRING
            every { header.stringCellValue } returns "Data do Negócio"
            every { header.columnIndex } returns 0

            shouldThrow<HeaderParseException> {
                open(byteArrayOf(1), listOf("Data do Negócio", "Preço"))
            }

            verify { workbook.close() }
        }
    }
})
