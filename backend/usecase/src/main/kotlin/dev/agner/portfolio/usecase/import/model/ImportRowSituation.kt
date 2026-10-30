package dev.agner.portfolio.usecase.import.model

import com.fasterxml.jackson.annotation.JsonProperty

// The review's per-row verdict, computed on read with the same natural tuple + occurrence walk apply
// uses. `NEW` is the first occurrence past what the ledger already holds; `DUPLICATE_IN_LEDGER` is an
// occurrence the ledger already holds; `DUPLICATE_IN_FILE` repeats a row that is itself new in this
// file; `ERROR` is a row the parser flagged. Apply writes every occurrence past the ledger, because
// the ordinal model keeps legitimate same-day same-price executions, so `duplicate_in_file` is an
// attention flag on a row that will still be written — the ledger-covered prefix and `error` rows
// are what apply skips. The wire spelling is lower-case snake, unlike the enum-name spelling the
// other edge enums use.
enum class ImportRowSituation {
    @JsonProperty("new")
    NEW,

    @JsonProperty("duplicate_in_file")
    DUPLICATE_IN_FILE,

    @JsonProperty("duplicate_in_ledger")
    DUPLICATE_IN_LEDGER,

    @JsonProperty("error")
    ERROR,
}
