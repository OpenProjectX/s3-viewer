package org.openprojectx.s3.viewer.core

class S3ViewerException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)

class S3ViewerReadOnlyException(
    message: String = "S3 Viewer is configured for read-only access"
) : RuntimeException(message)
