package org.openprojectx.s3.viewer.autoconfigure

import org.openprojectx.s3.viewer.autoconfigure.api.ApiExceptionHandler
import org.openprojectx.s3.viewer.autoconfigure.api.ViewerController
import org.openprojectx.s3.viewer.core.S3ViewerService
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type.REACTIVE
import org.springframework.context.annotation.Bean
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.server.RouterFunction
import org.springframework.web.reactive.function.server.RouterFunctions
import org.springframework.web.reactive.function.server.ServerResponse

@AutoConfiguration(after = [S3ViewerAutoConfiguration::class])
@ConditionalOnWebApplication(type = REACTIVE)
class S3ViewerWebAutoConfiguration {

    @Bean
    fun viewerController(s3ViewerService: S3ViewerService): ViewerController =
        ViewerController(s3ViewerService)

    @Bean
    fun apiExceptionHandler(): ApiExceptionHandler = ApiExceptionHandler()

    /**
     * Serves /s3-viewer/ui/config.js — a small JS file that writes
     * window.__S3_VIEWER_CONFIG__ so the React app knows the API base URL at runtime.
     * This lets deployments behind a reverse proxy configure the path without rebuilding the UI.
     */
    @Bean
    fun uiConfigRouter(properties: S3ViewerProperties): RouterFunction<ServerResponse> =
        RouterFunctions.route(
            org.springframework.web.reactive.function.server.RequestPredicates.GET("/s3-viewer/ui/config.js")
        ) { _ ->
            val js =
                "window.__S3_VIEWER_CONFIG__ = { apiBase: '${properties.ui.apiBasePath}/v1', readOnlyAccess: ${properties.readOnlyAccess} };"
            ServerResponse.ok()
                .contentType(MediaType.parseMediaType("application/javascript"))
                .bodyValue(js)
        }
}
