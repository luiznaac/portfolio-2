package dev.agner.portfolio.integrationTest.config

import dev.agner.portfolio.persistence.migration.migrate
import io.kotest.core.extensions.Extension
import io.kotest.core.listeners.AfterProjectListener
import io.kotest.core.listeners.AfterTestListener
import io.kotest.core.listeners.BeforeProjectListener
import io.kotest.core.test.TestCase
import io.kotest.core.test.TestResult
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.testcontainers.containers.MySQLContainer
import java.net.ServerSocket

const val WIREMOCK_PORT_PROPERTY = "wiremock.port"

// One MySQL container and one app port per test JVM, so two concurrent suite runs never contend for
// a host port. The container is started before any spec and the coordinates it exposes are published
// as system properties — which outrank application.yaml — so DatabaseConfig, KtorConfig and
// AppHealthGateway follow the harness without a code change.
object MySqlContainerExtension : Extension, BeforeProjectListener, AfterProjectListener, AfterTestListener {

    override suspend fun beforeProject() {
        // Published first: the dynamic port must be known before any Spring context resolves
        // ${wiremock.port} from application-test.yaml.
        System.setProperty(WIREMOCK_PORT_PROPERTY, HttpMockService.port.toString())

        TestMySql.start()
        publishCoordinates()
        // The container starts empty — db/migration/V*.sql is applied by Flyway, not by an init
        // script — so migrate it to head before any spec boots the Spring context. The mapped host
        // port is the one this run's JDBC URL uses.
        migrate(
            host = TestMySql.host,
            port = TestMySql.mappedPort,
            user = TestMySql.username,
            password = TestMySql.password,
        )
    }

    override suspend fun afterProject() {
        TestMySql.stop()
    }

    override suspend fun afterTest(testCase: TestCase, result: TestResult) {
        // `index` stays: V1 seeds CDI/IPCA/SELIC and index_value/bond/checking_account carry a FK
        // to it, so truncating it would make later `insert ignore` writes silently drop.
        val database = TestMySql.databaseName
        withContext(Dispatchers.IO) {
            launch {
                TestMySql.execInContainer(
                    "bash",
                    "-c",
                    """
                        mysql -uroot -Nse 'show tables' $database | while read table; do
                           if [ "${'$'}table" != 'flyway_schema_history' ] && [ "${'$'}table" != 'index' ]; then
                               mysql -e "set FOREIGN_KEY_CHECKS=0; truncate table \`${'$'}table\`" $database;
                           fi
                        done
                    """.trimIndent(),
                ).exitCode shouldBe 0
            }
        }.join()
    }

    private fun publishCoordinates() {
        System.setProperty("mysql.host", TestMySql.host)
        System.setProperty("mysql.port", TestMySql.mappedPort.toString())
        System.setProperty("mysql.user", TestMySql.username)
        System.setProperty("mysql.password", TestMySql.password)
        System.setProperty("ktor.port", freePort().toString())
    }
}

// Mirrors backend/docker-compose.yml's image and database so the suite runs against the same
// MySQL the dev stack and the image do.
internal class MySqlTestContainer : MySQLContainer<MySqlTestContainer>("mysql:26.10.0") {
    init {
        withDatabaseName("portfolio")
        withUsername("root")
        withPassword("")
        // See src/test/resources/mysql-test-conf/my.cnf.
        withConfigurationOverride("mysql-test-conf")
    }
}

internal val TestMySql = MySqlTestContainer()

internal val MySqlTestContainer.mappedPort: Int
    get() = getMappedPort(MySQLContainer.MYSQL_PORT)

// Probe-then-rebind has a millisecond-wide TOCTOU window; Testcontainers itself relies on
// OS-assigned ports the same way. The OS ephemeral range is disjoint from the dev block range.
private fun freePort() = ServerSocket(0).use { it.localPort }
