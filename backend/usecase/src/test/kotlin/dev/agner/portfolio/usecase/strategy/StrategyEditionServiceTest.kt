package dev.agner.portfolio.usecase.strategy

import dev.agner.portfolio.usecase.strategy.model.StrategyEdition
import dev.agner.portfolio.usecase.strategy.model.StrategyEditionCreation
import dev.agner.portfolio.usecase.strategy.model.StrategyTarget
import dev.agner.portfolio.usecase.strategy.parser.IStrategyReportParser
import dev.agner.portfolio.usecase.strategy.parser.ParsedStrategyReport
import dev.agner.portfolio.usecase.strategy.parser.StrategyReportParseException
import dev.agner.portfolio.usecase.strategy.repository.IStrategyEditionRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.datetime.LocalDate
import java.math.BigDecimal

class StrategyEditionServiceTest : StringSpec({
    val repository = mockk<IStrategyEditionRepository>(relaxed = true)
    val parser = mockk<IStrategyReportParser>()
    val diffCalculator = StrategyDiffCalculator()
    val service = StrategyEditionService(repository, parser, diffCalculator)

    val validTargets = listOf(
        StrategyTarget("PETR4", BigDecimal("0.60")),
        StrategyTarget("VALE3", BigDecimal("0.40")),
    )
    val referenceDate = LocalDate.parse("2026-09-01")

    beforeTest { clearAllMocks() }

    "should save a valid parsed report as a new edition" {
        every { parser.parse(any()) } returns
            ParsedStrategyReport(referenceDate, "changelog", validTargets)
        val saved = StrategyEdition(1, strategyId = 5, referenceDate, "changelog", validTargets)
        coEvery { repository.save(any()) } returns saved
        coEvery { repository.exists(5, referenceDate) } returns false

        val result = service.importReport(5, byteArrayOf(1))

        result shouldBe saved
        coVerify {
            repository.save(
                StrategyEditionCreation(
                    strategyId = 5,
                    referenceDate = referenceDate,
                    changesText = "changelog",
                    targets = validTargets,
                ),
            )
        }
    }

    "should reject a report with no targets" {
        every { parser.parse(any()) } returns ParsedStrategyReport(referenceDate, null, emptyList())

        shouldThrow<StrategyReportParseException> { service.importReport(5, byteArrayOf(1)) }
        coVerify(exactly = 0) { repository.save(any()) }
    }

    "should reject a report with a non-B3 ticker" {
        every { parser.parse(any()) } returns
            ParsedStrategyReport(referenceDate, null, listOf(StrategyTarget("NOTATICKER", BigDecimal("1.0"))))

        shouldThrow<StrategyReportParseException> { service.importReport(5, byteArrayOf(1)) }
    }

    "should reject a report whose weights sum below 100%" {
        every { parser.parse(any()) } returns
            ParsedStrategyReport(referenceDate, null, listOf(StrategyTarget("PETR4", BigDecimal("0.50"))))

        shouldThrow<StrategyReportParseException> { service.importReport(5, byteArrayOf(1)) }
        coVerify(exactly = 0) { repository.save(any()) }
    }

    "should reject a report whose weights sum above 100%" {
        val above100 = listOf(
            StrategyTarget("PETR4", BigDecimal("0.601")),
            StrategyTarget("VALE3", BigDecimal("0.40")),
        )
        every { parser.parse(any()) } returns ParsedStrategyReport(referenceDate, null, above100)

        shouldThrow<StrategyReportParseException> { service.importReport(5, byteArrayOf(1)) }
        coVerify(exactly = 0) { repository.save(any()) }
    }

    "should accept weights that sum exactly to 100%" {
        every { parser.parse(any()) } returns ParsedStrategyReport(referenceDate, null, validTargets)
        coEvery { repository.exists(5, referenceDate) } returns false
        coEvery { repository.save(any()) } returns StrategyEdition(1, 5, referenceDate, null, validTargets)

        service.importReport(5, byteArrayOf(1))

        coVerify { repository.save(any()) }
    }

    "should reject a duplicate reference date" {
        every { parser.parse(any()) } returns ParsedStrategyReport(referenceDate, null, validTargets)
        coEvery { repository.exists(5, referenceDate) } returns true

        shouldThrow<StrategyEditionAlreadyExistsException> { service.importReport(5, byteArrayOf(1)) }
        coVerify(exactly = 0) { repository.save(any()) }
    }

    "fetchEditions should attach a diff to every edition but the first" {
        val edition1 = StrategyEdition(1, 5, LocalDate.parse("2026-08-01"), null, validTargets)
        val edition2 = StrategyEdition(
            2,
            5,
            LocalDate.parse("2026-09-01"),
            null,
            listOf(StrategyTarget("PETR4", BigDecimal("0.60")), StrategyTarget("ITUB4", BigDecimal("0.40"))),
        )
        coEvery { repository.fetchByStrategyId(5) } returns listOf(edition1, edition2)

        val result = service.fetchEditions(5)

        result.size shouldBe 2
        result[0].diff shouldBe null
        result[1].diff?.entered shouldBe listOf(StrategyTarget("ITUB4", BigDecimal("0.40")))
        result[1].diff?.exited shouldBe listOf(StrategyTarget("VALE3", BigDecimal("0.40")))
    }
})
