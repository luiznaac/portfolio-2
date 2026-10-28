package dev.agner.portfolio.gateway.cotahist

private const val RECORD_LENGTH = 245

// Builds a fixed-width COTAHIST record by placing each value at its 1-based start position, so a
// test reads against B3's published layout instead of a wall of padding.
internal fun cotahistLine(vararg fields: Pair<Int, String>): String {
    val record = StringBuilder(" ".repeat(RECORD_LENGTH))
    fields.forEach { (position, value) ->
        record.replace(position - 1, position - 1 + value.length, value)
    }
    return record.toString()
}

internal fun petr4Record() = cotahistLine(
    1 to "01",
    3 to "20261014",
    11 to "02",
    13 to "PETR4",
    25 to "010",
    28 to "PETROBRAS",
    109 to "0000000004892",
    225 to "BRPETRACNPR6",
)
