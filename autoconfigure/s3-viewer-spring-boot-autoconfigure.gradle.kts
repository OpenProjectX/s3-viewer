plugins {
    id("buildsrc.convention.kotlin-jvm")
    `kotlin-kapt`
}


dependencies {

    api(project(":core"))

    val bootBom = platform("org.springframework.boot:spring-boot-dependencies:${libs.versions.springBoot.get()}")
    implementation(bootBom)
    kapt(bootBom)

    implementation("org.springframework.boot:spring-boot-autoconfigure")
    api("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation(libs.awsJavaNioS3)

    implementation(platform("software.amazon.awssdk:bom:2.39.2"))
    implementation("software.amazon.awssdk:auth")
    implementation("software.amazon.awssdk:netty-nio-client")
    implementation("software.amazon.awssdk:s3")

    kapt("org.springframework.boot:spring-boot-configuration-processor")



}
