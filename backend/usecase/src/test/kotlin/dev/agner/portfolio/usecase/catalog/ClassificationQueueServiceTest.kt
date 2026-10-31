package dev.agner.portfolio.usecase.catalog

import dev.agner.portfolio.usecase.catalog.model.AssetClass
import dev.agner.portfolio.usecase.catalog.model.AssetKind
import dev.agner.portfolio.usecase.catalog.model.ClassificationQueueItem
import dev.agner.portfolio.usecase.catalog.model.UnclassifiedReason
import dev.agner.portfolio.usecase.catalog.repository.ICatalogRepository
import dev.agner.portfolio.usecase.catalogEntry
import dev.agner.portfolio.usecase.listedasset.model.Position
import dev.agner.portfolio.usecase.listedasset.repository.IPositionRepository
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.mockk
import java.math.BigDecimal

// The bulk classification queue: only held tickers with no effective class, each with a named reason
// so the owner can tell a delisted paper from a BDI code the app does not map.
class ClassificationQueueServiceTest : StringSpec({
    val positions = mockk<IPositionRepository>()
    val catalog = mockk<ICatalogRepository>()
    val service = ClassificationQueueService(positions, catalog)

    beforeTest {
        clearAllMocks()
        coEvery { positions.fetchAll() } returns emptyList()
        coEvery { catalog.fetchAll() } returns emptyList()
    }

    "lists only held unclassified tickers with short name, quantity and a named reason" {
        coEvery { positions.fetchAll() } returns listOf(
            position("PETR4", "10"),
            position("XPTO3", "5"),
            position("DELIST3", "3"),
        )
        coEvery { catalog.fetchAll() } returns listOf(
            catalogEntry(ticker = "PETR4", assetClass = AssetClass.ACOES),
            catalogEntry(
                ticker = "XPTO3",
                shortName = "XPTO",
                bdiType = "99",
                assetKind = AssetKind.UNKNOWN,
                assetClass = null,
            ),
            catalogEntry(
                ticker = "DELIST3",
                shortName = "DELIST",
                bdiType = "??",
                assetKind = AssetKind.UNKNOWN,
                assetClass = null,
            ),
        )

        service.fetchQueue() shouldBe listOf(
            ClassificationQueueItem(
                ticker = "DELIST3",
                shortName = "DELIST",
                quantity = BigDecimal("3"),
                reason = UnclassifiedReason.ABSENT_FROM_LATEST_FILE,
            ),
            ClassificationQueueItem(
                ticker = "XPTO3",
                shortName = "XPTO",
                quantity = BigDecimal("5"),
                reason = UnclassifiedReason.UNHANDLED_BDI_CODE,
            ),
        )
    }

    "counts an override-only ticker as classified and skips a ticker the book does not hold" {
        coEvery { positions.fetchAll() } returns listOf(
            position("OVR3", "4"),
            position("ZERO3", "0"),
        )
        coEvery { catalog.fetchAll() } returns listOf(
            catalogEntry(
                ticker = "OVR3",
                bdiType = "99",
                assetKind = AssetKind.UNKNOWN,
                assetClass = null,
                assetClassOverride = AssetClass.FIIS,
            ),
        )

        service.fetchQueue().shouldBeEmpty()
    }
})

private fun position(ticker: String, quantity: String) =
    Position(ticker = ticker, quantity = BigDecimal(quantity), averagePrice = BigDecimal("1"))
