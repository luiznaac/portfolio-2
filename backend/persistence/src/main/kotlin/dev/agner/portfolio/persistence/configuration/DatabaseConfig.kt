package dev.agner.portfolio.persistence.configuration

import org.jetbrains.exposed.v1.jdbc.Database
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

const val DATABASE_NAME = "portfolio"

@Configuration
class DatabaseConfig {

    @Bean
    fun databaseConnection(
        @Value("\${mysql.host}") host: String,
        @Value("\${mysql.port}") port: Int,
        @Value("\${mysql.user}") user: String,
        @Value("\${mysql.password}") password: String,
    ) = Database.connect(
        url = mysqlJdbcUrl(host = host, port = port, database = DATABASE_NAME),
        driver = "com.mysql.cj.jdbc.Driver",
        user = user,
        password = password,
    )
}
