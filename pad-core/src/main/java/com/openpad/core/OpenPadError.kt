package com.openpad.core

/**
 * Errors that can occur during SDK initialization or analysis.
 */
sealed class OpenPadError(val message: String) {
    class NotInitialized : OpenPadError("OpenPad.initialize() must be called before analyze()")
    class InitializationFailed(cause: String) : OpenPadError("Pipeline initialization failed: $cause")
    class CameraUnavailable : OpenPadError("Camera is not available on this device")
    class PermissionDenied : OpenPadError("Camera permission was denied")
    class AlreadyRunning : OpenPadError("An analysis session is already in progress")

    override fun toString(): String = "${this::class.simpleName}($message)"
}
