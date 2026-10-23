package dev.agner.portfolio.integrationTest.tests

import dev.agner.portfolio.integrationTest.config.ClockMock
import dev.agner.portfolio.integrationTest.config.IntegrationTest
import dev.agner.portfolio.integrationTest.config.getRequest
import dev.agner.portfolio.integrationTest.helpers.getBean
import dev.agner.portfolio.usecase.catalog.model.AssetClass
import dev.agner.portfolio.usecase.catalog.model.AssetKind
import dev.agner.portfolio.usecase.catalog.model.CatalogEntry
import dev.agner.portfolio.usecase.catalog.repository.ICatalogRepository
import dev.agner.portfolio.usecase.institution.InstitutionService
import dev.agner.portfolio.usecase.institution.model.InstitutionCreation
import dev.agner.portfolio.usecase.listedasset.AttributionService
import dev.agner.portfolio.usecase.listedasset.TradeService
import dev.agner.portfolio.usecase.listedasset.model.AttributionSliceCreation
import dev.agner.portfolio.usecase.listedasset.model.TradeCreation
import dev.agner.portfolio.usecase.listedasset.model.TradeSide
import dev.agner.portfolio.usecase.monthlyclose.SnapshotAlreadyExistsException
import dev.agner.portfolio.usecase.monthlyclose.repository.ISnapshotRepository
import dev.agner.portfolio.usecase.portfolio.MonthService
import dev.agner.portfolio.usecase.portfolio.model.ClassTarget
import dev.agner.portfolio.usecase.portfolio.model.MonthCreation
import dev.agner.portfolio.usecase.portfolio.model.StrategyTarget
import dev.agner.portfolio.usecase.portfolio.model.Targets
import dev.agner.portfolio.usecase.portfolio.model.TickerTarget
import dev.agner.portfolio.usecase.quotes.model.Quote
import dev.agner.portfolio.usecase.quotes.model.QuoteSource
import dev.agner.portfolio.usecase.quotes.repository.IQuoteRepository
import dev.agner.portfolio.usecase.strategy.StrategyService
import dev.agner.portfolio.usecase.strategy.model.StrategyCreation
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.http.HttpStatusCode
import io.mockk.every
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import java.math.BigDecimal
import java.time.Instant

// The close's guarantees only hold across a real MySQL round-trip: the month lock, the snapshot's
// JSON state, the closed-month list and the immutable second write. A mocked unit test cannot see
// the unique key or the serialized document.
@IntegrationTest
class MonthlyCloseTest : StringSpec({

    beforeTest {
        every { ClockMock.clock.instant() } returns Instant.parse("2026-10-31T18:00:00Z")
    }

    "closing the open month persists an immutable snapshot and lists it" {
        val month = getBean<MonthService>().open(
            MonthCreation(period = period, capital = BigDecimal("245000"), floor = BigDecimal("20000")),
        )
        val client = getBean<HttpClient>()

        val response = client.post("http://localhost:8080/month/current/close")

        response.status shouldBe HttpStatusCode.OK
        val body = response.body<Map<String, Any>>()
        (body["month_id"] as Number).toInt() shouldBe month.month.id
        @Suppress("UNCHECKED_CAST")
        val state = body["state"] as Map<String, Any>
        state["period"] shouldBe "2026-10"

        val snapshots = getBean<ISnapshotRepository>()
        val persisted = snapshots.fetchByMonth(month.month.id)!!
        persisted.state.period shouldBe period
        persisted.state.capital.compareTo(BigDecimal("245000.00")) shouldBe 0
        persisted.state.caixa.compareTo(BigDecimal("245000.00")) shouldBe 0

        val closed = getRequest<List<Map<String, Any>>> { path = "/months" }
        closed.single()["period"] shouldBe "2026-10"
        closed.single()["status"] shouldBe "CLOSED"

        client.post("http://localhost:8080/month/current/close").status shouldBe HttpStatusCode.NotFound

        shouldThrow<SnapshotAlreadyExistsException> {
            snapshots.save(month.month.id, persisted.state)
        }
    }

    "a pending transfer blocks the close and leaves the month open" {
        val monthId = openMonthWithPendingTransfer()
        val client = getBean<HttpClient>()

        val response = client.post("http://localhost:8080/month/current/close")

        response.status shouldBe HttpStatusCode.Conflict
        val body = response.body<Map<String, Any>>()
        body["error"] shouldBe "pending-transfers"
        @Suppress("UNCHECKED_CAST")
        val pending = body["pending_transfers"] as List<Map<String, Any>>
        pending.single()["ticker"] shouldBe "ITUB4"
        pending.single()["from_strategy"] shouldBe "Small Caps"
        pending.single()["to_strategy"] shouldBe "Top Dividendos"

        getBean<MonthService>().fetchCurrent()!!.month.id shouldBe monthId
        getBean<ISnapshotRepository>().fetchByMonth(monthId) shouldBe null
    }
})

private val period = YearMonth.parse("2026-10")

// Two slices of one ticker, 150 units apart, at R$10 — the donor's 50-unit transfer is worth exactly
// R$500, so the threshold leaves it pending for a human.
private suspend fun openMonthWithPendingTransfer(): Int {
    val institution = getBean<InstitutionService>().create(InstitutionCreation("XP INVESTIMENTOS"))
    getBean<TradeService>().createManual(
        TradeCreation(
            ticker = "ITUB4",
            side = TradeSide.BUY,
            date = LocalDate.parse("2026-10-05"),
            venue = "VISTA",
            quantity = BigDecimal("200"),
            unitPrice = BigDecimal("10"),
            institutionId = institution.id,
        ),
    )
    getBean<ICatalogRepository>().upsertAll(
        listOf(
            CatalogEntry(
                ticker = "ITUB4",
                shortName = "ITUB4",
                bdiType = "02",
                isin = null,
                assetKind = AssetKind.STOCK,
                assetClass = AssetClass.ACOES,
                firstSeen = LocalDate.parse("2026-10-05"),
                lastSeen = LocalDate.parse("2026-10-05"),
            ),
        ),
    )
    getBean<IQuoteRepository>().upsertAll(
        listOf(
            Quote(
                ticker = "ITUB4",
                date = LocalDate.parse("2026-10-05"),
                close = BigDecimal("10"),
                source = QuoteSource.COTAHIST,
            ),
        ),
    )
    getBean<StrategyService>().create(StrategyCreation("Top Dividendos", AssetClass.ACOES, BigDecimal("50")))
    val small = getBean<StrategyService>().create(StrategyCreation("Small Caps", AssetClass.ACOES, BigDecimal("50")))

    val month = getBean<MonthService>().open(
        MonthCreation(period = period, capital = BigDecimal("1000"), floor = BigDecimal.ZERO),
    )
    getBean<MonthService>().replaceCurrentTargets(targets())
    getBean<AttributionService>().setSlice("ITUB4", AttributionSliceCreation(small.id, BigDecimal("200")))
    return month.month.id
}

private fun targets() = Targets(
    classes = listOf(ClassTarget(AssetClass.ACOES, BigDecimal("100"))),
    strategies = listOf(
        StrategyTarget(AssetClass.ACOES, "Top Dividendos", BigDecimal("50")),
        StrategyTarget(AssetClass.ACOES, "Small Caps", BigDecimal("50")),
    ),
    subClasses = emptyList(),
    tickers = listOf(
        TickerTarget(strategy = "Top Dividendos", ticker = "ITUB4", percentage = BigDecimal("100")),
        TickerTarget(strategy = "Small Caps", ticker = "ITUB4", percentage = BigDecimal("100")),
    ),
)
