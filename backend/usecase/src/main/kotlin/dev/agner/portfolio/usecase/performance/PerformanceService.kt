package dev.agner.portfolio.usecase.performance

import dev.agner.portfolio.usecase.catalog.repository.ICatalogRepository
import dev.agner.portfolio.usecase.commons.defaultScale
import dev.agner.portfolio.usecase.commons.logger
import dev.agner.portfolio.usecase.monthlyclose.model.CloseState
import dev.agner.portfolio.usecase.monthlyclose.model.HoldingSnapshot
import dev.agner.portfolio.usecase.monthlyclose.model.Snapshot
import dev.agner.portfolio.usecase.monthlyclose.repository.ISnapshotRepository
import dev.agner.portfolio.usecase.performance.benchmark.BenchmarkBlend
import dev.agner.portfolio.usecase.performance.benchmark.BenchmarkBlendCalculator
import dev.agner.portfolio.usecase.performance.benchmark.BenchmarkCalculator
import dev.agner.portfolio.usecase.performance.benchmark.BenchmarkIndex
import dev.agner.portfolio.usecase.performance.benchmark.BenchmarkObservation
import dev.agner.portfolio.usecase.performance.benchmark.classBenchmark
import dev.agner.portfolio.usecase.performance.gateway.IBenchmarkGateway
import dev.agner.portfolio.usecase.performance.model.ClassPerformance
import dev.agner.portfolio.usecase.performance.model.MonthPerformance
import dev.agner.portfolio.usecase.performance.model.PerformanceDecomposition
import dev.agner.portfolio.usecase.performance.model.PerformanceSeries
import dev.agner.portfolio.usecase.performance.model.PeriodBenchmark
import dev.agner.portfolio.usecase.performance.model.PeriodClass
import dev.agner.portfolio.usecase.performance.model.PeriodPerformance
import dev.agner.portfolio.usecase.performance.model.TickerPerformance
import dev.agner.portfolio.usecase.portfolio.model.Targets
import dev.agner.portfolio.usecase.portfolio.repository.ITargetRepository
import dev.agner.portfolio.usecase.quotes.repository.IQuoteRepository
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode

