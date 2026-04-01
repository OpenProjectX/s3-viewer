plugins {
    id("buildsrc.convention.spring-kotlin")
}

dependencies {
    implementation(project(":s3-viewer-spring-boot-starter"))

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.projectreactor:reactor-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")

    testImplementation(platform("org.testcontainers:testcontainers-bom:${libs.versions.testcontainers.get()}"))
    testImplementation("org.testcontainers:localstack")
    testImplementation("org.testcontainers:junit-jupiter")

    // AWS SDK for seeding test data into LocalStack
    testImplementation(platform("software.amazon.awssdk:bom:2.39.2"))
    testImplementation("software.amazon.awssdk:s3")
    testImplementation("software.amazon.awssdk:auth")

    testImplementation("com.microsoft.playwright:playwright:${libs.versions.playwright.get()}")
}

// Standard integration tests (API-level, no browser required)
tasks.named<Test>("test") {
    useJUnitPlatform {
        excludeTags("e2e")
    }
}

// Browser E2E tests — run separately so CI can skip if browsers aren't installed
tasks.register<Test>("e2eTest") {
    description = "Runs Playwright browser end-to-end tests."
    group = "verification"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform {
        includeTags("e2e")
    }
    // Install Playwright browsers before running
    dependsOn("playwrightInstall")
}

tasks.register<JavaExec>("playwrightInstall") {
    description = "Downloads Playwright browser binaries."
    group = "verification"
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.microsoft.playwright.CLI")
    args = listOf("install", "--with-deps", "chromium")
}
