package dev.agner.portfolio.persistence.migration

import dev.agner.portfolio.persistence.configuration.resolveMysqlPort

// Connection details for the migration tooling, which runs outside the Spring context and so
// can't read application.yaml. Defaults mirror `backend/docker-compose.yml up -d mysql`.
internal data class MysqlEnv(
    val host: String,
    val port: Int,
    val user: String,
    val password: String,
) {
    companion object {
        fun fromEnvironment() = MysqlEnv(
            host = System.getenv("MYSQL_HOST") ?: "localhost",
            port = resolveMysqlPort(System.getenv("MYSQL_PORT")),
            user = System.getenv("MYSQL_USER") ?: "root",
            password = System.getenv("MYSQL_PASSWORD") ?: "",
        )
    }
}
