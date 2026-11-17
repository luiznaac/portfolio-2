package dev.agner.portfolio.usecase.catalog.model

// B3 lists a subscription right under the underlying's code with its final class digit replaced by
// 2 (RBRP11 -> RBRP12): a right is its own paper, priced and taxed on its own book, never the
// underlying FII. A code already ending in 2 is returned unchanged.
fun String.rightsTicker(): String {
    val normalized = normalizedTicker()
    return if (normalized.lastOrNull()?.isDigit() == true) {
        normalized.dropLast(1) + "2"
    } else {
        normalized
    }
}

// Whether a code is shaped like a subscription right by the same rule: a stock right is the
// underlying's five-character code with its final class digit replaced by 2 (PETR4 -> PETR2), and a
// unit/FII right is the six-character `XXXX11` code replaced by `XXXX12` (RBRP11 -> RBRP12). A BDR's
// `XXXX32` is six characters ending in 32, so it is not a right. The import uses this to recognise a
// rights paper before the catalog has been told the ticker is one.
fun String.isRightsTicker(): Boolean {
    val code = normalizedTicker()
    return (code.length == 5 && code.endsWith("2")) || (code.length == 6 && code.endsWith("12"))
}
