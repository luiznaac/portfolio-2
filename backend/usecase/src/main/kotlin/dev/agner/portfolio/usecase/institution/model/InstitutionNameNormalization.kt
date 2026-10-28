package dev.agner.portfolio.usecase.institution.model

private val WHITESPACE_RUN = Regex("\\s+")

// Loose normalization for institution names: imports spell the same broker "XP", "Xp" and
// "XP  Investimentos", and a rename must merge rather than fork. Uppercase and collapse internal
// whitespace so equal-normalized names are the same institution.
fun String.normalizedInstitutionName(): String = trim().uppercase().replace(WHITESPACE_RUN, " ")
