package org.openprojectx.s3.viewer.app

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication


@SpringBootApplication
class S3ViewerApplication

fun main(args: Array<String>) {

    runApplication<S3ViewerApplication>(*args)
}