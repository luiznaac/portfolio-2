package dev.agner.portfolio.usecase.tax

import dev.agner.portfolio.usecase.commons.defaultScale
import dev.agner.portfolio.usecase.tax.model.CarryBalances
import dev.agner.portfolio.usecase.tax.model.CarryBucket
import dev.agner.portfolio.usecase.tax.model.CarrySeed
import dev.agner.portfolio.usecase.tax.model.RealizedKind
import dev.agner.portfolio.usecase.tax.model.RealizedResult
import kotlinx.datetime.YearMonth
import java.math.BigDecimal

// Rolls realized month results into the three species-bound loss buckets. A loss banks even in an
// exempt month (Lei 11.033/2004 art. 64); a gain consumes the bucket only when that kind is taxable
// that month. A seeded bucket skips ledger months up to and including its adoption close, because
// its opening balance already represents those results. Pure, so a month can be replayed without a
// repository.
internal object CarryRoller {

    fun roll(
        seed: CarrySeed,
        priorMonths: List<MonthRealized>,
        current: MonthRealized,
    ): List<CarryBucket> {
        val openings = when (seed) {
            CarrySeed.None -> emptyList()
            is CarrySeed.Adopted -> seed.openings
        }
        val openingBalances = openings.associate { it.kind to it.openingBalance }
        val boundaries = openings.associate { it.kind to it.asOfPeriod }
        val carried = priorMonths.fold(openingBalances) { balances, month ->
            apply(balances, month, boundaries)
        }

        return current.results.map { result ->
            val boundary = boundaries[result.kind]
            if (boundary != null && current.period <= boundary) {
                // The seed's balance already represents this month's results: showing a movement on
                // top of it would double count the boundary month.
                return@map CarryBucket(
                    kind = result.kind,
                    available = false,
                    openingBalance = null,
                    lossBanked = null,
                    lossApplied = null,
                    closingBalance = null,
                )
            }
            val opening = carried[result.kind] ?: BigDecimal.ZERO
            val movement = movement(opening, result, current.swingExceeded)
            CarryBucket(
                kind = result.kind,
                available = true,
                openingBalance = opening.defaultScale(),
                lossBanked = movement.banked.defaultScale(),
                lossApplied = movement.applied.defaultScale(),
                closingBalance = movement.closing(opening).defaultScale(),
            )
        }
    }

    private fun apply(
        balances: CarryBalances,
        month: MonthRealized,
        boundaries: Map<RealizedKind, YearMonth>,
    ): CarryBalances = month.results.associate { result ->
        val opening = balances[result.kind] ?: BigDecimal.ZERO
        val boundary = boundaries[result.kind]
        val closing = if (boundary != null && month.period <= boundary) {
            opening
        } else {
            movement(opening, result, month.swingExceeded).closing(opening)
        }
        result.kind to closing
    }

    private fun movement(opening: BigDecimal, result: RealizedResult, swingExceeded: Boolean): Movement =
        when {
            result.result.signum() < 0 -> Movement(banked = result.result.negate(), applied = BigDecimal.ZERO)
            result.result.signum() > 0 && isTaxable(result.kind, swingExceeded) ->
                Movement(banked = BigDecimal.ZERO, applied = minOf(opening, result.result))
            else -> Movement(banked = BigDecimal.ZERO, applied = BigDecimal.ZERO)
        }

    // Swing STOCK is taxable only when the month crossed the R$20k meter; day trade and FII have no
    // exemption, so a two-way branch is the whole rule.
    private fun isTaxable(kind: RealizedKind, swingExceeded: Boolean): Boolean =
        if (kind == RealizedKind.SWING) swingExceeded else true

    private data class Movement(
        val banked: BigDecimal,
        val applied: BigDecimal,
    ) {
        fun closing(opening: BigDecimal) = opening.add(banked).subtract(applied)
    }
}
