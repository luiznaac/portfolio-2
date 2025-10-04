package dev.agner.portfolio.httpapi.configuration

import com.fasterxml.jackson.databind.ObjectMapper
import dev.agner.portfolio.usecase.configuration.JsonMapper
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.ktor.util.reflect.typeInfo
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.charsets.Charsets
import kotlinx.datetime.LocalDate
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.ByteArrayOutputStream
import java.math.BigDecimal

@Suppress("UNCHECKED_CAST")
class XlsxConverterTest : DescribeSpec({

    lateinit var converter: XlsxConverter
    lateinit var objectMapper: ObjectMapper

    beforeEach {
        objectMapper = JsonMapper.mapper
        converter = XlsxConverter(objectMapper)
    }

    describe("XlsxConverter deserialization") {

        it("should deserialize xlsx with complete data") {
            converter.register<TestOrder>(
                DataDef("Date", "date") { LocalDate.parse(this!!) },
                DataDef("Amount", "amount") { this!!.toBigDecimal().setScale(2) },
                DataDef("Description", "description")
            )

            val xlsxBytes = createTestXlsx(
                headers = listOf("Date", "Amount", "Description"),
                rows = listOf(
                    listOf("2023-01-15", "100.50", "Test Order 1"),
                    listOf("2023-02-20", "250.75", "Test Order 2")
                )
            )

            val result = converter.deserialize(
                charset = Charsets.UTF_8,
                typeInfo = typeInfo<List<TestOrder>>(),
                content = ByteReadChannel(xlsxBytes)
            ) as List<TestOrder>

            result.size shouldBe 2
            result[0].date shouldBe LocalDate.parse("2023-01-15")
            result[0].amount shouldBe BigDecimal("100.50")
            result[0].description shouldBe "Test Order 1"
            result[1].date shouldBe LocalDate.parse("2023-02-20")
            result[1].amount shouldBe BigDecimal("250.75")
            result[1].description shouldBe "Test Order 2"
        }

        it("should handle empty cells correctly") {
            converter.register<TestOrder>(
                DataDef("Date", "date") { LocalDate.parse(this!!) },
                DataDef("Amount", "amount") { this?.toBigDecimal()?.setScale(2) ?: BigDecimal("0.00") },
                DataDef("Description", "description")
            )

            val xlsxBytes = createTestXlsx(
                headers = listOf("Date", "Amount", "Description"),
                rows = listOf(
                    listOf("2023-01-15", "", "Test Order 1"),
                    listOf("2023-02-20", "", "Test Order 2")
                )
            )

            val result = converter.deserialize(
                charset = Charsets.UTF_8,
                typeInfo = typeInfo<List<TestOrder>>(),
                content = ByteReadChannel(xlsxBytes)
            ) as List<TestOrder>

            result.size shouldBe 2
            result[0].date shouldBe LocalDate.parse("2023-01-15")
            result[0].amount shouldBe BigDecimal("0.00")
            result[0].description shouldBe "Test Order 1"
            result[1].date shouldBe LocalDate.parse("2023-02-20")
            result[1].amount shouldBe BigDecimal("0.00")
            result[1].description shouldBe "Test Order 2"
        }

        it("should handle data definitions without target property") {
            converter.register<TestOrder>(
                DataDef("Date", "date") { LocalDate.parse(this!!) },
                DataDef("Amount", "amount") { this!!.toBigDecimal().setScale(2) },
                DataDef("Description", "description"),
                DataDef("Ignored Column") // No target property, should be ignored
            )

            val xlsxBytes = createTestXlsx(
                headers = listOf("Date", "Amount", "Description", "Ignored Column"),
                rows = listOf(
                    listOf("2023-01-15", "100.50", "Test Order", "This should be ignored")
                )
            )

            val result = converter.deserialize(
                charset = Charsets.UTF_8,
                typeInfo = typeInfo<List<TestOrder>>(),
                content = ByteReadChannel(xlsxBytes)
            ) as List<TestOrder>

            result.size shouldBe 1
            result[0].date shouldBe LocalDate.parse("2023-01-15")
            result[0].amount shouldBe BigDecimal("100.50")
            result[0].description shouldBe "Test Order"
        }

        it("should throw exception when no matching definition found") {
            converter.register<TestOrder>(
                DataDef("Date", "date") { LocalDate.parse(this!!) },
                DataDef("Amount", "amount") { this!!.toBigDecimal().setScale(2) }
            )

            val xlsxBytes = createTestXlsx(
                headers = listOf("WrongHeader1", "WrongHeader2"),
                rows = listOf(listOf("Value1", "Value2"))
            )

            shouldThrow<NoSuchElementException> {
                converter.deserialize(
                    charset = Charsets.UTF_8,
                    typeInfo = typeInfo<List<TestOrder>>(),
                    content = ByteReadChannel(xlsxBytes)
                )
            }
        }

        it("should handle multiple registered types and pick correct one") {
            converter.register<TestOrder>(
                DataDef("Date", "date") { LocalDate.parse(this!!) },
                DataDef("Amount", "amount") { this!!.toBigDecimal().setScale(2) },
                DataDef("Description", "description")
            )

            converter.register<AnotherTestOrder>(
                DataDef("Order Date", "order_date") { LocalDate.parse(this!!) },
                DataDef("Price", "price") { this!!.toBigDecimal().setScale(2) }
            )

            val xlsxBytes = createTestXlsx(
                headers = listOf("Order Date", "Price"),
                rows = listOf(listOf("2023-01-15", "100.50"))
            )

            val result = converter.deserialize(
                charset = Charsets.UTF_8,
                typeInfo = typeInfo<List<AnotherTestOrder>>(),
                content = ByteReadChannel(xlsxBytes)
            ) as List<AnotherTestOrder>

            result.size shouldBe 1
            result[0].orderDate shouldBe LocalDate.parse("2023-01-15")
            result[0].price shouldBe BigDecimal("100.50")
        }
    }
})

private fun createTestXlsx(headers: List<String>, rows: List<List<String>>): ByteArray {
    val workbook: Workbook = XSSFWorkbook()
    val sheet: Sheet = workbook.createSheet("Sheet1")

    // Create header row
    val headerRow: Row = sheet.createRow(0)
    headers.forEachIndexed { index, header ->
        headerRow.createCell(index).setCellValue(header)
    }

    // Create data rows
    rows.forEachIndexed { rowIndex, rowData ->
        val row: Row = sheet.createRow(rowIndex + 1)
        rowData.forEachIndexed { cellIndex, cellValue ->
            if (cellValue.isNotEmpty()) row.createCell(cellIndex).setCellValue(cellValue)
        }
    }

    val outputStream = ByteArrayOutputStream()
    workbook.use { it.write(outputStream) }
    return outputStream.toByteArray()
}

data class TestOrder(
    val date: LocalDate,
    val amount: BigDecimal,
    val description: String?
)

data class AnotherTestOrder(
    val orderDate: LocalDate,
    val price: BigDecimal
)
