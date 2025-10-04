package dev.agner.portfolio.usecase.upload.model

import dev.agner.portfolio.usecase.bond.model.BondOrderCreation
import dev.agner.portfolio.usecase.bond.model.BondOrderType
import kotlinx.datetime.LocalDate
import java.math.BigDecimal

data class KinvoOrder(
    val date: LocalDate,
    val action: Action,
    val amount: BigDecimal,
) {
    enum class Action(val value: String) {
        BUY("Aplicação"),
        SELL("Resgate"),
        ;

        companion object {
            fun fromValue(value: String) = entries.firstOrNull { it.value.equals(value, ignoreCase = true) }
                ?: throw IllegalArgumentException("Invalid value for Action: $value")
        }
    }

    fun toBondOrderCreation(bondId: Int) = BondOrderCreation(
        bondId = bondId,
        type = when (action) {
            Action.BUY -> BondOrderType.BUY
            Action.SELL -> BondOrderType.SELL
        },
        date = date,
        amount = amount,
    )
}
