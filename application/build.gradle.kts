plugins {
    application
}

application {
    mainClass.set("dev.agner.portfolio.application.BootKt")
}

dependencies {
    implementation(project(":http-api"))
    implementation(project(":persistence"))
    implementation(project(":usecase"))

    implementation("org.springframework.boot:spring-boot-starter-webflux:3.5.0")
    implementation("ch.qos.logback:logback-classic:1.5.18")
}
