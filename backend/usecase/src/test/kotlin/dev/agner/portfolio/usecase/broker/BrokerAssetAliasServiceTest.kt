package dev.agner.portfolio.usecase.broker

import dev.agner.portfolio.usecase.broker.model.BrokerAssetAlias
import dev.agner.portfolio.usecase.broker.model.BrokerDescriptionResolution
import dev.agner.portfolio.usecase.broker.repository.IBrokerAssetAliasRepository
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

// The Broker Asset Alias resolves a broker description to the ticker the ledger keys trades by. A
// per-institution alias is the owner's confirmed answer for that broker and wins over the global
// fallback; the fallback covers every broker. No alias, or two globals that disagree, is a question
// for the owner — never a guess.
class BrokerAssetAliasServiceTest : StringSpec({

    "resolves an exact global fallback" {
        val service = BrokerAssetAliasService(InMemoryAliases(global(description = "FII BCFF11", ticker = "BCFF11")))

        service.resolve(null, "FII BCFF11") shouldBe BrokerDescriptionResolution.Resolved("BCFF11")
    }

    "prefers a per-institution alias over the global fallback" {
        val service = BrokerAssetAliasService(
            InMemoryAliases(
                global(description = "ITAUUNIBANCO PN", ticker = "GLOBAL4"),
                specific(institutionId = 7, description = "ITAUUNIBANCO PN", ticker = "ITSA4"),
            ),
        )

        service.resolve(7, "ITAUUNIBANCO PN") shouldBe BrokerDescriptionResolution.Resolved("ITSA4")
    }

    "falls back to the global alias when the institution has no alias of its own" {
        val service = BrokerAssetAliasService(
            InMemoryAliases(
                global(description = "ITAUUNIBANCO PN", ticker = "ITSA4"),
                specific(institutionId = 7, description = "OTHER", ticker = "OTHER4"),
            ),
        )

        service.resolve(7, "ITAUUNIBANCO PN") shouldBe BrokerDescriptionResolution.Resolved("ITSA4")
    }

    "reports a description no alias covers as unresolved" {
        val service = BrokerAssetAliasService(InMemoryAliases(global(description = "FII BCFF11", ticker = "BCFF11")))

        service.resolve(null, "ATIVO INEXISTENTE XPTO") shouldBe BrokerDescriptionResolution.Unresolved
    }

    "reports two globals that disagree as ambiguous instead of guessing" {
        val service = BrokerAssetAliasService(
            InMemoryAliases(
                global(description = "ATIVO DUVIDOSO", ticker = "AAAA3"),
                global(description = "ATIVO DUVIDOSO", ticker = "BBBB3"),
            ),
        )

        service.resolve(null, "ATIVO DUVIDOSO") shouldBe
            BrokerDescriptionResolution.Ambiguous(setOf("AAAA3", "BBBB3"))
    }

    "treats repeated globals with the same ticker as one answer" {
        val service = BrokerAssetAliasService(
            InMemoryAliases(
                global(description = "ATIVO DUVIDOSO", ticker = "AAAA3"),
                global(description = "ATIVO DUVIDOSO", ticker = "AAAA3"),
            ),
        )

        service.resolve(null, "ATIVO DUVIDOSO") shouldBe BrokerDescriptionResolution.Resolved("AAAA3")
    }

    "resolves a rights row to its own rights ticker, never the underlying FII" {
        val service = BrokerAssetAliasService(InMemoryAliases(global(description = "FII RBR PROP", ticker = "RBRP11")))

        service.resolve(null, "FII RBR PROP DM 90,00") shouldBe
            BrokerDescriptionResolution.Resolved("RBRP12")
    }

    "an exact rights alias wins over the derived rights ticker" {
        val service = BrokerAssetAliasService(
            InMemoryAliases(
                global(description = "FII RBR PROP", ticker = "RBRP11"),
                global(description = "FII RBR PROP DM 88,27", ticker = "RBRP2"),
            ),
        )

        service.resolve(null, "FII RBR PROP DM 88,27") shouldBe
            BrokerDescriptionResolution.Resolved("RBRP2")
    }

    "an exact DM alias naming the underlying still resolves to the rights ticker" {
        val service = BrokerAssetAliasService(
            InMemoryAliases(
                global(description = "FII RBR PROP", ticker = "RBRP11"),
                global(description = "FII RBR PROP DM 88,27", ticker = "RBRP11"),
            ),
        )

        service.resolve(null, "FII RBR PROP DM 88,27") shouldBe
            BrokerDescriptionResolution.Resolved("RBRP12")
    }

    "an unresolved rights underlying stays unresolved instead of inventing a ticker" {
        val service = BrokerAssetAliasService(InMemoryAliases())

        service.resolve(null, "ATIVO DESCONHECIDO DM 1,00") shouldBe
            BrokerDescriptionResolution.Unresolved
    }

    "confirm stores the owner's answer for the institution under the normalized description" {
        val repository = InMemoryAliases()
        val service = BrokerAssetAliasService(repository)

        service.confirm(
            institutionId = 7,
            normalizedDescription = "ATIVO NOVO",
            ticker = "NOVO3",
        ) shouldBe BrokerAssetAlias(
            id = 1,
            institutionId = 7,
            normalizedDescription = "ATIVO NOVO",
            ticker = "NOVO3",
        )

        repository.fetchByDescription("ATIVO NOVO") shouldBe listOf(
            BrokerAssetAlias(
                id = 1,
                institutionId = 7,
                normalizedDescription = "ATIVO NOVO",
                ticker = "NOVO3",
            ),
        )
    }

    "confirm replaces the institution's previous answer for the same description" {
        val repository = InMemoryAliases(specific(institutionId = 7, description = "ATIVO NOVO", ticker = "OLD3"))
        val service = BrokerAssetAliasService(repository)

        service.confirm(institutionId = 7, normalizedDescription = "ATIVO NOVO", ticker = "NEW3")

        repository.fetchByDescription("ATIVO NOVO").map { it.ticker } shouldBe listOf("NEW3")
    }
})

private class InMemoryAliases(vararg aliases: BrokerAssetAlias) : IBrokerAssetAliasRepository {

    private val stored = aliases.toMutableList()
    private var nextId = stored.size + 1

    override suspend fun fetchByDescription(normalizedDescription: String): List<BrokerAssetAlias> =
        stored.filter { it.normalizedDescription == normalizedDescription }

    override suspend fun save(
        institutionId: Int?,
        normalizedDescription: String,
        ticker: String,
    ): BrokerAssetAlias {
        val existing = stored.firstOrNull {
            it.institutionId == institutionId && it.normalizedDescription == normalizedDescription
        }
        val saved = existing?.copy(ticker = ticker) ?: BrokerAssetAlias(
            id = nextId++,
            institutionId = institutionId,
            normalizedDescription = normalizedDescription,
            ticker = ticker,
        )
        stored.remove(existing)
        stored.add(saved)
        return saved
    }
}

private fun global(description: String, ticker: String) =
    alias(institutionId = null, description = description, ticker = ticker)

private fun specific(institutionId: Int, description: String, ticker: String) =
    alias(institutionId = institutionId, description = description, ticker = ticker)

private fun alias(institutionId: Int?, description: String, ticker: String) = BrokerAssetAlias(
    id = 0,
    institutionId = institutionId,
    normalizedDescription = description,
    ticker = ticker,
)
