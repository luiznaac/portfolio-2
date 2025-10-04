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
    implementation("org.apache.pdfbox:pdfbox:3.0.5")

    testImplementation(libs.bundles.testDependencies)
}
