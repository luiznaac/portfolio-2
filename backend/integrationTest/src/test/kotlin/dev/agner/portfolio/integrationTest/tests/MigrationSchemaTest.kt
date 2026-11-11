package dev.agner.portfolio.integrationTest.tests

import dev.agner.portfolio.integrationTest.config.TestMySql
import dev.agner.portfolio.integrationTest.config.mappedPort
import dev.agner.portfolio.persistence.configuration.DATABASE_NAME
import dev.agner.portfolio.persistence.configuration.mysqlJdbcUrl
import dev.agner.portfolio.persistence.migration.allTables
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import org.jetbrains.exposed.v1.core.ExperimentalDatabaseMigrationApi
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.migration.jdbc.MigrationUtils

// The guard that keeps db/migration/ and the Exposed tables from drifting apart — the failure
// mode that made `mysql/init.sql` unmaintainable. MySqlContainerExtension.beforeProject() already
// migrated this database to head before any spec runs; this asks Exposed what would still have
// to change for it to match `allTables`. Anything non-empty means someone edited a *Table without
// writing the migration, or vice versa. The mapped port is the one this run's container exposes.
@OptIn(ExperimentalDatabaseMigrationApi::class)
class MigrationSchemaTest : StringSpec({

    "migrations produce exactly the schema the Exposed tables declare" {
        val db = Database.connect(
            url = mysqlJdbcUrl(
                host = TestMySql.host,
                port = TestMySql.mappedPort,
                database = DATABASE_NAME,
            ),
            driver = "com.mysql.cj.jdbc.Driver",
            user = TestMySql.username,
            password = TestMySql.password,
        )
        transaction(db) {
            MigrationUtils.statementsRequiredForDatabaseMigration(*allTables).shouldBeEmpty()
        }
    }
})
