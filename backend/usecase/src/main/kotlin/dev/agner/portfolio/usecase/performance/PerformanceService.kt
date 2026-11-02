package dev.agner.portfolio.usecase.performance

import dev.agner.portfolio.usecase.catalog.model.AssetClass
import dev.agner.portfolio.usecase.catalog.model.effectiveClassByTicker
import dev.agner.portfolio.usecase.catalog.repository.ICatalogRepository
import dev.agner.portfolio.usecase.commons.defaultScale
import dev.agner.portfolio.usecase.commons.logger
import dev.agner.portfolio.usecase.commons.today
import dev.agner.portfolio.usecase.corporateaction.model.CorporateAction
import dev.agner.portfolio.usecase.corporateaction.repository.ICorporateActionRepository
import dev.agner.portfolio.usecase.income.repository.IIncomeRepository
import dev.agner.portfolio.usecase.listedasset.PositionCalculator
import dev.agner.portfolio.usecase.listedasset.model.Gap
import dev.agner.portfolio.usecase.listedasset.model.GapStatus
import dev.agner.portfolio.usecase.listedasset.model.Position
import dev.agner.portfolio.usecase.listedasset.model.Trade
import dev.agner.portfolio.usecase.listedasset.repository.IGapRepository
import dev.agner.portfolio.usecase.listedasset.repository.ITradeRepository
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
import dev.agner.portfolio.usecase.performance.model.PerformanceSource
import dev.agner.portfolio.usecase.performance.model.PeriodBenchmark
import dev.agner.portfolio.usecase.performance.model.PeriodClass
import dev.agner.portfolio.usecase.performance.model.PeriodPerformance
import dev.agner.portfolio.usecase.performance.model.PeriodStrategy
import dev.agner.portfolio.usecase.performance.model.StrategyPerformance
import dev.agner.portfolio.usecase.performance.model.TickerPerformance
import dev.agner.portfolio.usecase.portfolio.model.Targets
import dev.agner.portfolio.usecase.portfolio.repository.ITargetRepository
import dev.agner.portfolio.usecase.quotes.model.Quote
import dev.agner.portfolio.usecase.quotes.repository.IQuoteRepository
import dev.agner.portfolio.usecase.strategy.model.Strategy
import dev.agner.portfolio.usecase.strategy.model.StrategyKind
import dev.agner.portfolio.usecase.strategy.repository.IStrategyRepository
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.yearMonth
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock

// Composes the performance reads from two eras: the pre-planning months the live ledger supports
// (Positions only — no Month, close or Snapshot is ever written) and the immutable snapshots. The
// snapshots stay the stored truth for the closed months; nothing here re-derives a closed book from
// the ledger.
private typealias BenchmarkObservations = Map<BenchmarkIndex, List<BenchmarkObservation>>

