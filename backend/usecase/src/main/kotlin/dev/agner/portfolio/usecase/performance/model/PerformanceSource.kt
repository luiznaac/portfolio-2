package dev.agner.portfolio.usecase.performance.model

import com.fasterxml.jackson.annotation.JsonProperty

// Which era a series point belongs to: the pre-planning months computed from the live ledger
// (`computed`), or the months replayed from their immutable Snapshot (`snapshot`). The two eras have
// different bases, so a point never hides which one it came from.
enum class PerformanceSource {
    @JsonProperty("computed")
    COMPUTED,

    @JsonProperty("snapshot")
    SNAPSHOT,
}
