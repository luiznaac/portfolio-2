package dev.agner.portfolio.usecase.upload.model

import dev.agner.portfolio.usecase.bond.model.BondCreation
import dev.agner.portfolio.usecase.bond.model.BondCreation.FloatingRateBondCreation
import dev.agner.portfolio.usecase.bond.model.BondOrderCreation
import dev.agner.portfolio.usecase.bond.model.BondOrderType
import dev.agner.portfolio.usecase.index.model.IndexId
import kotlinx.datetime.LocalDate

data class KinvoOrder(
    val date: LocalDate,
    val description: String,
    val type: Type,
    val action: Action,
    val amount: Double,
) {
    enum class Type(val value: String) {
        CHECKING_ACCOUNT("Conta Corrente"),
        ;

        companion object {
            fun fromValue(value: String) = entries.firstOrNull { it.value.equals(value, ignoreCase = true) }
                ?: throw IllegalArgumentException("Invalid value for Type: $value")
        }
    }

    enum class Action(val value: String) {
        BUY("Aplicação"),
        SELL("Resgate"),
        ;

        companion object {
            fun fromValue(value: String) = entries.firstOrNull { it.value.equals(value, ignoreCase = true) }
                ?: throw IllegalArgumentException("Invalid value for Action: $value")
        }
    }

    fun toBondCreation(): BondCreation {
        return FloatingRateBondCreation(
            name = description,
            value = percentageRegex.find(description)!!.groupValues[1].toDouble(),
            indexId = IndexId.CDI,
        )
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

    private val percentageRegex = Regex("""(\d+(?:\.\d+)?)%""")
}
