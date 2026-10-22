package dev.agner.portfolio.usecase.order.model

// The slice of the order plan a read asks for, cumulative deepest-layer-first. IDEALS is the chain
// alone; CANDIDATES adds the whole-unit leaf orders it implies; FUNDING adds the SELLs that fund
// those buys. Pro-rata and the full plan join in later slices.
enum class OrderPlanPhase {
    IDEALS,
    CANDIDATES,
    FUNDING,
    ;

    // Every phase past the chain-only preview emits the whole-unit leaf orders.
    val includesCandidates: Boolean get() = this != IDEALS

    // Only the funding pass — and the full phase after it — sells positions to cover buy deficits.
    val includesFunding: Boolean get() = this == FUNDING

    companion object {
        // What the error body names when a caller sends an unknown phase; derived from the enum so a
        // new phase needs no second edit.
        val supportedPhases: String get() = entries.joinToString { it.name.lowercase() }

        // Query strings are lowercase (`?phase=candidates`); accept either case and default to the
        // shallowest phase. An unknown value is a caller error, not a silently empty plan.
        fun from(value: String?): OrderPlanPhase =
            if (value == null) {
                IDEALS
            } else {
                entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
                    ?: throw InvalidOrderPlanPhaseException(value)
            }
    }
}
