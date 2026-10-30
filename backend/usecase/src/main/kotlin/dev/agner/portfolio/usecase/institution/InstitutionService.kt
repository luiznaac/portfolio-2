package dev.agner.portfolio.usecase.institution

import dev.agner.portfolio.usecase.institution.model.Institution
import dev.agner.portfolio.usecase.institution.model.InstitutionCreation
import dev.agner.portfolio.usecase.institution.model.normalizedInstitutionName
import dev.agner.portfolio.usecase.institution.repository.IInstitutionRepository
import org.springframework.stereotype.Service

@Service
class InstitutionService(
    private val repository: IInstitutionRepository,
) {

    suspend fun fetchAll() = repository.fetchAll()

    // The read-only lookup: the import review matches a staged row's institution against the ledger
    // without creating one on a GET. A name no institution holds resolves to null.
    suspend fun fetchByName(name: String): Institution? =
        repository.fetchByName(name.normalizedInstitutionName())

    // Loose normalization + merge-on-collision: a name that normalizes to an existing institution
    // returns that row instead of forking a duplicate (the auto-create-on-import path).
    suspend fun create(creation: InstitutionCreation): Institution {
        val name = creation.name.normalizedInstitutionName()
        return repository.fetchByName(name) ?: repository.save(InstitutionCreation(name))
    }

    // Renaming onto a name another institution already holds merges the two: the renamed row is
    // dropped and the surviving row is returned. Renaming onto the row's own name is a no-op (it
    // must not delete itself). A free name updates in place. Null when the id does not exist.
    suspend fun rename(id: Int, name: String): Institution? {
        val normalized = name.normalizedInstitutionName()
        val existing = repository.fetchByName(normalized)

        return when (existing?.id) {
            null -> repository.update(id, normalized)
            id -> existing
            else -> {
                repository.delete(id)
                existing
            }
        }
    }

    suspend fun delete(id: Int) = repository.delete(id)
}
