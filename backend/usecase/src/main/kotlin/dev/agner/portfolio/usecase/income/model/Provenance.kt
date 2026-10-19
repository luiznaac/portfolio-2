package dev.agner.portfolio.usecase.income.model

// The origin of a cash movement (CONTEXT.md -> Provenance). The "Proventos a Receber" export is a
// B3 extract, so that is what the parser records; the other origins describe feeds that do not
// exist yet.
enum class Provenance {
    B3,
    XP,
    KINVO,
    MANUAL,
}
