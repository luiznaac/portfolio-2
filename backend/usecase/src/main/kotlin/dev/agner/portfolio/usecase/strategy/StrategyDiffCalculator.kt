package dev.agner.portfolio.usecase.strategy

import dev.agner.portfolio.usecase.strategy.model.StrategyTarget
import dev.agner.portfolio.usecase.strategy.model.StrategyTargetChange
import dev.agner.portfolio.usecase.strategy.model.StrategyTargetDiff
import org.springframework.stereotype.Component

/** Pure: what changed between two consecutive editions' target lists, by ticker. */
@Component
class StrategyDiffCalculator {

    fun diff(before: List<StrategyTarget>, after: List<StrategyTarget>): StrategyTargetDiff {
        val beforeByTicker = before.associateBy { it.ticker }
        val afterByTicker = after.associateBy { it.ticker }

        return StrategyTargetDiff(
            entered = after.filter { it.ticker !in beforeByTicker },
            exited = before.filter { it.ticker !in afterByTicker },
            changed = after.mapNotNull { current ->
                val previous = beforeByTicker[current.ticker] ?: return@mapNotNull null
                if (previous.weight == current.weight) {
                    null
                } else {
                    StrategyTargetChange(ticker = current.ticker, before = previous, after = current)
                }
            },
        )
    }
}
