package com.openpad.core.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.openpad.core.OpenPadError
import com.openpad.core.di.PadSessionHolder
import com.openpad.core.ui.theme.OpenPadTheme
import com.openpad.core.ui.viewmodel.PadViewModel
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors

@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
internal interface PadViewModelFactoryProvider {
    fun padViewModelFactory(): PadViewModel.Factory
}

/**
 * Thin Activity shell that hosts the PAD camera verification flow.
 *
 * All UI logic lives in [PadRoute] and [CameraScreen].
 */
@AndroidEntryPoint
class PadActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val params = PadSessionHolder.pending
        if (params == null) {
            finish()
            return
        }
        PadSessionHolder.pending = null

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleClose(null)
            }
        })

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }

        val factory = EntryPointAccessors.fromApplication(
            applicationContext,
            PadViewModelFactoryProvider::class.java
        ).padViewModelFactory()

        setContent {
            OpenPadTheme {
                PadRoute(
                    factory = factory,
                    params = params,
                    onDone = ::handleDone,
                    onClose = ::handleClose
                )
            }
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            PadSessionHolder.pending?.callback?.onError(OpenPadError.PermissionDenied())
            finish()
        }
    }

    private fun handleDone(vm: PadViewModel) {
        val result = vm.buildSdkResult()
        vm.callback.onResult(result)
        finish()
    }

    private fun handleClose(vm: PadViewModel?) {
        vm?.callback?.onCancelled()
        finish()
    }
}
