package dev.agner.portfolio.usecase.income.model

// The Proventos lifecycle (CONTEXT.md -> Income): a payment is announced by the file, becomes
// payable on its payable date, and is received at the file's net amount once that date passes.
// Reconciled is the last stage only a match against the "Movimentações" feed can reach; it is the
// one stage a stored row owns, so it never regresses.
enum class IncomeStatus {
    ANNOUNCED,
    PAYABLE,
    RECEIVED,
    RECONCILED,
}
