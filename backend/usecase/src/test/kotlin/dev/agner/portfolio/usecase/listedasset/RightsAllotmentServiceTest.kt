package dev.agner.portfolio.usecase.listedasset

import dev.agner.portfolio.usecase.buy
import dev.agner.portfolio.usecase.catalog.CatalogService
import dev.agner.portfolio.usecase.catalog.model.AssetKind
import dev.agner.portfolio.usecase.catalog.model.CatalogEntry
import dev.agner.portfolio.usecase.corporateAction
import dev.agner.portfolio.usecase.corporateaction.model.CorporateActionCreation
import dev.agner.portfolio.usecase.corporateaction.model.CorporateActionKind
import dev.agner.portfolio.usecase.corporateaction.model.CorporateActionSource
import dev.agner.portfolio.usecase.corporateaction.model.CorporateActionStatus
import dev.agner.portfolio.usecase.corporateaction.repository.ICorporateActionRepository
import dev.agner.portfolio.usecase.listedasset.repository.ITradeRepository
import dev.agner.portfolio.usecase.tickerAliases
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.datetime.LocalDate
import java.math.BigDecimal

// A rights sale with no receipt is a rights issuance the import did not carry. The service derives a
// zero-cost allotment of the deficit on the sale date, marked DERIVED so a later real allotment can
// replace it without doubling the position.
class RightsAllotmentServiceTest : StringSpec({
    val trades = mockk<ITradeRepository>(relaxed = true)
    val corporateActions = mockk<ICorporateActionRepository>(relaxed = true)
    val catalog = mockk<CatalogService>(relaxed = true)
    val service = RightsAllotmentService(trades, corporateActions, catalog, tickerAliases())

    beforeTest {
        clearAllMocks()
        coEvery { trades.fetchAllBy(any()) } returns emptyList()
        coEvery { corporateActions.fetchByTicker(any()) } returns emptyList()
        coEvery { catalog.fetchBy("RBRP12") } returns rightsEntry
    }

    "derives a zero-cost allotment of the sold quantity on the sale date" {
        val saved = slot<CorporateActionCreation>()
        coEvery { corporateActions.save(capture(saved), CorporateActionStatus.APPLIED) } returns
            derivedAction()

        service.ensureReceipt("RBRP12", SALE_DATE, BigDecimal("10"))

        saved.captured.let {
            it.ticker shouldBe "RBRP12"
            it.kind shouldBe CorporateActionKind.RIGHTS_ALLOTMENT
            it.source shouldBe CorporateActionSource.DERIVED
            it.exDate shouldBe SALE_DATE
            it.grantedQuantity!!.compareTo(BigDecimal("10")) shouldBe 0
        }
    }

    "derives only the deficit when the book already holds part of the sold quantity" {
        coEvery { trades.fetchAllBy("RBRP12") } returns
            listOf(buy(ticker = "RBRP12", date = "2019-05-01", quantity = "4", unitPrice = "0"))
        val saved = slot<CorporateActionCreation>()
        coEvery { corporateActions.save(capture(saved), CorporateActionStatus.APPLIED) } returns
            derivedAction()

        service.ensureReceipt("RBRP12", SALE_DATE, BigDecimal("10"))

        saved.captured.grantedQuantity!!.compareTo(BigDecimal("6")) shouldBe 0
    }

    "derives nothing when the book already holds the whole sold quantity" {
        coEvery { trades.fetchAllBy("RBRP12") } returns
            listOf(buy(ticker = "RBRP12", date = "2019-05-01", quantity = "10", unitPrice = "3"))

        service.ensureReceipt("RBRP12", SALE_DATE, BigDecimal("10"))

        coVerify(exactly = 0) { corporateActions.save(any(), any()) }
    }

    "derives nothing for a sale of a ticker that is not a right" {
        coEvery { catalog.fetchBy("PETR4") } returns stockEntry

        service.ensureReceipt("PETR4", SALE_DATE, BigDecimal("10"))

        coVerify(exactly = 0) { corporateActions.save(any(), any()) }
    }

    "recognizes an uncatalogued rights-shaped code and marks it" {
        coEvery { catalog.fetchBy("RBRP12") } returns null
        val saved = slot<CorporateActionCreation>()
        coEvery { corporateActions.save(capture(saved), CorporateActionStatus.APPLIED) } returns
            derivedAction()

        service.ensureReceipt("RBRP12", SALE_DATE, BigDecimal("10"))

        saved.captured.grantedQuantity!!.compareTo(BigDecimal("10")) shouldBe 0
        coVerify { catalog.markRights("RBRP12") }
    }

    "a later-dated receipt does not cover an earlier sale" {
        coEvery { trades.fetchAllBy("RBRP12") } returns
            listOf(buy(ticker = "RBRP12", date = "2019-06-01", quantity = "10", unitPrice = "0"))
        val saved = slot<CorporateActionCreation>()
        coEvery { corporateActions.save(capture(saved), CorporateActionStatus.APPLIED) } returns
            derivedAction()

        service.ensureReceipt("RBRP12", SALE_DATE, BigDecimal("10"))

        saved.captured.grantedQuantity!!.compareTo(BigDecimal("10")) shouldBe 0
    }

    "retires the derived allotments dated on or after the real one and leaves the real one in place" {
        coEvery { corporateActions.fetchByTicker("RBRP12") } returns listOf(
            derivedAction(id = 1, exDate = "2019-05-06"),
            derivedAction(id = 2, exDate = "2019-04-01"),
            realAction(id = 3),
        )

        service.replaceDerived("RBRP12", LocalDate.parse("2019-05-06"))

        coVerify(exactly = 1) { corporateActions.delete(1) }
        coVerify(exactly = 0) { corporateActions.delete(2) }
        coVerify(exactly = 0) { corporateActions.delete(3) }
    }
})

private val SALE_DATE = LocalDate.parse("2019-05-06")

private val rightsEntry = CatalogEntry(
    ticker = "RBRP12",
    shortName = "RBRP12",
    bdiType = "??",
    isin = null,
    assetKind = AssetKind.RIGHT,
    assetClass = null,
    firstSeen = LocalDate.parse("2019-05-06"),
    lastSeen = LocalDate.parse("2019-05-06"),
)

private val stockEntry = CatalogEntry(
    ticker = "PETR4",
    shortName = "PETR4",
    bdiType = "02",
    isin = null,
    assetKind = AssetKind.STOCK,
    assetClass = null,
    firstSeen = LocalDate.parse("2019-05-06"),
    lastSeen = LocalDate.parse("2019-05-06"),
)

private fun derivedAction(id: Int = 1, ticker: String = "RBRP12", exDate: String = "2019-05-06") = corporateAction(
    CorporateActionKind.RIGHTS_ALLOTMENT,
    id = id,
    ticker = ticker,
    exDate = exDate,
    grantedQuantity = "10",
    source = CorporateActionSource.DERIVED,
)

private fun realAction(id: Int = 3, ticker: String = "RBRP12") = corporateAction(
    CorporateActionKind.RIGHTS_ALLOTMENT,
    id = id,
    ticker = ticker,
    exDate = "2019-05-06",
    grantedQuantity = "10",
)
