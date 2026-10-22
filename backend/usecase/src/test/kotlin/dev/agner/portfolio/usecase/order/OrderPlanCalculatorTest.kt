package dev.agner.portfolio.usecase.order

import dev.agner.portfolio.usecase.catalog.model.AssetClass
import dev.agner.portfolio.usecase.classTarget
import dev.agner.portfolio.usecase.fixedincome.model.FixedIncomeSubClass
import dev.agner.portfolio.usecase.order.model.EquitiesOrderPlan
import dev.agner.portfolio.usecase.order.model.FixedIncomeOrderPlan
import dev.agner.portfolio.usecase.order.model.OrderPlanPhase
import dev.agner.portfolio.usecase.order.model.OrderSide
import dev.agner.portfolio.usecase.plain
import dev.agner.portfolio.usecase.portfolio.model.StrategyTarget
import dev.agner.portfolio.usecase.portfolio.model.Targets
import dev.agner.portfolio.usecase.portfolio.model.TickerTarget
import dev.agner.portfolio.usecase.strategy.model.StrategyTicker
import dev.agner.portfolio.usecase.strategyTarget
import dev.agner.portfolio.usecase.subClassTarget
import dev.agner.portfolio.usecase.tickerTarget
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import kotlinx.datetime.YearMonth
import java.math.BigDecimal

