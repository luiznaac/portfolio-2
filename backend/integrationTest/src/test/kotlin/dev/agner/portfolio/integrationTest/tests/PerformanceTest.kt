package dev.agner.portfolio.integrationTest.tests

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.http.RequestMethod
import dev.agner.portfolio.integrationTest.config.ClockMock
import dev.agner.portfolio.integrationTest.config.HttpMockService.configureResponses
import dev.agner.portfolio.integrationTest.config.IntegrationTest
import dev.agner.portfolio.integrationTest.helpers.getBean
import dev.agner.portfolio.usecase.catalog.model.AssetClass
import dev.agner.portfolio.usecase.catalog.model.AssetKind
import dev.agner.portfolio.usecase.catalog.model.CatalogEntry
import dev.agner.portfolio.usecase.catalog.repository.ICatalogRepository
import dev.agner.portfolio.usecase.institution.InstitutionService
import dev.agner.portfolio.usecase.institution.model.InstitutionCreation
import dev.agner.portfolio.usecase.listedasset.model.GapCreation
import dev.agner.portfolio.usecase.listedasset.model.TradeCreation
import dev.agner.portfolio.usecase.listedasset.model.TradeSide
import dev.agner.portfolio.usecase.listedasset.model.TradeSource
import dev.agner.portfolio.usecase.listedasset.repository.IGapRepository
import dev.agner.portfolio.usecase.listedasset.repository.IPositionRepository
import dev.agner.portfolio.usecase.listedasset.repository.ITradeRepository
import dev.agner.portfolio.usecase.monthlyclose.model.ClassTotalSnapshot
import dev.agner.portfolio.usecase.monthlyclose.model.CloseState
import dev.agner.portfolio.usecase.monthlyclose.model.HoldingSnapshot
import dev.agner.portfolio.usecase.monthlyclose.model.IncomeTotals
import dev.agner.portfolio.usecase.monthlyclose.model.OrdersSnapshot
import dev.agner.portfolio.usecase.monthlyclose.model.TaxCloseSnapshot
import dev.agner.portfolio.usecase.monthlyclose.repository.ISnapshotRepository
import dev.agner.portfolio.usecase.portfolio.MonthService
import dev.agner.portfolio.usecase.portfolio.model.ClassTarget
import dev.agner.portfolio.usecase.portfolio.model.MonthCreation
import dev.agner.portfolio.usecase.portfolio.model.Targets
import dev.agner.portfolio.usecase.quotes.model.Quote
import dev.agner.portfolio.usecase.quotes.model.QuoteSource
import dev.agner.portfolio.usecase.quotes.repository.IQuoteRepository
import dev.agner.portfolio.usecase.tax.model.ExemptionMeter
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.mockk.every
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import java.math.BigDecimal
import java.time.Instant

