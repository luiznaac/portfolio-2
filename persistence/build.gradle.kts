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
}
