package dev.agner.portfolio.usecase.import.model

// The outcome of one staging request. A file whose hash is unknown creates its Import (`created`);
// a file whose hash is already known returns that import untouched whatever its status, and the
// caller answers 200 instead of 201. The source carried by the request is ignored on the match.
data class StagingResult(
    val record: Import,
    val created: Boolean,
)
