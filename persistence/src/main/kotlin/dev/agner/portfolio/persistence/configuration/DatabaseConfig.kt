package dev.agner.portfolio.persistence.configuration

import org.jetbrains.exposed.v1.jdbc.Database
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class DatabaseConfig {

    @Bean
    fun databaseConnection() = Database.connect(
        url = "jdbc:mysql://localhost:3306/portfolio",
        driver = "com.mysql.cj.jdbc.Driver",
        user = "root",
    )
}
