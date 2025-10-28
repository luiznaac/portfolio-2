package dev.agner.portfolio.usecase.consolidation

import dev.agner.portfolio.usecase.commons.mapAsync
import dev.agner.portfolio.usecase.commons.onEachAsyncDeferred
import dev.agner.portfolio.usecase.schedule.ScheduleService
import kotlinx.coroutines.awaitAll
import org.springframework.stereotype.Service

@Service
class ConsolidationService(
    private val consolidators: Set<ProductConsolidator<*>>,
    private val scheduleService: ScheduleService,
) {

    suspend fun scheduleConsolidations() =
        consolidators
            .mapAsync { it.type to it.getConsolidatableIds() }
            .onEachAsyncDeferred {
                it.second.mapAsync { id ->
                    scheduleService.scheduleProductConsolidation(id, it.first)
                }.awaitAll()
            }
            .awaitAll()
            .toMap()

    suspend fun consolidateProduct(productId: Int, type: ProductType) =
        with(consolidators.first { it.type == type }) {
            consolidate(buildContext(productId))
        }
}