// The order engine's arithmetic, pinned on the rebalancing spec's worked example (the ITUB4 case):
// the ideal chain scales the Investable Capital (Capital minus the cash Floor) at every rung, and the
// leaf target is the sum of its strategy slices' whole-unit floors. No repositories reach this seam.
class OrderPlanCalculatorTest : StringSpec({

    "computes the ideal chain from the Investable Capital and the ITUB4 ticker target" {
        val plan = compute(
            targets = chain(
                strategies = listOf(
                    strategyTarget(AssetClass.ACOES, "Top", "50"),
                    strategyTarget(AssetClass.ACOES, "Dividendos", "25"),
                    strategyTarget(AssetClass.ACOES, "Dividendos Plus", "25"),
                ),
                tickers = listOf(
                    tickerTarget("Top", "ITUB4", "10"),
                    tickerTarget("Dividendos", "ITUB4", "15"),
                    tickerTarget("Dividendos Plus", "ITUB4", "10"),
                ),
            ),
            positions = mapOf("ITUB4" to "76"),
            prices = mapOf("ITUB4" to "42.62"),
        )

        plan.phase shouldBe OrderPlanPhase.CANDIDATES
        plan.investableCapital.plain() shouldBe "33075"

        val acoes = plan.classes.filterIsInstance<EquitiesOrderPlan>()
            .single { it.assetClass == AssetClass.ACOES }
        acoes.ideal.plain() shouldBe "33075"
        acoes.strategies.map { it.ideal.plain() } shouldBe listOf("16537.5", "8268.75", "8268.75")
        acoes.strategies.map { it.tickers.single().targetQuantity!!.plain() } shouldBe listOf("38", "29", "19")

        val candidate = plan.candidates.single()
        candidate.ticker shouldBe "ITUB4"
        candidate.side shouldBe OrderSide.BUY
        candidate.quantity.plain() shouldBe "10"
        candidate.targetQuantity.plain() shouldBe "86"
        candidate.positionQuantity.plain() shouldBe "76"
        candidate.price!!.plain() shouldBe "42.62"
        candidate.amount!!.plain() shouldBe "426.2"
        candidate.deficit!!.plain() shouldBe "481.82"

        plan.classes.sumOf { it.ideal }.add(plan.floor).plain() shouldBe plan.capital.plain()
    }

    "previews the chain without candidates in the ideals phase" {
        val plan = compute(
            targets = chain(tickers = listOf(tickerTarget("Top", "ITUB4", "10"))),
            prices = mapOf("ITUB4" to "42.62"),
            phase = OrderPlanPhase.IDEALS,
        )

        val ticker = plan.classes.filterIsInstance<EquitiesOrderPlan>()
            .single { it.assetClass == AssetClass.ACOES }
            .strategies.single()
            .tickers.single()
        ticker.targetQuantity!!.plain() shouldBe "77"
        plan.candidates shouldBe emptyList()
    }

    "opens a NEW_ENTRY when the position is zero" {
        val plan = compute(
            capital = "10000",
            floor = "0",
            targets = chain(tickers = listOf(tickerTarget("Top", "VALE3", "100"))),
            prices = mapOf("VALE3" to "30"),
        )

        val candidate = plan.candidates.single()
        candidate.ticker shouldBe "VALE3"
        candidate.side shouldBe OrderSide.NEW_ENTRY
        candidate.quantity.plain() shouldBe "333"
        candidate.amount!!.plain() shouldBe "9990"
        candidate.deficit!!.plain() shouldBe "10000"
    }

    "floors a BUY to whole units, leaving the remainder idle" {
        val plan = compute(
            capital = "10000",
            floor = "0",
            targets = chain(tickers = listOf(tickerTarget("Top", "VALE3", "100"))),
            positions = mapOf("VALE3" to "10"),
            prices = mapOf("VALE3" to "30"),
        )

        val candidate = plan.candidates.single()
        candidate.side shouldBe OrderSide.BUY
        candidate.quantity.plain() shouldBe "323"
    }

    "leaves a shortfall under one whole unit idle" {
        val plan = compute(
            capital = "1000",
            floor = "0",
            targets = chain(tickers = listOf(tickerTarget("Top", "VALE3", "100"))),
            positions = mapOf("VALE3" to "25.9"),
            prices = mapOf("VALE3" to "38"),
        )

        plan.candidates shouldBe emptyList()
    }

    "floors a fractional position's net to whole units" {
        val plan = compute(
            capital = "1000",
            floor = "0",
            targets = chain(tickers = listOf(tickerTarget("Top", "VALE3", "100"))),
            positions = mapOf("VALE3" to "20.9"),
            prices = mapOf("VALE3" to "38"),
        )

        plan.candidates.single().quantity.plain() shouldBe "5"
    }

    "holds a surplus position and EXITs a dropped ticker at its full position" {
        val plan = compute(
            capital = "10000",
            floor = "0",
            targets = chain(
                tickers = listOf(
                    tickerTarget("Top", "VALE3", "100"),
                    tickerTarget("Top", "ITUB4", "0"),
                ),
            ),
            positions = mapOf(
                "VALE3" to "400",
                "ITUB4" to "7",
                "MXRF11" to "25",
            ),
            prices = mapOf(
                "VALE3" to "30",
                "ITUB4" to "20",
                "MXRF11" to "10",
            ),
        )

        plan.candidates.map { it.ticker to it.side } shouldBe listOf(
            "ITUB4" to OrderSide.EXIT,
            "MXRF11" to OrderSide.EXIT,
        )
        plan.candidates.map { it.side } shouldNotContain OrderSide.SELL
        plan.candidates.associate { it.ticker to it.quantity.plain() } shouldBe mapOf(
            "ITUB4" to "7",
            "MXRF11" to "25",
        )
        plan.candidates.single { it.ticker == "MXRF11" }.amount!!.plain() shouldBe "250"
        plan.candidates.single { it.ticker == "MXRF11" }.deficit!!.plain() shouldBe "-250"
    }

    "leaves a candidate's deficit unset when no quote values the position" {
        val plan = compute(
            capital = "10000",
            floor = "0",
            targets = chain(tickers = listOf(tickerTarget("Top", "VALE3", "100"))),
            positions = mapOf("MXRF11" to "25"),
        )

        val candidate = plan.candidates.single()
        candidate.ticker shouldBe "MXRF11"
        candidate.side shouldBe OrderSide.EXIT
        candidate.price shouldBe null
        candidate.amount shouldBe null
        candidate.deficit shouldBe null
    }

    "floors an EXIT to whole units" {
        val plan = compute(
            capital = "10000",
            floor = "0",
            targets = chain(tickers = listOf(tickerTarget("Top", "VALE3", "100"))),
            positions = mapOf(
                "VALE3" to "400",
                "MXRF11" to "25.9",
            ),
            prices = mapOf(
                "VALE3" to "30",
                "MXRF11" to "10",
            ),
        )

        plan.candidates.single().quantity.plain() shouldBe "25"
        plan.candidates.single().deficit!!.plain() shouldBe "-259"
    }

    "computes the RF sub-class ideals without leaf orders" {
        val plan = compute(
            capital = "100000",
            floor = "20000",
            targets = Targets(
                classes = listOf(classTarget(AssetClass.RENDA_FIXA, "100")),
                strategies = emptyList(),
                subClasses = listOf(
                    subClassTarget(FixedIncomeSubClass.FLOATING_RATE, "60"),
                    subClassTarget(FixedIncomeSubClass.INFLATION_LINKED, "40"),
                ),
                tickers = emptyList(),
            ),
        )

        val fixedIncome = plan.classes.filterIsInstance<FixedIncomeOrderPlan>().single()
        fixedIncome.ideal.plain() shouldBe "80000"
        fixedIncome.subClasses.single { it.subClass == FixedIncomeSubClass.FLOATING_RATE }
            .ideal.plain() shouldBe "48000"
        fixedIncome.subClasses.single { it.subClass == FixedIncomeSubClass.INFLATION_LINKED }
            .ideal.plain() shouldBe "32000"
        fixedIncome.subClasses.single { it.subClass == FixedIncomeSubClass.FIXED_RATE }
            .ideal.plain() shouldBe "0"
        plan.candidates shouldBe emptyList()
    }

    "keeps the ideal but emits no order for a ticker without a quote" {
        val plan = compute(
            capital = "10000",
            floor = "0",
            targets = chain(tickers = listOf(tickerTarget("Top", "VALE3", "100"))),
        )

        val ticker = plan.classes.filterIsInstance<EquitiesOrderPlan>()
            .single { it.assetClass == AssetClass.ACOES }
            .strategies.single()
            .tickers.single()
        ticker.ideal.plain() shouldBe "10000"
        ticker.price shouldBe null
        ticker.targetQuantity shouldBe null
        plan.candidates shouldBe emptyList()
    }

    "blocks the buy for a NEUTRO slice and reports its deficit unfunded" {
        val plan = compute(
            capital = "8262.60",
            floor = "0",
            targets = chain(
                strategies = listOf(strategyTarget(AssetClass.ACOES, "Dividendos", "100")),
                tickers = listOf(tickerTarget("Dividendos", "B3SA3", "10")),
            ),
            prices = mapOf("B3SA3" to "17.58"),
            neutroSlices = setOf(StrategyTicker("Dividendos", "B3SA3")),
        )

        val slice = plan.classes.filterIsInstance<EquitiesOrderPlan>()
            .single { it.assetClass == AssetClass.ACOES }
            .strategies.single()
            .tickers.single()
        slice.neutro shouldBe true
        slice.targetQuantity!!.plain() shouldBe "47"
        slice.deficit!!.plain() shouldBe "826.26"
        plan.candidates shouldBe emptyList()
    }

    "keeps a NEUTRO holding out of a sibling slice's buy sizing" {
        val plan = compute(
            capital = "1000",
            floor = "0",
            targets = chain(
                strategies = listOf(
                    strategyTarget(AssetClass.ACOES, "Top", "50"),
                    strategyTarget(AssetClass.ACOES, "Dividendos", "50"),
                ),
                tickers = listOf(
                    tickerTarget("Top", "VALE3", "100"),
                    tickerTarget("Dividendos", "VALE3", "100"),
                ),
            ),
            positions = mapOf("VALE3" to "100"),
            prices = mapOf("VALE3" to "10"),
            neutroSlices = setOf(StrategyTicker("Dividendos", "VALE3")),
            attributionBySlice = mapOf(StrategyTicker("Dividendos", "VALE3") to BigDecimal("100")),
        )

        val candidate = plan.candidates.single()
        candidate.side shouldBe OrderSide.BUY
        candidate.quantity.plain() shouldBe "50"
    }

    "leaves the non-NEUTRO slices of a ticker buyable" {
        val plan = compute(
            capital = "2000",
            floor = "0",
            targets = chain(
                strategies = listOf(
                    strategyTarget(AssetClass.ACOES, "Top", "50"),
                    strategyTarget(AssetClass.ACOES, "Dividendos", "50"),
                ),
                tickers = listOf(
                    tickerTarget("Top", "ITUB4", "10"),
                    tickerTarget("Dividendos", "ITUB4", "10"),
                ),
            ),
            prices = mapOf("ITUB4" to "10"),
            neutroSlices = setOf(StrategyTicker("Top", "ITUB4")),
        )

        val candidate = plan.candidates.single()
        candidate.side shouldBe OrderSide.NEW_ENTRY
        candidate.quantity.plain() shouldBe "10"
        candidate.targetQuantity.plain() shouldBe "10"
    }
})

private fun compute(
    capital: String = "49075",
    floor: String = "16000",
    targets: Targets,
    positions: Map<String, String> = emptyMap(),
    prices: Map<String, String> = emptyMap(),
    phase: OrderPlanPhase = OrderPlanPhase.CANDIDATES,
    neutroSlices: Set<StrategyTicker> = emptySet(),
    attributionBySlice: Map<StrategyTicker, BigDecimal> = emptyMap(),
) = OrderPlanCalculator.compute(
    OrderPlanContext(
        period = YearMonth.parse("2026-10"),
        phase = phase,
        capital = BigDecimal(capital),
        floor = BigDecimal(floor),
        targets = targets,
        positions = positions.mapValues { BigDecimal(it.value) },
        prices = prices.mapValues { BigDecimal(it.value) },
        neutroSlices = neutroSlices,
        attributionBySlice = attributionBySlice,
    ),
)

private fun chain(
    strategies: List<StrategyTarget> = listOf(strategyTarget(AssetClass.ACOES, "Top", "100")),
    tickers: List<TickerTarget>,
) = Targets(
    classes = listOf(classTarget(AssetClass.ACOES, "100")),
    strategies = strategies,
    subClasses = emptyList(),
    tickers = tickers,
)
