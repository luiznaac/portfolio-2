package dev.agner.portfolio.gateway.cotahist

import dev.agner.portfolio.usecase.catalog.gateway.CotahistFetchException
import dev.agner.portfolio.usecase.catalog.gateway.ICotahistSource
import dev.agner.portfolio.usecase.catalog.model.CotahistRow
import dev.agner.portfolio.usecase.commons.yesterday
import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.path
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.format.FormatStringsInDatetimeFormats
import kotlinx.datetime.format.byUnicodePattern
import kotlinx.datetime.minus
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.io.InputStream
import java.time.Clock
import java.util.zip.ZipInputStream

@OptIn(FormatStringsInDatetimeFormats::class)
private val cotahistFileDateFormat = LocalDate.Format { byUnicodePattern("ddMMyyyy") }

// B3's public COTAHIST series: one ZIP per trading day at
// /InstDados/SerHist/COTAHIST_D<ddMMyyyy>.ZIP and one per year (the current year appears as a
// year-to-date file) at /InstDados/SerHist/COTAHIST_A<yyyy>.ZIP. The daily file appears after close
// (~20:37 BRT, not a contract), so the requested day may not exist yet — fetchTradingDay walks back
// over weekends and holidays to the most recent published file.
//
// Every request asks for `Accept: */*` explicitly: the shared client's JSON content negotiation
// otherwise narrows the header to application/json and B3 answers the ZIP request with 406.
@Service
class CotahistGateway(
    private val client: HttpClient,
    private val parser: CotahistParser,
    private val clock: Clock,
    @param:Value("\${gateways.cotahist.host}") private val host: String,
) : ICotahistSource {

    override suspend fun fetchTradingDay(): List<CotahistRow> {
        val requested = LocalDate.yesterday(clock)

        for (offset in 0..<MAX_LOOKBACK_DAYS) {
            val date = requested.minus(offset, DateTimeUnit.DAY)
            val response = get("/InstDados/SerHist/COTAHIST_D${cotahistFileDateFormat.format(date)}.ZIP")

            // Only a missing file means "not published yet"; every other non-200 is a real failure.
            if (response.status == HttpStatusCode.NotFound) continue
            if (response.status != HttpStatusCode.OK) throw statusFailure("COTAHIST daily file for $date", response)

            return parseDaily(response, date)
        }

        throw CotahistFetchException("No COTAHIST daily file was published in the last $MAX_LOOKBACK_DAYS days")
    }

    // A year's file is far larger than a daily one, so it is decompressed and read line by line
    // instead of being held whole. `flowOn(IO)` keeps the blocking reader off the caller's
    // dispatcher; the request itself starts only when the flow is collected.
    override fun fetchYear(year: Int): Flow<CotahistRow> = flow {
        val response = get("/InstDados/SerHist/COTAHIST_A$year.ZIP", ANNUAL_REQUEST_TIMEOUT_MS)
        if (response.status != HttpStatusCode.OK) throw statusFailure("COTAHIST annual file for $year", response)

        try {
            withZipLines(response.bodyAsChannel().toInputStream()) { lines ->
                var parsed = 0
                for (line in lines) {
                    parser.parseLine(line)?.let {
                        parsed += 1
                        emit(it)
                    }
                }
                if (parsed == 0) throw IllegalStateException("file has no quote records")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: CotahistFetchException) {
            throw e
        } catch (e: Exception) {
            throw CotahistFetchException("Could not parse the COTAHIST annual file for $year", e)
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun get(path: String, timeoutMillis: Long = DAILY_REQUEST_TIMEOUT_MS): HttpResponse = try {
        client.get(host) {
            url { path(path) }
            header(HttpHeaders.Accept, ACCEPT_ANY)
            timeout {
                requestTimeoutMillis = timeoutMillis
                socketTimeoutMillis = timeoutMillis
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        throw CotahistFetchException("Could not download $path", e)
    }

    private suspend fun parseDaily(response: HttpResponse, date: LocalDate): List<CotahistRow> = try {
        withZipLines(response.bodyAsChannel().toInputStream()) { lines ->
            lines.mapNotNull(parser::parseLine)
                .toList()
                .ifEmpty { throw IllegalStateException("file has no quote records") }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        throw CotahistFetchException("Could not parse the COTAHIST daily file for $date", e)
    }

    private inline fun <T> withZipLines(input: InputStream, block: (Sequence<String>) -> T): T =
        ZipInputStream(input).use { zip ->
            val entry = zip.nextEntry
            if (entry == null || entry.isDirectory) throw IllegalStateException("ZIP has no file entry")
            block(zip.bufferedReader(LATIN_1).lineSequence())
        }

    private fun statusFailure(description: String, response: HttpResponse) = CotahistFetchException(
        "$description answered HTTP ${response.status.value}",
    )

    private companion object {
        const val MAX_LOOKBACK_DAYS = 7
        const val ACCEPT_ANY = "*/*"
        const val DAILY_REQUEST_TIMEOUT_MS = 60_000L
        const val ANNUAL_REQUEST_TIMEOUT_MS = 300_000L // a ~90 MB year-to-date file can take >1 min cold
        val LATIN_1 = Charsets.ISO_8859_1 // B3 publishes COTAHIST in Latin-1; accents must survive
    }
}
