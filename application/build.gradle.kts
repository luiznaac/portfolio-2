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

    implementation(libs.spring.webflux)
}
