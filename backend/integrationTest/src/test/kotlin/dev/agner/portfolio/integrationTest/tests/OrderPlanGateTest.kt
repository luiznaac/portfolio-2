package dev.agner.portfolio.integrationTest.tests

import com.fasterxml.jackson.databind.ObjectMapper
import dev.agner.portfolio.integrationTest.config.ClockMock
import dev.agner.portfolio.integrationTest.config.IntegrationTest
import dev.agner.portfolio.integrationTest.helpers.getBean
import dev.agner.portfolio.usecase.catalog.model.AssetClass
import dev.agner.portfolio.usecase.listedasset.model.Position
import dev.agner.portfolio.usecase.listedasset.repository.IPositionRepository
import dev.agner.portfolio.usecase.portfolio.MonthService
import dev.agner.portfolio.usecase.portfolio.model.ClassTarget
import dev.agner.portfolio.usecase.portfolio.model.MonthCreation
import dev.agner.portfolio.usecase.portfolio.model.StrategyTarget
import dev.agner.portfolio.usecase.portfolio.model.Targets
import dev.agner.portfolio.usecase.portfolio.model.TickerTarget
import dev.agner.portfolio.usecase.quotes.model.Quote
import dev.agner.portfolio.usecase.quotes.model.QuoteSource
import dev.agner.portfolio.usecase.quotes.repository.IQuoteRepository
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.mockk.every
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import java.math.BigDecimal
import java.time.Instant

// The plan's two incomplete-book gates over a real round-trip: an unpriced or unclassified held
// ticker must stop the plan before it sizes anything, and a target-only ticker (no position, no
// close) must not. The error body is the canonical domain-exception shape.
@IntegrationTest
class OrderPlanGateTest : StringSpec({

    beforeTest {
        every { ClockMock.clock.instant() } returns Instant.parse("2026-10-14T10:00:00Z")
    }

    "the plan refuses with unpriced-positions first and unclassified-positions second" {
        openMonth()
        position("ZZZZ3", "10")
        position("YYYY3", "5")
        quote("YYYY3", "2026-10-13", "10")

        val unpriced = orders()
        unpriced.status shouldBe HttpStatusCode.BadRequest
        val unpricedBody = ObjectMapper().readTree(unpriced.bodyAsText())
        unpricedBody.get("error").asText() shouldBe "unpriced-positions"
        unpricedBody.get("detail").asText() shouldContain "ZZZZ3"

        quote("ZZZZ3", "2026-10-13", "10")

        val unclassified = orders()
        unclassified.status shouldBe HttpStatusCode.BadRequest
        val unclassifiedBody = ObjectMapper().readTree(unclassified.bodyAsText())
        unclassifiedBody.get("error").asText() shouldBe "unclassified-positions"
        unclassifiedBody.get("detail").asText() shouldContain "YYYY3"
    }

    "a target-only ticker with no position and no close does not block the plan" {
        openMonth()
        getBean<MonthService>().replaceCurrentTargets(
            Targets(
                classes = listOf(ClassTarget(AssetClass.ACOES, BigDecimal("100"))),
                strategies = listOf(StrategyTarget(AssetClass.ACOES, "Top", BigDecimal("100"))),
                subClasses = emptyList(),
                tickers = listOf(TickerTarget("Top", "NOPOS3", BigDecimal("100"))),
            ),
        )

        orders().status shouldBe HttpStatusCode.OK
    }
})

private suspend fun orders() = getBean<HttpClient>().get("http://localhost:8080/month/current/orders")

private suspend fun openMonth() {
    getBean<MonthService>().open(
        MonthCreation(
            period = YearMonth.parse("2026-10"),
            capital = BigDecimal("10000"),
            floor = BigDecimal.ZERO,
        ),
    )
}

private suspend fun position(ticker: String, quantity: String) {
    getBean<IPositionRepository>().save(
        Position(ticker = ticker, quantity = BigDecimal(quantity), averagePrice = BigDecimal("10")),
    )
}

private suspend fun quote(ticker: String, date: String, close: String) {
    getBean<IQuoteRepository>().upsertAll(
        listOf(
            Quote(
                ticker = ticker,
                date = LocalDate.parse(date),
                close = BigDecimal(close),
                source = QuoteSource.COTAHIST,
            ),
        ),
    )
}
