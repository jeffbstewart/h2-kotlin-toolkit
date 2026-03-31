plugins {
    kotlin("jvm") version "2.3.10"
    `java-library`
    `maven-publish`
}

group = "net.stewart"
version = "0.1.0"

repositories {
    mavenCentral()
}

dependencies {
    api("com.zaxxer:HikariCP:5.1.0")
    api("com.h2database:h2:2.4.240")
    api("org.flywaydb:flyway-core:10.15.2")
    api("org.slf4j:slf4j-api:2.0.17")

    // JDBI for schema updater tracking (lightweight, no ORM opinion)
    implementation("org.jdbi:jdbi3-core:3.49.4")

    testImplementation(kotlin("test"))
    testImplementation("org.slf4j:slf4j-simple:2.0.17")
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
    withSourcesJar()
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}
