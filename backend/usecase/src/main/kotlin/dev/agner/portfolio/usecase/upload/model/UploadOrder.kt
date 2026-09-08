package dev.agner.portfolio.usecase.upload.model

import dev.agner.portfolio.usecase.bond.model.BondOrderCreation
import dev.agner.portfolio.usecase.bond.model.BondOrderType
import kotlinx.datetime.LocalDate
import java.math.BigDecimal

data class UploadOrder(
    val date: LocalDate,
    val action: Action,
    val amount: BigDecimal,
) {
    enum class Action(val values: List<String>) {
        BUY(listOf("Aplicação", "Guardado")),
        SELL(listOf("Resgate", "Resgatado")),
        ;

        companion object {
            fun fromValue(value: String) = entries.firstOrNull {
                    act ->
                act.values.any { it.equals(value, ignoreCase = true) }
            } ?: throw IllegalArgumentException("Invalid value for Action: $value")
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
