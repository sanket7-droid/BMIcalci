package com.example.bmicalci

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.bmicalci.reminders.NotificationWorker
import com.example.bmicalci.ui.theme.BmiHealthProTheme
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setupHydrationReminders()
        setContent {
            BmiHealthProTheme {
                NotificationPermissionRequest()
                AppRoot()
            }
        }
    }

    private fun setupHydrationReminders() {
        val request = PeriodicWorkRequestBuilder<NotificationWorker>(3, TimeUnit.HOURS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "HydrationNotificationWork",
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }
}

/** Asks for POST_NOTIFICATIONS once on Android 13+; the result is handled by the worker's own check. */
@Composable
private fun NotificationPermissionRequest() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }
}
