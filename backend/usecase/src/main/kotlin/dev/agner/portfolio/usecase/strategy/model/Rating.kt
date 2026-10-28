package dev.agner.portfolio.usecase.strategy.model

// An Edition Entry's analyst recommendation, as the XP reports print it ("Compra"/"Neutro"/"Venda").
// The keys are English and cross the API; the pt-BR label lives in the frontend's i18n.
enum class Rating {
    BUY,
    NEUTRAL,
    SELL,
}
