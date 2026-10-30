package dev.agner.portfolio.usecase.catalog.gateway

import dev.agner.portfolio.usecase.catalog.model.CotahistRow
import kotlinx.coroutines.flow.Flow

// The most recent published B3 COTAHIST file — the shared source for both the ticker catalog and
// EOD quotes — plus the annual series files the historical close range is built from. A download
// or parse that cannot produce rows raises CotahistFetchException instead of returning empty.
interface ICotahistSource {

    suspend fun fetchTradingDay(): List<CotahistRow>

    // One completed year's annual file, read through a streaming line reader so a full year's fixed-
    // width records never sit decompressed in memory at once. Cold: the request starts on collect.
    fun fetchYear(year: Int): Flow<CotahistRow>
}
