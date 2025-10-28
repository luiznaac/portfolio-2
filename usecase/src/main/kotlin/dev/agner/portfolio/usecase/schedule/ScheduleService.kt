package dev.agner.portfolio.usecase.schedule

import dev.agner.portfolio.usecase.consolidation.ProductType
import dev.agner.portfolio.usecase.schedule.gateway.IScheduleGateway
import dev.agner.portfolio.usecase.schedule.model.ScheduleContext
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class ScheduleService(
    private val gateway: IScheduleGateway,
    @param:Value("\${app-own-host}") private val host: String,
) {

    suspend fun scheduleProductConsolidation(productId: Int, type: ProductType) {
        val context = ScheduleContext(host, "/consolidations/$type/$productId")
        gateway.scheduleOneTimeJob(context)
    }
}
