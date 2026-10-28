package dev.agner.portfolio.usecase.tax.model

// The three tax species a listed-asset sale is realized into. A STOCK sale is SWING unless the
// same-day same-institution buy offsets it, in which case the matched quantity is DAY_TRADE; an FII
// sale is always FII because its loss bucket may only offset FII gains (Lei 8.668/1993 art. 16-A,
// as amended). Every other AssetKind is outside this ticket's three buckets.
enum class RealizedKind {
    SWING,
    DAY_TRADE,
    FII,
}
