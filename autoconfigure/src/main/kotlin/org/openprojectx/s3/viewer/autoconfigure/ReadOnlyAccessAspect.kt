package org.openprojectx.s3.viewer.autoconfigure

import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.annotation.Before
import org.openprojectx.s3.viewer.core.S3ViewerReadOnlyException

@Aspect
class ReadOnlyAccessAspect(
    private val properties: S3ViewerProperties
) {
    @Before(
        "execution(* org.openprojectx.s3.viewer.core.S3ViewerService.createFolder(..)) || " +
            "execution(* org.openprojectx.s3.viewer.core.S3ViewerService.uploadObject(..)) || " +
            "execution(* org.openprojectx.s3.viewer.core.S3ViewerService.deleteObjects(..))"
    )
    fun rejectWriteOperationWhenReadOnly() {
        if (properties.readOnlyAccess) {
            throw S3ViewerReadOnlyException()
        }
    }
}
