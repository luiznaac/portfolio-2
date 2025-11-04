package dev.agner.portfolio.usecase.bond.consolidation

import dev.agner.portfolio.usecase.bond.model.Bond
import dev.agner.portfolio.usecase.index.IndexValueService
import dev.agner.portfolio.usecase.index.model.IndexId
import dev.agner.portfolio.usecase.index.model.IndexValue
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class YieldRateServiceTest {

    private val indexValueService: IndexValueService = mockk(relaxed = true)
    private val service = YieldRateService(indexValueService)

    @Test
    fun `buildRateFor should map index values to YieldRateContext for floating rate bond`() = runBlocking {
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

        assertEquals(0, ctx1.rate.compareTo(expectedRate1))
        assertEquals(0, ctx2.rate.compareTo(expectedRate2))
        assertEquals(2, result.size)
    }
}
