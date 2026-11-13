package dev.agner.portfolio.usecase.corporateaction.model

import java.math.BigDecimal

// The owner's decision on a parked event: the template data the B3 export did not carry. A kind the
// engine can compute is applied with the supplied fields; a kind it can never compute on its own (a
// rights issue, a merger) is recorded as handled with no ledger effect. An absent field keeps the
// stored value, so a re-submitted form never erases what the export already brought.
data class CorporateActionResolution(
    val ratio: BigDecimal? = null,
    val declaredValuePerShare: BigDecimal? = null,
    val targetTicker: String? = null,
) {
    fun mergedWith(action: CorporateAction): CorporateActionCreation = CorporateActionCreation(
        ticker = action.ticker,
        kind = action.kind,
        exDate = action.exDate,
        approvalDate = action.approvalDate,
        ratio = ratio ?: action.ratio,
        declaredValuePerShare = declaredValuePerShare ?: action.declaredValuePerShare,
        targetTicker = targetTicker ?: action.targetTicker,
        source = action.source,
        processNumber = action.processNumber,
        rawType = action.rawType,
        ordinal = action.ordinal,
        fileHash = action.fileHash,
    ).normalized()
}
