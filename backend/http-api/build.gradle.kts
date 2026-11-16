// The committed acceptance artifacts are read from the repo's docs/ tree rather than copied into
// test resources, so the parser tests pin the exact files the acceptance runbook uploads.
val docsDir = rootProject.projectDir.parentFile.resolve("docs")

tasks.withType<Test> {
    inputs.file(docsDir.resolve("acceptance/fixtures/b3-negociacao-2020.xlsx"))
    inputs.file(docsDir.resolve("acceptance/fixtures/posicao-custodia-2020.xlsx"))
    inputs.file(docsDir.resolve("acceptance/fixtures/xp-relatorio-2026-10.pdf"))
    systemProperty("docsDir", docsDir.absolutePath)
}

dependencies {
    implementation(project(":usecase"))

    implementation(libs.bundles.dependencies)

    implementation(libs.ktor.core)
    implementation(libs.ktor.netty)
    implementation(libs.ktor.compression)
    implementation(libs.ktor.cors)
    implementation(libs.ktor.status.pages)
    implementation(libs.ktor.content.negotiation)
    implementation(libs.ktor.jackson)
    implementation(libs.apache.poi)
    implementation("org.apache.pdfbox:pdfbox:3.0.8")

    implementation(libs.opentelemetry.api)
    implementation(libs.opentelemetry.ktor)

    testImplementation(libs.bundles.testDependencies)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.opentelemetry.sdk.testing)
    testImplementation(testFixtures(project(":usecase")))
}
