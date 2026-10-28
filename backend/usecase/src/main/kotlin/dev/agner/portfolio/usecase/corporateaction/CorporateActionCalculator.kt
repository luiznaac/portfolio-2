package dev.agner.portfolio.usecase.corporateaction

import dev.agner.portfolio.usecase.corporateaction.model.CorporateAction
import dev.agner.portfolio.usecase.corporateaction.model.CorporateActionKind
import dev.agner.portfolio.usecase.listedasset.model.Position
import java.math.BigDecimal
import java.math.RoundingMode

// The holder-side math of a corporate action. Pure and side-effect free, so the Position replay and
// the unit tests both drive it without a repository or a clock.
//
// `ratio` follows the issuer's own direction: a split multiplies the quantity by it, a reverse
// split divides by it. Both preserve total cost (the Brazilian fiscal rule: split shares cost
// zero, a reverse split keeps the cost of the remaining units); a bonificação raises cost by the
// declared capitalised value per prior share, and a restituição lowers it by the cash returned.
object CorporateActionCalculator {

    private const val SCALE = 6

    fun apply(position: Position, action: CorporateAction): Position = when (action.kind) {
        CorporateActionKind.SPLIT -> split(position, action)
        CorporateActionKind.REVERSE_SPLIT -> reverseSplit(position, action)
        CorporateActionKind.BONUS_SHARES -> bonusShares(position, action)
        CorporateActionKind.CAPITAL_RETURN -> capitalReturn(position, action)
        else -> position
    }

    // How an attribution slice moves with the event. The slice is scaled by the same rule as the
    // position so the attributed/unattributed split keeps its proportion; a reverse split floors each
    // slice, and the sum stays within the floored position.
    fun scaleQuantity(quantity: BigDecimal, action: CorporateAction): BigDecimal = when (action.kind) {
        CorporateActionKind.SPLIT -> ratio(action)?.let { quantity.multiply(it) } ?: quantity
        CorporateActionKind.BONUS_SHARES ->
            ratio(action)?.let { quantity.multiply(BigDecimal.ONE.add(it)) } ?: quantity
        CorporateActionKind.REVERSE_SPLIT ->
            ratio(action)?.let { quantity.divide(it, 0, RoundingMode.FLOOR) } ?: quantity
        else -> quantity
    }

    // The cash the event pays the holder, if any. A reverse split's leftover fraction is auctioned and
    // paid in cash; without a market price for the auction, its cost basis at the prior average is
    // the best estimate the ledger can make, and the movement is tagged OTHER (no tax character).
    fun cashAmount(position: Position, action: CorporateAction): BigDecimal? = when (action.kind) {
        CorporateActionKind.CAPITAL_RETURN,
        CorporateActionKind.AMORTIZATION,
        CorporateActionKind.CASH_BONUS,
        -> perShareCash(position, action)
        CorporateActionKind.REVERSE_SPLIT -> fractionCash(position, action)
        else -> null
    }

    private fun split(position: Position, action: CorporateAction): Position {
        val ratio = ratio(action) ?: return position
        return position.copy(
            quantity = position.quantity.multiply(ratio),
            averagePrice = position.averagePrice.divide(ratio, SCALE, RoundingMode.HALF_UP),
        )
    }

    private fun reverseSplit(position: Position, action: CorporateAction): Position {
        val ratio = ratio(action) ?: return position
        return position.copy(
            quantity = position.quantity.divide(ratio, 0, RoundingMode.FLOOR),
            averagePrice = position.averagePrice.multiply(ratio).setScale(SCALE, RoundingMode.HALF_UP),
        )
    }

    private fun bonusShares(position: Position, action: CorporateAction): Position {
        val ratio = ratio(action) ?: return position
        val newQuantity = position.quantity.multiply(BigDecimal.ONE.add(ratio))
        if (newQuantity.signum() <= 0) return position.copy(quantity = newQuantity, averagePrice = BigDecimal.ZERO)
        val priorCost = position.quantity.multiply(position.averagePrice)
        val grantedCost = position.quantity.multiply(action.declaredValuePerShare ?: BigDecimal.ZERO)
        return position.copy(
            quantity = newQuantity,
            averagePrice = priorCost.add(grantedCost).divide(newQuantity, SCALE, RoundingMode.HALF_UP),
        )
    }

    private fun capitalReturn(position: Position, action: CorporateAction): Position {
        val cash = cashAmount(position, action) ?: return position
        val remainingCost = position.quantity.multiply(position.averagePrice).subtract(cash).max(BigDecimal.ZERO)
        val average = if (position.quantity.signum() > 0) {
            remainingCost.divide(position.quantity, SCALE, RoundingMode.HALF_UP)
        } else {
            BigDecimal.ZERO
        }
        return position.copy(averagePrice = average)
    }

    private fun perShareCash(position: Position, action: CorporateAction): BigDecimal? {
        val perShare = action.declaredValuePerShare ?: return null
        return perShare.multiply(position.quantity).takeIf { it.signum() > 0 }
    }

    private fun fractionCash(position: Position, action: CorporateAction): BigDecimal? {
        val ratio = ratio(action) ?: return null
        val converted = position.quantity.divide(ratio, 0, RoundingMode.FLOOR).multiply(ratio)
        val fraction = position.quantity.subtract(converted)
        return fraction.multiply(position.averagePrice).takeIf { it.signum() > 0 }
    }

    private fun ratio(action: CorporateAction): BigDecimal? =
        action.ratio?.takeIf { it.signum() > 0 }
}
