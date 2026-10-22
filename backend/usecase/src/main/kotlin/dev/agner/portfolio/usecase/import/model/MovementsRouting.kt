package dev.agner.portfolio.usecase.import.model

import dev.agner.portfolio.usecase.corporateaction.model.CorporateActionCreation
import dev.agner.portfolio.usecase.income.model.IncomeCreation

// One Movimentações XLSX split by destination: trade rows join the negotiation ledger, cash
// proventos reconcile the income ledger, and every other row becomes a corporate action (applied or
// review-queued). A row is never dropped — an unrecognised type is an OTHER corporate action.
data class MovementsRouting(
    val trades: List<ImportRowPayload> = emptyList(),
    val incomes: List<IncomeCreation> = emptyList(),
    val corporateActions: List<CorporateActionCreation> = emptyList(),
)
