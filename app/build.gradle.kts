plugins {
    id("buildsrc.convention.spring-kotlin")
    id("com.google.cloud.tools.jib") version "3.4.5"
    id("org.openprojectx.bigdata-test") version "0.1.26"
}

bigDataTest {
    config.add("classpath:spring-bigdata-test.toml")
    extensionConfig.add("classpath:spring-bigdata-extensions.toml")
    autoConfigureTestTasks = false
    autoConfigureJavaExecTasks = false
}

tasks.matching { it.name == "bigDataTest" || it.name.startsWith("bigDataTest") }.configureEach {
    enabled = false
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
    testImplementation("org.apache.avro:avro:1.12.1")
    testImplementation("org.apache.parquet:parquet-avro:1.17.1")

    testImplementation("com.microsoft.playwright:playwright:${libs.versions.playwright.get()}")

}

jib {
    from {
        image = providers.gradleProperty("jibFromImage")
            .orElse("eclipse-temurin:17-jre")
            .get()
    }
    to {
        image = providers.gradleProperty("jibToImage")
            .orElse(providers.environmentVariable("JIB_TO_IMAGE"))
            .orElse("ghcr.io/openprojectx/s3-viewer")
            .get()
        tags = setOf(project.version.toString())

        val username = providers.gradleProperty("jibToUsername")
            .orElse(providers.environmentVariable("JIB_TO_USERNAME"))
            .orNull
        val password = providers.gradleProperty("jibToPassword")
            .orElse(providers.environmentVariable("JIB_TO_PASSWORD"))
            .orNull

        if (!username.isNullOrBlank() && !password.isNullOrBlank()) {
            auth {
                this.username = username
                this.password = password
            }
        }
    }
    container {
        ports = listOf("8081")
        creationTime = "USE_CURRENT_TIMESTAMP"
    }
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
