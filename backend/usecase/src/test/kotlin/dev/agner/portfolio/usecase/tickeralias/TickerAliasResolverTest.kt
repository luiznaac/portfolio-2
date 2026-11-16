package dev.agner.portfolio.usecase.tickeralias

import dev.agner.portfolio.usecase.corporateaction.model.CorporateActionSource
import dev.agner.portfolio.usecase.tickeralias.model.TickerAlias
import dev.agner.portfolio.usecase.tickeralias.repository.ITickerAliasRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.datetime.LocalDate

class TickerAliasResolverTest : StringSpec({
    val repository = mockk<ITickerAliasRepository>()
    val resolver = TickerAliasResolver(repository)

    beforeTest { clearAllMocks() }

    fun alias(old: String, canonical: String) = TickerAlias(
        oldTicker = old,
        canonicalTicker = canonical,
        effectiveDate = LocalDate.parse("2026-10-20"),
        source = CorporateActionSource.B3_MOVEMENTS,
    )

    "a single rename resolves the old code to the new one" {
        coEvery { repository.fetchAll() } returns listOf(alias("OLD3", "NEW3"))

        resolver.canonical("OLD3") shouldBe "NEW3"
        resolver.canonical("NEW3") shouldBe "NEW3"
        resolver.canonical("old3") shouldBe "NEW3"
    }

    "the alias set names both codes of a single rename" {
        coEvery { repository.fetchAll() } returns listOf(alias("OLD3", "NEW3"))

        resolver.aliasesOf("OLD3") shouldContainExactlyInAnyOrder listOf("OLD3", "NEW3")
        resolver.aliasesOf("NEW3") shouldContainExactlyInAnyOrder listOf("OLD3", "NEW3")
    }

    "the resolver follows a chain to the latest canonical ticker" {
        coEvery { repository.fetchAll() } returns listOf(
            alias("OLD3", "MID3"),
            alias("MID3", "NEW3"),
        )

        resolver.canonical("OLD3") shouldBe "NEW3"
        resolver.aliasesOf("OLD3") shouldContainExactlyInAnyOrder listOf("OLD3", "MID3", "NEW3")
    }

    "a cycle is rejected rather than guessed" {
        coEvery { repository.fetchAll() } returns listOf(
            alias("AAA3", "BBB3"),
            alias("BBB3", "AAA3"),
        )

        shouldThrow<TickerAliasCycleException> { resolver.canonical("AAA3") }
        shouldThrow<TickerAliasCycleException> { resolver.aliasesOf("BBB3") }
    }

    "canonicalAll resolves every distinct code with one alias read" {
        coEvery { repository.fetchAll() } returns listOf(
            alias("OLD3", "MID3"),
            alias("MID3", "NEW3"),
        )

        resolver.canonicalAll(listOf("OLD3", "MID3", "NEW3", "VALE3")) shouldBe mapOf(
            "OLD3" to "NEW3",
            "MID3" to "NEW3",
            "NEW3" to "NEW3",
            "VALE3" to "VALE3",
        )
    }
})
