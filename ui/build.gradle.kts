import com.github.gradle.node.yarn.task.YarnTask

plugins {
    alias(libs.plugins.node.gradle)
}

node {
    version.set("24.0.2")
    yarnVersion.set("1.22.22")
    download.set(true)
    nodeProjectDir.set(projectDir)
}

tasks.register<YarnTask>("yarnInstall") {
    args.set(listOf("install"))
}

tasks.register<YarnTask>("yarnDev") {
    dependsOn("yarnInstall")
    args.set(listOf("dev", "--host", "0.0.0.0"))
}

tasks.register<YarnTask>("yarnBuild") {
    dependsOn("yarnInstall")
    args.set(listOf("build"))
}
