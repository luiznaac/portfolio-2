package dev.agner.portfolio.usecase.import.model

// The object an import's rows land on. The kind is typed so the parked session, the resume strip
// and the apply landing read the destination instead of guessing it from the file's source.
enum class ImportDestinationObject {
    TRADES,
    POSITIONS,
    INCOME,
    STRATEGY_EDITION,
    CORPORATE_ACTIONS,
}

// Where an import lands: the object plus the entity it belongs to when the object is a detail (a
// strategy, an account). Recorded at staging, so the record alone can answer where it goes.
data class ImportDestination(
    val objectType: ImportDestinationObject,
    val entityId: Int? = null,
)
