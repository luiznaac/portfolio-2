package dev.agner.portfolio.usecase.institution.repository

import dev.agner.portfolio.usecase.institution.model.Institution
import dev.agner.portfolio.usecase.institution.model.InstitutionCreation

interface IInstitutionRepository {

    suspend fun fetchAll(): List<Institution>

    // Looked up by the already-normalized name.
    suspend fun fetchByName(name: String): Institution?

    suspend fun save(creation: InstitutionCreation): Institution

    // Null when no institution has that id.
    suspend fun update(id: Int, name: String): Institution?

    suspend fun delete(id: Int)
}
