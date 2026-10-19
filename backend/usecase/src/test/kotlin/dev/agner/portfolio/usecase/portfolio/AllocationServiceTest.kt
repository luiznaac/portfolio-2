package dev.agner.portfolio.usecase.portfolio

import dev.agner.portfolio.usecase.bond.BondService
import dev.agner.portfolio.usecase.bond.model.Bond
import dev.agner.portfolio.usecase.bond.position.BondPositionService
import dev.agner.portfolio.usecase.bond.position.model.BondPosition
import dev.agner.portfolio.usecase.catalog.CatalogService
import dev.agner.portfolio.usecase.catalog.model.AssetClass
import dev.agner.portfolio.usecase.catalog.model.AssetKind
import dev.agner.portfolio.usecase.catalog.model.CatalogEntry
import dev.agner.portfolio.usecase.index.model.IndexId
import dev.agner.portfolio.usecase.listedasset.PositionService
import dev.agner.portfolio.usecase.listedasset.model.Position
import dev.agner.portfolio.usecase.plain
import dev.agner.portfolio.usecase.portfolio.model.ClassTarget
import dev.agner.portfolio.usecase.portfolio.model.CurrentMonth
import dev.agner.portfolio.usecase.portfolio.model.Month
import dev.agner.portfolio.usecase.portfolio.model.MonthStatus
import dev.agner.portfolio.usecase.portfolio.model.Targets
import dev.agner.portfolio.usecase.quotes.QuoteService
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import java.math.BigDecimal

// The orchestration seam: which book reads feed the allocation tree and how each value is resolved —
// Position × the latest quote for the listed side, the consolidation engine's principal + yield for
// the RF side.
class AllocationServiceTest : StringSpec({
    val months = mockk<MonthService>(relaxed = true)
    val positions = mockk<PositionService>(relaxed = true)
    val catalog = mockk<CatalogService>(relaxed = true)
    val quotes = mockk<QuoteService>(relaxed = true)
    val bonds = mockk<BondService>(relaxed = true)
    val bondPositions = mockk<BondPositionService>(relaxed = true)
    val service = AllocationService(months, positions, catalog, quotes, bonds, bondPositions)

    beforeTest { clearAllMocks() }

    "returns null when no month is open and reads no book" {
        coEvery { months.fetchCurrent() } returns null

        service.fetchCurrent() shouldBe null

        coVerify(exactly = 0) { positions.fetchAll() }
        coVerify(exactly = 0) { bonds.fetchAll() }
    }

    "composes the listed positions (Position × quote) and the RF consolidated values" {
        coEvery { months.fetchCurrent() } returns currentMonth
        coEvery { positions.fetchAll() } returns listOf(Position("VALE3", BigDecimal("100"), BigDecimal("10")))
        coEvery { catalog.fetchBy("VALE3") } returns catalogEntry
        coEvery { quotes.latestClose("VALE3") } returns BigDecimal("10")
        coEvery { bonds.fetchAll() } returns setOf(bond)
        coEvery { bondPositions.getByBondId(1) } returns listOf(bondPosition)

        val allocation = service.fetchCurrent()!!

        allocation.classes.single { it.assetClass == AssetClass.ACOES }.actual.plain() shouldBe "1000"
        allocation.classes.single { it.assetClass == AssetClass.RENDA_FIXA }.actual.plain() shouldBe "2000"
        allocation.actual.plain() shouldBe "3000"
    }

    "counts every RF product once, whichever way the account holds it" {
        coEvery { months.fetchCurrent() } returns currentMonth
        coEvery { positions.fetchAll() } returns emptyList()
        coEvery { bonds.fetchAll() } returns setOf(bond, secondBond)
        coEvery { bondPositions.getByBondId(1) } returns listOf(bondPosition)
        coEvery { bondPositions.getByBondId(2) } returns listOf(secondBondPosition)

        val allocation = service.fetchCurrent()!!

        allocation.classes.single { it.assetClass == AssetClass.RENDA_FIXA }.actual.plain() shouldBe "5000"
        allocation.actual.plain() shouldBe "5000"
    }

    "values a listed position at the current quote" {
        coEvery { months.fetchCurrent() } returns currentMonth
        coEvery { positions.fetchAll() } returns listOf(Position("VALE3", BigDecimal("100"), BigDecimal("10")))
        coEvery { catalog.fetchBy("VALE3") } returns catalogEntry
        coEvery { quotes.latestClose("VALE3") } returns BigDecimal("12")
        coEvery { bonds.fetchAll() } returns emptySet()

        val allocation = service.fetchCurrent()!!

        allocation.classes.single { it.assetClass == AssetClass.ACOES }.actual.plain() shouldBe "1200"
    }

    "values a position with no stored quote at zero" {
        coEvery { months.fetchCurrent() } returns currentMonth
        coEvery { positions.fetchAll() } returns listOf(Position("VALE3", BigDecimal("100"), BigDecimal("10")))
        coEvery { catalog.fetchBy("VALE3") } returns catalogEntry
        coEvery { quotes.latestClose("VALE3") } returns null
        coEvery { bonds.fetchAll() } returns emptySet()

        val allocation = service.fetchCurrent()!!

        allocation.classes.single { it.assetClass == AssetClass.ACOES }.actual.plain() shouldBe "0"
    }
})

private val currentMonth = CurrentMonth(
    month = Month(
        id = 7,
        period = YearMonth.parse("2026-09"),
        capital = BigDecimal("100000.00"),
        floor = BigDecimal("20000.00"),
        status = MonthStatus.OPEN,
    ),
    targets = Targets(
        classes = listOf(
            ClassTarget(AssetClass.ACOES, BigDecimal("50")),
            ClassTarget(AssetClass.RENDA_FIXA, BigDecimal("50")),
        ),
        strategies = emptyList(),
        subClasses = emptyList(),
        tickers = emptyList(),
    ),
)

private val catalogEntry = CatalogEntry(
    ticker = "VALE3",
    shortName = "VALE",
    bdiType = "02",
    isin = null,
    assetKind = AssetKind.STOCK,
    assetClass = AssetClass.ACOES,
    firstSeen = LocalDate.parse("2026-01-01"),
    lastSeen = LocalDate.parse("2026-09-10"),
)

private val bond = Bond.FloatingRateBond(
    id = 1,
    name = "Tesouro Selic 2031",
    rate = BigDecimal("10.00"),
    maturityDate = LocalDate.parse("2031-01-01"),
    indexId = IndexId.CDI,
)

private val secondBond = Bond.FloatingRateBond(
    id = 2,
    name = "Conta corrente [2026-09]",
    rate = BigDecimal("10.00"),
    maturityDate = LocalDate.parse("2031-01-01"),
    indexId = IndexId.CDI,
)

private val bondPosition = BondPosition(
    date = LocalDate.parse("2026-09-10"),
    principal = BigDecimal("1500.00"),
    yield = BigDecimal("500.00"),
    taxes = BigDecimal("50.00"),
)

private val secondBondPosition = BondPosition(
    date = LocalDate.parse("2026-09-10"),
    principal = BigDecimal("2500.00"),
    yield = BigDecimal("500.00"),
    taxes = BigDecimal("50.00"),
)
