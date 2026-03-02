package com.openpad.app

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openpad.core.OpenPad
import com.openpad.core.OpenPadError
import com.openpad.core.OpenPadListener
import com.openpad.core.OpenPadResult

class MainActivity : ComponentActivity() {

    private val padListener = object : OpenPadListener {
        override fun onLiveConfirmed(result: OpenPadResult) {
            Toast.makeText(
                this@MainActivity,
                "LIVE confirmed (confidence: %.0f%%, %dms)".format(
                    result.confidence * 100, result.durationMs
                ),
                Toast.LENGTH_LONG
            ).show()
        }

        override fun onSpoofDetected(result: OpenPadResult) {
            Toast.makeText(
                this@MainActivity,
                "Spoof detected (%d attempts)".format(result.spoofAttempts),
                Toast.LENGTH_LONG
            ).show()
        }

        override fun onError(error: OpenPadError) {
            Toast.makeText(
                this@MainActivity,
                "Error: $error",
                Toast.LENGTH_LONG
            ).show()
        }

        override fun onCancelled() {
            Toast.makeText(this@MainActivity, "Verification cancelled", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF0D1B2A)
                ) {
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
                            text = "OpenPad Demo",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Choose an integration mode to test\nthe liveness verification SDK.",
                            fontSize = 15.sp,
                            color = Color.White.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(48.dp))

                        // UI Mode — full built-in flow
                        Button(
                            onClick = {
                                if (OpenPad.isInitialized) {
                                    OpenPad.analyze(this@MainActivity, padListener)
                                } else {
                                    Toast.makeText(
                                        this@MainActivity,
                                        "SDK still initializing\u2026",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF2962FF),
                                contentColor = Color.White
                            )
                        ) {
                            Text(
                                "UI Mode \u2014 Full Verification",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Headless Mode — bring your own camera
                        OutlinedButton(
                            onClick = {
                                if (OpenPad.isInitialized) {
                                    startHeadlessDemo()
                                } else {
                                    Toast.makeText(
                                        this@MainActivity,
                                        "SDK still initializing\u2026",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, Color(0xFF2962FF).copy(alpha = 0.5f)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color(0xFF448AFF)
                            )
                        ) {
                            Text(
                                "Headless Mode \u2014 Own Camera",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Spacer(modifier = Modifier.height(32.dp))

                        Text(
                            text = "Theme is customizable via OpenPadThemeConfig\nset in OpenPadApp.kt",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.35f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }

    private fun startHeadlessDemo() {
        val session = OpenPad.createSession(padListener)
        if (session == null) {
            Toast.makeText(this, "Failed to create session", Toast.LENGTH_SHORT).show()
            return
        }

        val intent = android.content.Intent(this, HeadlessActivity::class.java)
        HeadlessActivity.activeSession = session
        startActivity(intent)
    }
}
