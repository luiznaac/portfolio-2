package dev.agner.portfolio.usecase.tickeralias

import dev.agner.portfolio.usecase.catalog.model.normalizedTicker
import dev.agner.portfolio.usecase.tickeralias.model.TickerAlias
import dev.agner.portfolio.usecase.tickeralias.repository.ITickerAliasRepository
import org.springframework.stereotype.Service

// The listed-asset identity boundary. Every read that groups or looks up a ticker asks this resolver
// for the canonical code and the alias set it must query, so a buy under an old code and a sell under
// the new one land in one position, one tax book and one performance series. Repositories keep the
// raw rows; the resolver only names which raw codes belong to the same paper.
@Service
class TickerAliasResolver(
    private val repository: ITickerAliasRepository,
) {

    // The latest code in the rename chain that starts at `ticker`.
    suspend fun canonical(ticker: String): String = resolve(ticker.normalizedTicker(), repository.fetchAll())

    // Every code that resolves to the same canonical ticker, canonical included — the set a read
    // must query to see the paper's whole history. Walking the edges backwards from the canonical
    // code keeps an unrelated cycle elsewhere in the table from breaking this paper's read.
    suspend fun aliasesOf(ticker: String): Set<String> {
        val edges = repository.fetchAll()
        val canonical = resolve(ticker.normalizedTicker(), edges)
        val byCanonical = edges.groupBy { it.canonicalTicker }
        val aliases = mutableSetOf(canonical)
        val pending = ArrayDeque(listOf(canonical))
        while (pending.isNotEmpty()) {
            byCanonical[pending.removeFirst()].orEmpty().forEach { edge ->
                if (aliases.add(edge.oldTicker)) pending.addLast(edge.oldTicker)
            }
        }
        return aliases
    }

    // The bulk form: one read of the edges, one canonical per distinct input. Grouping a whole
    // ledger by canonical ticker should not re-read the alias table per row.
    suspend fun canonicalAll(tickers: Collection<String>): Map<String, String> {
        val edges = repository.fetchAll()
        return tickers.associate { it to resolve(it.normalizedTicker(), edges) }
    }

    // A cycle is rejected instead of guessed: with no latest code there is no single identity a
    // position or tax book could be keyed on.
    private fun resolve(start: String, edges: List<TickerAlias>): String {
        val next = edges.associate { it.oldTicker to it.canonicalTicker }
        val visited = mutableSetOf(start)
        var current = start
        while (true) {
            val target = next[current] ?: return current
            if (!visited.add(target)) throw TickerAliasCycleException(start)
            current = target
        }
    }
}
