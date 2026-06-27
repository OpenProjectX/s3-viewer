package org.openprojectx.s3.viewer.app

import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.utility.DockerImageName
import org.testcontainers.utility.MountableFile
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.CreateBucketRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.S3Exception
import java.time.Duration
import java.util.Base64
import java.util.Hashtable
import javax.naming.Context
import javax.naming.directory.InitialDirContext
import javax.naming.directory.SearchControls

class S3ViewerLdapSecurityIntegrationTest : S3ViewerLocalStackIntegrationTest() {
    @LocalServerPort
    private var port: Int = 0

    private val webTestClient: WebTestClient
        get() = WebTestClient.bindToServer()
            .baseUrl("http://localhost:$port")
            .build()

    @BeforeAll
    fun seedData() {
        waitForLdapUser("bbrown")
        waitForLdapUser("aadams")
        s3Client().use { s3 ->
            s3.createBucketIfMissing("test-bucket")
            s3.putObject(
                PutObjectRequest.builder().bucket("test-bucket").key("docs/readme.txt").build(),
                RequestBody.fromString("secured content")
            )
            s3.putObject(
                PutObjectRequest.builder().bucket("test-bucket").key("docs/delete-me.txt").build(),
                RequestBody.fromString("delete me")
            )
        }
    }

    @Test
    fun `basic ldap authentication and memberOf rbac protect api routes`() {
        webTestClient.get()
            .uri("/s3-viewer/api/v1/providers")
            .exchange()
            .expectStatus().isUnauthorized

        webTestClient.get()
            .uri("/s3-viewer/api/v1/providers")
            .basicAuth("bbrown", "secret")
            .exchange()
            .expectStatus().isOk

        webTestClient.method(HttpMethod.DELETE)
            .uri("/s3-viewer/api/v1/providers/test/buckets/test-bucket/objects")
            .basicAuth("bbrown", "secret")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"keys":["docs/delete-me.txt"]}""")
            .exchange()
            .expectStatus().isForbidden

        webTestClient.method(HttpMethod.DELETE)
            .uri("/s3-viewer/api/v1/providers/test/buckets/test-bucket/objects")
            .basicAuth("aadams", "secret")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"keys":["docs/delete-me.txt"]}""")
            .exchange()
            .expectStatus().isNoContent
    }

    private fun <S : WebTestClient.RequestHeadersSpec<S>> S.basicAuth(username: String, password: String): S {
        val credentials = Base64.getEncoder().encodeToString("$username:$password".toByteArray())
        return header("Authorization", "Basic $credentials")
    }

    private fun S3Client.createBucketIfMissing(bucketName: String) {
        try {
            createBucket(CreateBucketRequest.builder().bucket(bucketName).build())
        } catch (exception: S3Exception) {
            if (exception.statusCode() != 409) {
                throw exception
            }
        }
    }

    companion object {
        private const val LDAP_PORT = 10389
        private const val LDIF_DIRECTORY = "/var/lib/apacheds/default/ldif"
        private const val BUNDLED_LOG4J_CONFIG = "/opt/apacheds/conf/log4j.properties"
        private const val APACHEDS_ROLLING_LOG = "/var/lib/apacheds/default/log/apacheds-rolling.log"
        private val apachedsLogger = LoggerFactory.getLogger("org.openprojectx.s3.viewer.test.apacheds")

        @Container
        @JvmField
        val apacheds: GenericContainer<*> =
            GenericContainer(DockerImageName.parse("ghcr.io/openprojectx/directory-server/apacheds:latest"))
                .withExposedPorts(LDAP_PORT)
                .withCopyFileToContainer(
                    MountableFile.forClasspathResource("ldap/apacheds-log4j.properties"),
                    BUNDLED_LOG4J_CONFIG
                )
                .withCopyFileToContainer(
                    MountableFile.forClasspathResource("ldap/00-ad-compat-schema.ldif"),
                    "$LDIF_DIRECTORY/00-ad-compat-schema.ldif"
                )
                .withCopyFileToContainer(
                    MountableFile.forClasspathResource("ldap/10-example-directory.ldif"),
                    "$LDIF_DIRECTORY/10-example-directory.ldif"
                )
                .waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(2)))
                .apply {
                    if (isCi()) {
                        withLogConsumer(Slf4jLogConsumer(apachedsLogger))
                    }
                }

        @JvmStatic
        @DynamicPropertySource
        fun configureSecurityProperties(registry: DynamicPropertyRegistry) {
            registry.add("s3-viewer.security.enabled") { true }
            registry.add("spring.ldap.urls") {
                startApacheDsIfNecessary()
                "ldap://${apacheds.host}:${apacheds.getMappedPort(LDAP_PORT)}"
            }
            registry.add("spring.ldap.base") { "dc=example,dc=com" }
            registry.add("spring.ldap.username") { "uid=admin,ou=system" }
            registry.add("spring.ldap.password") { "secret" }
            registry.add("s3-viewer.security.ldap.user-search-base") { "ou=Users" }
            registry.add("s3-viewer.security.ldap.user-search-filter") { "(uid={0})" }
            registry.add("s3-viewer.security.ldap.role-mappings.S3_VIEWER_ADMIN[0]") {
                "cn=Engineering,ou=Groups,dc=example,dc=com"
            }
        }

        private fun startApacheDsIfNecessary() {
            if (!apacheds.isRunning) {
                try {
                    apacheds.start()
                } catch (exception: Exception) {
                    throw IllegalStateException("ApacheDS test container failed to start; ${ldapDiagnostics()}", exception)
                }
            }
        }

        private fun waitForLdapUser(username: String) {
            val timeout = Duration.ofSeconds(8)
            val deadline = System.nanoTime() + timeout.toNanos()
            var lastFailure: Exception? = null

            while (System.nanoTime() < deadline) {
                try {
                    if (ldapUserExists(username)) {
                        return
                    }
                } catch (exception: Exception) {
                    lastFailure = exception
                }
                Thread.sleep(250)
            }

            val lastFailureMessage = lastFailure
                ?.let { "${it::class.qualifiedName}: ${it.message}" }
                ?: "no LDAP exception captured"
            throw IllegalStateException(
                "LDAP test user '$username' was not ready after ${timeout.seconds}s; " +
                    "last failure: $lastFailureMessage; ${ldapDiagnostics()}",
                lastFailure
            )
        }

        private fun ldapDiagnostics(): String {
            val ldifDirectory = runCatching {
                apacheds.execInContainer("sh", "-c", "ls -l $LDIF_DIRECTORY").stdout.trim()
            }.getOrElse { "failed to list LDIF directory: ${it.message}" }
            val logTail = runCatching {
                apacheds.logs.takeLast(diagnosticLogChars())
            }.getOrElse { "failed to read container logs: ${it.message}" }
            val rollingLogTail = runCatching {
                apacheds.execInContainer("sh", "-c", "tail -n ${diagnosticLogLines()} $APACHEDS_ROLLING_LOG")
                    .stdout
                    .trim()
            }.getOrElse { "failed to read ApacheDS rolling log: ${it.message}" }

            return """
                ApacheDS diagnostics:
                containerRunning=${apacheds.isRunning}
                containerId=${apacheds.containerId}
                ldifDirectory:
                $ldifDirectory
                dockerLogTail:
                $logTail
                rollingLogTail:
                $rollingLogTail
            """.trimIndent()
        }

        private fun diagnosticLogChars(): Int =
            if (isCi()) 20_000 else 4_000

        private fun diagnosticLogLines(): Int =
            if (isCi()) 1_000 else 200

        private fun isCi(): Boolean =
            System.getenv("CI").equals("true", ignoreCase = true)

        private fun ldapUserExists(username: String): Boolean {
            val environment = Hashtable<String, String>().apply {
                put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory")
                put(Context.PROVIDER_URL, "ldap://${apacheds.host}:${apacheds.getMappedPort(LDAP_PORT)}/dc=example,dc=com")
                put(Context.SECURITY_AUTHENTICATION, "simple")
                put(Context.SECURITY_PRINCIPAL, "uid=admin,ou=system")
                put(Context.SECURITY_CREDENTIALS, "secret")
            }

            val context = InitialDirContext(environment)
            try {
                val controls = SearchControls().apply {
                    searchScope = SearchControls.SUBTREE_SCOPE
                    returningAttributes = arrayOf("uid")
                }
                val results = context.search("ou=Users", "(uid=$username)", controls)
                try {
                    return results.hasMore()
                } finally {
                    results.close()
                }
            } finally {
                context.close()
            }
        }
    }
}
