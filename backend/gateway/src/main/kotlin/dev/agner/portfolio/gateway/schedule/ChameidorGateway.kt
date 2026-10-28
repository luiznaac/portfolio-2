package dev.agner.portfolio.gateway.schedule

import dev.agner.portfolio.usecase.schedule.gateway.IScheduleGateway
import dev.agner.portfolio.usecase.schedule.model.ScheduleContext
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.path
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class ChameidorGateway(
    private val client: HttpClient,
    @param:Value("\${gateways.chameidor.host}") private val host: String,
    @param:Value("\${gateways.chameidor.token}") private val token: String,
) : IScheduleGateway {

    override suspend fun scheduleOneTimeJob(context: ScheduleContext) {
        client.post(host) {
            headers.append(HttpHeaders.Authorization, "Bearer $token")
            url {
                contentType(ContentType.Application.Json)
                path("/tasks/one-time")
            }
            setBody(context)
        }
    }
}
