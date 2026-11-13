package dev.agner.portfolio.httpapi.import

import dev.agner.portfolio.usecase.commons.today
import dev.agner.portfolio.usecase.import.model.ImportDestinationObject
import dev.agner.portfolio.usecase.import.model.ImportRowPayload
import dev.agner.portfolio.usecase.listedasset.model.TradeSide
import kotlinx.datetime.LocalDate
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.Clock

// The staging adapter for the B3 Posição export: the same custody layout B3PositionParser reads,
// mapped onto the import row shape so a snapshot can be staged, reviewed and applied through the
// import assistant. A custody line carries only a ticker and a quantity, so the row is stamped with
// the upload date and a custody venue and the POSITIONS apply reads only those two fields.
@Component
class B3PositionImportParser(
    private val positions: B3PositionParser,
    private val clock: Clock = Clock.systemDefaultZone(),
) : IImportParser {

    override val destination = ImportDestinationObject.POSITIONS

    override fun shouldExecute(fileName: String, content: ByteArray): Boolean =
        positions.shouldExecute(fileName, content)

    override fun parse(fileName: String, content: ByteArray): List<ImportRowPayload> =
        positions.parse(fileName, content).map { position ->
            ImportRowPayload(
                date = LocalDate.today(clock),
                side = TradeSide.BUY,
                venue = CUSTODY_VENUE,
                ticker = position.ticker,
                quantity = position.quantity,
                unitPrice = BigDecimal.ZERO,
                institutionName = CUSTODY_VENUE,
            )
        }

    private companion object {
        const val CUSTODY_VENUE = "CUSTODIA"
    }
}