// Composes the performance reads from the immutable snapshots: a closed month's decomposition and
// its since-inception XIRR, the same math per class and per ticker, and the target-weighted
// benchmark the BCB SGS series price. The snapshots are the stored truth; nothing here re-derives
// the book from the live ledger.
@Service
class PerformanceService(
    private val snapshots: ISnapshotRepository,
    private val catalog: ICatalogRepository,
    private val quotes: IQuoteRepository,
    private val targets: ITargetRepository,
    private val benchmarks: IBenchmarkGateway,
) {

    private val log = logger()

    // Null when the month has no snapshot (it was never closed) — the edge maps that to a 404.
    suspend fun fetchForMonth(monthId: Int): MonthPerformance? {
        val all = orderedSnapshots()
        val index = all.indexOfFirst { it.monthId == monthId }
        if (index < 0) return null

        val current = all[index]
        val previous = all.getOrNull(index - 1)
        val period = current.state.period
        val previousEnd = previous?.state?.period?.endDate()
        val monthEnd = period.endDate()
        val incomeByTicker = current.state.incomeByTicker.associate { it.ticker to it.net }
        val chain = targets.fetchByMonth(monthId)
        val observations = benchmarkObservations(period, period)
        val returns = returnsFor(observations, period)
        val blend = BenchmarkBlendCalculator.compute(chain, returns)

        return MonthPerformance(
            monthId = monthId,
            period = period,
            decomposition = decompose(previous?.state, current.state),
            xirr = xirr(all.take(index + 1)),
            benchmark = blend,
            classes = classPerformances(
                ClassPerformanceContext(
                    previous = previous?.state,
                    current = current.state,
                    incomeByTicker = incomeByTicker,
                    chain = chain,
                    blend = blend,
                ),
            ),
            tickers = tickerPerformances(
                TickerPerformanceContext(
                    previous = previous?.state,
                    current = current.state,
                    incomeByTicker = incomeByTicker,
                    previousEnd = previousEnd,
                    currentEnd = monthEnd,
                    chain = chain,
                    blend = blend,
                ),
            ),
        )
    }

    // Every closed month, oldest first. The benchmark series is fetched once for the whole span and
    // each month is sliced out of it, so the series read does not multiply SGS calls by month count.
    suspend fun fetchSeries(): PerformanceSeries {
        val all = orderedSnapshots()
        if (all.isEmpty()) return PerformanceSeries(emptyList(), null)

        val observations = benchmarkObservations(all.first().state.period, all.last().state.period)

        val months = all.mapIndexed { index, snapshot ->
            val period = snapshot.state.period
            val previous = all.getOrNull(index - 1)
            val chain = targets.fetchByMonth(snapshot.monthId)
            val returns = returnsFor(observations, period)
            PeriodPerformance(
                period = period,
                decomposition = decompose(previous?.state, snapshot.state),
                xirr = xirr(all.take(index + 1)),
                benchmarkReturn = BenchmarkBlendCalculator.compute(chain, returns).returnPercentage,
                benchmarks = returns.map { (benchmark, returnPercentage) ->
                    PeriodBenchmark(benchmark, returnPercentage)
                },
                classes = snapshot.state.classes.map { classTotal ->
                    PeriodClass(
                        assetClass = classTotal.assetClass,
                        valueEnd = classTotal.actual,
                    )
                },
            )
        }

        return PerformanceSeries(months, xirr(all))
    }

    private suspend fun orderedSnapshots() = snapshots.fetchAll().sortedBy { it.state.period }

    private fun decompose(previous: CloseState?, current: CloseState): PerformanceDecomposition =
        PerformanceCalculator.decompose(
            valueStart = previous?.capital,
            valueEnd = current.capital,
            netAportes = current.aportes,
            netIncome = current.income.net,
        )

    // XIRR since inception over the dated snapshot values: the first snapshot is the opening value
    // (an outflow), each later month's net Aportes is an outflow dated month-end, and the last
    // snapshot is the current value (an inflow). The first month's own Aportes are already inside
    // its opening value, so they are not counted twice.
    private fun xirr(snapshots: List<Snapshot>): BigDecimal? {
        if (snapshots.size < 2) return null

        val flows = mutableListOf(
            XirrFlow(snapshots.first().state.period.endDate(), snapshots.first().state.capital.negate()),
        )
        for (snapshot in snapshots.drop(1)) {
            if (snapshot.state.aportes.signum() != 0) {
                flows += XirrFlow(snapshot.state.period.endDate(), snapshot.state.aportes.negate())
            }
        }
        flows += XirrFlow(snapshots.last().state.period.endDate(), snapshots.last().state.capital)

        return XirrCalculator.calculate(flows)
    }

    private suspend fun classPerformances(context: ClassPerformanceContext): List<ClassPerformance> {
        val previousByClass = context.previous?.classes?.associateBy { it.assetClass }.orEmpty()
        val targetByClass = context.chain.classes.associate { it.assetClass to it.percentage }
        val totalTarget = targetByClass.values.fold(BigDecimal.ZERO, BigDecimal::add)
        val assetClasses = catalog.fetchAll().associate { it.ticker to it.assetClass }
        val incomeByClass = context.incomeByTicker.entries
            .groupBy({ (ticker, _) -> assetClasses[ticker] }, { (_, net) -> net })
            .mapValues { (_, amounts) -> amounts.fold(BigDecimal.ZERO, BigDecimal::add).defaultScale() }

        return context.current.classes.map { classTotal ->
            val target = targetByClass[classTotal.assetClass] ?: BigDecimal.ZERO
            val aportes = shareOf(context.current.aportes, target, totalTarget)
            ClassPerformance(
                assetClass = classTotal.assetClass,
                decomposition = PerformanceCalculator.decompose(
                    valueStart = previousByClass[classTotal.assetClass]?.actual,
                    valueEnd = classTotal.actual,
                    netAportes = aportes,
                    netIncome = incomeByClass[classTotal.assetClass] ?: BigDecimal.ZERO,
                ),
                benchmarkReturn = context.blend.classBenchmark(classTotal.assetClass),
            )
        }
    }

    private suspend fun tickerPerformances(context: TickerPerformanceContext): List<TickerPerformance> {
        val previousByTicker = context.previous?.holdings?.associateBy { it.ticker }.orEmpty()
        val currentByTicker = context.current.holdings.associateBy { it.ticker }
        val assetClasses = catalog.fetchAll().associate { it.ticker to it.assetClass }

        val endValues = currentByTicker.mapValues { (_, holding) -> valueAt(holding, context.currentEnd) }
        val endByClass = endValues.entries
            .groupBy({ (ticker, _) -> assetClasses[ticker] }, { (_, value) -> value })
            .mapValues { (_, values) -> values.fold(BigDecimal.ZERO, BigDecimal::add) }
        val targetByClass = context.chain.classes.associate { it.assetClass to it.percentage }
        val totalTarget = targetByClass.values.fold(BigDecimal.ZERO, BigDecimal::add)

        return (previousByTicker.keys + currentByTicker.keys).sorted().map { ticker ->
            val valueEnd = endValues[ticker] ?: BigDecimal.ZERO
            val valueStart = previousByTicker[ticker]?.let { holding ->
                context.previousEnd?.let { valueAt(holding, it) }
            }
            val assetClass = assetClasses[ticker]
            // The ticker inherits only its own class's share of the month's aportes, then spreads that
            // within the class by end value — otherwise a fixed-income contribution would land on the
            // listed tickers.
            val classAportes = shareOf(
                context.current.aportes,
                assetClass?.let { targetByClass[it] } ?: BigDecimal.ZERO,
                totalTarget,
            )
            TickerPerformance(
                ticker = ticker,
                assetClass = assetClass,
                decomposition = PerformanceCalculator.decompose(
                    valueStart = valueStart,
                    valueEnd = valueEnd,
                    netAportes = assetClass?.let { shareOf(classAportes, valueEnd, endByClass[it] ?: BigDecimal.ZERO) }
                        ?: BigDecimal.ZERO,
                    netIncome = context.incomeByTicker[ticker] ?: BigDecimal.ZERO,
                ),
                benchmarkReturn = assetClass?.let { context.blend.classBenchmark(it) },
            )
        }
    }

    // A ticker/class has no stored per-asset contribution stream, so the month's net Aportes is split
    // in proportion to the weight the rung carries (class target for the class rung, end value for
    // the ticker rung). The split sums to the total the decomposition already nets out.
    private fun shareOf(total: BigDecimal, part: BigDecimal, whole: BigDecimal): BigDecimal =
        if (whole.signum() == 0) {
            BigDecimal.ZERO
        } else {
            total.multiply(part).divide(whole, PERFORMANCE_SCALE, RoundingMode.HALF_UP)
        }

    private suspend fun valueAt(holding: HoldingSnapshot, date: LocalDate): BigDecimal {
        val close = quotes.fetchAllBy(holding.ticker)
            .lastOrNull { it.date <= date }
            ?.close
            ?: holding.averagePrice
        return (holding.quantity * close).defaultScale()
    }

    // A small lookback before the first month gives the IBOV level an anchor: its return is measured
    // against the last close *before* the period, and the first day itself usually has no quote yet.
    private suspend fun benchmarkObservations(
        from: YearMonth,
        to: YearMonth,
    ): Map<BenchmarkIndex, List<BenchmarkObservation>> {
        val start = from.firstDay().minus(LOOKBACK_DAYS, DateTimeUnit.DAY)
        val end = to.endDate()
        return BenchmarkIndex.entries.associateWith { index ->
            runCatching { benchmarks.fetchSeries(index, start, end) }
                .onFailure { log.warn("Benchmark {} series unavailable between {} and {}", index, start, end, it) }
                .getOrDefault(emptyList())
        }
    }

    // Each policy index's own return for the period, sliced out of the one span-wide SGS fetch.
    private fun returnsFor(
        observations: Map<BenchmarkIndex, List<BenchmarkObservation>>,
        period: YearMonth,
    ): Map<BenchmarkIndex, BigDecimal?> {
        val from = period.firstDay()
        val to = period.endDate()
        return BenchmarkIndex.entries.associateWith { index ->
            BenchmarkCalculator.returnFor(index, observations[index].orEmpty(), from, to)
        }
    }

    private fun YearMonth.firstDay(): LocalDate = LocalDate(year, month, 1)

    private fun YearMonth.endDate(): LocalDate =
        firstDay().plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY)

    private companion object {
        const val LOOKBACK_DAYS = 10
    }
}

private data class ClassPerformanceContext(
    val previous: CloseState?,
    val current: CloseState,
    val incomeByTicker: Map<String, BigDecimal>,
    val chain: Targets,
    val blend: BenchmarkBlend,
)

private data class TickerPerformanceContext(
    val previous: CloseState?,
    val current: CloseState,
    val incomeByTicker: Map<String, BigDecimal>,
    val previousEnd: LocalDate?,
    val currentEnd: LocalDate,
    val chain: Targets,
    val blend: BenchmarkBlend,
)
