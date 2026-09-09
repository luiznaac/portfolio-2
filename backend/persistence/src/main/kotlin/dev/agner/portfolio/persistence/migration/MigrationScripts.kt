package dev.agner.portfolio.persistence.migration

import dev.agner.portfolio.persistence.allocation.AssetClassTargetTable
import dev.agner.portfolio.persistence.allocation.CapitalSnapshotTable
import dev.agner.portfolio.persistence.allocation.FixedIncomeSubClassTargetTable
import dev.agner.portfolio.persistence.allocation.ProductClassificationTable
import dev.agner.portfolio.persistence.attribution.AttributionMovementTable
import dev.agner.portfolio.persistence.bond.BondOrderPositionTable
import dev.agner.portfolio.persistence.bond.BondOrderStatementTable
import dev.agner.portfolio.persistence.bond.BondOrderTable
import dev.agner.portfolio.persistence.bond.BondTable
import dev.agner.portfolio.persistence.checkingaccount.CheckingAccountTable
import dev.agner.portfolio.persistence.configuration.mysqlJdbcUrl
import dev.agner.portfolio.persistence.corporateaction.CorporateActionTable
import dev.agner.portfolio.persistence.index.IndexTable
import dev.agner.portfolio.persistence.index.IndexValueTable
import dev.agner.portfolio.persistence.listedasset.ListedAssetPositionTable
import dev.agner.portfolio.persistence.listedasset.ListedAssetTable
import dev.agner.portfolio.persistence.listedasset.ListedAssetTickerHistoryTable
import dev.agner.portfolio.persistence.listedasset.TickerCatalogTable
import dev.agner.portfolio.persistence.strategy.StrategyEditionTable
import dev.agner.portfolio.persistence.strategy.StrategyTable
import dev.agner.portfolio.persistence.strategy.StrategyTargetTable
import dev.agner.portfolio.persistence.strategy.StrategyWeightTable
import dev.agner.portfolio.persistence.trade.TradeTable
import org.jetbrains.exposed.v1.core.ExperimentalDatabaseMigrationApi
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.migration.jdbc.MigrationUtils

const val MIGRATIONS_DIRECTORY = "src/main/resources/db/migration"

// Every table this service maps. Single source for the two things that need the whole set:
// MigrationSchemaTest (which diffs it against a migrated database) and this file's own
// `generateMigrationScript` entry point. A new *Table object must be added here.
val allTables: Array<Table> = arrayOf(
    IndexTable,
    IndexValueTable,
    CheckingAccountTable,
    BondTable,
    BondOrderTable,
    BondOrderStatementTable,
    BondOrderPositionTable,
    ListedAssetTable,
    ListedAssetTickerHistoryTable,
    TradeTable,
    CorporateActionTable,
    ListedAssetPositionTable,
    TickerCatalogTable,
    CapitalSnapshotTable,
    AssetClassTargetTable,
    FixedIncomeSubClassTargetTable,
    ProductClassificationTable,
    StrategyTable,
    StrategyEditionTable,
    StrategyTargetTable,
    StrategyWeightTable,
    AttributionMovementTable,
)

// Authoring half of the migration workflow (backend/CLAUDE.md §7). Diffs `allTables` against a
// local database already migrated to head and writes the SQL that closes the gap into
// db/migration/. Exposed only *generates* — Flyway applies. Always read the output before
// committing it: the diff is mechanical and won't, for instance, know that a rename is a rename
// rather than a drop plus an add.
@OptIn(ExperimentalDatabaseMigrationApi::class)
fun main() {
    val name = System.getProperty("migration.name").orEmpty()
    require(name.isNotBlank()) { "pass the migration name: ./gradlew generateMigrationScript -Pname=V2__add_x" }

    val mysql = MysqlEnv.fromEnvironment()
    Database.connect(
        url = mysqlJdbcUrl(mysql.host),
        driver = "com.mysql.cj.jdbc.Driver",
        user = mysql.user,
        password = mysql.password,
    )

    val script = transaction {
        MigrationUtils.generateMigrationScript(
            *allTables,
            scriptDirectory = MIGRATIONS_DIRECTORY,
            scriptName = name,
        )
    }
    println("wrote ${script.absolutePath}")
}
