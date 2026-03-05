package com.openpad.core.ui.viewmodel

import com.openpad.core.InternalPadConfig
import com.openpad.core.OpenPadError
import com.openpad.core.OpenPadResult
import com.openpad.core.OpenPadThemeConfig
import com.openpad.core.testing.TestDispatcherRule
import com.openpad.core.testing.TestPadPipeline
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PadViewModelTest {

    @get:Rule
    val dispatcherRule = TestDispatcherRule()

    private lateinit var pipeline: TestPadPipeline
    private lateinit var callback: RecordingCallback
    private lateinit var vm: PadViewModel

    @Before
    fun setup() {
        pipeline = TestPadPipeline(
            config = InternalPadConfig.Default.copy(
                challengeTimeoutMs = 0L,
                liveSustainMs = 50L,
                evaluatingDurationMs = 50L
            )
        )
        callback = RecordingCallback()
        vm = PadViewModel(
            pipeline = pipeline,
            sessionStartMs = System.currentTimeMillis(),
            callback = callback,
            themeConfig = OpenPadThemeConfig.Default
        )
    }

    @Test
    fun `initial state is Idle`() {
        assertTrue(vm.ui.value is PadUiState.Idle)
    }

    @Test
    fun `initFromSdk transitions to Active`() {
        vm.initFromSdk()
        assertTrue(vm.ui.value is PadUiState.Active)
    }

    @Test
    fun `outcome starts as Pending`() {
        assertEquals(PadOutcome.Pending, vm.outcome)
    }

    @Test
    fun `close intent emits CloseRequested effect`() = runTest {
        vm.initFromSdk()
        val effects = mutableListOf<PadEffect>()
        val job = kotlinx.coroutines.CoroutineScope(dispatcherRule.testDispatcher).launch {
            vm.effects.collect { effects.add(it) }
        }

        vm.dispatch(PadIntent.OnCloseClicked)
        advanceUntilIdle()

        assertTrue(effects.any { it is PadEffect.CloseRequested })
        job.cancel()
    }

    @Test
    fun `buildSdkResult returns valid result`() {
        vm.initFromSdk()
        val result = vm.buildSdkResult()
        assertEquals(false, result.isLive)
    }

    private class RecordingCallback : PadSessionCallback {
        val results = mutableListOf<OpenPadResult>()
        val errors = mutableListOf<OpenPadError>()
        var cancelled = false

        override fun onResult(result: OpenPadResult) {
            results.add(result)
        }

        override fun onError(error: OpenPadError) {
            errors.add(error)
        }

        override fun onCancelled() {
            cancelled = true
        }
    }
}
