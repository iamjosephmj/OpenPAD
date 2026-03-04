package com.openpad.core.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.openpad.core.OpenPadError
import com.openpad.core.ui.theme.OpenPadTheme
import com.openpad.core.ui.viewmodel.PadEffect
import com.openpad.core.ui.viewmodel.PadIntent
import com.openpad.core.ui.viewmodel.PadSessionCallback
import com.openpad.core.ui.viewmodel.PadViewModel
import com.openpad.core.ui.viewmodel.PadViewModelFactory
import kotlinx.coroutines.flow.collectLatest
import java.util.concurrent.Executors

/**
 * Internal Activity that hosts the PAD camera verification flow.
 *
 * Launched by [OpenPad.analyze]. Handles camera permission,
 * displays the camera screen, and delivers results back to
 * the integrator via [OpenPad] callbacks.
 */
class PadActivity : ComponentActivity() {

    private var sessionCallback: PadSessionCallback? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            sessionCallback?.onError(OpenPadError.PermissionDenied())
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val factory = PadViewModelFactory.pending
        if (factory == null) {
            finish()
            return
        }
        PadViewModelFactory.pending = null

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
                PadCameraHost(
                    factory = factory,
                    onDone = { handleDone() },
                    onClose = { handleClose() }
                )
            }
        }
    }

    private fun handleDone() {
        val vm = lastViewModel ?: return
        val result = vm.buildSdkResult()
        vm.callback.onResult(result)
        finish()
    }

    private fun handleClose() {
        val vm = lastViewModel
        vm?.callback?.onCancelled() ?: sessionCallback?.onCancelled()
        finish()
    }

    private var lastViewModel: PadViewModel? = null

    @Composable
    private fun PadCameraHost(
        factory: PadViewModelFactory,
        onDone: () -> Unit,
        onClose: () -> Unit
    ) {
        val viewModel: PadViewModel = viewModel(factory = factory)
        lastViewModel = viewModel
        sessionCallback = viewModel.callback

        val ui by viewModel.ui.collectAsStateWithLifecycle()
        val surfaceRequest by viewModel.surfaceRequest.collectAsStateWithLifecycle()
        val lifecycleOwner = LocalLifecycleOwner.current
        val context = LocalContext.current
        val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

        LaunchedEffect(Unit) {
            viewModel.initFromSdk()
        }

        // Bind camera once initialized
        LaunchedEffect(ui.isInitialized) {
            if (ui.isInitialized) {
                viewModel.dispatch(PadIntent.OnScreenStarted)
                viewModel.bindCamera(context, lifecycleOwner, analysisExecutor)
            }
        }

        // Handle effects
        LaunchedEffect(Unit) {
            viewModel.effects.collectLatest { effect ->
                when (effect) {
                    PadEffect.CloseRequested -> onClose()
                    PadEffect.Done -> {
                        viewModel.finish()
                        onDone()
                    }
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
}
