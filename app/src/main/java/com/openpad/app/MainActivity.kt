package com.openpad.app

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.openpad.core.OpenPad
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Thin Activity shell for the demo app.
 *
 * All UI logic lives in [MainRoute] and [MainScreen].
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        vm.initSdk()

        lifecycleScope.launch {
            vm.sideEffect.collect { effect ->
                when (effect) {
                    MainViewModel.SideEffect.LaunchVerification -> {
                        OpenPad.analyze(this@MainActivity, vm.listener)
                    }
                    is MainViewModel.SideEffect.LaunchHeadless -> {
                        val session = OpenPad.createSession(effect.listener) ?: return@collect
                        HeadlessActivity.activeSession = session
                        startActivity(Intent(this@MainActivity, HeadlessActivity::class.java))
                    }
                    is MainViewModel.SideEffect.Toast -> {
                        Toast.makeText(this@MainActivity, effect.message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                MainRoute(vm = vm)
            }
        }
    }
}

/**
 * Stateful composable that collects [MainViewModel] state and delegates
 * rendering to stateless [MainScreen], [ConfigBottomSheet], and [ResultBottomSheet].
 */
@Composable
private fun MainRoute(vm: MainViewModel) {
    val uiState by vm.state.collectAsStateWithLifecycle()

    when (val state = uiState) {
        is MainViewModel.UiState.Initializing -> {
            MainScreen(
                isReady = false,
                isInitializing = true,
                lastVerification = null,
                onStartVerification = {},
                onStartHeadless = {},
                onOpenConfig = {},
                onResultTapped = {}
            )
        }
        is MainViewModel.UiState.Ready -> {
            MainScreen(
                isReady = true,
                isInitializing = false,
                lastVerification = state.lastVerification,
                onStartVerification = vm::onStartVerification,
                onStartHeadless = vm::onStartHeadless,
                onOpenConfig = { vm.onToggleConfig(true) },
                onResultTapped = vm::onResultTapped
            )

            if (state.showConfig) {
                ConfigBottomSheet(
                    config = state.config,
                    onConfigChange = vm::onConfigChange,
                    onDismiss = { vm.onToggleConfig(false) }
                )
            }

            val verification = state.lastVerification
            if (state.showResult && verification != null) {
                ResultBottomSheet(
                    result = verification.result,
                    onDismiss = vm::onDismissResult
                )
            }
        }
        is MainViewModel.UiState.Error -> {
            MainScreen(
                isReady = false,
                isInitializing = false,
                isError = true,
                lastVerification = null,
                onStartVerification = {},
                onStartHeadless = {},
                onOpenConfig = {},
                onResultTapped = {}
            )
        }
    }
}
