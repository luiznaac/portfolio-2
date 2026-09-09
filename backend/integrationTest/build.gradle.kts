dependencies {
    testImplementation(project(":application"))
    // Reaches KtorConfig etc. transitively via :application already, but MigrationSchemaTest
    // needs persistence's own migrate()/allTables directly — depend on it explicitly rather
    // than lean on the transitive edge.
    testImplementation(project(":persistence"))
    testImplementation(project(":http-api"))
    testImplementation(project(":gateway"))
    testImplementation(project(":usecase"))
    testImplementation(libs.bundles.testDependencies)
    testImplementation(libs.kotest.extensions.spring)
    testImplementation(libs.ktor.client.core)
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.testcontainers)
    testImplementation(libs.wiremock)
    implementation(libs.jackson.kotlin)

    // persistence declares these as `implementation`, so they don't come along transitively;
    // MigrationSchemaTest calls Database.connect/transaction/MigrationUtils directly.
    testImplementation(libs.exposed.core)
    testImplementation(libs.exposed.jdbc)
    testImplementation(libs.exposed.migration.core)
    testImplementation(libs.exposed.migration.jdbc)
}
