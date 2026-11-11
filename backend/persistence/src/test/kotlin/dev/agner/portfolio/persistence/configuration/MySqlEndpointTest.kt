package dev.agner.portfolio.persistence.configuration

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class MySqlEndpointTest : StringSpec({

    "the canonical fallback is the assigned block's mysql role" {
        DEFAULT_MYSQL_PORT shouldBe 8112
    }

    "resolves the configured port and falls back to the canonical one" {
        resolveMysqlPort("8112") shouldBe 8112
        resolveMysqlPort("3307") shouldBe 3307
        resolveMysqlPort(null) shouldBe DEFAULT_MYSQL_PORT
        resolveMysqlPort("") shouldBe DEFAULT_MYSQL_PORT
        resolveMysqlPort("not-a-number") shouldBe DEFAULT_MYSQL_PORT
    }

    "builds one JDBC URL from the resolved host, port and database" {
        mysqlJdbcUrl(host = "db", port = 8112, database = "portfolio") shouldBe
            "jdbc:mysql://db:8112/portfolio"
    }
})
