dependencies {
    implementation(project(":usecase"))

    implementation(libs.spring.webflux)
    implementation(libs.bundles.dependencies)
    implementation(libs.kotlinx.coroutines.reactor)
    implementation(libs.kotlinx.coroutines.reactive)
}
