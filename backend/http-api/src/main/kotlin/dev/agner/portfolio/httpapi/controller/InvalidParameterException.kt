package dev.agner.portfolio.httpapi.controller

import dev.agner.portfolio.usecase.commons.DomainException

// Client-driven path parsing: an absent or non-numeric segment must surface as a domain error
// (mapped to 400) instead of a NumberFormatException/KotlinNullPointerException 500. Unlike the
// field-specific exceptions it replaced, the detail names the field that failed, so every route
// that parses a path parameter gets the same contract for free.
class InvalidParameterException(fieldName: String, received: String?) :
    DomainException(
        error = "invalid-parameter",
        userMessage = "Invalid parameter",
        detail = "Invalid value for field $fieldName: ${received ?: "<missing>"}",
    )