@Service
class PerformanceService(
    private val snapshots: ISnapshotRepository,
    private val catalog: ICatalogRepository,
    private val quotes: IQuoteRepository,
    private val targets: ITargetRepository,
    private val strategies: IStrategyRepository,
    private val benchmarks: IBenchmarkGateway,
    private val trades: ITradeRepository,
    private val incomes: IIncomeRepository,
    private val corporateActions: ICorporateActionRepository,
    private val gaps: IGapRepository,
    private val clock: Clock,
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
            xirr = xirr(all.take(index + 1).map { it.periodPoint() }),
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

    // One ordered list: the computed pre-planning months first, then the closed months. The
    // benchmark series is fetched once for the whole span and each month is sliced out of it, so the
    // series read does not multiply provider calls by month count.
    suspend fun fetchSeries(): PerformanceSeries {
        val all = orderedSnapshots()
        val ledgerRange = trades.fetchExecutionDateRange()
        val computedPeriods = ComputedPerformanceCalculator.window(
            earliestLedgerMonth = ledgerRange?.first?.yearMonth,
            firstSnapshotMonth = all.firstOrNull()?.state?.period,
            lastCompletedMonth = lastCompletedMonth(),
        )
        val observations = benchmarkSpan(computedPeriods, all)
        val computed = computedPoints(computedPeriods, observations)

        return PerformanceSeries(
            months = computed + snapshotPoints(all, observations),
            xirr = xirr(all.map { it.periodPoint() }),
            computedXirr = xirr(computed.map { it.toPeriodPoint() }),
            ledgerEmpty = ledgerRange == null,
        )
    }

    private suspend fun benchmarkSpan(
        computedPeriods: List<YearMonth>,
        snapshots: List<Snapshot>,
    ): BenchmarkObservations {
        val from = computedPeriods.firstOrNull() ?: snapshots.firstOrNull()?.state?.period ?: return emptyMap()
        val to = snapshots.lastOrNull()?.state?.period ?: computedPeriods.last()
        return benchmarkObservations(from, to)
    }

    private suspend fun snapshotPoints(
        all: List<Snapshot>,
        observations: BenchmarkObservations,
    ): List<PeriodPerformance> =
        all.mapIndexed { index, snapshot ->
            val period = snapshot.state.period
            val previous = all.getOrNull(index - 1)
            val chain = targets.fetchByMonth(snapshot.monthId)
            val returns = returnsFor(observations, period)
            PeriodPerformance(
                period = period,
                source = PerformanceSource.SNAPSHOT,
                decomposition = decompose(previous?.state, snapshot.state),
                xirr = xirr(all.take(index + 1).map { it.periodPoint() }),
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
                strategies = snapshot.state.strategies.map { slice ->
                    PeriodStrategy(
                        strategyId = slice.strategyId,
                        strategy = slice.strategy,
                        kind = slice.kind,
                        valueEnd = slice.valueEnd,
                        flow = slice.flow,
                    )
                },
            )
        }

    // The pre-planning era: every month of the window is a point, priced from the as-of Position
    // replay at its month end. The first point has no prior value, so it carries no return; every
    // later point decomposes against the previous computed value.
    private suspend fun computedPoints(
        periods: List<YearMonth>,
        observations: BenchmarkObservations,
    ): List<PeriodPerformance> {
        if (periods.isEmpty()) return emptyList()

        val ledger = trades.fetchAll()
        val incomeRows = incomes.fetchAll()
        val actions = corporateActions.fetchApplied()
        val openGaps = gaps.fetchAll().filter { it.status == GapStatus.OPEN }
        val assetClasses = catalog.fetchAll().effectiveClassByTicker()
        val quoteSeries = (ledger.map { it.ticker } + actions.map { it.ticker } + openGaps.map { it.ticker })
            .toSet()
            .associateWith { quotes.fetchAllBy(it) }

        val raw: List<PeriodPerformance> = periods
            .runningFold(emptyList<PeriodPerformance>()) { points, period ->
                val asOf = period.endDate()
                val positions = positionsAt(ledger, actions, openGaps, asOf)
                val closes = positions.mapNotNull { position ->
                    lastClose(quoteSeries[position.ticker].orEmpty(), asOf)?.let { position.ticker to it }
                }.toMap()
                val monthValue = ComputedPerformanceCalculator.valueAt(positions, closes, assetClasses)
                val netAportes = ComputedPerformanceCalculator.netAportes(ledger, incomeRows, period)
                val benchmarks = returnsFor(observations, period).map { (benchmark, returnPercentage) ->
                    PeriodBenchmark(benchmark, returnPercentage)
                }
                points + PeriodPerformance(
                    period = period,
                    source = PerformanceSource.COMPUTED,
                    decomposition = PerformanceCalculator.decompose(
                        valueStart = points.lastOrNull()?.decomposition?.valueEnd,
                        valueEnd = monthValue.value,
                        netAportes = netAportes,
                        netIncome = ComputedPerformanceCalculator.netIncome(incomeRows, period),
                    ),
                    xirr = null,
                    // No benchmark blend exists before the first close: the Target chain it would
                    // weight is a planning object the history never had.
                    benchmarkReturn = null,
                    benchmarks = benchmarks,
                    classes = monthValue.classes,
                    partial = monthValue.partial,
                )
            }
            .drop(1)
            .map { it.last() }

        return raw.mapIndexed { index, point ->
            point.copy(xirr = xirr(raw.take(index + 1).map { it.toPeriodPoint() }))
        }
    }

    // The as-of replay of every ticker's ledger up to a date: trades, the applied corporate actions
    // and the open GAPs, exactly the input the stored Position is replayed from.
    private fun positionsAt(
        ledger: List<Trade>,
        actions: List<CorporateAction>,
        openGaps: List<Gap>,
        asOf: LocalDate,
    ): List<Position> {
        val tradesByTicker = ledger.filter { it.date <= asOf }.groupBy { it.ticker }
        val actionsByTicker = actions.filter { it.exDate <= asOf }.groupBy { it.ticker }
        val gapsByTicker = openGaps.filter { it.date <= asOf }.groupBy { it.ticker }
        val tickers = tradesByTicker.keys + actionsByTicker.keys + gapsByTicker.keys

        return tickers.map { ticker ->
            PositionCalculator.replay(
                ticker = ticker,
                trades = tradesByTicker[ticker].orEmpty(),
                corporateActions = actionsByTicker[ticker].orEmpty(),
                gaps = gapsByTicker[ticker].orEmpty(),
            )
        }
    }

    private fun lastClose(quotes: List<Quote>, asOf: LocalDate): BigDecimal? =
        quotes.filter { it.date <= asOf }.maxByOrNull { it.date }?.close

    private fun lastCompletedMonth(): YearMonth {
        val today = LocalDate.today(clock)
        return LocalDate(today.year, today.month, 1).minus(1, DateTimeUnit.MONTH).yearMonth
    }

    private suspend fun orderedSnapshots() = snapshots.fetchAll().sortedBy { it.state.period }

    private fun decompose(previous: CloseState?, current: CloseState): PerformanceDecomposition =
        PerformanceCalculator.decompose(
            valueStart = previous?.capital,
            valueEnd = current.capital,
            netAportes = current.aportes,
            netIncome = current.income.net,
        )

    // XIRR over the dated era values: the first point is the opening value (an outflow), each later
    // month's net Aportes is an outflow dated month-end, and the last point is the current value (an
    // inflow). The first month's own Aportes are already inside its opening value, so they are not
    // counted twice. Each era is measured on its own, never across the basis change.
    private fun xirr(points: List<PeriodPoint>): BigDecimal? {
        if (points.size < 2) return null

        val flows = mutableListOf(
            XirrFlow(points.first().date, points.first().value.negate()),
        )
        for (point in points.drop(1)) {
            if (point.aportes.signum() != 0) {
                flows += XirrFlow(point.date, point.aportes.negate())
            }
        }
        flows += XirrFlow(points.last().date, points.last().value)

        return XirrCalculator.calculate(flows)
    }

    private suspend fun classPerformances(context: ClassPerformanceContext): List<ClassPerformance> {
        val previousByClass = context.previous?.classes?.associateBy { it.assetClass }.orEmpty()
        val targetByClass = context.chain.classes.associate { it.assetClass to it.percentage }
        val totalTarget = targetByClass.values.fold(BigDecimal.ZERO, BigDecimal::add)
        val assetClasses = catalog.fetchAll().effectiveClassByTicker()
        val incomeByClass = context.incomeByTicker.entries
            .groupBy({ (ticker, _) -> assetClasses[ticker] }, { (_, net) -> net })
            .mapValues { (_, amounts) -> amounts.fold(BigDecimal.ZERO, BigDecimal::add).defaultScale() }
        // A Strategy's Asset Class is immutable — there is no conversion — so the live catalog
        // supplies the rung a frozen slice was written under; the slice itself freezes its identity.
        val strategyCatalog = strategies.fetchAll().associateBy { it.id }
        val reportTickers = context.chain.tickers
            .groupBy({ it.strategy }, { it.ticker })
            .mapValues { (_, tickers) -> tickers.toSet() }

        return context.current.classes.map { classTotal ->
            val target = targetByClass[classTotal.assetClass] ?: BigDecimal.ZERO
            val aportes = shareOf(context.current.aportes, target, totalTarget)
            val classStart = previousByClass[classTotal.assetClass]?.actual
            val classIncome = incomeByClass[classTotal.assetClass] ?: BigDecimal.ZERO
            val classDecomposition = PerformanceCalculator.decompose(
                valueStart = classStart,
                valueEnd = classTotal.actual,
                netAportes = aportes,
                netIncome = classIncome,
            )
            val slices = strategySlices(
                context = StrategySliceContext(
                    assetClass = classTotal.assetClass,
                    current = context.current,
                    previous = context.previous,
                    classAportes = aportes,
                    classEnd = classTotal.actual,
                    strategyCatalog = strategyCatalog,
                    reportTickers = reportTickers,
                ),
            )
            ClassPerformance(
                assetClass = classTotal.assetClass,
                decomposition = classDecomposition,
                benchmarkReturn = context.blend.classBenchmark(classTotal.assetClass),
                strategies = slices,
                outside = outsideDecomposition(
                    assetClass = classTotal.assetClass,
                    classDecomposition = classDecomposition,
                    slices = slices,
                    // A snapshot written before the strategy rung froze no prior slices, so its prior
                    // class value cannot be split between the slices and the outside row. Treat the
                    // prior book as unknown rather than handing the row the whole class value.
                    priorSliceBookKnown = context.previous == null ||
                        context.previous.strategies.isNotEmpty() ||
                        slices.isEmpty(),
                ),
            )
        }
    }

    // One realised-return slice per Strategy of the class, the Manual Strategy included. A report
    // Strategy owns only the tickers its Target chain names; the bucket owns every ticker it is
    // attributed. A shared Ticker splits by quantity: each slice takes its own units' share of the
    // ticker's income, and its end value drives its share of the class aportes. `flow` is the frozen
    // bookkeeping change valued at the close, so a transfer is not read as performance.
    private fun strategySlices(context: StrategySliceContext): List<StrategyPerformance> {
        val previous = context.previous
        val previousSlices = previous?.strategies.orEmpty().associateBy { it.strategyId }
        val attributionsByStrategy = context.current.attributions.groupBy { it.strategyId }
        val custodyByTicker = context.current.holdings.associate { it.ticker to it.quantity }
        val incomeByTicker = context.current.incomeByTicker.associate { it.ticker to it.net }

        return context.current.strategies
            .filter { context.strategyCatalog[it.strategyId]?.assetClass == context.assetClass }
            .sortedBy { it.strategy }
            .map { slice ->
                val insideTickers = if (slice.kind == StrategyKind.MANUAL) {
                    null
                } else {
                    context.reportTickers[slice.strategy].orEmpty()
                }
                val netIncome = attributionsByStrategy[slice.strategyId].orEmpty()
                    .filter { row -> insideTickers == null || row.ticker in insideTickers }
                    .fold(BigDecimal.ZERO) { total, row ->
                        total + shareOf(
                            total = incomeByTicker[row.ticker] ?: BigDecimal.ZERO,
                            part = row.quantity,
                            whole = custodyByTicker[row.ticker] ?: BigDecimal.ZERO,
                        )
                    }
                    .defaultScale()
                val valueStart = when {
                    // The first close has no prior point to measure any slice from.
                    previous == null -> null
                    previousSlices.containsKey(slice.strategyId) -> previousSlices.getValue(slice.strategyId).valueEnd
                    // A snapshot written before the strategy rung froze no prior slices: the prior
                    // book is unknown, not zero.
                    previous.strategies.isEmpty() -> null
                    // A genuinely new Strategy started from a zero book this month.
                    else -> BigDecimal.ZERO
                }
                StrategyPerformance(
                    strategyId = slice.strategyId,
                    strategy = slice.strategy,
                    kind = slice.kind,
                    decomposition = PerformanceCalculator.decompose(
                        valueStart = valueStart,
                        valueEnd = slice.valueEnd,
                        netAportes = shareOf(
                            total = context.classAportes,
                            part = slice.valueEnd,
                            whole = context.classEnd,
                        ),
                        netIncome = netIncome,
                        flow = slice.flow,
                    ),
                )
            }
    }

    // The read-only remainder after the class's slices: the class line is the direct formula over all
    // its holdings, and this row carries whatever no Strategy holds. It nets the counterpart of the
    // slices' flows, so the class result equals its slices plus this row. Renda Fixa has no Strategy
    // and is never "outside" one, so it carries no row.
    private fun outsideDecomposition(
        assetClass: AssetClass,
        classDecomposition: PerformanceDecomposition,
        slices: List<StrategyPerformance>,
        priorSliceBookKnown: Boolean,
    ): PerformanceDecomposition? {
        if (assetClass == AssetClass.RENDA_FIXA) return null

        return PerformanceCalculator.decompose(
            valueStart = classDecomposition.valueStart
                ?.takeIf { priorSliceBookKnown }
                ?.minus(slices.sumOf { it.decomposition.valueStart ?: BigDecimal.ZERO }),
            valueEnd = classDecomposition.valueEnd - slices.sumOf { it.decomposition.valueEnd },
            netAportes = classDecomposition.netAportes - slices.sumOf { it.decomposition.netAportes },
            netIncome = classDecomposition.netIncome - slices.sumOf { it.decomposition.netIncome },
            flow = slices.sumOf { it.decomposition.flow }.negate(),
        )
    }

    private suspend fun tickerPerformances(context: TickerPerformanceContext): List<TickerPerformance> {
        val previousByTicker = context.previous?.holdings?.associateBy { it.ticker }.orEmpty()
        val currentByTicker = context.current.holdings.associateBy { it.ticker }
        val assetClasses = catalog.fetchAll().effectiveClassByTicker()

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
        val close = lastClose(quotes.fetchAllBy(holding.ticker), date) ?: holding.averagePrice
        return (holding.quantity * close).defaultScale()
    }

    // A small lookback before the first month gives the IBOV level an anchor: its return is measured
    // against the last close *before* the period, and the first day itself usually has no quote yet.
    private suspend fun benchmarkObservations(
        from: YearMonth,
        to: YearMonth,
    ): BenchmarkObservations {
        val start = from.firstDay().minus(LOOKBACK_DAYS, DateTimeUnit.DAY)
        val end = to.endDate()
        return BenchmarkIndex.entries.associateWith { index ->
            runCatching { benchmarks.fetchSeries(index, start, end) }
                .onFailure { log.warn("Benchmark {} series unavailable between {} and {}", index, start, end, it) }
                .getOrDefault(emptyList())
        }
    }

    // Each policy index's own return for the period, sliced out of the one span-wide series fetch.
    private fun returnsFor(
        observations: BenchmarkObservations,
        period: YearMonth,
    ): Map<BenchmarkIndex, BigDecimal?> {
        val from = period.firstDay()
        val to = period.endDate()
        return BenchmarkIndex.entries.associateWith { index ->
            BenchmarkCalculator.returnFor(index, observations[index].orEmpty(), from, to)
        }
    }

    private fun YearMonth.firstDay(): LocalDate = LocalDate(year, month, 1)

    private fun YearMonth.endDate(): LocalDate = endOfMonth()

    private fun Snapshot.periodPoint() = PeriodPoint(
        date = state.period.endDate(),
        value = state.capital,
        aportes = state.aportes,
    )

    private companion object {
        const val LOOKBACK_DAYS = 10
    }
}

private fun YearMonth.endOfMonth(): LocalDate =
    LocalDate(year, month, 1).plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY)

private fun PeriodPerformance.toPeriodPoint() =
    PeriodPoint(period.endOfMonth(), decomposition.valueEnd, decomposition.netAportes)

// One era's dated point for the XIRR walk.
private data class PeriodPoint(
    val date: LocalDate,
    val value: BigDecimal,
    val aportes: BigDecimal,
)

private data class ClassPerformanceContext(
    val previous: CloseState?,
    val current: CloseState,
    val incomeByTicker: Map<String, BigDecimal>,
    val chain: Targets,
    val blend: BenchmarkBlend,
)

private data class StrategySliceContext(
    val assetClass: AssetClass,
    val current: CloseState,
    val previous: CloseState?,
    val classAportes: BigDecimal,
    val classEnd: BigDecimal,
    val strategyCatalog: Map<Int, Strategy>,
    val reportTickers: Map<String, Set<String>>,
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
