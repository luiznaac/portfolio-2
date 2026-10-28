package dev.agner.portfolio.usecase.strategy

import dev.agner.portfolio.usecase.commons.DomainException
import kotlinx.datetime.YearMonth

// A corrected report may replace an edition still awaiting review, but never one already accepted:
// history is immutable, so re-importing an active month is a caller error.
class EditionAlreadyActiveException(strategyId: Int, referenceMonth: YearMonth) : DomainException(
    error = "edition-already-active",
    userMessage = "This edition is already active",
    detail = "Strategy $strategyId already has an active edition for $referenceMonth",
)
