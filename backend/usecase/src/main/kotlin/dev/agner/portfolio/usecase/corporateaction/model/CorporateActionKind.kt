package dev.agner.portfolio.usecase.corporateaction.model

import dev.agner.portfolio.usecase.income.model.TaxNature

// The kind of corporate action a listed asset undergoes. The quantity events mutate a holder's
// Position (and its attribution slices); the cash-only kinds leave the quantity alone and produce a
// CashMovement with the matching tax nature. The review kinds are persisted for a human decision
// and never applied automatically — a rights issue or a merger ratio needs information the B3
// movement export does not carry. OTHER is the catch-all for a movement type the parser does not
// classify (an RF row, an unknown label): it is queued, never dropped.
//
// The English keys are what the API serialises; the canonical pt-BR labels (desdobramento,
// grupamento, bonificação em ações, restituição, …) live in the frontend.
enum class CorporateActionKind {
    SPLIT,
    REVERSE_SPLIT,
    BONUS_SHARES,
    TICKER_CHANGE,
    RIGHTS_ISSUE,
    RIGHTS_ASSIGNMENT,
    INCORPORATION,
    MERGER,
    SPIN_OFF,
    CAPITAL_RETURN,
    AMORTIZATION,
    CASH_BONUS,
    OTHER,
    ;

    // Kinds that change the holder's quantity and therefore scale the attribution slices.
    val isQuantityEvent: Boolean
        get() = this == SPLIT || this == REVERSE_SPLIT || this == BONUS_SHARES

    // Kinds that pay cash without changing the quantity.
    val isCashOnly: Boolean
        get() = this == CAPITAL_RETURN || this == AMORTIZATION || this == CASH_BONUS

    // Kinds the engine refuses to apply on its own. A rights issue and a rights assignment are
    // voluntary and priced by the holder; incorporation/merger/spin-off carry an assembly-fixed
    // exchange ratio that is not in the export; OTHER is an unclassified row.
    val requiresReview: Boolean
        get() = this == RIGHTS_ISSUE || this == RIGHTS_ASSIGNMENT || this == INCORPORATION ||
            this == MERGER || this == SPIN_OFF || this == OTHER

    // The tax character a cash event's movement carries. A capital return reduces cost (IN 1.585
    // §8); an amortization returns capital; a cash bonus and an auctioned fraction have no character
    // the sources pin down, so they stay OTHER.
    val cashTaxNature: TaxNature?
        get() = when (this) {
            CAPITAL_RETURN -> TaxNature.COST_REDUCTION
            AMORTIZATION -> TaxNature.RETURN_OF_CAPITAL
            CASH_BONUS -> TaxNature.OTHER
            else -> null
        }
}
