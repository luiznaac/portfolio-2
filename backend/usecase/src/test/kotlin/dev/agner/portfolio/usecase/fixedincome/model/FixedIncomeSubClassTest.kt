package dev.agner.portfolio.usecase.fixedincome.model

import dev.agner.portfolio.usecase.bond.model.Bond
import dev.agner.portfolio.usecase.checkingaccount.model.CheckingAccount
import dev.agner.portfolio.usecase.index.model.IndexId
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import java.math.BigDecimal

class FixedIncomeSubClassTest : StringSpec({

    "a fixed rate derives FIXED_RATE" {
        FixedIncomeRate.Fixed.subClass shouldBe FixedIncomeSubClass.FIXED_RATE
    }

    "a floating rate derives its sub-class from the index" {
        FixedIncomeRate.Floating(IndexId.IPCA).subClass shouldBe FixedIncomeSubClass.INFLATION_LINKED
        FixedIncomeRate.Floating(IndexId.CDI).subClass shouldBe FixedIncomeSubClass.FLOATING_RATE
        FixedIncomeRate.Floating(IndexId.SELIC).subClass shouldBe FixedIncomeSubClass.FLOATING_RATE
    }

    "a fixed rate bond carries FIXED_RATE" {
        fixedBond().subClass shouldBe FixedIncomeSubClass.FIXED_RATE
    }

    "a floating rate bond derives its sub-class from the index" {
        floatingBond(IndexId.IPCA).subClass shouldBe FixedIncomeSubClass.INFLATION_LINKED
        floatingBond(IndexId.CDI).subClass shouldBe FixedIncomeSubClass.FLOATING_RATE
        floatingBond(IndexId.SELIC).subClass shouldBe FixedIncomeSubClass.FLOATING_RATE
    }

    "a checking account is a floating rate product tied to its index" {
        checkingAccount(IndexId.CDI).subClass shouldBe FixedIncomeSubClass.FLOATING_RATE
        checkingAccount(IndexId.IPCA).subClass shouldBe FixedIncomeSubClass.INFLATION_LINKED
    }
})

private fun fixedBond() = Bond.FixedRateBond(
    id = 1,
    name = "Tesouro Prefixado 2031",
    rate = BigDecimal("12.50"),
    maturityDate = LocalDate.parse("2031-01-01"),
)

private fun floatingBond(indexId: IndexId) = Bond.FloatingRateBond(
    id = 2,
    name = "Tesouro IPCA+ 2035",
    rate = BigDecimal("6.00"),
    maturityDate = LocalDate.parse("2035-05-15"),
    indexId = indexId,
)

private fun checkingAccount(indexId: IndexId) = CheckingAccount(
    id = 3,
    name = "CDB 110% CDI",
    rate = BigDecimal("110.00"),
    indexId = indexId,
    maturityDuration = DatePeriod.parse("P3Y"),
)
