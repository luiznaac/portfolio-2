package dev.agner.portfolio.usecase.institution.model

// A broker/institution a trade was executed through. Names arrive from imports in inconsistent
// casing and spacing, so the canonical form is the normalized name (see normalizedInstitutionName).
data class Institution(
    val id: Int,
    val name: String,
)
