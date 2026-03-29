package org.openprojectx.s3.viewer.core

class S3ViewerException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)
