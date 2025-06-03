import org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21

plugins {
	kotlin("jvm") version "2.1.21"
	kotlin("plugin.spring") version "1.9.25"
	id("org.springframework.boot") version "3.5.0"
	id("io.spring.dependency-management") version "1.1.7"
}

allprojects {
	buildscript {
		repositories {
			gradlePluginPortal()
			mavenCentral()
		}
	}

	repositories {
		mavenCentral()
	}
}

subprojects {
	group = "dev.agner"
	version = "1.0"

	apply(plugin = "kotlin")
	apply(plugin = "kotlin-spring")

	kotlin {
		compilerOptions {
			jvmTarget.set(JVM_21)
			allWarningsAsErrors.set(true)
			freeCompilerArgs.add("-Xjvm-default=all")
		}
	}

	java {
		toolchain {
			languageVersion = JavaLanguageVersion.of(21)
		}
	}
}

repositories {
	mavenCentral()
}

tasks.withType<Test> {
	useJUnitPlatform()
}
