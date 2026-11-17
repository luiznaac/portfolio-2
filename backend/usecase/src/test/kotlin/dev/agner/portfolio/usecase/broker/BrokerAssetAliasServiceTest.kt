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
})

private class InMemoryAliases(vararg aliases: BrokerAssetAlias) : IBrokerAssetAliasRepository {

    private val stored = aliases.toList()

    override suspend fun fetchByDescription(normalizedDescription: String): List<BrokerAssetAlias> =
        stored.filter { it.normalizedDescription == normalizedDescription }
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
