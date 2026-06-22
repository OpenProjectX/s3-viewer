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
import org.springframework.boot.test.web.server.LocalServerPort
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.model.PutObjectRequest

@Tag("e2e")
class S3ViewerE2ETest : S3ViewerLocalStackIntegrationTest() {

    @LocalServerPort
    var port: Int = 0

    private lateinit var playwright: Playwright
    private lateinit var browser: Browser
    private lateinit var page: Page

    @BeforeAll
    fun seedData() {
        s3Client().use { s3 ->
            ensureTestBucketExists(s3)
            s3.putObject(
                PutObjectRequest.builder().bucket("test-bucket").key("readme.txt").build(),
                RequestBody.fromString("Hello from E2E")
            )
            s3.putObject(
                PutObjectRequest.builder().bucket("test-bucket").key("images/photo.jpg").build(),
                RequestBody.fromBytes(ByteArray(64))
            )
        }
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
