package dev.agner.portfolio.usecase.listedasset.model

import kotlinx.datetime.LocalDate
import java.math.BigDecimal

enum class QuoteSource {
    BRAPI,
}

data class Quote(
    val price: BigDecimal,
    val date: LocalDate,
    val source: QuoteSource,
)
