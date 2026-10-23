package dev.agner.portfolio.usecase.tax.model

import kotlinx.datetime.YearMonth
import java.math.BigDecimal

typealias CarryBalances = Map<RealizedKind, BigDecimal>

// The starting point of the carry roll. `None` means the ledger covers all history from zero;
// `Adopted` means one or more buckets were seeded at an adoption close, so each seeded bucket's
// roll skips ledger months up to and including its own `asOfPeriod`. A bucket without a seed starts
// at zero and rolls its whole ledger history.
sealed interface CarrySeed {
    data object None : CarrySeed

    data class Adopted(val openings: List<CarryBucketOpening>) : CarrySeed

    companion object {
        fun from(openings: List<CarryBucketOpening>): CarrySeed =
            if (openings.isEmpty()) None else Adopted(openings)

        // One adoption close for all the seeded buckets — the shape the adoption flow writes.
        fun adopted(openingBalances: CarryBalances, through: YearMonth): CarrySeed =
            Adopted(openingBalances.map { (kind, balance) -> CarryBucketOpening(kind, balance, through) })
    }
}
