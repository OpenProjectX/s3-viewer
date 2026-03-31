plugins {
    id("buildsrc.convention.spring-kotlin")
}

dependencies {
    implementation(project(":s3-viewer-spring-boot-starter"))

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.projectreactor:reactor-test")
}
