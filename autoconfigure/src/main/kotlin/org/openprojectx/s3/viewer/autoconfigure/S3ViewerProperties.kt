package org.openprojectx.s3.viewer.autoconfigure

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

@Validated
@ConfigurationProperties("s3-viewer")
data class S3ViewerProperties(
    @field:Valid
    val providers: List<Provider> = emptyList()
) {
    data class Provider(
        @field:NotBlank
        val id: String,
        @field:NotBlank
        val name: String,
        @field:NotBlank
        val endpoint: String,
        @field:NotBlank
        val region: String,
        @field:NotBlank
        val accessKey: String,
        @field:NotBlank
        val secretKey: String,
        val pathStyleAccess: Boolean = true,
        val buckets: List<String> = emptyList()
    )
}
