package dev.agner.portfolio.usecase.bond.model

import dev.agner.portfolio.usecase.fixedincome.model.FixedIncomeRate
import dev.agner.portfolio.usecase.fixedincome.model.FixedIncomeSubClass
import dev.agner.portfolio.usecase.index.model.IndexId
import kotlinx.datetime.LocalDate
import java.math.BigDecimal

sealed class Bond(
    open val id: Int,
    open val name: String,
    open val rate: BigDecimal,
    open val maturityDate: LocalDate,
) {
    // Derived on read, never stored — see FixedIncomeSubClass. Each subtype differs only in the
    // fixed-income rate it carries, which is exactly what the derivation keys on.
    abstract val subClass: FixedIncomeSubClass

    data class FixedRateBond(
        override val id: Int,
        override val name: String,
        override val rate: BigDecimal,
        override val maturityDate: LocalDate,
    ) : Bond(id, name, rate, maturityDate) {
        override val subClass = FixedIncomeRate.Fixed.subClass
    }

    data class FloatingRateBond(
        override val id: Int,
        override val name: String,
        override val rate: BigDecimal,
        override val maturityDate: LocalDate,
        val indexId: IndexId,
    ) : Bond(id, name, rate, maturityDate) {
        override val subClass = FixedIncomeRate.Floating(indexId).subClass
    }
}
