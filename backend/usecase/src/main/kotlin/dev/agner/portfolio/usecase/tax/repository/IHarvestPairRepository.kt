package dev.agner.portfolio.usecase.tax.repository

import dev.agner.portfolio.usecase.tax.model.HarvestPair
import dev.agner.portfolio.usecase.tax.model.HarvestPairCreation
import java.math.BigDecimal

interface IHarvestPairRepository {

    suspend fun fetchBy(monthId: Int): List<HarvestPair>

    // `loss` is the realized loss the service computed from the position's fiscal average price; it
    // is passed beside the creation so the row freezes the tax event without the caller setting it.
    suspend fun save(monthId: Int, creation: HarvestPairCreation, loss: BigDecimal): HarvestPair
}
