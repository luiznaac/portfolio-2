package dev.agner.portfolio.integrationTest.tests

import dev.agner.portfolio.integrationTest.config.ClockMock
import dev.agner.portfolio.integrationTest.config.IntegrationTest
import dev.agner.portfolio.integrationTest.config.getRequest
import dev.agner.portfolio.integrationTest.config.postRequest
import dev.agner.portfolio.integrationTest.helpers.getBean
import dev.agner.portfolio.usecase.catalog.model.AssetClass
import dev.agner.portfolio.usecase.catalog.model.AssetKind
import dev.agner.portfolio.usecase.catalog.model.CatalogEntry
import dev.agner.portfolio.usecase.catalog.repository.ICatalogRepository
import dev.agner.portfolio.usecase.institution.InstitutionService
import dev.agner.portfolio.usecase.institution.model.InstitutionCreation
import dev.agner.portfolio.usecase.listedasset.AttributionService
import dev.agner.portfolio.usecase.listedasset.model.AttributionSliceCreation
import dev.agner.portfolio.usecase.listedasset.model.LegacyAttributionAdd
import dev.agner.portfolio.usecase.listedasset.model.LegacyAttributionApplied
import dev.agner.portfolio.usecase.listedasset.model.LegacyAttributionGroup
import dev.agner.portfolio.usecase.listedasset.model.LegacyAttributionPreview
import dev.agner.portfolio.usecase.listedasset.model.LegacyAttributionResult
import dev.agner.portfolio.usecase.listedasset.model.LegacyAttributionSkip
import dev.agner.portfolio.usecase.listedasset.model.LegacyAttributionSkipReason
import dev.agner.portfolio.usecase.strategy.StrategyService
import dev.agner.portfolio.usecase.strategy.model.StrategyCreation
import dev.agner.portfolio.usecase.strategy.model.StrategyKind
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.http.HttpStatusCode
import io.mockk.every
import kotlinx.datetime.LocalDate
import java.math.BigDecimal
import java.time.Instant

// The attribution ledger's upsert, ceiling and delete only hold if they survive a real MySQL
// round-trip: the service and controller tests mock the repository away.
@IntegrationTest
class AttributionTest : StringSpec({

    beforeTest {
        every { ClockMock.clock.instant() } returns Instant.parse("2026-10-14T10:00:00Z")
    }

    "attribution slices persist, replace and delete against the position's custody" {
        val institution = getBean<InstitutionService>().create(InstitutionCreation("XP INVESTIMENTOS"))
        postRequest<Map<String, Any>> {
            path = "/trades"
            body = manualBody(institution.id)
        }
        val dividends = getBean<StrategyService>()
            .create(StrategyCreation("Top Dividendos", AssetClass.ACOES, StrategyKind.REPORT_DRIVEN))
        val acoes = getBean<StrategyService>()
            .create(StrategyCreation("Top Ações", AssetClass.ACOES, StrategyKind.REPORT_DRIVEN))
        // Classification is a precondition for attribution: the catalog must give the ticker a class.
        getBean<ICatalogRepository>().upsertAll(
            listOf(
                CatalogEntry(
                    ticker = "PETR4",
                    shortName = "PETR4",
                    bdiType = "02",
                    isin = null,
                    assetKind = AssetKind.STOCK,
                    assetClass = AssetClass.ACOES,
                    firstSeen = LocalDate.parse("2026-10-14"),
                    lastSeen = LocalDate.parse("2026-10-14"),
                ),
            ),
        )

        val first = attribute(dividends.id, "60")
        (first["attributed_quantity"] as Number).toInt() shouldBe 60
        (first["unattributed_quantity"] as Number).toInt() shouldBe 40

        val second = attribute(acoes.id, "25")
        (second["attributed_quantity"] as Number).toInt() shouldBe 85
        (second["unattributed_quantity"] as Number).toInt() shouldBe 15

        val read = readAttribution()
        (read["slices"] as List<*>).size shouldBe 2

        val response = getBean<HttpClient>().delete(
            "http://localhost:8080/positions/PETR4/attribution/${dividends.id}",
        )
        response.status shouldBe HttpStatusCode.OK
        val after = response.body<Map<String, Any>>()
        (after["attributed_quantity"] as Number).toInt() shouldBe 25
        (after["unattributed_quantity"] as Number).toInt() shouldBe 75
        (after["slices"] as List<*>).size shouldBe 1
    }

    "the bulk legacy action previews, applies and re-runs idempotently" {
        val institution = getBean<InstitutionService>().create(InstitutionCreation("XP INVESTIMENTOS"))
        buy("ITUB4", "100", institution.id)
        buy("VALE3", "50", institution.id)
        buy("HGLG11", "20", institution.id)
        buy("ZZZZ3", "10", institution.id)

        val legacy = getBean<StrategyService>()
            .create(StrategyCreation("Legado Ações", AssetClass.ACOES, StrategyKind.MANUAL))
        val report = getBean<StrategyService>()
            .create(StrategyCreation("Top Dividendos", AssetClass.ACOES, StrategyKind.REPORT_DRIVEN))
        getBean<ICatalogRepository>().upsertAll(
            listOf(
                catalogEntry("ITUB4", AssetClass.ACOES),
                catalogEntry("VALE3", AssetClass.ACOES),
                catalogEntry("HGLG11", AssetClass.FIIS),
            ),
        )
        // An inert report-driven slice keeps VALE3 out of the bulk action entirely.
        getBean<AttributionService>().setSlice("VALE3", AttributionSliceCreation(report.id, BigDecimal("10")))

        val preview = legacyPreview()
        preview.groups.single().strategyId shouldBe legacy.id
        preview.groups.single().adds.single().ticker shouldBe "ITUB4"
        preview.skips.map { it.ticker to it.reason } shouldBe listOf(
            "HGLG11" to LegacyAttributionSkipReason.NO_BUCKET,
            "VALE3" to LegacyAttributionSkipReason.HAS_ATTRIBUTION,
            "ZZZZ3" to LegacyAttributionSkipReason.UNCLASSIFIED,
        )

        val applied = applyLegacy()
        applied.attributed.single().ticker shouldBe "ITUB4"
        applied.attributed.single().quantity.toInt() shouldBe 100
        applied.skipped.map { it.ticker } shouldBe listOf("HGLG11", "VALE3", "ZZZZ3")

        val attributed = readAttribution("ITUB4")
        (attributed["attributed_quantity"] as Number).toInt() shouldBe 100
        (attributed["unattributed_quantity"] as Number).toInt() shouldBe 0

        // A second run has nothing to add: the bucket ticker is already reconciled.
        legacyPreview().groups shouldBe emptyList<LegacyAttributionGroup>()

        // A new buy grows the bucket slice, so a re-run absorbs the custody delta.
        buy("ITUB4", "30", institution.id)
        legacyPreview().groups.single().adds.single().quantity.toInt() shouldBe 130
        applyLegacy().attributed.single().quantity.toInt() shouldBe 130
        (readAttribution("ITUB4")["attributed_quantity"] as Number).toInt() shouldBe 130
    }
})

