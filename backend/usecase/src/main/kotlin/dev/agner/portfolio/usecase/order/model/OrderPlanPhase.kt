package dev.agner.portfolio.usecase.order.model

// The slice of the order plan a read asks for. The rebalancing engine lands in layers: IDEALS is the
// pure chain and its leaf candidates; funding, pro-rata and the full plan join it in later slices.
enum class OrderPlanPhase {
    IDEALS,
    ;

    companion object {
        // What the error body names when a caller sends an unknown phase; derived from the enum so a
        // new phase needs no second edit.
        val supportedPhases: String get() = entries.joinToString { it.name.lowercase() }

        // Query strings are lowercase (`?phase=ideals`); accept either case and default to the only
        // phase that exists. An unknown value is a caller error, not a silently empty plan.
        fun from(value: String?): OrderPlanPhase =
            if (value == null) {
                IDEALS
            } else {
                entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
                    ?: throw InvalidOrderPlanPhaseException(value)
            }
    }
}
