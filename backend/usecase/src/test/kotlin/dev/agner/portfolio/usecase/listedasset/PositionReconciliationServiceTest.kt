package dev.agner.portfolio.usecase.listedasset

import dev.agner.portfolio.usecase.listedasset.model.CustodyPosition
import dev.agner.portfolio.usecase.listedasset.model.Position
import dev.agner.portfolio.usecase.listedasset.repository.IPositionRepository
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.mockk
import java.math.BigDecimal

class PositionReconciliationServiceTest : StringSpec({
    val positions = mockk<IPositionRepository>(relaxed = true)
    val service = PositionReconciliationService(positions)

    beforeTest { clearAllMocks() }

    "a custody line that matches the ledger projection is not drift" {
        coEvery { positions.fetchAll() } returns listOf(Position("PETR4", BigDecimal("100"), BigDecimal("10")))

        val result = service.reconcile(listOf(CustodyPosition("PETR4", BigDecimal("100"))))

        result.single().hasDrift shouldBe false
    }

    "a custody quantity that differs from the ledger is drift" {
        coEvery { positions.fetchAll() } returns listOf(Position("PETR4", BigDecimal("100"), BigDecimal("10")))

        val result = service.reconcile(listOf(CustodyPosition("PETR4", BigDecimal("80"))))

        result.single().hasDrift shouldBe true
        result.single().custodyQuantity.compareTo(BigDecimal("80")) shouldBe 0
        result.single().ledgerQuantity.compareTo(BigDecimal("100")) shouldBe 0
    }

    "a ledger ticker missing from custody is drift against zero" {
        coEvery { positions.fetchAll() } returns listOf(Position("PETR4", BigDecimal("100"), BigDecimal("10")))

        val result = service.reconcile(emptyList())

        result.single().ticker shouldBe "PETR4"
        result.single().custodyQuantity.compareTo(BigDecimal.ZERO) shouldBe 0
        result.single().hasDrift shouldBe true
    }

    "a custody ticker missing from the ledger is drift against zero" {
        coEvery { positions.fetchAll() } returns emptyList()

        val result = service.reconcile(listOf(CustodyPosition("PETR4", BigDecimal("100"))))

        result.single().ticker shouldBe "PETR4"
        result.single().ledgerQuantity.compareTo(BigDecimal.ZERO) shouldBe 0
        result.single().hasDrift shouldBe true
    }

    "custody across institutions is summed per ticker and the ticker normalized" {
        coEvery { positions.fetchAll() } returns listOf(Position("PETR4", BigDecimal("100"), BigDecimal("10")))

        val result = service.reconcile(
            listOf(
                CustodyPosition("PETR4", BigDecimal("60")),
                CustodyPosition("petr4f", BigDecimal("40")),
            ),
        )

        result.single().custodyQuantity.compareTo(BigDecimal("100")) shouldBe 0
        result.single().hasDrift shouldBe false
    }

    "the result is ordered by ticker over the union of both sides" {
        coEvery { positions.fetchAll() } returns
            listOf(Position("VALE3", BigDecimal("50"), BigDecimal("20")))

        val result = service.reconcile(listOf(CustodyPosition("PETR4", BigDecimal("100"))))

        result.map { it.ticker } shouldBe listOf("PETR4", "VALE3")
    }
})
