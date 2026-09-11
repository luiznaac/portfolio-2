dependencies {
    implementation(project(":usecase"))

    implementation(libs.spring.context)
    implementation(libs.exposed.core)
    implementation(libs.exposed.dao)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.datetime)
    implementation(libs.exposed.json)
    implementation(libs.jackson.kotlin)
    implementation("com.mysql:mysql-connector-j:9.4.0")

    // Schema migrations. Exposed's migration module only *generates* and *diffs* SQL (see
    // migration/MigrationScripts.kt and the MigrationSchemaTest guard); Flyway is what actually
    // applies the V*.sql files under db/migration.
    implementation(libs.exposed.migration.core)
    implementation(libs.exposed.migration.jdbc)
    implementation(libs.flyway.core)
    implementation(libs.flyway.mysql)
}

// Authoring half of the migration workflow (backend/AGENTS.md). Diffs the Exposed tables in
// migration/MigrationScripts.kt against a local database into src/main/resources/db/migration/.
//
//   docker compose -f docker-compose.yml up -d mysql
//   ./gradlew :persistence:generateMigrationScript -Pname=V2__add_something
tasks.register<JavaExec>("generateMigrationScript") {
    group = "database"
    description = "Diff the Exposed tables against the local database into db/migration/<name>.sql"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("dev.agner.portfolio.persistence.migration.MigrationScriptsKt")
    systemProperty("migration.name", providers.gradleProperty("name").getOrElse(""))
}

// Applies db/migration/V*.sql to the local database — the same entry point deploy/entrypoint.sh
// runs in the container, so what you get locally is what production gets.
tasks.register<JavaExec>("migrate") {
    group = "database"
    description = "Apply pending migrations to the local database"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("dev.agner.portfolio.persistence.migration.MigratorKt")
}
