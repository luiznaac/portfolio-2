package dev.agner.portfolio.usecase.strategy.model

// The import response: which parser read the report (the reference prototype's "Parser" column)
// beside the staged edition it produced. The parser name belongs to the parsed report, so the
// service folds it into the response rather than leaving the controller to re-derive it.
data class EditionImport(
    val parser: String,
    val edition: EditionDetail,
)
