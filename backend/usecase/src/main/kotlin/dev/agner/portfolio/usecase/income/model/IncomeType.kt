package dev.agner.portfolio.usecase.income.model

// The kind of payment a Proventos event carries. These are the English keys the API serialises; the
// pt-BR label the UI renders lives in the frontend. `FUND_INCOME` is a FII rendimento; `JCP` is
// *Juros sobre Capital Próprio*, the Brazilian interest-on-equity payment.
enum class IncomeType {
    DIVIDEND,
    JCP,
    FUND_INCOME,
}
