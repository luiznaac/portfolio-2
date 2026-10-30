package dev.agner.portfolio.usecase.portfolio

import dev.agner.portfolio.usecase.catalog.model.AssetClass
import dev.agner.portfolio.usecase.listedasset.model.TradeExecutionRange
import dev.agner.portfolio.usecase.listedasset.repository.ITradeRepository
import dev.agner.portfolio.usecase.portfolio.model.ClassTarget
import dev.agner.portfolio.usecase.portfolio.model.CurrentMonth
import dev.agner.portfolio.usecase.portfolio.model.Month
import dev.agner.portfolio.usecase.portfolio.model.MonthCreation
import dev.agner.portfolio.usecase.portfolio.model.MonthPeriodRange
import dev.agner.portfolio.usecase.portfolio.model.MonthStatus
import dev.agner.portfolio.usecase.portfolio.model.MonthUpdate
import dev.agner.portfolio.usecase.portfolio.model.StrategyTarget
import dev.agner.portfolio.usecase.portfolio.model.Targets
import dev.agner.portfolio.usecase.portfolio.model.TickerTarget
import dev.agner.portfolio.usecase.portfolio.repository.IMonthRepository
import dev.agner.portfolio.usecase.portfolio.repository.ITargetRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class MonthServiceTest : StringSpec({
    val monthRepository = mockk<IMonthRepository>(relaxed = true)
    val targetRepository = mockk<ITargetRepository>(relaxed = true)
    val trades = mockk<ITradeRepository>(relaxed = true)
    val service = MonthService(monthRepository, targetRepository, trades, fixedClock)

    beforeTest {
        clearAllMocks()
        coEvery { monthRepository.fetchAll() } returns emptyList()
        coEvery { trades.fetchExecutionDateRange() } returns null
    }

    "fetchCurrent returns null when no month is open" {
        coEvery { monthRepository.fetchOpen() } returns null

        service.fetchCurrent() shouldBe null

        coVerify(exactly = 0) { targetRepository.fetchByMonth(any()) }
    }

    "fetchCurrent pairs the open month with its targets" {
        coEvery { monthRepository.fetchOpen() } returns openMonth
        coEvery { targetRepository.fetchByMonth(7) } returns targets

        service.fetchCurrent() shouldBe CurrentMonth(openMonth, targets)
    }

    "open delegates to the repository and returns the new month's view" {
        coEvery { monthRepository.fetchByPeriod(any()) } returns null
        coEvery { monthRepository.fetchAll() } returns listOf(openMonth)
        coEvery { monthRepository.open(creation) } returns openMonth
        coEvery { targetRepository.fetchByMonth(7) } returns emptyTargets

        service.open(creation) shouldBe CurrentMonth(openMonth, emptyTargets)

        coVerify(exactly = 1) { monthRepository.open(creation) }
    }

    "open refuses a period that already has a month" {
        coEvery { monthRepository.fetchByPeriod(creation.period) } returns openMonth

        val error = runCatching { service.open(creation) }.exceptionOrNull()

        (error is MonthPeriodTakenException) shouldBe true
        coVerify(exactly = 0) { monthRepository.open(any()) }
    }

    "period range is the current month when there are no trades and no months" {
        coEvery { monthRepository.fetchAll() } returns emptyList()
        coEvery { trades.fetchExecutionDateRange() } returns null

        service.fetchPeriodRange() shouldBe MonthPeriodRange(
            min = YearMonth.parse("2026-10"),
            max = YearMonth.parse("2026-10"),
            default = YearMonth.parse("2026-10"),
        )
    }

    "period range takes the earlier existing month and the latest trade as default" {
        coEvery { monthRepository.fetchAll() } returns listOf(monthPeriod("2020-01")) + closedMonths
        coEvery { trades.fetchExecutionDateRange() } returns
            TradeExecutionRange(LocalDate.parse("2020-03-10"), LocalDate.parse("2020-12-15"))

        service.fetchPeriodRange() shouldBe MonthPeriodRange(
            min = YearMonth.parse("2020-01"),
            max = YearMonth.parse("2026-10"),
            default = YearMonth.parse("2020-12"),
        )
    }

    "period range clamps a future trade default into the window" {
        coEvery { monthRepository.fetchAll() } returns emptyList()
        coEvery { trades.fetchExecutionDateRange() } returns
            TradeExecutionRange(LocalDate.parse("2030-01-10"), LocalDate.parse("2030-05-10"))

        service.fetchPeriodRange() shouldBe MonthPeriodRange(
            min = YearMonth.parse("2026-10"),
            max = YearMonth.parse("2026-10"),
            default = YearMonth.parse("2026-10"),
        )
    }

    "open refuses a period outside the window" {
        coEvery { monthRepository.fetchByPeriod(any()) } returns null
        coEvery { monthRepository.fetchAll() } returns emptyList()
        coEvery { trades.fetchExecutionDateRange() } returns
            TradeExecutionRange(LocalDate.parse("2020-03-10"), LocalDate.parse("2020-12-15"))

        val error = shouldThrow<MonthPeriodOutsideWindowException> {
            service.open(creation.copy(period = YearMonth.parse("2020-02")))
        }

        error.period shouldBe YearMonth.parse("2020-02")
        coVerify(exactly = 0) { monthRepository.open(any()) }
    }

    "requireClosable refuses a month before the current calendar month" {
        shouldThrow<MonthCloseNotCurrentException> {
            service.requireClosable(openMonth.copy(period = YearMonth.parse("2026-09")))
        }
    }

    "requireClosable accepts the current calendar month" {
        service.requireClosable(openMonth.copy(period = YearMonth.parse("2026-10")))
    }

    "updateCurrent returns null when no month is open" {
        coEvery { monthRepository.updateCurrent(update) } returns null

        service.updateCurrent(update) shouldBe null

        coVerify(exactly = 0) { targetRepository.fetchByMonth(any()) }
    }

    "updateCurrent returns the refreshed month when one is open" {
        coEvery { monthRepository.updateCurrent(update) } returns openMonth
        coEvery { targetRepository.fetchByMonth(7) } returns targets

        service.updateCurrent(update) shouldBe CurrentMonth(openMonth, targets)
    }

    "fetchCurrentTargets returns null when no month is open" {
        coEvery { monthRepository.fetchOpen() } returns null

        service.fetchCurrentTargets() shouldBe null
    }

    "fetchCurrentTargets reads the open month's chain" {
        coEvery { monthRepository.fetchOpen() } returns openMonth
        coEvery { targetRepository.fetchByMonth(7) } returns targets

        service.fetchCurrentTargets() shouldBe targets
    }

    "close delegates the lock and returns the closed month" {
        coEvery { monthRepository.close(7) } returns openMonth.copy(status = MonthStatus.CLOSED)

        service.close(7)!!.status shouldBe MonthStatus.CLOSED

        coVerify(exactly = 1) { monthRepository.close(7) }
    }

    "close returns null when the month is not open" {
        coEvery { monthRepository.close(7) } returns null

        service.close(7) shouldBe null
    }

    "fetchClosed reads the closed months from the repository" {
        val closed = listOf(openMonth.copy(status = MonthStatus.CLOSED))
        coEvery { monthRepository.fetchClosed() } returns closed

        service.fetchClosed() shouldBe closed
    }

    "replaceCurrentTargets returns null when no month is open" {
        coEvery { monthRepository.fetchOpen() } returns null

        service.replaceCurrentTargets(targets) shouldBe null

        coVerify(exactly = 0) { targetRepository.replaceAll(any(), any()) }
    }

    "replaceCurrentTargets replaces the open month's chain and returns the view" {
        coEvery { monthRepository.fetchOpen() } returns openMonth
        coEvery { targetRepository.fetchByMonth(7) } returns targets

        service.replaceCurrentTargets(targets) shouldBe CurrentMonth(openMonth, targets)

        coVerify(exactly = 1) { targetRepository.replaceAll(7, targets) }
    }

    "replaceCurrentTargets rejects strategy shares above 100 in a class" {
        coEvery { monthRepository.fetchOpen() } returns openMonth
        val overplanned = targets.copy(
            strategies = listOf(
                StrategyTarget(AssetClass.ACOES, "Top Dividendos", BigDecimal("60.00")),
                StrategyTarget(AssetClass.ACOES, "Small Caps", BigDecimal("50.00")),
            ),
        )

        val error = runCatching { service.replaceCurrentTargets(overplanned) }.exceptionOrNull()

        (error is StrategyTargetsExceedClassException) shouldBe true
        coVerify(exactly = 0) { targetRepository.replaceAll(any(), any()) }
    }

    "replaceCurrentTargets accepts strategy shares that sum to exactly 100" {
        coEvery { monthRepository.fetchOpen() } returns openMonth
        coEvery { targetRepository.fetchByMonth(7) } returns targets
        val full = targets.copy(
            strategies = listOf(
                StrategyTarget(AssetClass.ACOES, "Top Dividendos", BigDecimal("60.00")),
                StrategyTarget(AssetClass.ACOES, "Small Caps", BigDecimal("40.00")),
            ),
        )

        service.replaceCurrentTargets(full) shouldBe CurrentMonth(openMonth, targets)

        coVerify(exactly = 1) { targetRepository.replaceAll(7, full) }
    }

    "replaceCurrentTargets normalizes each ticker to its canonical key" {
        coEvery { monthRepository.fetchOpen() } returns openMonth
        coEvery { targetRepository.fetchByMonth(7) } returns targets
        val raw = Targets(
            classes = emptyList(),
            strategies = emptyList(),
            subClasses = emptyList(),
            tickers = listOf(
                TickerTarget(strategy = "Top Dividendos", ticker = " vale3F ", percentage = BigDecimal("12.00")),
            ),
        )

        service.replaceCurrentTargets(raw)

        coVerify(exactly = 1) {
            targetRepository.replaceAll(7, match { it.tickers.single().ticker == "VALE3" })
        }
    }
})