private suspend fun buy(ticker: String, quantity: String, institutionId: Int) {
    postRequest<Map<String, Any>> {
        path = "/trades"
        body = mapOf(
            "ticker" to ticker,
            "side" to "BUY",
            "date" to "2026-10-14",
            "venue" to "VISTA",
            "quantity" to BigDecimal(quantity),
            "unit_price" to BigDecimal("10"),
            "institution_id" to institutionId,
        )
    }
}

private suspend fun legacyPreview(): LegacyAttributionPreview =
    getRequest<Map<String, Any>> { path = "/positions/legacy-attribution/preview" }.toPreview()

private suspend fun applyLegacy(): LegacyAttributionResult =
    postRequest<Map<String, Any>> { path = "/positions/legacy-attribution" }.toResult()

@Suppress("UNCHECKED_CAST")
private fun Map<String, Any>.toPreview(): LegacyAttributionPreview = LegacyAttributionPreview(
    groups = (this["groups"] as List<Map<String, Any>>).map { group ->
        LegacyAttributionGroup(
            assetClass = AssetClass.valueOf(group["asset_class"] as String),
            strategyId = (group["strategy_id"] as Number).toInt(),
            strategyName = group["strategy_name"] as String,
            adds = (group["adds"] as List<Map<String, Any>>).map { add ->
                LegacyAttributionAdd(
                    ticker = add["ticker"] as String,
                    quantity = BigDecimal((add["quantity"] as Number).toString()),
                    value = BigDecimal((add["value"] as Number).toString()),
                )
            },
        )
    },
    skips = (this["skips"] as List<Map<String, Any>>).map { it.toSkip() },
)

@Suppress("UNCHECKED_CAST")
private fun Map<String, Any>.toResult(): LegacyAttributionResult = LegacyAttributionResult(
    attributed = (this["attributed"] as List<Map<String, Any>>).map { applied ->
        LegacyAttributionApplied(
            ticker = applied["ticker"] as String,
            quantity = BigDecimal((applied["quantity"] as Number).toString()),
            value = BigDecimal((applied["value"] as Number).toString()),
            strategyId = (applied["strategy_id"] as Number).toInt(),
            strategyName = applied["strategy_name"] as String,
        )
    },
    skipped = (this["skipped"] as List<Map<String, Any>>).map { it.toSkip() },
)

private fun Map<String, Any>.toSkip(): LegacyAttributionSkip = LegacyAttributionSkip(
    ticker = this["ticker"] as String,
    reason = LegacyAttributionSkipReason.valueOf(this["reason"] as String),
    assetClass = (this["asset_class"] as String?)?.let(AssetClass::valueOf),
    message = this["message"] as String?,
)

private fun catalogEntry(ticker: String, assetClass: AssetClass) = CatalogEntry(
    ticker = ticker,
    shortName = ticker,
    bdiType = "02",
    isin = null,
    assetKind = if (assetClass == AssetClass.FIIS) AssetKind.FII else AssetKind.STOCK,
    assetClass = assetClass,
    firstSeen = LocalDate.parse("2026-10-14"),
    lastSeen = LocalDate.parse("2026-10-14"),
)

private suspend fun attribute(strategyId: Int, quantity: String) = postRequest<Map<String, Any>> {
    path = "/positions/PETR4/attribution"
    body = mapOf("strategy_id" to strategyId, "quantity" to BigDecimal(quantity))
}

private suspend fun readAttribution(ticker: String = "PETR4") = getRequest<Map<String, Any>> {
    path = "/positions/$ticker/attribution"
}

private fun manualBody(institutionId: Int) = mapOf(
    "ticker" to "PETR4",
    "side" to "BUY",
    "date" to "2026-10-14",
    "venue" to "VISTA",
    "quantity" to BigDecimal("100"),
    "unit_price" to BigDecimal("10"),
    "institution_id" to institutionId,
)
