package dev.agner.portfolio.usecase.import.model

import com.fasterxml.jackson.annotation.JsonProperty

// What apply did with one staged row. `APPLIED` rows are in the ledger, `SKIPPED_DUPLICATE` rows
// were already there and are ignored, and `REJECTED` rows stay attached to the import as the retry
// batch — never all-or-nothing. The wire spelling is lower-case snake, like the row situation.
enum class ImportRowOutcome {
    @JsonProperty("applied")
    APPLIED,

    @JsonProperty("rejected")
    REJECTED,

    @JsonProperty("skipped_duplicate")
    SKIPPED_DUPLICATE,
}
