package dev.agner.portfolio.usecase.bond.consolidation

import dev.agner.portfolio.usecase.bond.model.Bond
import dev.agner.portfolio.usecase.index.IndexValueService
import dev.agner.portfolio.usecase.index.model.IndexId
import dev.agner.portfolio.usecase.index.model.IndexValue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.equals.shouldBeEqual
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.datetime.LocalDate
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class YieldRateServiceTest : StringSpec({

    val indexValueService: IndexValueService = mockk(relaxed = true)
    val clock = mockk<Clock>()
    val service = YieldRateService(indexValueService, clock)

    beforeEach {
        every { clock.zone } returns ZoneId.systemDefault()
    }

    "buildRateFor should map index values to YieldRateContext for floating rate bond" {
        val startingAt = LocalDate.parse("2024-01-01")
        val indexId = IndexId.CDI
        val indexValues = listOf(
            IndexValue(LocalDate.parse("2024-01-05"), BigDecimal("0.0100")),
            IndexValue(LocalDate.parse("2024-01-06"), BigDecimal("0.0200")),
        )
        coEvery { indexValueService.fetchAllBy(indexId, startingAt) } returns indexValues

        val bond = Bond.FloatingRateBond(
            id = 1,
            name = "Test Floating Bond",
            value = BigDecimal("120"), // 120% of index
            maturityDate = LocalDate.parse("2030-12-31"),
            indexId = indexId,
        )

        val result = service.buildRateFor(bond, startingAt)

        // Expected rates: (120/100) * indexValue
        val expectedRate1 = BigDecimal("120").divide(BigDecimal("100")).multiply(BigDecimal("0.0100"))
        val expectedRate2 = BigDecimal("120").divide(BigDecimal("100")).multiply(BigDecimal("0.0200"))

        val ctx1 = result[LocalDate.parse("2024-01-05")]!!
        val ctx2 = result[LocalDate.parse("2024-01-06")]!!

        ctx1.rate.compareTo(expectedRate1) shouldBeEqual 0
        ctx2.rate.compareTo(expectedRate2) shouldBeEqual 0
        result.size shouldBeEqual 2
    }

    "buildRateFor should map index values to YieldRateContext for fixed rate bond" {
        every { clock.instant() } returns Instant.parse("2025-11-04T10:00:00Z")

        val rate = BigDecimal("15.00")
        val bond = Bond.FixedRateBond(
            id = 1,
            name = "Test Floating Bond",
            value = rate,
            maturityDate = LocalDate.parse("2030-12-31"),
        )

        val startingAt = LocalDate.parse("2024-01-01")
        val result = service.buildRateFor(bond, startingAt)

        val total2024 = result
            .filterKeys { it.year == 2024 }
            .values.fold(BigDecimal.ONE) { acc, rateContext ->
                acc.multiply(BigDecimal.ONE + rateContext.rate / BigDecimal("100")).setScale(20, RoundingMode.HALF_EVEN)
            }
            .minus(BigDecimal.ONE)
            .multiply(BigDecimal("100"))
            .setScale(2, RoundingMode.HALF_EVEN)

        val total2025 = result
            .filterKeys { it.year == 2025 }
            .values.fold(BigDecimal.ONE) { acc, rateContext ->
                acc.multiply(BigDecimal.ONE + rateContext.rate / BigDecimal("100")).setScale(20, RoundingMode.HALF_EVEN)
            }
            .minus(BigDecimal.ONE)
            .multiply(BigDecimal("100"))
            .setScale(2, RoundingMode.HALF_EVEN)

        total2024 shouldBeEqual rate
        total2025 shouldBeEqual rate
    }

    "buildRateFor should filter out dates before startingAt for fixed rate bond" {
        every { clock.instant() } returns Instant.parse("2024-12-31T10:00:00Z")

        val bond = Bond.FixedRateBond(
            id = 2,
            name = "Fixed Bond Filter Test",
            value = BigDecimal("10.00"),
            maturityDate = LocalDate.parse("2030-12-31"),
        )

        val startingAt = LocalDate.parse("2024-03-15")
        val result = service.buildRateFor(bond, startingAt)

        result.keys.any { it < startingAt } shouldBeEqual false
        result.containsKey(startingAt) shouldBeEqual true
    }
})
