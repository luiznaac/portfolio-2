package dev.agner.portfolio.usecase.income

import dev.agner.portfolio.usecase.income.model.IncomeStatus
import kotlinx.datetime.LocalDate

// The Proventos lifecycle engine, as a pure rule so a read can derive a movement's stage without
// writing (backend/AGENTS.md -> separate commands from queries). The one date the "Proventos a
// Receber" export carries is its payment forecast, so that date drives the stages: ahead of it the
// payment is announced, on it it is payable, past it the file's net amount is taken as received —
// the export states the net to receive, so no withholding re-derivation and no receipt feed is
// needed to fold a past payment in. Reconciled is owned by a stored row (the Movimentações match),
// so it always wins and the lifecycle never walks a reconciled movement backwards.
object IncomeLifecycle {

    fun statusAt(stored: IncomeStatus, payableDate: LocalDate, today: LocalDate): IncomeStatus = when {
        stored == IncomeStatus.RECONCILED -> IncomeStatus.RECONCILED
        today > payableDate -> IncomeStatus.RECEIVED
        today == payableDate -> IncomeStatus.PAYABLE
        else -> IncomeStatus.ANNOUNCED
    }
}
