import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.9.21"
    kotlin("plugin.spring") version "1.9.21"
    id("org.springframework.boot") version "3.2.1"
    id("io.spring.dependency-management") version "1.1.4"
    application
}

group = "com.wokstym"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.jenetics:jenetics:7.2.0")


    testImplementation(kotlin("test"))
    implementation("com.google.ortools:ortools-java:9.7.2996")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("net.dv8tion:JDA:5.0.0-beta.12")
    testImplementation("io.kotest:kotest-assertions-core:5.8.0")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.7.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.7.0")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("io.arrow-kt:arrow-core:1.1.3")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    implementation("org.springframework.boot:spring-boot-starter-web")


    implementation("com.google.code.gson:gson:2.10")

}

java {
    sourceCompatibility = JavaVersion.VERSION_17
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs += "-Xjsr305=strict"
        jvmTarget = "17"
    }
}

tasks.test {
    useJUnitPlatform()
}

application {
    mainClass.set("MainKt")
}
