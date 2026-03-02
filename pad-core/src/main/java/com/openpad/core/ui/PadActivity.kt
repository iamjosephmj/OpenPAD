package com.openpad.core.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.openpad.core.OpenPad
import com.openpad.core.OpenPadError
import com.openpad.core.ui.theme.OpenPadTheme
import com.openpad.core.ui.viewmodel.PadEffect
import com.openpad.core.ui.viewmodel.PadIntent
import com.openpad.core.ui.viewmodel.PadViewModel
import com.openpad.core.ui.viewmodel.SdkScreen
import kotlinx.coroutines.flow.collectLatest
import java.util.concurrent.Executors

/**
 * Internal Activity that hosts the full PAD verification flow.
 *
 * Launched by [OpenPad.analyze]. Handles camera permission,
 * displays Intro -> Camera -> Verdict screens, and delivers
 * results back to the integrator via [OpenPad] callbacks.
 */
class PadActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            OpenPad.deliverError(OpenPadError.PermissionDenied())
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (OpenPad.pipeline == null) {
            OpenPad.deliverError(OpenPadError.NotInitialized())
            finish()
            return
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleClose()
            }
        })

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }

        setContent {
            OpenPadTheme {
                PadFlowHost(
                    onDone = { handleDone() },
                    onClose = { handleClose() }
                )
            }
        }
    }

    private fun handleDone() {
        val vm = lastViewModel
        if (vm != null) {
            val result = vm.buildSdkResult()
            OpenPad.deliverResult(result)
        }
        finish()
    }

    private fun handleClose() {
        OpenPad.deliverCancelled()
        finish()
    }

    private var lastViewModel: PadViewModel? = null

    @Composable
    private fun PadFlowHost(
        onDone: () -> Unit,
        onClose: () -> Unit
    ) {
        val viewModel: PadViewModel = viewModel()
        lastViewModel = viewModel

        val ui by viewModel.ui.collectAsStateWithLifecycle()
        val surfaceRequest by viewModel.surfaceRequest.collectAsStateWithLifecycle()
        val lifecycleOwner = LocalLifecycleOwner.current
        val context = LocalContext.current
        val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

        LaunchedEffect(Unit) {
            viewModel.initFromSdk()
        }

        // Bind camera when transitioning to CAMERA screen
        LaunchedEffect(ui.currentScreen) {
            if (ui.currentScreen == SdkScreen.CAMERA && ui.isInitialized) {
                viewModel.dispatch(PadIntent.OnScreenStarted)
                viewModel.bindCamera(context, lifecycleOwner, analysisExecutor)
            }
        }

        // Handle effects
        LaunchedEffect(Unit) {
            viewModel.effects.collectLatest { effect ->
                when (effect) {
                    PadEffect.CloseRequested -> onClose()
                    PadEffect.Done -> onDone()
                    PadEffect.FinishLiveFlow -> {
                        viewModel.finish()
                        onDone()
                    }
                }
            }
        }

        DisposableEffect(Unit) {
            onDispose {
                analysisExecutor.shutdown()
            }
        }

        AnimatedContent(
            targetState = ui.currentScreen,
            transitionSpec = {
                when {
                    // Intro -> Camera: slide in from right
                    targetState == SdkScreen.CAMERA && initialState == SdkScreen.INTRO ->
                        (fadeIn(tween(350)) + slideInHorizontally(tween(400)) { it / 4 }) togetherWith
                            (fadeOut(tween(250)) + slideOutHorizontally(tween(350)) { -it / 4 })

                    // Camera -> Verdict: gentle scale-down + fade
                    targetState == SdkScreen.VERDICT && initialState == SdkScreen.CAMERA ->
                        (fadeIn(tween(400, delayMillis = 100)) +
                            scaleIn(tween(400, delayMillis = 100), initialScale = 0.92f)) togetherWith
                            (fadeOut(tween(300)) +
                                scaleOut(tween(300), targetScale = 1.04f))

                    // Verdict -> Camera (retry): slide back from left
                    targetState == SdkScreen.CAMERA && initialState == SdkScreen.VERDICT ->
                        (fadeIn(tween(350)) + slideInHorizontally(tween(400)) { -it / 4 }) togetherWith
                            (fadeOut(tween(250)) + slideOutHorizontally(tween(350)) { it / 4 })

                    else ->
                        fadeIn(tween(300)) togetherWith fadeOut(tween(250))
                }
            },
            modifier = Modifier.fillMaxSize(),
            label = "sdkScreen"
        ) { screen ->
            when (screen) {
                SdkScreen.INTRO -> {
                    IntroScreen(
                        onBeginVerification = {
                            viewModel.dispatch(PadIntent.OnBeginVerification)
                        }
                    )
                }

                SdkScreen.CAMERA -> {
                    CameraScreen(
                        surfaceRequest = surfaceRequest,
                        status = ui.status,
                        phase = ui.phase,
                        faceBoxWidthFraction = ui.faceBoxWidthFraction,
                        faceBoxHeightFraction = ui.faceBoxHeightFraction,
                        challengeProgress = ui.challengeProgress,
                        messageOverride = ui.messageOverride,
                        onClose = { viewModel.dispatch(PadIntent.OnCloseClicked) }
                    )
                }

                SdkScreen.VERDICT -> {
                    VerdictScreen(
                        verdictState = ui.verdictState ?: return@AnimatedContent,
                        onRetry = { viewModel.dispatch(PadIntent.OnRetryClicked) },
                        onClose = {
                            viewModel.dispatch(PadIntent.OnDone)
                        }
                    )
                }
            }
        }
    }
}
