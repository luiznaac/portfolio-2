package dev.agner.portfolio.usecase.bond.model

import kotlinx.datetime.LocalDate
import java.math.BigDecimal

enum class BondOrderType {
    BUY,
    SELL,
    FULL_REDEMPTION,
    MATURITY,
}

sealed class BondOrder(
    open val id: Int,
    open val date: LocalDate,
) {

    sealed class Contribution(
        override val id: Int,
        override val date: LocalDate,
        open val bond: Bond,
        open val amount: BigDecimal,
    ) : BondOrder(id, date) {

        data class Buy(
            override val id: Int,
            override val date: LocalDate,
            override val amount: BigDecimal,
            override val bond: Bond,
        ) : Contribution(id, date, bond, amount)
    }

    sealed class Redemption(
        override val id: Int,
        override val date: LocalDate,
        open val amount: BigDecimal,
    ) : BondOrder(id, date) {

        data class Sell(
            override val id: Int,
            override val date: LocalDate,
            override val amount: BigDecimal,
            val bond: Bond,
        ) : Redemption(id, date, amount)
    }

    sealed class DownToZero(
        override val id: Int,
        override val date: LocalDate,
    ) : BondOrder(id, date) {

        data class FullRedemption(
            override val id: Int,
            override val date: LocalDate,
            val bond: Bond,
        ) : DownToZero(id, date)

        data class Maturity(
            override val id: Int,
            override val date: LocalDate,
            val bond: Bond,
        ) : DownToZero(id, date)
    }
}

data class BondOrderCreation(
    val bondId: Int?,
    val type: BondOrderType,
    val date: LocalDate,
    val amount: BigDecimal = BigDecimal("0.00"),
    val checkingAccountId: Int? = null,
)
