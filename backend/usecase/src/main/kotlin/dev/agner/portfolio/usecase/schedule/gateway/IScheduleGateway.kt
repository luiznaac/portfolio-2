package dev.agner.portfolio.usecase.schedule.gateway

import dev.agner.portfolio.usecase.schedule.model.ScheduleContext

interface IScheduleGateway {

    suspend fun scheduleOneTimeJob(context: ScheduleContext)
}
