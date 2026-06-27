package org.openprojectx.s3.viewer.autoconfigure

import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type.REACTIVE
import org.springframework.context.annotation.Bean
import org.springframework.http.HttpMethod
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.ReactiveAuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.config.Customizer
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.core.Authentication
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.ldap.authentication.BindAuthenticator
import org.springframework.security.ldap.authentication.LdapAuthenticationProvider
import org.springframework.security.ldap.search.FilterBasedLdapUserSearch
import org.springframework.security.ldap.userdetails.LdapAuthoritiesPopulator
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.ldap.core.DirContextOperations
import org.springframework.ldap.core.support.BaseLdapPathContextSource
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

@AutoConfiguration(after = [S3ViewerWebAutoConfiguration::class])
@ConditionalOnClass(ServerHttpSecurity::class)
@ConditionalOnWebApplication(type = REACTIVE)
class S3ViewerSecurityAutoConfiguration {
    @Bean
    @ConditionalOnProperty(prefix = "s3-viewer.security", name = ["enabled"], havingValue = "false", matchIfMissing = true)
    fun s3ViewerPermitAllSecurityWebFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain =
        http
            .csrf { it.disable() }
            .httpBasic { it.disable() }
            .formLogin { it.disable() }
            .authorizeExchange { exchanges -> exchanges.anyExchange().permitAll() }
            .build()

    @Bean
    @ConditionalOnProperty(prefix = "s3-viewer.security", name = ["enabled"], havingValue = "true")
    fun s3ViewerLdapAuthenticationManager(
        contextSource: BaseLdapPathContextSource,
        properties: S3ViewerProperties
    ): ReactiveAuthenticationManager =
        LdapReactiveAuthenticationManager(contextSource, properties.security.ldap)

    @Bean
    @ConditionalOnProperty(prefix = "s3-viewer.security", name = ["enabled"], havingValue = "true")
    fun s3ViewerSecurityWebFilterChain(
        http: ServerHttpSecurity,
        authenticationManager: ReactiveAuthenticationManager,
        properties: S3ViewerProperties
    ): SecurityWebFilterChain =
        http
            .csrf { it.disable() }
            .formLogin { it.disable() }
            .logout { it.disable() }
            .httpBasic(Customizer.withDefaults())
            .authenticationManager(authenticationManager)
            .authorizeExchange { exchanges ->
                exchanges.pathMatchers("/actuator/health", "/actuator/health/**").permitAll()
                if (properties.security.rbac.enabled) {
                    properties.security.rbac.rules.forEach { rule ->
                        val authorities = rule.roles.map { toAuthority(it, properties.security.ldap.rolePrefix) }.toTypedArray()
                        if (rule.methods.isEmpty()) {
                            val access = exchanges.pathMatchers(rule.path)
                            if (authorities.isEmpty()) access.authenticated() else access.hasAnyAuthority(*authorities)
                        } else {
                            rule.methods.forEach { method ->
                                val httpMethod = HttpMethod.valueOf(method.uppercase())
                                val access = exchanges.pathMatchers(httpMethod, rule.path)
                                if (authorities.isEmpty()) access.authenticated() else access.hasAnyAuthority(*authorities)
                            }
                        }
                    }
                }
                exchanges.pathMatchers("/s3-viewer/**").authenticated()
                exchanges.anyExchange().denyAll()
            }
            .build()
}

private class LdapReactiveAuthenticationManager(
    private val contextSource: BaseLdapPathContextSource,
    private val properties: S3ViewerProperties.LdapProperties
) : ReactiveAuthenticationManager {
    private val authenticationProvider: LdapAuthenticationProvider by lazy { ldapAuthenticationProvider() }

    override fun authenticate(authentication: Authentication): Mono<Authentication> {
        val username = authentication.name.orEmpty()
        val password = authentication.credentials?.toString().orEmpty()
        if (username.isBlank() || password.isBlank()) {
            return Mono.error(BadCredentialsException("Invalid username or password"))
        }

        return Mono.fromCallable<Authentication> {
            authenticationProvider.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(username, password)
            )
        }
            .subscribeOn(Schedulers.boundedElastic())
    }

    private fun ldapAuthenticationProvider(): LdapAuthenticationProvider {
        val userSearch = FilterBasedLdapUserSearch(
            properties.userSearchBase.trim().trim(','),
            properties.userSearchFilter,
            contextSource
        ).apply {
            setSearchSubtree(true)
            setReturningAttributes(arrayOf(properties.memberOfAttribute, "distinguishedName"))
        }

        val authenticator = BindAuthenticator(contextSource).apply {
            setUserSearch(userSearch)
            afterPropertiesSet()
        }

        return LdapAuthenticationProvider(authenticator, MemberOfAuthoritiesPopulator(properties)).apply {
            setHideUserNotFoundExceptions(true)
        }
    }
}

private class MemberOfAuthoritiesPopulator(
    private val properties: S3ViewerProperties.LdapProperties
) : LdapAuthoritiesPopulator {
    override fun getGrantedAuthorities(userData: DirContextOperations, username: String): Collection<GrantedAuthority> {
        val memberOf = userData.getStringAttributes(properties.memberOfAttribute)?.toList().orEmpty()
        val groupNames = memberOf.mapNotNull(::extractCommonName)
        val roles = linkedSetOf<String>()

        groupNames.forEach { roles.add(it) }

        properties.roleMappings.forEach { (role, groups) ->
            val matches = groups.any { configuredGroup ->
                memberOf.any { it.equals(configuredGroup, ignoreCase = true) } ||
                        groupNames.any { it.equals(configuredGroup, ignoreCase = true) }
            }
            if (matches) {
                roles.add(role)
            }
        }

        return roles
            .map { toAuthority(it, properties.rolePrefix) }
            .map(::SimpleGrantedAuthority)
    }
}

private fun toAuthority(role: String, rolePrefix: String): String {
    val trimmedRole = role.trim().uppercase()
    val normalizedPrefix = rolePrefix.ifBlank { "ROLE_" }.uppercase()
    if (trimmedRole.startsWith(normalizedPrefix)) {
        return normalizeAuthority(trimmedRole, normalizedPrefix)
    }
    return "$normalizedPrefix${normalizeRoleName(trimmedRole)}"
}

private fun normalizeAuthority(authority: String, rolePrefix: String): String =
    "$rolePrefix${normalizeRoleName(authority.removePrefix(rolePrefix))}"

private fun normalizeRoleName(role: String): String =
    role.uppercase().replace(Regex("[^A-Z0-9]+"), "_").trim('_')

private fun extractCommonName(dn: String): String? =
    Regex("(?i)(?:^|,)\\s*CN=([^,]+)").find(dn)?.groupValues?.get(1)?.trim()
