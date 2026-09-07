package dev.agner.portfolio.persistence.configuration

import org.jetbrains.exposed.v1.jdbc.Database
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

const val DATABASE_NAME = "portfolio"

// Shared by the Exposed connection below and by the Flyway migrator (persistence/migration/), which
// runs in its own process before the app boots and so cannot read the Spring context. One
// definition, so the two can't drift apart.
fun mysqlJdbcUrl(host: String) = "jdbc:mysql://$host:3306/$DATABASE_NAME"

@Configuration
class DatabaseConfig {

    @Bean
    fun databaseConnection(
        @Value("\${mysql.host}") host: String,
        @Value("\${mysql.user}") user: String,
        @Value("\${mysql.password}") password: String,
    ) = Database.connect(
        url = mysqlJdbcUrl(host),
        driver = "com.mysql.cj.jdbc.Driver",
        user = user,
        password = password,
    )
}
