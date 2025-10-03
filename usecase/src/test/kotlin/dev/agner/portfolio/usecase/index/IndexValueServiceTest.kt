package dev.agner.portfolio.usecase.index

import dev.agner.portfolio.usecase.index.gateway.IIndexValueGateway
import dev.agner.portfolio.usecase.index.model.IndexId.CDI
import dev.agner.portfolio.usecase.index.model.IndexValue
import dev.agner.portfolio.usecase.index.model.IndexValueCreation
import dev.agner.portfolio.usecase.index.model.TheirIndexValue
import dev.agner.portfolio.usecase.index.repository.IIndexValueRepository
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.datetime.LocalDate
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant

class IndexValueServiceTest : StringSpec({
    val repository = mockk<IIndexValueRepository>(relaxed = true)
    val gateway = mockk<IIndexValueGateway>(relaxed = true)
    val clock = mockk<Clock>()
    val service = IndexValueService(repository, gateway, clock)

    beforeTest { clearAllMocks() }

    "fetchAllIndexValuesBy should call repository" {
        val indexValues = listOf(IndexValue(date = LocalDate.parse("2025-01-03"), value = BigDecimal("12.50")))
        coEvery { repository.fetchAllBy(any()) } returns indexValues

        val result = service.fetchAllBy(CDI)

        result shouldBe indexValues

        coVerify { repository.fetchAllBy(CDI) }
    }

    "hydrateIndexValues should use default start date when last record is null" {
        coEvery { repository.fetchLastBy(any()) } returns null
        every { clock.instant() } returns Instant.parse("2020-01-04T10:00:00Z")
        val theirIndexValues = listOf(
            TheirIndexValue(LocalDate.parse("2020-01-01"), BigDecimal("1.00")),
            TheirIndexValue(LocalDate.parse("2020-01-02"), BigDecimal("2.00")),
            TheirIndexValue(LocalDate.parse("2020-01-03"), BigDecimal("3.00")),
        )
        coEvery { gateway.getIndexValuesForDateRange(any(), any(), any()) } returns theirIndexValues
        coEvery { repository.saveAll(any(), any()) } returns Unit

        val result = service.hydrateIndexValues(CDI)

        result shouldBe 3
        coVerify(exactly = 1) { repository.fetchLastBy(CDI) }
        coVerify(exactly = 1) {
            gateway.getIndexValuesForDateRange(CDI, LocalDate.parse("2020-01-01"), LocalDate.parse("2020-01-03"))
        }
        coVerify(exactly = 1) { repository.saveAll(CDI, theirIndexValues.map(::theirToCreation)) }
    }

    "hydrateIndexValues should use the next day of the last date as start date" {
        coEvery { repository.fetchLastBy(any()) } returns IndexValue(LocalDate.parse("2025-01-02"), BigDecimal("0.5"))
        every { clock.instant() } returns Instant.parse("2025-01-07T10:00:00Z")
        val theirIndexValues = listOf(
            TheirIndexValue(LocalDate.parse("2025-01-03"), BigDecimal("3.0")),
            TheirIndexValue(LocalDate.parse("2025-01-04"), BigDecimal("4.0")),
        )
        coEvery { gateway.getIndexValuesForDateRange(any(), any(), any()) } returns theirIndexValues
        coEvery { repository.saveAll(any(), any()) } returns Unit

        val result = service.hydrateIndexValues(CDI)

        result shouldBe 2
        coVerify(exactly = 1) { repository.fetchLastBy(CDI) }
        coVerify(exactly = 1) {
            gateway.getIndexValuesForDateRange(CDI, LocalDate.parse("2025-01-03"), LocalDate.parse("2025-01-06"))
        }
        coVerify(exactly = 1) { repository.saveAll(CDI, theirIndexValues.map(::theirToCreation)) }
    }

    "hydrateIndexValues should correctly generate ranges when over 100 days" {
        coEvery { repository.fetchLastBy(any()) } returns IndexValue(LocalDate.parse("2025-01-01"), BigDecimal("0.5"))
        every { clock.instant() } returns Instant.parse("2025-12-31T10:00:00Z")
        val theirIndexValues1 = listOf(TheirIndexValue(LocalDate.parse("2025-01-03"), BigDecimal("3.0")))
        val theirIndexValues2 = listOf(TheirIndexValue(LocalDate.parse("2025-04-15"), BigDecimal("4.0")))
        val theirIndexValues3 = listOf(TheirIndexValue(LocalDate.parse("2025-08-23"), BigDecimal("5.0")))
        val theirIndexValues4 = listOf(TheirIndexValue(LocalDate.parse("2025-12-30"), BigDecimal("6.0")))
        coEvery { gateway.getIndexValuesForDateRange(any(), any(), any()) } returnsMany listOf(
            theirIndexValues1,
            theirIndexValues2,
            theirIndexValues3,
            theirIndexValues4,
        )
        coEvery { repository.saveAll(any(), any()) } returns Unit

        val result = service.hydrateIndexValues(CDI)

        result shouldBe 4
        coVerify(exactly = 1) { repository.fetchLastBy(CDI) }
        coVerify(exactly = 1) {
            gateway.getIndexValuesForDateRange(CDI, LocalDate.parse("2025-01-02"), LocalDate.parse("2025-04-12"))
        }
        coVerify(exactly = 1) {
            gateway.getIndexValuesForDateRange(CDI, LocalDate.parse("2025-04-13"), LocalDate.parse("2025-07-22"))
        }
        coVerify(exactly = 1) {
            gateway.getIndexValuesForDateRange(CDI, LocalDate.parse("2025-07-23"), LocalDate.parse("2025-10-31"))
        }
        coVerify(exactly = 1) {
            gateway.getIndexValuesForDateRange(CDI, LocalDate.parse("2025-11-01"), LocalDate.parse("2025-12-30"))
        }
        coVerify(exactly = 1) { repository.saveAll(CDI, theirIndexValues1.map(::theirToCreation)) }
        coVerify(exactly = 1) { repository.saveAll(CDI, theirIndexValues2.map(::theirToCreation)) }
        coVerify(exactly = 1) { repository.saveAll(CDI, theirIndexValues3.map(::theirToCreation)) }
        coVerify(exactly = 1) { repository.saveAll(CDI, theirIndexValues4.map(::theirToCreation)) }
    }

    "hydrateIndexValues should not search indexes if last date is today" {
        coEvery { repository.fetchLastBy(any()) } returns IndexValue(LocalDate.parse("2025-01-07"), BigDecimal("0.5"))
        every { clock.instant() } returns Instant.parse("2025-01-07T10:00:00Z")

        val result = service.hydrateIndexValues(CDI)

        result shouldBe 0
        coVerify(exactly = 1) { repository.fetchLastBy(CDI) }
        coVerify(exactly = 0) { gateway.getIndexValuesForDateRange(any(), any(), any()) }
        coVerify(exactly = 0) { repository.saveAll(any(), any()) }
    }
})

private fun theirToCreation(their: TheirIndexValue) = IndexValueCreation(
    date = their.date,
    value = their.value,
)
