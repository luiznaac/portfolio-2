package dev.agner.portfolio.usecase.performance.benchmark

// The healthy publication cadence shared by the coverage rules and the store's gap finder: how far
// a point may lag a request bound, and how wide an internal step may grow before it is a hole.
internal object BenchmarkCadence {

    const val LEVEL_BASE_LOOKBACK_DAYS = 10
    const val LEVEL_END_TOLERANCE_DAYS = 5
    const val DAILY_BOUND_TOLERANCE_DAYS = 5
    const val DAILY_MAX_INTERNAL_GAP_DAYS = 5
    const val MONTHLY_LEADING_TOLERANCE_DAYS = 27
    const val MONTHLY_TOLERANCE_DAYS = 31

    // One day short of the level lookback: a first point landing on the period start itself leaves
    // no base to measure from, so it must trigger a fetch even though it is within the lookback.
    const val REQUEST_LEADING_TOLERANCE_DAYS = LEVEL_BASE_LOOKBACK_DAYS - 1
}
