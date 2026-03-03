package com.openpad.app

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openpad.core.OpenPad
import com.openpad.core.OpenPadConfig
import com.openpad.core.OpenPadError
import com.openpad.core.OpenPadListener
import com.openpad.core.OpenPadResult
import timber.log.Timber

class MainActivity : ComponentActivity() {

    private var config by mutableStateOf(OpenPadConfig())
    private var appliedConfig: OpenPadConfig? = null
    private var showConfig by mutableStateOf(false)
    private var loading by mutableStateOf(false)

    private var resultText by mutableStateOf<String?>(null)
    private var resultIsLive by mutableStateOf(false)
    private var lastResult by mutableStateOf<OpenPadResult?>(null)
    private var showResult by mutableStateOf(false)

    private val listener = object : OpenPadListener {
        override fun onLiveConfirmed(result: OpenPadResult) {
            resultIsLive = true
            resultText = "Live  %.0f%%  %,dms".format(result.confidence * 100, result.durationMs)
            lastResult = result
            showResult = true
            Timber.d("LIVE  confidence=%.2f  duration=%dms", result.confidence, result.durationMs)
        }

        override fun onSpoofDetected(result: OpenPadResult) {
            resultIsLive = false
            resultText = "Spoof  %.0f%%  %,dms".format(result.confidence * 100, result.durationMs)
            lastResult = result
            showResult = true
            Timber.d("SPOOF  confidence=%.2f  duration=%dms", result.confidence, result.durationMs)
        }

        override fun onError(error: OpenPadError) {
            resultText = null
            Timber.e("Error: %s", error)
            Toast.makeText(this@MainActivity, "Error: $error", Toast.LENGTH_SHORT).show()
        }

        override fun onCancelled() {
            resultText = null
            Timber.d("Cancelled")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        initSdk()

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(Modifier.fillMaxSize(), color = Color(0xFF0D1B2A)) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(WindowInsets.statusBars.asPaddingValues())
                            .padding(WindowInsets.navigationBars.asPaddingValues())
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            "OpenPad Demo",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Liveness verification SDK",
                            fontSize = 15.sp,
                            color = Color.White.copy(alpha = 0.5f)
                        )

                        // Loading indicator
                        AnimatedVisibility(
                            visible = loading,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Spacer(Modifier.height(32.dp))
                                LinearProgressIndicator(
                                    modifier = Modifier
                                        .fillMaxWidth(0.6f)
                                        .height(4.dp),
                                    color = Color(0xFF2962FF),
                                    trackColor = Color.White.copy(alpha = 0.08f),
                                    strokeCap = StrokeCap.Round
                                )
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    "Loading models\u2026",
                                    fontSize = 13.sp,
                                    color = Color.White.copy(alpha = 0.4f)
                                )
                            }
                        }

                        Spacer(Modifier.height(48.dp))

                        // Start verification
                        Button(
                            onClick = ::startVerification,
                            enabled = !loading,
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF2962FF),
                                contentColor = Color.White,
                                disabledContainerColor = Color(0xFF2962FF).copy(alpha = 0.3f),
                                disabledContentColor = Color.White.copy(alpha = 0.4f)
                            )
                        ) {
                            Text("Start Verification", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        }

                        Spacer(Modifier.height(16.dp))

                        // Headless + Config row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = ::startHeadless,
                                enabled = !loading,
                                modifier = Modifier.weight(1f).height(48.dp),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Color(0xFF2962FF).copy(alpha = if (loading) 0.15f else 0.4f)),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = Color(0xFF448AFF)
                                )
                            ) {
                                Text("Headless", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            }
                            OutlinedButton(
                                onClick = { showConfig = true },
                                modifier = Modifier.width(110.dp).height(48.dp),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = Color.White.copy(alpha = 0.7f)
                                )
                            ) {
                                Text("Config", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            }
                        }

                        // Last result (tap to view details)
                        resultText?.let { text ->
                            Spacer(Modifier.height(32.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (resultIsLive) Color(0xFF00C853).copy(alpha = 0.12f)
                                else Color(0xFFD32F2F).copy(alpha = 0.12f),
                                modifier = Modifier.clickable { showResult = true }
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (resultIsLive) Color(0xFF00C853) else Color(0xFFD32F2F),
                                        textAlign = TextAlign.Center
                                    )
                                    Text(
                                        "Tap for details",
                                        fontSize = 11.sp,
                                        color = Color.White.copy(alpha = 0.35f)
                                    )
                                }
                            }
                        }
                    }
                }

                if (showConfig) {
                    ConfigBottomSheet(
                        config = config,
                        onConfigChange = { config = it },
                        onDismiss = { showConfig = false }
                    )
                }

                val pendingResult = lastResult
                if (showResult && pendingResult != null) {
                    ResultBottomSheet(
                        result = pendingResult,
                        onDismiss = { showResult = false }
                    )
                }
            }
        }
    }

    private fun initSdk() {
        if (OpenPad.isInitialized) return
        loading = true
        appliedConfig = config
        OpenPad.initialize(
            context = this,
            config = config,
            onReady = { loading = false },
            onError = { error ->
                loading = false
                Toast.makeText(this, "Init failed: $error", Toast.LENGTH_LONG).show()
            }
        )
    }

    private fun startVerification() {
        ensureConfig()
        if (loading) return
        OpenPad.analyze(this, listener)
    }

    private fun startHeadless() {
        ensureConfig()
        if (loading) return
        val session = OpenPad.createSession(listener) ?: return
        HeadlessActivity.activeSession = session
        startActivity(android.content.Intent(this, HeadlessActivity::class.java))
    }

    private fun ensureConfig() {
        if (appliedConfig == config && OpenPad.isInitialized) return
        OpenPad.release()
        loading = true
        appliedConfig = config
        OpenPad.initialize(
            context = this,
            config = config,
            onReady = { loading = false },
            onError = { error ->
                loading = false
                Toast.makeText(this, "Init failed: $error", Toast.LENGTH_LONG).show()
            }
        )
    }
}
