package dev.agner.portfolio.usecase.tax

import dev.agner.portfolio.usecase.bd
import dev.agner.portfolio.usecase.buy
import dev.agner.portfolio.usecase.catalog.CatalogService
import dev.agner.portfolio.usecase.catalog.model.AssetClass
import dev.agner.portfolio.usecase.catalog.model.AssetKind
import dev.agner.portfolio.usecase.catalog.model.CatalogEntry
import dev.agner.portfolio.usecase.corporateAction
import dev.agner.portfolio.usecase.corporateaction.model.CorporateActionKind
import dev.agner.portfolio.usecase.corporateaction.repository.ICorporateActionRepository
import dev.agner.portfolio.usecase.listedasset.model.Trade
import dev.agner.portfolio.usecase.listedasset.repository.ITradeRepository
import dev.agner.portfolio.usecase.plain
import dev.agner.portfolio.usecase.portfolio.MonthService
import dev.agner.portfolio.usecase.portfolio.model.CurrentMonth
import dev.agner.portfolio.usecase.portfolio.model.Month
import dev.agner.portfolio.usecase.portfolio.model.MonthStatus
import dev.agner.portfolio.usecase.portfolio.model.Targets
import dev.agner.portfolio.usecase.sell
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth

class StockTaxServiceTest : StringSpec({

    "returns null when no month is open" {
        val service = StockTaxService(
            months = months(current = null),
            trades = mockk(),
            corporateActions = mockk(),
            catalog = catalog(),
        )

        service.fetchCurrent().shouldBeNull()
    }

    "meters the open month's stock sales" {
        val service = StockTaxService(
            months = months(),
            trades = trades(
                buy(ticker = "PETR4", quantity = "2001", unitPrice = "8"),
                sell(id = 2, ticker = "PETR4", quantity = "2001", unitPrice = "10"),
            ),
            corporateActions = actions(),
            catalog = catalog("PETR4" to AssetKind.STOCK),
        )

        val report = requireNotNull(service.fetchCurrent())

        report.period shouldBe YearMonth.parse("2026-10")
        report.meter.grossSales.plain() shouldBe "20010"
        report.meter.exceeded shouldBe true
        report.meter.tax.plain() shouldBe "600.3"
        report.averagePrices.shouldBeEmpty()
    }

    "excludes non-stock and uncatalogued tickers from the meter" {
        val service = StockTaxService(
            months = months(),
            trades = trades(
                buy(ticker = "HGLG11", quantity = "100", unitPrice = "150"),
                sell(id = 2, ticker = "HGLG11", quantity = "100", unitPrice = "160"),
                buy(id = 3, ticker = "MYST3", quantity = "100", unitPrice = "10"),
                sell(id = 4, ticker = "MYST3", quantity = "100", unitPrice = "20"),
            ),
            corporateActions = actions(),
            catalog = catalog("HGLG11" to AssetKind.FII),
        )

        val report = requireNotNull(service.fetchCurrent())

        report.meter.grossSales.plain() shouldBe "0"
        report.meter.realizedGain.plain() shouldBe "0"
    }

    "replays the applied corporate actions into the average price" {
        val service = StockTaxService(
            months = months(),
            trades = trades(
                buy(ticker = "PETR4", date = "2026-10-01", quantity = "100", unitPrice = "10"),
                sell(id = 2, ticker = "PETR4", date = "2026-10-15", quantity = "100", unitPrice = "6"),
            ),
            corporateActions = actions(
                corporateAction(CorporateActionKind.SPLIT, exDate = "2026-10-10", ratio = "2"),
            ),
            catalog = catalog("PETR4" to AssetKind.STOCK),
        )

        val report = requireNotNull(service.fetchCurrent())

        report.meter.realizedGain.plain() shouldBe "100"
        report.averagePrices.single().averagePrice.plain() shouldBe "5"
    }
})

private fun months(current: CurrentMonth? = currentMonth): MonthService = mockk {
    coEvery { fetchCurrent() } returns current
}

private fun trades(vararg ledgers: Trade): ITradeRepository = mockk {
    coEvery { fetchAll() } returns ledgers.toList()
}

private fun actions(vararg actions: dev.agner.portfolio.usecase.corporateaction.model.CorporateAction) =
    mockk<ICorporateActionRepository> {
        coEvery { fetchApplied() } returns actions.toList()
    }

private fun catalog(vararg kinds: Pair<String, AssetKind>): CatalogService = mockk {
    coEvery { fetchAll() } returns kinds.map { (ticker, kind) ->
        CatalogEntry(
            ticker = ticker,
            shortName = ticker,
            bdiType = "??",
            isin = null,
            assetKind = kind,
            assetClass = AssetClass.ACOES,
            firstSeen = LocalDate.parse("2026-01-01"),
            lastSeen = LocalDate.parse("2026-10-01"),
        )
    }
}

private val currentMonth = CurrentMonth(
    month = Month(
        id = 1,
        period = YearMonth.parse("2026-10"),
        capital = bd("100000"),
        floor = bd("10000"),
        status = MonthStatus.OPEN,
    ),
    targets = Targets(classes = emptyList(), strategies = emptyList(), subClasses = emptyList(), tickers = emptyList()),
)
