package dev.agner.portfolio.usecase.strategy

import dev.agner.portfolio.usecase.strategy.model.DiffChange
import dev.agner.portfolio.usecase.strategy.model.DiffWeight
import dev.agner.portfolio.usecase.strategy.model.EditionDiff
import dev.agner.portfolio.usecase.strategy.model.EditionEntry
import org.springframework.stereotype.Component
import java.math.BigDecimal

// Diffs an Edition's entries against the previous active edition's, keyed by ticker. A ticker only
// in the new set entered; one only in the old set exited with weight 0 (the report dropping it means
// "do not hold it", never "forget it"); one in both whose weight or rating moved changed.
@Component
class EditionDiffCalculator {

    fun compute(previous: List<EditionEntry>, current: List<EditionEntry>): EditionDiff {
        val previousByTicker = previous.associateBy { it.ticker }
        val currentByTicker = current.associateBy { it.ticker }

        return EditionDiff(
            entered = current
                .filter { it.ticker !in previousByTicker }
                .map { DiffWeight(it.ticker, it.weight) },
            exited = previous
                .filter { it.ticker !in currentByTicker }
                .map { DiffWeight(it.ticker, EXITED_WEIGHT) },
            changed = current.mapNotNull { changeFrom(previousByTicker[it.ticker], it) },
        )
    }

    private fun changeFrom(before: EditionEntry?, after: EditionEntry): DiffChange? = when {
        before == null -> null
        before.weight.compareTo(after.weight) == 0 && before.rating == after.rating -> null
        else -> DiffChange(
            ticker = after.ticker,
            weightBefore = before.weight,
            weightAfter = after.weight,
            ratingBefore = before.rating,
            ratingAfter = after.rating,
        )
    }

    private companion object {
        val EXITED_WEIGHT: BigDecimal = BigDecimal("0.00")
    }
}
