plugins {
    id("buildsrc.convention.kotlin-jvm")
    id("buildsrc.convention.spring-kotlin")
    id("org.openapi.generator") version "7.20.0"

}


dependencies {

    implementation(project(":s3-viewer-spring-boot-starter"))

}