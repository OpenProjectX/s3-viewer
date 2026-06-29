package org.openprojectx.s3.viewer.autoconfigure

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

@Validated
@ConfigurationProperties("s3-viewer")
data class S3ViewerProperties(
    @field:Valid
    val providers: List<Provider> = emptyList(),
    val readOnlyAccess: Boolean = false,
    val ui: UiProperties = UiProperties(),
    val security: SecurityProperties = SecurityProperties()
) {
    data class UiProperties(
        /**
         * Base path of the S3 Viewer REST API, without a trailing slash.
         * Override this when the application is deployed behind a reverse proxy that does not
         * strip the context prefix (e.g. set to "/prod/service/s3-viewer/api").
         * Default matches the out-of-the-box Spring Boot path.
         */
        val apiBasePath: String = "/s3-viewer/api"
    )

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
        val allBuckets: Boolean = false,
        val buckets: List<String> = emptyList()
    ) {
        fun allowsAllBuckets(): Boolean = allBuckets || buckets.isEmpty()
    }

    data class SecurityProperties(
        val enabled: Boolean = false,
        val ldap: LdapProperties = LdapProperties(),
        val rbac: RbacProperties = RbacProperties()
    )

    data class LdapProperties(
        val authenticationMode: LdapAuthenticationMode = LdapAuthenticationMode.SEARCH,
        val userSearchBase: String = "",
        val userSearchFilter: String = "(&(objectClass=user)(sAMAccountName={0}))",
        val userDnPatterns: List<String> = emptyList(),
        val memberOfAttribute: String = "memberOf",
        val rolePrefix: String = "ROLE_",
        val roleMappings: Map<String, List<String>> = emptyMap()
    )

    enum class LdapAuthenticationMode {
        SEARCH,
        DIRECT_BIND
    }

    data class RbacProperties(
        val enabled: Boolean = true,
        val rules: List<AccessRule> = defaultAccessRules()
    )

    data class AccessRule(
        val path: String,
        val methods: List<String> = emptyList(),
        val roles: List<String> = emptyList()
    )

    companion object {
        private fun defaultAccessRules(): List<AccessRule> =
            listOf(
                AccessRule(
                    "/s3-viewer/api/v1/providers/{providerId}/buckets/{bucketName}/upload",
                    listOf("POST"),
                    listOf("S3_VIEWER_ADMIN", "ADMIN")
                ),
                AccessRule(
                    "/s3-viewer/api/v1/providers/{providerId}/buckets/{bucketName}/folders",
                    listOf("POST"),
                    listOf("S3_VIEWER_ADMIN", "ADMIN")
                ),
                AccessRule(
                    "/s3-viewer/api/v1/providers/{providerId}/buckets/{bucketName}/objects",
                    listOf("DELETE"),
                    listOf("S3_VIEWER_ADMIN", "ADMIN")
                )
            )
    }
}
