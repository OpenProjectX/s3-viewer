package software.amazon.nio.spi.s3

import software.amazon.nio.spi.s3.util.S3FileSystemInfo
import java.net.URI

internal class SpringS3FileSystemInfo(
    private val fsKey: String,
    private val fsBucket: String,
    private val fsEndpoint: String,
    private val fsAccessKey: String?,
    private val fsAccessSecret: String?
) : S3FileSystemInfo(URI.create("s3://$fsBucket")) {

    override fun key(): String = fsKey

    override fun bucket(): String = fsBucket

    override fun endpoint(): String = fsEndpoint

    override fun accessKey(): String? = fsAccessKey

    override fun accessSecret(): String? = fsAccessSecret
}