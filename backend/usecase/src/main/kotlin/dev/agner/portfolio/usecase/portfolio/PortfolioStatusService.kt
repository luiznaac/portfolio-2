package dev.agner.portfolio.usecase.portfolio

import dev.agner.portfolio.usecase.catalog.ClassificationQueueService
import dev.agner.portfolio.usecase.commons.mapAsync
import dev.agner.portfolio.usecase.commons.today
import dev.agner.portfolio.usecase.corporateaction.CorporateActionService
import dev.agner.portfolio.usecase.corporateaction.model.CorporateActionStatus
import dev.agner.portfolio.usecase.import.ImportService
import dev.agner.portfolio.usecase.import.model.ImportStatus
import dev.agner.portfolio.usecase.listedasset.GapService
import dev.agner.portfolio.usecase.listedasset.PositionService
import dev.agner.portfolio.usecase.listedasset.model.GapStatus
import dev.agner.portfolio.usecase.listedasset.model.heldTickers
import dev.agner.portfolio.usecase.monthlyclose.repository.ISnapshotRepository
import dev.agner.portfolio.usecase.order.OrderLifecycleService
import dev.agner.portfolio.usecase.portfolio.model.PortfolioStatus
import dev.agner.portfolio.usecase.portfolio.model.PortfolioStatusInput
import dev.agner.portfolio.usecase.quotes.QuoteService
import dev.agner.portfolio.usecase.strategy.model.EditionStatus
import dev.agner.portfolio.usecase.strategy.repository.IEditionRepository
import dev.agner.portfolio.usecase.strategy.repository.IStrategyRepository
import kotlinx.coroutines.awaitAll
import kotlinx.datetime.LocalDate
import org.springframework.stereotype.Service
import java.time.Clock

// The landing's single aggregate read. It gathers the standing status from the existing reads —
// the open month and its plan, the allocation valuation, the staged inboxes, the data-quality
// queues and the quote book — and hands them to PortfolioStatusCalculator, which owns every rule.
// This class stays orchestration only, so the status derivation is unit-tested without a server.
@Service
class PortfolioStatusService(
    private val months: MonthService,
    private val allocations: AllocationService,
    private val orders: OrderLifecycleService,
    private val imports: ImportService,
    private val strategies: IStrategyRepository,
    private val editions: IEditionRepository,
    private val classificationQueue: ClassificationQueueService,
    private val gaps: GapService,
    private val corporateActions: CorporateActionService,
    private val positions: PositionService,
    private val quotes: QuoteService,
    private val snapshots: ISnapshotRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
) {

    suspend fun fetch(): PortfolioStatus {
        val allPositions = positions.fetchAll()
        val latestQuotes = allPositions.heldTickers()
            .mapAsync { ticker -> ticker to quotes.latestQuote(ticker) }
            .awaitAll()
            .mapNotNull { (ticker, quote) -> quote?.let { ticker to it } }
            .toMap()
        val stagedEditions = strategies.fetchAll()
            .mapAsync { strategy -> editions.fetchByStrategy(strategy.id) }
            .awaitAll()
            .flatten()
            .filter { it.status == EditionStatus.STAGED }

        return PortfolioStatusCalculator.compute(
            PortfolioStatusInput(
                asOf = LocalDate.today(clock),
                current = months.fetchCurrent(),
                allocation = allocations.fetchCurrent(),
                orders = orders.fetch(),
                closedMonths = months.fetchClosed(),
                lastSnapshot = snapshots.fetchAll().maxByOrNull { it.state.period },
                positions = allPositions,
                latestQuotes = latestQuotes,
                openGaps = gaps.fetchAll().filter { it.status == GapStatus.OPEN },
                pendingImports = imports.fetchAll()
                    .filter { it.status == ImportStatus.STAGED || it.status == ImportStatus.REVIEWED },
                stagedEditions = stagedEditions,
                unclassifiedTickers = classificationQueue.fetchQueue(),
                reviewActions = corporateActions.fetchAll()
                    .filter { it.status == CorporateActionStatus.NEEDS_REVIEW },
            ),
        )
    }
}
