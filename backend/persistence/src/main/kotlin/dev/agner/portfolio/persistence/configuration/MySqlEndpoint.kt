package dev.agner.portfolio.persistence.configuration

// portfolio-2's assigned mysql role in the family port block (api 8110, web 8111, mysql 8112,
// frontend-dev 8113). The Spring path reads `${mysql.port}` from application.yaml; a standalone
// run (the Flyway `main`, the integration harness) resolves the environment with this fallback.
const val DEFAULT_MYSQL_PORT = 8112

internal fun resolveMysqlPort(raw: String?) = raw?.toIntOrNull() ?: DEFAULT_MYSQL_PORT

// The one place a JDBC URL is built, so the port a run resolves is the port it connects to.
fun mysqlJdbcUrl(host: String, port: Int, database: String) = "jdbc:mysql://$host:$port/$database"
