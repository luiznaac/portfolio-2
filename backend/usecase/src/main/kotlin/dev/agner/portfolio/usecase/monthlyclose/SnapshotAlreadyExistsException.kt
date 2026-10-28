package dev.agner.portfolio.usecase.monthlyclose

import dev.agner.portfolio.usecase.commons.DomainException

// Raised when a snapshot would overwrite an existing one. The snapshot is immutable: a month closes
// once, and any correction belongs in a later month's snapshot rather than an edit of history.
class SnapshotAlreadyExistsException(monthId: Int) : DomainException(
    error = "snapshot-already-exists",
    userMessage = "This month already has a snapshot",
    detail = "Month $monthId already has a snapshot; snapshots are immutable",
)
