package dev.agner.portfolio.persistence.institution

import dev.agner.portfolio.usecase.commons.now
import dev.agner.portfolio.usecase.institution.model.Institution
import dev.agner.portfolio.usecase.institution.model.InstitutionCreation
import dev.agner.portfolio.usecase.institution.repository.IInstitutionRepository
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.springframework.stereotype.Component
import java.time.Clock

@Component
class InstitutionRepository(
    private val clock: Clock,
) : IInstitutionRepository {

    override suspend fun fetchAll(): List<Institution> = transaction {
        InstitutionEntity.all()
            .orderBy(InstitutionTable.id to SortOrder.ASC)
            .map { it.toModel() }
    }

    override suspend fun fetchByName(name: String): Institution? = transaction {
        InstitutionEntity.find { InstitutionTable.name eq name }.firstOrNull()?.toModel()
    }

    override suspend fun save(creation: InstitutionCreation): Institution = transaction {
        InstitutionEntity.new {
            name = creation.name
            createdAt = LocalDateTime.now(clock)
        }.toModel()
    }

    override suspend fun update(id: Int, name: String): Institution? = transaction {
        InstitutionEntity.findById(id)?.apply { this.name = name }?.toModel()
    }

    override suspend fun delete(id: Int) {
        transaction {
            InstitutionEntity.findById(id)?.delete()
        }
    }
}
