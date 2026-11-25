package com.example.bmicalci.musclemirror

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bmicalci.ui.components.GlowButton
import com.example.bmicalci.ui.components.OutlineChipButton
import com.example.bmicalci.ui.theme.Neon
import com.example.bmicalci.ui.theme.Space

private enum class PermissionState { Unknown, Granted, Denied, PermanentlyDenied }

/** Muscle Mirror entry screen: permission flow + live front-camera preview (Milestone 1, stage 1). */
@Composable
fun MuscleMirrorScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val activity = context as? android.app.Activity

    var permission by remember {
        mutableStateOf(
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
                PermissionState.Granted else PermissionState.Unknown,
        )
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permission = when {
            granted -> PermissionState.Granted
            activity != null && !androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA) ->
                PermissionState.PermanentlyDenied
            else -> PermissionState.Denied
        }
    }

    // Re-check when the user comes back from system settings.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
            ) permission = PermissionState.Granted
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    Column(Modifier.fillMaxSize().background(Neon.Bg)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Space.md, vertical = Space.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            OutlineChipButton("Back", onClick = onBack)
            Text("Muscle Mirror", color = Neon.TextPrimary, style = MaterialTheme.typography.titleMedium)
        }
        when (permission) {
            PermissionState.Granted -> CameraPreviewArea(Modifier.weight(1f).fillMaxWidth())
            else -> PermissionPrompt(
                state = permission,
                onRequest = { launcher.launch(Manifest.permission.CAMERA) },
                onOpenSettings = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                    )
                },
            )
        }
    }
}

@Composable
private fun PermissionPrompt(state: PermissionState, onRequest: () -> Unit, onOpenSettings: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(Space.lg),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Camera access needed",
            color = Neon.TextPrimary,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Text(
            "Muscle Mirror watches your body through the live camera to track your skeleton. " +
                "Video never leaves your phone.",
            color = Neon.TextSecondary,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Space.sm, bottom = Space.md),
        )
        if (state == PermissionState.PermanentlyDenied) {
            Text(
                "Camera permission is blocked. Open Settings and allow Camera.",
                color = Neon.Orange,
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = Space.md),
            )
            GlowButton("Open Settings", onClick = onOpenSettings)
        } else {
            GlowButton("Allow Camera", onClick = onRequest)
        }
    }
}

@Composable
private fun CameraPreviewArea(modifier: Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val controller = remember { CameraController(context) }
    val processor = remember { PoseLandmarkerProcessor(context) }
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    var status by remember { mutableStateOf("Starting camera...") }
    val vm: MuscleMirrorViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val raw by processor.frames.collectAsStateWithLifecycle()
    val info by processor.info.collectAsStateWithLifecycle()
    val ui by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(raw) { vm.onFrame(raw) }
    val frame = ui.frame

    // Bind once per lifecycle owner (not on every recomposition, frames update ~30x/s).
    DisposableEffect(lifecycleOwner) {
        controller.bind(
            owner = lifecycleOwner,
            previewView = previewView,
            analyzer = processor,
            onBound = { status = "Front camera live" },
            onError = { status = "Camera error: ${it.message}" },
        )
        onDispose { controller.unbind() }
    }
    DisposableEffect(Unit) {
        onDispose {
            controller.shutdown()
            processor.close()
            vm.onCameraStopped()
        }
    }

    Box(modifier) {
        AndroidView(modifier = Modifier.fillMaxSize(), factory = { previewView })
        PoseOverlay(frame = frame, previewView = previewView, modifier = Modifier.fillMaxSize())
        Text(
            "$status | $info | ${ui.fps} fps" + (frame?.let { " | ${it.inferenceMs} ms" } ?: ""),
            color = Neon.TextPrimary,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(Space.sm)
                .background(Neon.Surface.copy(alpha = 0.75f))
                .padding(horizontal = Space.sm, vertical = Space.xs),
        )
        val ready = ui.calibration == CalibrationState.READY
        Text(
            ui.guidance,
            color = if (ready) Neon.TextPrimary else Neon.Orange,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(Space.md)
                .background(Neon.Surface.copy(alpha = 0.85f))
                .padding(Space.md),
        )
    }
}
