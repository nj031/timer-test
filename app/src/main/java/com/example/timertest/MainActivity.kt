package com.example.timertest

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.timertest.ui.theme.TimerTestTheme
import java.util.Locale
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            TimerTestTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    TimerScreen(
                        onStart = { durationMillis -> startTimer(durationMillis) },
                        onStop = { stopTimer() }
                    )
                }
            }
        }
    }

    private fun startTimer(durationMillis: Long) {
        val intent = Intent(this, TimerService::class.java).apply {
            action = TimerService.ACTION_START
            putExtra(TimerService.EXTRA_DURATION_MILLIS, durationMillis)
        }
        ContextCompat.startForegroundService(this, intent)
    }

    private fun stopTimer() {
        val intent = Intent(this, TimerService::class.java).apply {
            action = TimerService.ACTION_STOP
        }
        ContextCompat.startForegroundService(this, intent)
    }
}

@Composable
fun TimerScreen(
    onStart: (Long) -> Unit,
    onStop: () -> Unit
) {
    val uiState by TimerService.state.collectAsStateWithLifecycle()
    var minutesInput by remember { mutableStateOf("5") }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = formatTime(if (uiState.isRunning) uiState.remainingMillis else TimeUnit.MINUTES.toMillis(
                    minutesInput.toLongOrNull() ?: 0L
                )),
                fontSize = 56.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(32.dp))

            if (!uiState.isRunning) {
                OutlinedTextField(
                    value = minutesInput,
                    onValueChange = { value -> if (value.all { it.isDigit() }) minutesInput = value },
                    label = { Text("Minutes") }
                )
                Spacer(modifier = Modifier.height(24.dp))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                if (uiState.isRunning) {
                    Button(onClick = onStop) {
                        Text("Stop")
                    }
                } else {
                    Button(onClick = {
                        val minutes = minutesInput.toLongOrNull() ?: 0L
                        if (minutes > 0L) onStart(TimeUnit.MINUTES.toMillis(minutes))
                    }) {
                        Text("Start")
                    }
                }
            }
        }
    }
}

private fun formatTime(millis: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60
    return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
}
