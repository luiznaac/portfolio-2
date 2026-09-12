package dev.agner.portfolio.usecase.income

import dev.agner.portfolio.usecase.corporateaction.repository.ICorporateActionRepository
import dev.agner.portfolio.usecase.income.model.ReceivedIncome
import dev.agner.portfolio.usecase.listedasset.gateway.IDividendGateway
import dev.agner.portfolio.usecase.listedasset.model.AssetKind
import dev.agner.portfolio.usecase.listedasset.model.DividendDeclaration
import dev.agner.portfolio.usecase.listedasset.model.DividendType.DIVIDENDO
import dev.agner.portfolio.usecase.listedasset.model.DividendType.JCP
import dev.agner.portfolio.usecase.listedasset.model.DividendType.RENDIMENTO
import dev.agner.portfolio.usecase.listedasset.model.ListedAsset
import dev.agner.portfolio.usecase.listedasset.repository.IListedAssetRepository
import dev.agner.portfolio.usecase.trade.AveragePriceCalculator
import dev.agner.portfolio.usecase.trade.model.Trade
import dev.agner.portfolio.usecase.trade.repository.ITradeRepository
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.datetime.LocalDate
import java.math.BigDecimal

class IncomeServiceTest : StringSpec({
    val listedAssetRepository = mockk<IListedAssetRepository>()
    val tradeRepository = mockk<ITradeRepository>()
    val corporateActionRepository = mockk<ICorporateActionRepository>()
    val dividendGateway = mockk<IDividendGateway>()

    val service = IncomeService(
        listedAssetRepository,
        tradeRepository,
        corporateActionRepository,
        dividendGateway,
        AveragePriceCalculator(),
    )

    val petr4 = ListedAsset(1, "PETR4", AssetKind.STOCK, "Petrobras", "PETROBRAS")

    "should size the event by the quantity held on the ex-date, not the current quantity" {
        coEvery { listedAssetRepository.fetchById(1) } returns petr4
        coEvery { tradeRepository.fetchByAssetId(1) } returns listOf(
            Trade(1, 1, LocalDate(2026, 1, 1), BigDecimal("100"), BigDecimal("10.00")),
            // bought after the ex-date — shouldn't count toward this dividend
            Trade(2, 1, LocalDate(2026, 9, 1), BigDecimal("50"), BigDecimal("30.00")),
        )
        coEvery { corporateActionRepository.fetchByAssetId(1) } returns emptyList()
        coEvery { dividendGateway.getDividends(petr4) } returns listOf(
            DividendDeclaration(DIVIDENDO, BigDecimal("2.00"), LocalDate(2026, 6, 1), LocalDate(2026, 6, 15)),
        )

        val events = service.eventsForAsset(1)

        events shouldBe listOf(
            dev.agner.portfolio.usecase.income.model.IncomeEvent(
                listedAssetId = 1,
                ticker = "PETR4",
                type = DIVIDENDO,
                exDate = LocalDate(2026, 6, 1),
                paymentDate = LocalDate(2026, 6, 15),
                quantityHeld = BigDecimal("100"),
                grossAmount = BigDecimal("200.00"),
                retainedTax = BigDecimal("0.00"),
                netAmount = BigDecimal("200.00"),
            ),
        )
    }

    "should withhold 15% for JCP but nothing for DIVIDENDO" {
        coEvery { listedAssetRepository.fetchById(1) } returns petr4
        coEvery { tradeRepository.fetchByAssetId(1) } returns listOf(
            Trade(1, 1, LocalDate(2026, 1, 1), BigDecimal("100"), BigDecimal("10.00")),
        )
        coEvery { corporateActionRepository.fetchByAssetId(1) } returns emptyList()
        coEvery { dividendGateway.getDividends(petr4) } returns listOf(
            DividendDeclaration(JCP, BigDecimal("1.00"), LocalDate(2026, 6, 1), null),
        )

        val events = service.eventsForAsset(1)

        events[0].grossAmount shouldBe BigDecimal("100.00")
        events[0].retainedTax shouldBe BigDecimal("15.00")
        events[0].netAmount shouldBe BigDecimal("85.00")
    }

    "should skip a declaration for a date with no position held" {
        coEvery { listedAssetRepository.fetchById(1) } returns petr4
        coEvery { tradeRepository.fetchByAssetId(1) } returns emptyList()
        coEvery { corporateActionRepository.fetchByAssetId(1) } returns emptyList()
        coEvery { dividendGateway.getDividends(petr4) } returns listOf(
            DividendDeclaration(DIVIDENDO, BigDecimal("2.00"), LocalDate(2026, 6, 1), null),
        )

        service.eventsForAsset(1) shouldBe emptyList()
    }

    "should reconcile previsto against recebido by ticker, month and type" {
        coEvery { listedAssetRepository.fetchAll() } returns listOf(petr4)
        coEvery { tradeRepository.fetchByAssetId(1) } returns listOf(
            Trade(1, 1, LocalDate(2026, 1, 1), BigDecimal("100"), BigDecimal("10.00")),
        )
        coEvery { corporateActionRepository.fetchByAssetId(1) } returns emptyList()
        coEvery { dividendGateway.getDividends(petr4) } returns listOf(
            DividendDeclaration(DIVIDENDO, BigDecimal("2.00"), LocalDate(2026, 6, 1), null),
        )

        val received = listOf(
            ReceivedIncome(LocalDate(2026, 6, 15), "PETR4", DIVIDENDO, BigDecimal("199.50")),
        )

        val result = service.reconcile(received)

        result shouldBe listOf(
            dev.agner.portfolio.usecase.income.model.IncomeReconciliation(
                ticker = "PETR4",
                month = LocalDate(2026, 6, 1),
                type = DIVIDENDO,
                previsto = BigDecimal("200.00"),
                recebido = BigDecimal("199.50"),
            ),
        )
        result[0].matches shouldBe false
    }

    "should reconcile on the payment month when it lags the ex-date" {
        coEvery { listedAssetRepository.fetchAll() } returns listOf(petr4)
        coEvery { tradeRepository.fetchByAssetId(1) } returns listOf(
            Trade(1, 1, LocalDate(2026, 1, 1), BigDecimal("100"), BigDecimal("10.00")),
        )
        coEvery { corporateActionRepository.fetchByAssetId(1) } returns emptyList()
        coEvery { dividendGateway.getDividends(petr4) } returns listOf(
            // JCP declared with a June ex-date but paid in July — the common lag, not an edge case.
            DividendDeclaration(JCP, BigDecimal("2.00"), LocalDate(2026, 6, 1), LocalDate(2026, 7, 15)),
        )

        val received = listOf(
            ReceivedIncome(LocalDate(2026, 7, 15), "PETR4", JCP, BigDecimal("170.00")),
        )

        val result = service.reconcile(received)

        result shouldBe listOf(
            dev.agner.portfolio.usecase.income.model.IncomeReconciliation(
                ticker = "PETR4",
                month = LocalDate(2026, 7, 1),
                type = JCP,
                previsto = BigDecimal("170.00"),
                recebido = BigDecimal("170.00"),
            ),
        )
        result[0].month shouldBe LocalDate(2026, 7, 1)
        result[0].matches shouldBe true
    }

    "should fall back to the ex-date month when the declaration has no payment date" {
        coEvery { listedAssetRepository.fetchAll() } returns listOf(petr4)
        coEvery { tradeRepository.fetchByAssetId(1) } returns listOf(
            Trade(1, 1, LocalDate(2026, 1, 1), BigDecimal("100"), BigDecimal("10.00")),
        )
        coEvery { corporateActionRepository.fetchByAssetId(1) } returns emptyList()
        coEvery { dividendGateway.getDividends(petr4) } returns listOf(
            DividendDeclaration(RENDIMENTO, BigDecimal("1.00"), LocalDate(2026, 6, 1), null),
        )

        val received = listOf(
            ReceivedIncome(LocalDate(2026, 6, 20), "PETR4", RENDIMENTO, BigDecimal("100.00")),
        )

        val result = service.reconcile(received)

        result shouldBe listOf(
            dev.agner.portfolio.usecase.income.model.IncomeReconciliation(
                ticker = "PETR4",
                month = LocalDate(2026, 6, 1),
                type = RENDIMENTO,
                previsto = BigDecimal("100.00"),
                recebido = BigDecimal("100.00"),
            ),
        )
    }
})
