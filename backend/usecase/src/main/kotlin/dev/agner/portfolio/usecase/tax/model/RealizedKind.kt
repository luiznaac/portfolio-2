package dev.agner.portfolio.usecase.tax.model

// The four tax species a listed-asset sale is realized into. A STOCK sale is SWING unless the
// same-day same-institution buy offsets it, in which case the matched quantity is DAY_TRADE; an FII
// sale is always FII because its loss bucket may only offset FII gains (Lei 8.668/1993 art. 16-A,
// as amended); a RIGHT sale is RIGHTS — 15% under DARF 6015, outside the R$20k stock meter and the
// FII bucket, with its own loss carry. Cross-kind offset stays out of scope. Every other AssetKind
// is outside the report's four buckets.
enum class RealizedKind {
    SWING,
    DAY_TRADE,
    FII,
    RIGHTS,
}
