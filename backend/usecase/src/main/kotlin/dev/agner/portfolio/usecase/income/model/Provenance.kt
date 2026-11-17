package dev.agner.portfolio.usecase.income.model

// The origin of a cash movement (CONTEXT.md -> Provenance). The "Proventos a Receber" export is a
// B3 extract, so that is what the parser records; BRAPI is the one-off sync that fills the years no
// export covers; the remaining origins describe feeds that do not exist yet.
enum class Provenance {
    B3,
    BRAPI,
    XP,
    KINVO,
    MANUAL,
}
