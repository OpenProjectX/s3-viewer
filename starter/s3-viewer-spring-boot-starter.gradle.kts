plugins {
    kotlin("jvm")
}

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(17)) }
}

dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:${libs.versions.springBoot.get()}"))

    api(project(":s3-viewer-spring-boot-autoconfigure"))
    api("org.springframework.boot:spring-boot-starter-webflux")
}