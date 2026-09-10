package dev.agner.portfolio.usecase.tax.capitalgains

import dev.agner.portfolio.usecase.corporateaction.repository.ICorporateActionRepository
import dev.agner.portfolio.usecase.listedasset.model.AssetKind
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

class CapitalGainsServiceTest : StringSpec({
    val listedAssetRepository = mockk<IListedAssetRepository>()
    val tradeRepository = mockk<ITradeRepository>()
    val corporateActionRepository = mockk<ICorporateActionRepository>()

    val service = CapitalGainsService(
        listedAssetRepository,
        tradeRepository,
        corporateActionRepository,
        AveragePriceCalculator(),
        CapitalGainsCalculator(),
    )

    "should tag each asset's realized sales as stock or FII before handing them to the calculator" {
        val stock = ListedAsset(1, "PETR4", AssetKind.STOCK, "Petrobras", "PETROBRAS")
        val fii = ListedAsset(2, "MXRF11", AssetKind.FII, "Maxi Renda", "MXRF")

        coEvery { listedAssetRepository.fetchAll() } returns listOf(stock, fii)
        coEvery { tradeRepository.fetchByAssetId(1) } returns listOf(
            Trade.Buy(1, 1, LocalDate(2026, 8, 1), BigDecimal("100"), BigDecimal("10.00")),
            Trade.Sell(2, 1, LocalDate(2026, 8, 15), BigDecimal("100"), BigDecimal("12.00")),
        )
        coEvery { tradeRepository.fetchByAssetId(2) } returns listOf(
            Trade.Buy(3, 2, LocalDate(2026, 8, 1), BigDecimal("100"), BigDecimal("10.00")),
            Trade.Sell(4, 2, LocalDate(2026, 8, 15), BigDecimal("100"), BigDecimal("11.00")),
        )
        coEvery { corporateActionRepository.fetchByAssetId(any()) } returns emptyList()

        val result = service.monthlyReport()

        result.size shouldBe 2
        result.first { !it.isFii }.grossGain shouldBe BigDecimal("200.00")
        result.first { it.isFii }.grossGain shouldBe BigDecimal("100.00")
        result.first { it.isFii }.exempt shouldBe false
    }
})
