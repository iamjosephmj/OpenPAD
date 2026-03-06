package com.openpad.core

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import com.openpad.core.di.PadSessionHolder
import com.openpad.core.ui.PadActivity
import com.openpad.core.ui.viewmodel.PadSessionCallback
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Main entry point for the OpenPad SDK.
 *
 * Supports two integration modes:
 *
 * ## UI Mode (full-screen verification flow)
 * ```
 * OpenPad.initialize(context) { }
 * OpenPad.analyze(activity, listener)
 * ```
 *
 * ## Headless Mode (bring your own camera)
 * ```
 * OpenPad.initialize(context) { }
 * val session = OpenPad.createSession(listener)
 * imageAnalysis.setAnalyzer(executor, session.frameAnalyzer)
 * // observe session.status, session.phase, session.instruction
 * session.release()
 * ```
 *
 * ## Theme Customization
 * ```
 * OpenPad.theme = OpenPadThemeConfig(
 *     primary = 0xFF1565C0,
 *     success = 0xFF2E7D32
 * )
 * ```
 *
 * **Important**: The host application must be annotated with `@HiltAndroidApp`
 * for the built-in UI mode to function correctly.
 */
object OpenPad {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var initExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    @Volatile
    internal var pipeline: PadPipeline? = null
        private set

    @Volatile
    internal var sdkConfig: OpenPadConfig = OpenPadConfig.Default
        private set

    @Volatile
    internal var appContext: Context? = null
        private set

    @Volatile
    internal var activeListener: OpenPadListener? = null

    @Volatile
    internal var sessionStartMs: Long = 0L

    private val initializing = AtomicBoolean(false)

    /**
     * UI theme configuration. Set this before calling [analyze] to customize
     * colors across all SDK screens (intro, camera overlay, verdict).
     *
     * Has no effect on headless sessions (integrators own the UI).
     * Changes take effect on the next [analyze] call.
     */
    @Volatile
    var theme: OpenPadThemeConfig = OpenPadThemeConfig.Default

    /** Whether the SDK has been initialized and is ready to analyze. */
    val isInitialized: Boolean
        get() = pipeline != null

    /**
     * Initialize the SDK by loading all ML models.
     *
     * This is an async operation that loads TFLite models from assets.
     * Must be called before [analyze] or [createSession]. Safe to call
     * multiple times (no-ops if already initialized).
     *
     * @param context Application or Activity context.
     * @param config SDK configuration. Defaults to [OpenPadConfig.Default].
     * @param onReady Called on the main thread when initialization completes.
     * @param onError Called on the main thread if initialization fails.
     */
    fun initialize(
        context: Context,
        config: OpenPadConfig = OpenPadConfig.Default,
        onReady: () -> Unit = {},
        onError: (OpenPadError) -> Unit = {}
    ) {
        if (pipeline != null) {
            mainHandler.post(onReady)
            return
        }

        if (!initializing.compareAndSet(false, true)) {
            return
        }

        sdkConfig = config
        appContext = context.applicationContext

        if (initExecutor.isShutdown) {
            initExecutor = Executors.newSingleThreadExecutor()
        }
        initExecutor.execute {
            try {
                val p = PadPipeline.create(context.applicationContext, config.toInternal())
                pipeline = p
                mainHandler.post(onReady)
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                mainHandler.post { onError(OpenPadError.InitializationFailed(e.message ?: "unknown")) }
            } finally {
                initializing.set(false)
            }
        }
    }

    /**
     * Launch a liveness verification session with the built-in UI.
     *
     * Opens a full-screen Activity with camera preview, challenge-response flow,
     * and verdict screen. Results are delivered via [listener]. The UI uses
     * colors from [theme].
     *
     * @param activity The calling Activity (used to launch the SDK Activity).
     * @param listener Callback for receiving the verification result.
     */
    fun analyze(
        activity: Activity,
        listener: OpenPadListener
    ) {
        val p = pipeline
        if (p == null) {
            listener.onError(OpenPadError.NotInitialized())
            return
        }

        if (activeListener != null) {
            listener.onError(OpenPadError.AlreadyRunning())
            return
        }

        activeListener = listener
        sessionStartMs = System.currentTimeMillis()

        val callback = object : PadSessionCallback {
            override fun onResult(result: OpenPadResult) {
                deliverResult(result)
            }
            override fun onError(error: OpenPadError) {
                deliverError(error)
            }
            override fun onCancelled() {
                deliverCancelled()
            }
        }

        PadSessionHolder.pending = PadSessionHolder.Params(
            sessionStartMs = sessionStartMs,
            callback = callback,
            themeConfig = theme
        )

        val intent = Intent(activity, PadActivity::class.java)
        activity.startActivity(intent)
    }

    /**
     * Create a headless analysis session for integrators with their own camera UI.
     *
     * The returned [OpenPadSession] provides:
     * - [OpenPadSession.frameAnalyzer] -- plug into `ImageAnalysis.setAnalyzer(...)`
     * - [OpenPadSession.status] -- current PAD status (ANALYZING, LIVE, SPOOF_SUSPECTED, ...)
     * - [OpenPadSession.phase] -- current challenge phase (ANALYZING, CHALLENGE_CLOSER, ...)
     * - [OpenPadSession.challengeProgress] -- progress in [0.0, 1.0]
     * - [OpenPadSession.instruction] -- user-facing guidance text
     *
     * The session runs the full challenge-response pipeline. When the verdict is
     * reached, exactly one callback on [listener] is invoked. Call
     * [OpenPadSession.release] when done.
     *
     * @param listener Callback for receiving the verification result.
     * @return A new headless session, or null if the SDK is not initialized.
     */
    fun createSession(listener: OpenPadListener): OpenPadSession? {
        val p = pipeline
        if (p == null) {
            listener.onError(OpenPadError.NotInitialized())
            return null
        }

        return OpenPadSessionImpl(p, sdkConfig.toInternal(), listener, appContext!!)
    }

    /**
     * Release all resources held by the SDK (model memory, interpreters).
     * After calling this, [initialize] must be called again before [analyze]
     * or [createSession].
     */
    fun release() {
        pipeline?.close()
        pipeline = null
        activeListener = null
        initializing.set(false)
        initExecutor.shutdownNow()
    }

    internal fun deliverResult(result: OpenPadResult) {
        val listener = activeListener ?: return
        activeListener = null
        mainHandler.post {
            if (result.isLive) listener.onLiveConfirmed(result)
            else listener.onSpoofDetected(result)
        }
    }

    internal fun deliverError(error: OpenPadError) {
        val listener = activeListener ?: return
        activeListener = null
        mainHandler.post { listener.onError(error) }
    }

    internal fun deliverCancelled() {
        val listener = activeListener ?: return
        activeListener = null
        mainHandler.post { listener.onCancelled() }
    }
}
