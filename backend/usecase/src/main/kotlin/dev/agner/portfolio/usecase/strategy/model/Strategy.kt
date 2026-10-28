package dev.agner.portfolio.usecase.strategy.model

import dev.agner.portfolio.usecase.catalog.model.AssetClass

// A named bucket of Listed-Assets money (CONTEXT.md -> Strategy). A REPORT_DRIVEN Strategy is
// driven by an external report and planned through Targets; a MANUAL one is the legacy bucket,
// holding money through Attribution only. Its share lives in the month's Target rows, never here.
data class Strategy(
    val id: Int,
    val name: String,
    val assetClass: AssetClass,
    val kind: StrategyKind,
)

// The creation shape: no id (the repository assigns it) and no editions. `kind` is nullable so a
// request that omits it reaches the service as a domain error instead of a deserialization 500;
// `StrategyService.create` rejects the null and the repository asserts it was validated.
data class StrategyCreation(
    val name: String,
    val assetClass: AssetClass,
    val kind: StrategyKind? = null,
)
