package dev.agner.portfolio.gateway.cotahist

import dev.agner.portfolio.usecase.catalog.gateway.ICotahistSource
import dev.agner.portfolio.usecase.catalog.model.CotahistRow
import dev.agner.portfolio.usecase.commons.yesterday
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.http.path
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.format.FormatStringsInDatetimeFormats
import kotlinx.datetime.format.byUnicodePattern
import kotlinx.datetime.minus
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.io.ByteArrayInputStream
import java.time.Clock
import java.util.zip.ZipInputStream

@OptIn(FormatStringsInDatetimeFormats::class)
private val cotahistFileDateFormat = LocalDate.Format { byUnicodePattern("ddMMyyyy") }

// B3's public COTAHIST daily file: one ZIP per trading day at
// /InstDados/SerHist/COTAHIST_D<ddMMyyyy>.ZIP. The file appears after close (~20:37 BRT, not a
// contract), so the requested day may not exist yet — fetchTradingDay walks back over weekends and
// holidays to the most recent published file instead of failing.
@Service
class CotahistGateway(
    private val client: HttpClient,
    private val parser: CotahistParser,
    private val clock: Clock,
    @param:Value("\${gateways.cotahist.host}") private val host: String,
) : ICotahistSource {

    override suspend fun fetchTradingDay(): List<CotahistRow> {
        val requested = LocalDate.yesterday(clock)

        for (offset in 0 until MAX_LOOKBACK_DAYS) {
            val rows = runCatching { fetch(requested.minus(offset, DateTimeUnit.DAY)) }.getOrNull()
            if (rows != null) return rows
        }

        return emptyList()
    }

    private suspend fun fetch(date: LocalDate): List<CotahistRow>? {
        val response = client.get(host) {
            url { path("/InstDados/SerHist/COTAHIST_D${cotahistFileDateFormat.format(date)}.ZIP") }
        }

        if (response.status != HttpStatusCode.OK) return null

        return unzip(response.body())
            .lineSequence()
            .mapNotNull(parser::parseLine)
            .toList()
    }

    private fun unzip(bytes: ByteArray): String = ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
        val entry = zip.nextEntry
        if (entry == null || entry.isDirectory) "" else zip.readBytes().decodeToString()
    }

    private companion object {
        const val MAX_LOOKBACK_DAYS = 7
    }
}
