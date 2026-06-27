package org.openprojectx.s3.viewer.autoconfigure

import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type.REACTIVE
import org.springframework.context.annotation.Bean
import org.springframework.http.HttpMethod
import org.springframework.security.authentication.AuthenticationServiceException
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.ReactiveAuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.config.Customizer
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.core.Authentication
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.web.server.SecurityWebFilterChain
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.util.Hashtable
import javax.naming.Context
import javax.naming.NamingEnumeration
import javax.naming.NamingException
import javax.naming.directory.Attribute
import javax.naming.directory.Attributes
import javax.naming.directory.InitialDirContext
import javax.naming.directory.SearchControls
import javax.naming.directory.SearchResult

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
    fun s3ViewerLdapAuthenticationManager(properties: S3ViewerProperties): ReactiveAuthenticationManager =
        LdapReactiveAuthenticationManager(properties.security.ldap)

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
    private val properties: S3ViewerProperties.LdapProperties
) : ReactiveAuthenticationManager {
    override fun authenticate(authentication: Authentication): Mono<Authentication> {
        val username = authentication.name.orEmpty()
        val password = authentication.credentials?.toString().orEmpty()
        if (username.isBlank() || password.isBlank()) {
            return Mono.error(BadCredentialsException("Invalid username or password"))
        }

        return Mono.fromCallable<Authentication> {
            val user = findUser(username)
            bindUser(user.dn, password)
            UsernamePasswordAuthenticationToken.authenticated(
                username,
                null,
                authoritiesFromMemberOf(user.memberOf)
            )
        }
            .subscribeOn(Schedulers.boundedElastic())
            .onErrorMap(NamingException::class.java) { BadCredentialsException("Invalid username or password", it) }
    }

    private fun findUser(username: String): LdapUser {
        validateLdapConfig()
        val context = serviceContext()
        try {
            val controls = SearchControls().apply {
                searchScope = SearchControls.SUBTREE_SCOPE
                returningAttributes = arrayOf(properties.memberOfAttribute, "distinguishedName")
                countLimit = 2
            }
            val results = context.search(searchBase(), properties.userSearchFilter, arrayOf(username), controls)
            val first = results.nextOrNull() ?: throw BadCredentialsException("Invalid username or password")
            if (results.hasMore()) {
                throw AuthenticationServiceException("LDAP search returned more than one user for '$username'")
            }
            return LdapUser(
                dn = first.nameInNamespace,
                memberOf = first.attributes.memberOfValues(properties.memberOfAttribute)
            )
        } finally {
            context.close()
        }
    }

    private fun bindUser(userDn: String, password: String) {
        InitialDirContext(contextEnvironment(userDn, password)).close()
    }

    private fun serviceContext(): InitialDirContext =
        if (properties.managerDn.isNullOrBlank()) {
            InitialDirContext(contextEnvironment(null, null))
        } else {
            InitialDirContext(contextEnvironment(properties.managerDn, properties.managerPassword.orEmpty()))
        }

    private fun contextEnvironment(principal: String?, credentials: String?): Hashtable<String, String> =
        Hashtable<String, String>().apply {
            put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory")
            put(Context.PROVIDER_URL, properties.url)
            if (principal.isNullOrBlank()) {
                put(Context.SECURITY_AUTHENTICATION, "none")
            } else {
                put(Context.SECURITY_AUTHENTICATION, "simple")
                put(Context.SECURITY_PRINCIPAL, principal)
                put(Context.SECURITY_CREDENTIALS, credentials.orEmpty())
            }
        }

    private fun authoritiesFromMemberOf(memberOf: List<String>): Collection<GrantedAuthority> {
        val roles = linkedSetOf<String>()
        val groupNames = memberOf.mapNotNull(::extractCommonName)

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

    private fun searchBase(): String =
        listOf(properties.userSearchBase, properties.baseDn)
            .map { it.trim().trim(',') }
            .filter { it.isNotBlank() }
            .joinToString(",")

    private fun validateLdapConfig() {
        if (properties.url.isBlank()) {
            throw AuthenticationServiceException("s3-viewer.security.ldap.url must be configured")
        }
        if (properties.baseDn.isBlank()) {
            throw AuthenticationServiceException("s3-viewer.security.ldap.base-dn must be configured")
        }
    }
}

private data class LdapUser(
    val dn: String,
    val memberOf: List<String>
)

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

private fun Attributes.memberOfValues(attributeName: String): List<String> =
    get(attributeName)?.values().orEmpty()

private fun Attribute.values(): List<String> {
    val values = mutableListOf<String>()
    val enumeration = all
    while (enumeration.hasMore()) {
        values.add(enumeration.next().toString())
    }
    return values
}

private fun <T> NamingEnumeration<T>.nextOrNull(): T? =
    if (hasMore()) next() else null
