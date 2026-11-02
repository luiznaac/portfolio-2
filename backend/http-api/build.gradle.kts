// The committed acceptance artifact is read from the repo's docs/ tree rather than copied into
// test resources, so the parser test pins the exact file the acceptance runbook uploads.
val docsDir = rootProject.projectDir.parentFile.resolve("docs")

tasks.withType<Test> {
    inputs.file(docsDir.resolve("acceptance/fixtures/b3-negociacao-2020.xlsx"))
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

    testImplementation(libs.bundles.testDependencies)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(testFixtures(project(":usecase")))
}