private val creation = MonthCreation(
    period = YearMonth.parse("2026-09"),
    capital = BigDecimal("245000.00"),
    floor = BigDecimal("20000.00"),
)

private val update = MonthUpdate(capital = BigDecimal("250000.00"), floor = BigDecimal("25000.00"))

private val openMonth = Month(
    id = 7,
    period = YearMonth.parse("2026-09"),
    capital = BigDecimal("245000.00"),
    floor = BigDecimal("20000.00"),
    status = MonthStatus.OPEN,
)

private val targets = Targets(
    classes = listOf(ClassTarget(AssetClass.ACOES, BigDecimal("50.00"))),
    strategies = emptyList(),
    subClasses = emptyList(),
    tickers = listOf(
        TickerTarget(strategy = "Top Dividendos", ticker = "VALE3", percentage = BigDecimal("12.00")),
    ),
)

private val emptyTargets =
    Targets(classes = emptyList(), strategies = emptyList(), subClasses = emptyList(), tickers = emptyList())

private val fixedClock = Clock.fixed(Instant.parse("2026-10-31T12:00:00Z"), ZoneOffset.UTC)

private fun monthPeriod(period: String) = openMonth.copy(id = 11, period = YearMonth.parse(period))

private val closedMonths = listOf(
    Month(
        id = 12,
        period = YearMonth.parse("2026-09"),
        capital = BigDecimal("240000.00"),
        floor = BigDecimal("20000.00"),
        status = MonthStatus.CLOSED,
    ),
)
