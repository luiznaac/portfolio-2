package dev.agner.portfolio.integrationTest.tests

import com.fasterxml.jackson.databind.ObjectMapper
import dev.agner.portfolio.integrationTest.config.ClockMock
import dev.agner.portfolio.integrationTest.config.IntegrationTest
import dev.agner.portfolio.integrationTest.helpers.getBean
import dev.agner.portfolio.usecase.catalog.model.AssetClass
import dev.agner.portfolio.usecase.institution.InstitutionService
import dev.agner.portfolio.usecase.institution.model.InstitutionCreation
import dev.agner.portfolio.usecase.listedasset.TradeService
import dev.agner.portfolio.usecase.listedasset.model.TradeCreation
import dev.agner.portfolio.usecase.listedasset.model.TradeSide
import dev.agner.portfolio.usecase.monthlyclose.repository.ISnapshotRepository
import dev.agner.portfolio.usecase.order.model.OrderSide
import dev.agner.portfolio.usecase.order.model.SuggestedOrderCreation
import dev.agner.portfolio.usecase.order.model.SuggestedOrderStatus
import dev.agner.portfolio.usecase.order.repository.ISuggestedOrderRepository
import dev.agner.portfolio.usecase.portfolio.MonthService
import dev.agner.portfolio.usecase.portfolio.model.ClassTarget
import dev.agner.portfolio.usecase.portfolio.model.MonthCreation
import dev.agner.portfolio.usecase.portfolio.model.Targets
import dev.agner.portfolio.usecase.portfolio.repository.IMonthRepository
import dev.agner.portfolio.usecase.portfolio.repository.ITargetRepository
import dev.agner.portfolio.usecase.tax.model.HarvestPairCreation
import dev.agner.portfolio.usecase.tax.repository.IHarvestPairRepository
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.mockk.every
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import java.math.BigDecimal
import java.time.Instant

// The month window is derived from three stores — trades, months and the server clock — and the
// backdated discard is a write across two tables, so only a real MySQL round-trip proves the rule.
@IntegrationTest
class MonthWindowTest : StringSpec({

    beforeTest {
        every { ClockMock.clock.instant() } returns Instant.parse("2026-10-31T18:00:00Z")
    }

    "derives the window from imported history and the calendar month" {
        buy("2020-03-10", "10")
        buy("2020-12-15", "12")

        val body = json(getBean<HttpClient>().get("http://localhost:8080/month/period-range"))

        body.get("min").asText() shouldBe "2020-03"
        body.get("max").asText() shouldBe "2026-10"
        body.get("default").asText() shouldBe "2020-12"
    }

    "refuses an out-of-window open and discards a backdated month when another opens" {
        buy("2020-03-10", "10")
        buy("2020-12-15", "12")
        val months = getBean<MonthService>()
        val client = getBean<HttpClient>()

        client.post("http://localhost:8080/month") {
            contentType(ContentType.Application.Json)
            setBody("""{"period":"2019-01","capital":1000.00,"floor":0.00}""")
        }.status shouldBe HttpStatusCode.BadRequest

        val backdated = months.open(
            MonthCreation(
                period = YearMonth.parse("2020-12"),
                capital = BigDecimal("1000"),
                floor = BigDecimal.ZERO,
            ),
        )
        months.replaceCurrentTargets(
            Targets(
                classes = listOf(ClassTarget(AssetClass.ACOES, BigDecimal("100"))),
                strategies = emptyList(),
                subClasses = emptyList(),
                tickers = emptyList(),
            ),
        )

        getBean<ISuggestedOrderRepository>().replaceAll(
            backdated.month.id,
            listOf(
                SuggestedOrderCreation(
                    ticker = "PETR4",
                    side = OrderSide.BUY,
                    quantity = BigDecimal("100"),
                    suggestedPrice = BigDecimal("10"),
                    executedQuantity = BigDecimal.ZERO,
                    status = SuggestedOrderStatus.SUGGESTED,
                    matchedTradeId = null,
                ),
            ),
        )
        getBean<IHarvestPairRepository>().save(
            backdated.month.id,
            HarvestPairCreation(
                ticker = "PETR4",
                strategyId = null,
                sellDate = LocalDate.parse("2020-12-10"),
                sellQuantity = BigDecimal("100"),
                sellPrice = BigDecimal("10"),
                rebuyDate = LocalDate.parse("2020-12-11"),
                rebuyQuantity = BigDecimal("100"),
                rebuyPrice = BigDecimal("10"),
            ),
            loss = BigDecimal("100"),
        )

        val close = client.post("http://localhost:8080/month/current/close")

        close.status shouldBe HttpStatusCode.BadRequest
        ObjectMapper().readTree(close.bodyAsText()).get("error").asText() shouldBe "month-close-not-current"
        getBean<ISnapshotRepository>().fetchByMonth(backdated.month.id) shouldBe null

        months.open(
            MonthCreation(
                period = YearMonth.parse("2026-10"),
                capital = BigDecimal("1000"),
                floor = BigDecimal.ZERO,
            ),
        )

        getBean<IMonthRepository>().fetchByPeriod(YearMonth.parse("2020-12")) shouldBe null
        getBean<ITargetRepository>().fetchByMonth(backdated.month.id).classes shouldBe emptyList()
    }

    "replays the tax as of the selected period and rejects a period outside the window" {
        buy("2020-03-10", "10")
        buy("2026-01-10", "100", ticker = "PETR4")
        val client = getBean<HttpClient>()

        val body = json(client.get("http://localhost:8080/tax/2020-12"))

        body.get("period").asText() shouldBe "2020-12"
        body.get("average_prices").get(0).get("ticker").asText() shouldBe "PETR4"
        body.get("average_prices").get(0).get("average_price").decimalValue()
            .compareTo(BigDecimal("10")) shouldBe 0

        val outside = client.get("http://localhost:8080/tax/2020-02")
        outside.status shouldBe HttpStatusCode.BadRequest
        ObjectMapper().readTree(outside.bodyAsText()).get("error").asText() shouldBe
            "tax-period-outside-window"
    }
})

private suspend fun json(text: HttpResponse) = ObjectMapper().readTree(text.bodyAsText())

private suspend fun buy(date: String, price: String, ticker: String = "PETR4") {
    val institution = getBean<InstitutionService>().create(InstitutionCreation("XP INVESTIMENTOS"))
    getBean<TradeService>().createManual(
        TradeCreation(
            ticker = ticker,
            side = TradeSide.BUY,
            date = LocalDate.parse(date),
            venue = "VISTA",
            quantity = BigDecimal("100"),
            unitPrice = BigDecimal(price),
            institutionId = institution.id,
        ),
    )
}
