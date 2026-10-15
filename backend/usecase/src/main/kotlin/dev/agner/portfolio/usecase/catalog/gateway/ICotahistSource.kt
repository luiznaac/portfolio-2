package dev.agner.portfolio.usecase.catalog.gateway

import dev.agner.portfolio.usecase.catalog.model.CotahistRow

// The most recent published B3 COTAHIST file — the shared source for both the ticker catalog and
// EOD quotes. The implementation owns which day that is (walking back over weekends/holidays) and
// returns an empty list when none is reachable.
interface ICotahistSource {

    suspend fun fetchTradingDay(): List<CotahistRow>
}
