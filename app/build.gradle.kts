import org.openapitools.generator.gradle.plugin.tasks.GenerateTask

plugins {
    id("buildsrc.convention.kotlin-jvm")
    id("buildsrc.convention.spring-kotlin")
    id("org.openapi.generator") version "7.20.0"
}

val copyUiDist = tasks.register<Copy>("copyUiDist") {
    val uiProject = project(":ui")
    dependsOn(uiProject.tasks.named("yarnBuild"))
    from(uiProject.layout.projectDirectory.dir("dist"))
    into(layout.buildDirectory.dir("resources/main/static"))
}

tasks.named("processResources") {
    dependsOn(copyUiDist)
}



//val openApiOutputDir = layout.buildDirectory.dir("generated/openapi")

//tasks.named<GenerateTask>("openApiGenerate") {
//    generatorName.set("kotlin-spring")
//    inputSpec.set("$projectDir/src/main/resources/openapi/api.yaml")
////    outputDir.set(openApiOutputDir.get().asFile.absolutePath)
//    logger.info("inner group is $group")
//    apiPackage.set("${group}.api")
//    modelPackage.set("${group}.model")
//    invokerPackage.set("${group}.invoker")
//
////    configOptions.set(
////        mapOf(
////            "interfaceOnly" to "true",
////            "useTags" to "true",
////            "reactive" to "true",
////            "useSpringBoot4" to "true",
////            "dateLibrary" to "java8",
//////            "skipDefaultInterface" to "true",
//////            "serializableModel" to "true",
////            "useJakartaEe" to "true"
////
////        )
////    )
//
//    configOptions.set(
//        mapOf(
//            "interfaceOnly" to "true",
//            "reactive" to "true",
//            "useSpringBoot4" to "true",
//            "dateLibrary" to "java8"
//        )
//    )
//    generateApiTests.set(false)
//    generateModelTests.set(false)
//}

openApiGenerate {

    generatorName.set("spring")

    inputSpec.set("$projectDir/src/main/resources/openapi/api.yaml")

//    outputDir.set("$buildDir/generated")

    apiPackage.set("${group}.${project.name}.api")

    modelPackage.set("${group}.${project.name}.model")

    invokerPackage.set("${group}.${project.name}.invoker")

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
//    named("main") {
//        java.srcDir(openApiOutputDir.map { it.dir("src/main/java") })
//        kotlin.srcDir(openApiOutputDir.map { it.dir("src/main/kotlin") })
//    }

    main {

        java {
            srcDir(layout.buildDirectory.dir("generate-resources/main/src/main/java"))
        }

        kotlin {
            srcDir(layout.buildDirectory.dir("generate-resources/main/src/main/kotlin"))

        }
    }
}

tasks.named("compileJava") {
    dependsOn(tasks.named("openApiGenerate"))
}

tasks.named("compileKotlin") {
    dependsOn(tasks.named("openApiGenerate"))
}

dependencies {

    implementation(project(":s3-viewer-spring-boot-starter"))
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springdoc:springdoc-openapi-starter-webflux-ui:3.0.2")
    implementation("org.openapitools:jackson-databind-nullable:0.2.10")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.projectreactor:reactor-test")


}
