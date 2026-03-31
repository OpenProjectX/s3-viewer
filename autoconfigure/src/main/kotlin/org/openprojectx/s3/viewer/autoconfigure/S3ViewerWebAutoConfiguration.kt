package org.openprojectx.s3.viewer.autoconfigure

import org.openprojectx.s3.viewer.autoconfigure.api.ApiExceptionHandler
import org.openprojectx.s3.viewer.autoconfigure.api.ViewerController
import org.openprojectx.s3.viewer.core.S3ViewerService
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type.REACTIVE
import org.springframework.context.annotation.Bean

@AutoConfiguration(after = [S3ViewerAutoConfiguration::class])
@ConditionalOnWebApplication(type = REACTIVE)
class S3ViewerWebAutoConfiguration {

    @Bean
    fun viewerController(s3ViewerService: S3ViewerService): ViewerController =
        ViewerController(s3ViewerService)

    @Bean
    fun apiExceptionHandler(): ApiExceptionHandler = ApiExceptionHandler()
}
