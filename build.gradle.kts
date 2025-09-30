import io.gitlab.arturbosch.detekt.Detekt
import org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21

plugins {
	kotlin("jvm") version "2.1.21"
	kotlin("plugin.spring") version "1.9.25"
	id("io.gitlab.arturbosch.detekt") version "1.23.6"
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
	group = "dev.agner.portfolio"
	version = "1.0"

	apply(plugin = "kotlin")
	apply(plugin = "kotlin-spring")
	apply(plugin = "java-test-fixtures")
	apply(plugin = "io.gitlab.arturbosch.detekt")

	dependencies {
		detektPlugins("io.gitlab.arturbosch.detekt:detekt-formatting:1.23.6")
	}

	tasks.withType<Detekt> {
		parallel = true
		disableDefaultRuleSets = true
		buildUponDefaultConfig = true
		autoCorrect = true
		ignoreFailures = false
		setSource(files(projectDir))
		include("*/.kt", "*/.kts")
		config.setFrom(files("$rootDir/config/detekt/config.yml", "$rootDir/config/detekt/format.yml"))
		reports {
			xml.required.set(false)
			html.required.set(true)
		}
	}

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

	tasks.withType<Test> {
		useJUnitPlatform()
		testLogging {
			events("passed", "skipped", "failed")
		}
	}
}

repositories {
	mavenCentral()
}
