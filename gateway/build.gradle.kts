dependencies {
    implementation(libs.logback.classic)
    implementation(libs.spring.context)
    implementation(libs.spring.webflux)
    implementation(libs.kotlinx.coroutines.reactor)
    implementation(libs.kotlinx.coroutines.reactive)
    implementation(libs.bundles.dependencies)
    implementation(libs.jackson.kotlin)
    implementation(libs.kotlin.reflect)
    implementation(libs.reactor.netty)
    implementation(libs.reactor.extensions)

    implementation(project(":usecase"))
}
