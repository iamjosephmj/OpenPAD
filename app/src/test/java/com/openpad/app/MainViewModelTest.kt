package com.openpad.app

import android.app.Application
import com.openpad.core.OpenPadConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [33])
class MainViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var vm: MainViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        val context = RuntimeEnvironment.getApplication()
        vm = MainViewModel(context)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is Initializing`() {
        assertTrue(vm.state.value is MainViewModel.UiState.Initializing)
    }

    @Test
    fun `onConfigChange updates config in Ready state`() = runTest {
        setReadyState()

        val newConfig = OpenPadConfig(challengeTimeoutMs = 5000)
        vm.onConfigChange(newConfig)

        val current = vm.state.value as MainViewModel.UiState.Ready
        assertEquals(5000L, current.config.challengeTimeoutMs)
    }

    @Test
    fun `onToggleConfig sets showConfig flag`() = runTest {
        setReadyState()

        vm.onToggleConfig(true)

        val current = vm.state.value as MainViewModel.UiState.Ready
        assertTrue(current.showConfig)
    }

    @Test
    fun `onDismissResult clears showResult flag`() = runTest {
        setReadyState(MainViewModel.UiState.Ready(showResult = true))

        vm.onDismissResult()

        val current = vm.state.value as MainViewModel.UiState.Ready
        assertTrue(!current.showResult)
    }

    @Test
    fun `onToggleConfig does nothing when state is not Ready`() {
        vm.onToggleConfig(true)

        assertTrue(vm.state.value is MainViewModel.UiState.Initializing)
    }

    @Suppress("UNCHECKED_CAST")
    private fun setReadyState(state: MainViewModel.UiState.Ready = MainViewModel.UiState.Ready()) {
        val field = MainViewModel::class.java.getDeclaredField("_state")
        field.isAccessible = true
        val flow = field.get(vm) as MutableStateFlow<MainViewModel.UiState>
        flow.value = state
    }
}
