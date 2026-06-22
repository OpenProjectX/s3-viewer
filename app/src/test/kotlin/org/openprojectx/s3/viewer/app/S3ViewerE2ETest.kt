package org.openprojectx.s3.viewer.app

import com.microsoft.playwright.Browser
import com.microsoft.playwright.BrowserType.LaunchOptions
import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.localstack.LocalStackContainer
import org.testcontainers.containers.localstack.LocalStackContainer.Service.S3
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.CreateBucketRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest

@Tag("e2e")
@SpringBootTest(webEnvironment = RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class S3ViewerE2ETest {

    companion object {
        @Container
        @JvmField
        val localstack: LocalStackContainer =
            LocalStackContainer(
                DockerImageName.parse("docker.io/localstack/localstack:4")
                    .asCompatibleSubstituteFor("localstack/localstack")
            )
                .withServices(S3)

        @JvmStatic
        @DynamicPropertySource
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("s3-viewer.providers[0].id") { "test" }
            registry.add("s3-viewer.providers[0].name") { "Test LocalStack" }
            registry.add("s3-viewer.providers[0].region") { "us-east-1" }
            registry.add("s3-viewer.providers[0].access-key") { "test" }
            registry.add("s3-viewer.providers[0].secret-key") { "test" }
            registry.add("s3-viewer.providers[0].path-style-access") { true }
            registry.add("s3-viewer.providers[0].buckets[0]") { "test-bucket" }
            registry.add("s3-viewer.providers[0].endpoint") {
                ensureLocalstackStarted()
                localstack.getEndpointOverride(S3).toString()
            }
        }

        private fun ensureLocalstackStarted() {
            if (!localstack.isRunning) {
                localstack.start()
            }
        }
    }

    @LocalServerPort
    var port: Int = 0

    private lateinit var playwright: Playwright
    private lateinit var browser: Browser
    private lateinit var page: Page

    @BeforeAll
    fun seedData() {
        ensureLocalstackStarted()
        val s3 = S3Client.builder()
            .endpointOverride(localstack.getEndpointOverride(S3))
            .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")))
            .region(Region.US_EAST_1)
            .forcePathStyle(true)
            .build()

        s3.createBucket(CreateBucketRequest.builder().bucket("test-bucket").build())
        s3.putObject(
            PutObjectRequest.builder().bucket("test-bucket").key("readme.txt").build(),
            RequestBody.fromString("Hello from E2E")
        )
        s3.putObject(
            PutObjectRequest.builder().bucket("test-bucket").key("images/photo.jpg").build(),
            RequestBody.fromBytes(ByteArray(64))
        )
    }

    @BeforeEach
    fun openBrowser() {
        playwright = Playwright.create()
        browser = playwright.chromium().launch(
            LaunchOptions().setHeadless(true).setArgs(listOf("--no-sandbox", "--disable-dev-shm-usage"))
        )
        page = browser.newPage()
    }

    @AfterEach
    fun closeBrowser() {
        page.close()
        browser.close()
        playwright.close()
    }

    private fun uiUrl(path: String = "") = "http://localhost:$port/s3-viewer/ui/$path"

    @Test
    fun `UI index page loads and shows empty state`() {
        page.navigate(uiUrl())
        assertThat(page).hasTitle("S3 Viewer")
        assertThat(page.getByText("Select a bucket to browse")).isVisible()
    }

    @Test
    fun `sidebar shows configured provider`() {
        page.navigate(uiUrl())
        // Wait for API call to resolve and provider name to appear
        assertThat(page.getByText("Test LocalStack")).isVisible()
    }

    @Test
    fun `expanding provider shows bucket and clicking loads file explorer`() {
        page.navigate(uiUrl())

        // Expand the provider tree item
        page.getByText("Test LocalStack").click()
        assertThat(page.getByText("test-bucket")).isVisible()

        // Click the bucket
        page.getByText("test-bucket").click()

        // File explorer should show the seeded files
        assertThat(page.getByText("readme.txt")).isVisible()
        assertThat(page.getByText("images")).isVisible()
    }

    @Test
    fun `search filters visible files`() {
        page.navigate(uiUrl())
        page.getByText("Test LocalStack").click()
        page.getByText("test-bucket").click()
        assertThat(page.getByText("readme.txt")).isVisible()

        // Type in the search field
        page.getByPlaceholder("Search…").fill("readme")
        assertThat(page.getByText("readme.txt")).isVisible()
        assertThat(page.getByText("images")).not().isVisible()
    }
}
