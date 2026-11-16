package dev.agner.portfolio.usecase

import dev.agner.portfolio.usecase.corporateaction.model.CorporateActionSource
import dev.agner.portfolio.usecase.tickeralias.TickerAliasResolver
import dev.agner.portfolio.usecase.tickeralias.model.TickerAlias
import dev.agner.portfolio.usecase.tickeralias.repository.ITickerAliasRepository
import kotlinx.datetime.LocalDate

// The resolver over an in-memory edge list, so a unit spec can state only the renames it cares
// about. No edges is the identity resolver: every code is its own canonical ticker.
fun tickerAliases(vararg aliases: TickerAlias): TickerAliasResolver =
    TickerAliasResolver(InMemoryTickerAliasRepository(aliases.toList()))

fun tickerAliasesRepository(vararg aliases: TickerAlias): ITickerAliasRepository =
    InMemoryTickerAliasRepository(aliases.toList())

fun rename(oldTicker: String, canonicalTicker: String, effectiveDate: String = "2026-10-20") = TickerAlias(
    oldTicker = oldTicker,
    canonicalTicker = canonicalTicker,
    effectiveDate = LocalDate.parse(effectiveDate),
    source = CorporateActionSource.B3_MOVEMENTS,
)

class InMemoryTickerAliasRepository(aliases: List<TickerAlias>) : ITickerAliasRepository {
    private val stored = aliases.toMutableList()

    override suspend fun fetchAll(): List<TickerAlias> = stored.toList()

    override suspend fun save(alias: TickerAlias): TickerAlias {
        stored.removeAll { it.oldTicker == alias.oldTicker }
        stored += alias
        return alias
    }
}
