package dev.agner.portfolio.usecase.upload.model

import kotlinx.datetime.LocalDate

data class KinvoOrder(
    val date: LocalDate,
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
}
