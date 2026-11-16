package dev.agner.portfolio.httpapi.import

import java.math.BigDecimal
import java.math.RoundingMode

private const val GROSS_SCALE = 2

// A broker export's gross value is derived from quantity × price, never stored: a mismatch is a
// row-level error, so the file still stages and the row can be reviewed or dropped. Shared by the
// B3 and Rico trade layouts.
internal fun grossMismatch(gross: BigDecimal?, quantity: BigDecimal, unitPrice: BigDecimal): String? {
    val gross = gross ?: return "Missing gross value"
    val expected = quantity.multiply(unitPrice).setScale(GROSS_SCALE, RoundingMode.HALF_UP)
    return if (gross.setScale(GROSS_SCALE, RoundingMode.HALF_UP).compareTo(expected) == 0) {
        null
    } else {
        "Gross $gross does not match quantity x price ($expected)"
    }
}
