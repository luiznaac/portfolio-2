package dev.agner.portfolio.integrationTest.helpers

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

// A minimal valid COTAHIST file: spot-market records (TPMERC 010) zipped the way B3 publishes
// them. Records are 245-char fixed-width lines built from the official 1-based offsets.
internal fun cotahistZip(vararg records: String): ByteArray {
    val bytes = ByteArrayOutputStream()
    ZipOutputStream(bytes).use { zip ->
        zip.putNextEntry(ZipEntry("COTAHIST_D.TXT"))
        for (record in records) zip.write((record + "\n").toByteArray())
        zip.closeEntry()
    }
    return bytes.toByteArray()
}

internal fun cotahistRecord(
    ticker: String,
    bdiType: String,
    date: String = "20261013",
    close: String = "0000000010000",
) = CharArray(245) { ' ' }.apply {
    put(0, "01")
    put(2, date)
    put(10, bdiType)
    put(12, ticker)
    put(24, "010")
    put(27, "TEST PAPER")
    put(39, "CI")
    put(108, close)
    put(230, "BRTESTACNOR0")
}.concatToString()

private fun CharArray.put(start: Int, value: String) {
    value.forEachIndexed { offset, char -> this[start + offset] = char }
}
