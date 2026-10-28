package dev.agner.portfolio.usecase

import dev.agner.portfolio.usecase.configuration.ITransactionTemplate
import dev.agner.portfolio.usecase.corporateaction.model.CorporateAction
import dev.agner.portfolio.usecase.corporateaction.model.CorporateActionCreation
import dev.agner.portfolio.usecase.corporateaction.model.CorporateActionKind
import dev.agner.portfolio.usecase.corporateaction.model.CorporateActionSource
import dev.agner.portfolio.usecase.corporateaction.model.CorporateActionStatus
import kotlinx.datetime.LocalDate
import java.math.BigDecimal

// Shared builders for the corporate-action tests: the event model, its write shape and the
// transaction stub. Keeping them here stops each spec from re-deriving the same fixture.

fun bd(value: String) = BigDecimal(value)

fun corporateAction(
    kind: CorporateActionKind,
    id: Int = 1,
    ticker: String = "PETR4",
    exDate: String = "2026-10-20",
    ratio: String? = null,
    declaredValuePerShare: String? = null,
    targetTicker: String? = null,
    processNumber: String? = null,
    rawType: String? = null,
    ordinal: Int = 1,
    status: CorporateActionStatus = CorporateActionStatus.APPLIED,
    fileHash: String? = null,
) = CorporateAction(
    id = id,
    ticker = ticker,
    kind = kind,
    exDate = LocalDate.parse(exDate),
    approvalDate = null,
    ratio = ratio?.let(::bd),
    declaredValuePerShare = declaredValuePerShare?.let(::bd),
    targetTicker = targetTicker,
    source = CorporateActionSource.B3_MOVEMENTS,
    processNumber = processNumber,
    rawType = rawType,
    ordinal = ordinal,
    fileHash = fileHash,
    status = status,
)

fun actionCreation(
    kind: CorporateActionKind,
    ticker: String = "PETR4",
    exDate: String = "2026-10-20",
    ratio: String? = null,
    declaredValuePerShare: String? = null,
    targetTicker: String? = null,
    ordinal: Int = 1,
    fileHash: String? = null,
) = CorporateActionCreation(
    ticker = ticker,
    kind = kind,
    exDate = LocalDate.parse(exDate),
    ratio = ratio?.let(::bd),
    declaredValuePerShare = declaredValuePerShare?.let(::bd),
    targetTicker = targetTicker,
    ordinal = ordinal,
    fileHash = fileHash,
)

fun CorporateActionCreation.toModel(
    status: CorporateActionStatus = CorporateActionStatus.APPLIED,
    id: Int = 7,
) = CorporateAction(
    id = id,
    ticker = ticker,
    kind = kind,
    exDate = exDate,
    approvalDate = approvalDate,
    ratio = ratio,
    declaredValuePerShare = declaredValuePerShare,
    targetTicker = targetTicker,
    source = source,
    processNumber = processNumber,
    rawType = rawType,
    ordinal = ordinal,
    fileHash = fileHash,
    status = status,
)

// Runs the block inline — the unit specs are not exercising the transaction boundary, only the
// service's composition inside it.
class InlineTransaction : ITransactionTemplate {
    override suspend fun <T> execute(block: suspend () -> T): T = block()
}