// The performance read composes the snapshot JSON, the month's target chain, the quote/catalog
// tables and the BCB SGS gateway; a real MySQL round-trip is the only way to prove the snapshot's
// aportes survive the close and that the series orders and replays the stored rows. The backfill is
// asserted on the real trade table too — its absorb deletes a row.
@IntegrationTest
class PerformanceTest : StringSpec({

    beforeTest {
        every { ClockMock.clock.instant() } returns Instant.parse("2026-03-01T12:00:00Z")
    }

    "reads a closed month's decomposition, xirr and blended benchmark" {
        stubBenchmarks()
        val february = seedTwoClosedMonths()

        val client = getBean<HttpClient>()
        val response = client.get("http://localhost:8080/months/${february.id}/performance")

        response.status shouldBe HttpStatusCode.OK
        val body = ObjectMapper().readTree(response.bodyAsText())
        body.get("period").asText() shouldBe "2026-02"
        body.get("decomposition").get("value_start").decimalValue().compareTo(BigDecimal("100000.00")) shouldBe 0
        body.get("decomposition").get("return_percentage").decimalValue().compareTo(BigDecimal("1.5000")) shouldBe 0
        body.get("xirr").decimalValue().compareTo(BigDecimal.ZERO) shouldBe 1
        body.get("benchmark").get("return_percentage").decimalValue().compareTo(BigDecimal("1.9802")) shouldBe 0
        body.get("classes").get(0).get("asset_class").asText() shouldBe "ACOES"
        body.get("tickers").get(0).get("ticker").asText() shouldBe "PETR4"
    }

    "reads the series oldest first" {
        stubBenchmarks()
        seedTwoClosedMonths()

        val client = getBean<HttpClient>()
        val response = client.get("http://localhost:8080/performance/series")

        response.status shouldBe HttpStatusCode.OK
        val body = ObjectMapper().readTree(response.bodyAsText())
        body.get("months").size() shouldBe 2
        body.get("months").get(0).get("period").asText() shouldBe "2026-01"
        val januaryReturn = body.get("months").get(0).get("decomposition").get("return_percentage")
        (januaryReturn == null || januaryReturn.isNull) shouldBe true
        body.get("months").get(1).get("period").asText() shouldBe "2026-02"
        body.get("xirr").isNull shouldBe false
    }

    "backfills: an OPENING seed covered by imported history is retired" {
        val client = getBean<HttpClient>()
        client.post("http://localhost:8080/history/openings") {
            contentType(ContentType.Application.Json)
            setBody("""[{"ticker":"PETR4","quantity":100,"unit_price":10,"date":"2026-03-01"}]""")
        }.status shouldBe HttpStatusCode.OK

        val institution = getBean<InstitutionService>().create(InstitutionCreation("XP INVESTIMENTOS"))
        val trades = getBean<ITradeRepository>()
        trades.save(
            TradeCreation(
                ticker = "PETR4",
                side = TradeSide.BUY,
                date = LocalDate.parse("2026-01-10"),
                venue = "VISTA",
                quantity = BigDecimal("60"),
                unitPrice = BigDecimal("10"),
                institutionId = institution.id,
            ),
            TradeSource.B3_IMPORT,
            ordinal = 1,
        )
        trades.save(
            TradeCreation(
                ticker = "PETR4",
                side = TradeSide.BUY,
                date = LocalDate.parse("2026-02-10"),
                venue = "VISTA",
                quantity = BigDecimal("40"),
                unitPrice = BigDecimal("10"),
                institutionId = institution.id,
            ),
            TradeSource.B3_IMPORT,
            ordinal = 1,
        )

        val response = client.post("http://localhost:8080/history/backfill")

        response.status shouldBe HttpStatusCode.OK
        val body = ObjectMapper().readTree(response.bodyAsText())
        body.get("absorbed_seeds").asInt() shouldBe 1
        body.get("positions_reprojected").asInt() shouldBe 1

        trades.fetchAllBySource(TradeSource.OPENING).map { it.id } shouldBe emptyList()
        getBean<IPositionRepository>().fetchBy("PETR4")!!.quantity.compareTo(BigDecimal("100")) shouldBe 0
    }

    "round-trips a GAP record" {
        val gap = getBean<IGapRepository>().save(
            GapCreation.MissingSale(
                ticker = "PETR4",
                date = LocalDate.parse("2026-02-20"),
                quantity = BigDecimal("40"),
            ),
        )

        val client = getBean<HttpClient>()
        val response = client.get("http://localhost:8080/gaps")

        response.status shouldBe HttpStatusCode.OK
        val body = ObjectMapper().readTree(response.bodyAsText())
        body.get(0).get("id").asInt() shouldBe gap.id
        body.get(0).get("realized_pending").asBoolean() shouldBe true
    }
})

private suspend fun stubBenchmarks() = configureResponses {
    response {
        method = RequestMethod.GET
        endpoint = "/dados/serie/bcdata.sgs.7/dados"
        payload = listOf(
            mapOf("data" to "30/12/2025", "valor" to "100000"),
            mapOf("data" to "30/01/2026", "valor" to "101000"),
            mapOf("data" to "27/02/2026", "valor" to "103000"),
        )
    }
    response {
        method = RequestMethod.GET
        endpoint = "/dados/serie/bcdata.sgs.12/dados"
        payload = emptyList<Map<String, String>>()
    }
    response {
        method = RequestMethod.GET
        endpoint = "/dados/serie/bcdata.sgs.433/dados"
        payload = emptyList<Map<String, String>>()
    }
}

