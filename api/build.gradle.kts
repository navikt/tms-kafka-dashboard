import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    alias(managed.plugins.kotlin)
    alias(managed.plugins.tms.jarBundling)
    application
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}


repositories {
    mavenCentral()
    maven("https://packages.confluent.io/maven")
    maven {
        url = uri("https://github-package-registry-mirror.gc.nav.no/cached/maven-release")
    }
    mavenLocal()
}

dependencies {
    implementation(libs.ulId.creater)

    implementation(managed.bundles.flyway)
    implementation(managed.jackson.datatypeJsr310)
    implementation(managed.kafka.clients)
    implementation(managed.hikariCp)
    implementation(managed.kotlinLogging)
    implementation(managed.ktor.server.auth)
    implementation(managed.ktor.server.contentNegotiation)
    implementation(managed.ktor.server.core)
    implementation(managed.ktor.server.cors)
    implementation(managed.ktor.server.netty)
    implementation(managed.kotliQuery)
    implementation(managed.ktor.server.statusPages)
    implementation(managed.ktor.serialization.jackson)
    implementation(managed.postgresql)
    implementation(managed.tms.commonLib.metrics)
    implementation(managed.tms.commonLib.postgres)
    implementation(managed.tms.commonLib.utils)
    implementation(managed.tms.ktorTokenSupport.entraId.verification)
    implementation(managed.tms.ktorTokenSupport.entraId.verificationMock)

    testImplementation(managed.junit.launcher)
    testImplementation(managed.junit.api)
    testImplementation(managed.junit.engine)
    testImplementation(managed.kotest.assertion)
}

application {
    mainClass.set("no.nav.tms.kafka.dashboard.ApplicationKt")
}

tasks {
    withType<Test> {
        useJUnitPlatform()
        testLogging {
            exceptionFormat = TestExceptionFormat.FULL
            events("passed", "skipped", "failed")
        }
    }
}
