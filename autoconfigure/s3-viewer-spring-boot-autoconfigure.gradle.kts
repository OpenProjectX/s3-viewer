import org.openapitools.generator.gradle.plugin.tasks.GenerateTask

plugins {
    id("buildsrc.convention.kotlin-jvm")
    `kotlin-kapt`
    id("org.openapi.generator") version "7.20.0"
    kotlin("plugin.spring")
}

val openApiGenerateTask = tasks.named("openApiGenerate")

openApiGenerate {
    generatorName.set("spring")
    inputSpec.set("$projectDir/src/main/resources/openapi/api.yaml")
    apiPackage.set("org.openprojectx.s3.viewer.autoconfigure.api")
    modelPackage.set("org.openprojectx.s3.viewer.autoconfigure.model")
    invokerPackage.set("org.openprojectx.s3.viewer.autoconfigure.invoker")
    configOptions.set(
        mapOf(
            "useTags" to "true",
            "interfaceOnly" to "true",
            "reactive" to "true",
            "useSpringBoot4" to "true",
            "useJakartaEe" to "true",
            "dateLibrary" to "java8"
        )
    )
}

sourceSets {
    main {
        java {
            srcDir(openApiGenerateTask.map {
                layout.buildDirectory.dir("generate-resources/main/src/main/java").get()
            })
        }
        kotlin {
            srcDir(openApiGenerateTask.map {
                layout.buildDirectory.dir("generate-resources/main/src/main/kotlin").get()
            })
        }
    }
}

tasks.named<ProcessResources>("processResources") {
    val uiProject = project(":ui")
    dependsOn(uiProject.tasks.named("yarnBuild"))
    from(uiProject.layout.projectDirectory.dir("dist")) {
        into("static/s3-viewer/ui")
    }
}

dependencies {
    api(project(":core"))

    val bootBom = platform("org.springframework.boot:spring-boot-dependencies:${libs.versions.springBoot.get()}")
    implementation(bootBom)
    kapt(bootBom)

    implementation("org.springframework.boot:spring-boot-autoconfigure")
    api("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework:spring-aop")
    implementation("org.aspectj:aspectjweaver")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springdoc:springdoc-openapi-starter-webflux-ui:3.0.2")
    implementation("org.openapitools:jackson-databind-nullable:0.2.10")
    implementation(libs.awsJavaNioS3)
    implementation("org.apache.parquet:parquet-hadoop:1.16.0")

    implementation(platform("software.amazon.awssdk:bom:2.39.2"))
    implementation("software.amazon.awssdk:auth")
    implementation("software.amazon.awssdk:netty-nio-client")
    implementation("software.amazon.awssdk:s3")

    kapt("org.springframework.boot:spring-boot-configuration-processor")
}
