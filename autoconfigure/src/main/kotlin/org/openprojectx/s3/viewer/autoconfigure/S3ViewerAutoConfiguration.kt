package org.openprojectx.s3.viewer.autoconfigure

import org.openprojectx.s3.viewer.core.S3ViewerService
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean

@AutoConfiguration
@EnableConfigurationProperties(S3ViewerProperties::class)
class S3ViewerAutoConfiguration {
    @Bean
    fun s3ViewerService(properties: S3ViewerProperties): S3ViewerService = DefaultS3ViewerService(properties)
}
