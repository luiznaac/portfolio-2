dependencies {
    implementation(project(":usecase"))

    implementation(libs.logback.classic)
    implementation(libs.spring.context)
    implementation(libs.bundles.dependencies)
    implementation(libs.kotlin.reflect)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.jackson)
    implementation("io.ktor:ktor-client-logging:3.2.3")
}
