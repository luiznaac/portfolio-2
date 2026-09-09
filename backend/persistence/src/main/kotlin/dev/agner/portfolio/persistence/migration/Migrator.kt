package dev.agner.portfolio.persistence.migration

import dev.agner.portfolio.persistence.configuration.mysqlJdbcUrl
import org.flywaydb.core.Flyway

const val MIGRATIONS_LOCATION = "classpath:db/migration"

// Applies every pending db/migration/V*.sql. Called from deploy/entrypoint.sh before the app
// starts, and from MigrationSchemaTest — never from the Spring context (KtorConfig blocks the
// main thread for the process's entire lifetime, so a Spring-context hook would never run before
// the server starts serving traffic anyway). A failed migration aborts the container instead of
// leaving a half-migrated app answering requests.
fun migrate(host: String, user: String, password: String) {
    Flyway.configure()
        .dataSource(mysqlJdbcUrl(host), user, password)
        .locations(MIGRATIONS_LOCATION)
        // A database that predates migrations is stamped at V1 rather than having it re-applied;
        // an empty schema just runs V1 normally.
        .baselineOnMigrate(true)
        .baselineVersion("1")
        .load()
        .migrate()
}

fun main() {
    val mysql = MysqlEnv.fromEnvironment()
    migrate(mysql.host, mysql.user, mysql.password)
}