private suspend fun seedTwoClosedMonths(): dev.agner.portfolio.usecase.portfolio.model.Month {
    val months = getBean<MonthService>()
    val snapshots = getBean<ISnapshotRepository>()

    // The month window is the ledger's span, so a backdated January needs imported history to open.
    val institution = getBean<InstitutionService>().create(InstitutionCreation("XP INVESTIMENTOS"))
    getBean<ITradeRepository>().save(
        TradeCreation(
            ticker = "PETR4",
            side = TradeSide.BUY,
            date = LocalDate.parse("2026-01-10"),
            venue = "VISTA",
            quantity = BigDecimal("1"),
            unitPrice = BigDecimal("10"),
            institutionId = institution.id,
        ),
        TradeSource.B3_IMPORT,
        ordinal = 1,
    )

    val january = months.open(MonthCreation(YearMonth.parse("2026-01"), BigDecimal("100000"), BigDecimal.ZERO))
    months.close(january.month.id)
    snapshots.save(
        january.month.id,
        closeState(YearMonth.parse("2026-01"), capital = "100000", aportes = "0", quantity = "10000"),
    )

    val february = months.open(
        MonthCreation(YearMonth.parse("2026-02"), BigDecimal("103500"), BigDecimal.ZERO, BigDecimal("2000")),
    )
    months.replaceCurrentTargets(
        Targets(
            classes = listOf(ClassTarget(AssetClass.ACOES, BigDecimal("100"))),
            strategies = emptyList(),
            subClasses = emptyList(),
            tickers = emptyList(),
        ),
    )
    months.close(february.month.id)
    snapshots.save(
        february.month.id,
        closeState(YearMonth.parse("2026-02"), capital = "103500", aportes = "2000", quantity = "10350"),
    )

    getBean<ICatalogRepository>().upsertAll(
        listOf(
            CatalogEntry(
                ticker = "PETR4",
                shortName = "PETROBRAS",
                bdiType = "02",
                isin = null,
                assetKind = AssetKind.STOCK,
                assetClass = AssetClass.ACOES,
                firstSeen = LocalDate.parse("2025-01-01"),
                lastSeen = LocalDate.parse("2026-02-27"),
            ),
        ),
    )
    getBean<IQuoteRepository>().upsertAll(
        listOf(
            Quote("PETR4", LocalDate.parse("2026-01-30"), BigDecimal("10"), QuoteSource.COTAHIST),
            Quote("PETR4", LocalDate.parse("2026-02-27"), BigDecimal("10"), QuoteSource.COTAHIST),
        ),
    )

    return february.month
}

private fun closeState(
    period: YearMonth,
    capital: String,
    aportes: String,
    quantity: String,
) = CloseState(
    period = period,
    capital = BigDecimal(capital),
    floor = BigDecimal.ZERO,
    aportes = BigDecimal(aportes),
    caixa = BigDecimal.ZERO,
    holdings = listOf(HoldingSnapshot("PETR4", BigDecimal(quantity), BigDecimal("10"))),
    attributions = emptyList(),
    classes = listOf(
        ClassTotalSnapshot(
            assetClass = AssetClass.ACOES,
            target = BigDecimal("100"),
            ideal = BigDecimal(capital),
            actual = BigDecimal(capital),
            deficit = BigDecimal.ZERO,
            drift = BigDecimal.ZERO,
            unattributed = BigDecimal.ZERO,
        ),
    ),
    income = IncomeTotals(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO),
    orders = OrdersSnapshot(emptyList(), emptyList(), emptyList()),
    tax = TaxCloseSnapshot(
        carry = emptyList(),
        meter = ExemptionMeter(
            grossSales = BigDecimal.ZERO,
            exemptionLimit = BigDecimal("20000"),
            exceeded = false,
            realizedGain = BigDecimal.ZERO,
            taxableGain = BigDecimal.ZERO,
            rate = BigDecimal("15"),
            tax = BigDecimal.ZERO,
        ),
    ),
)
