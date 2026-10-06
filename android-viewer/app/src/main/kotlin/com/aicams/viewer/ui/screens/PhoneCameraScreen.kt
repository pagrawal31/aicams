package com.aicams.viewer.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavHostController
import com.aicams.viewer.utils.Constants
import com.aicams.viewer.webrtc.PhoneCameraPublisher
import com.aicams.viewer.webrtc.StreamConnectionMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneCameraScreen(navController: NavHostController) {
    var status by remember { mutableStateOf("Camera is stopped") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isStreaming by remember { mutableStateOf(false) }
    var isFrontCamera by remember { mutableStateOf(false) }
    var audioAvailable by remember { mutableStateOf(false) }
    var audioEnabled by remember { mutableStateOf(true) }
    var connectionMode by remember { mutableStateOf(StreamConnectionMode.RELAY_VIA_LAPTOP) }
    var motionDetectionActive by remember { mutableStateOf(false) }
    var motionDetected by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val publisher = remember(context) {
        PhoneCameraPublisher(
            context = context.applicationContext,
            serverBaseUrl = Constants.API_BASE_URL,
            onStateChanged = { status = it },
            onError = { errorMessage = it.message ?: "Unable to publish camera" },
            onMotionChanged = { motionDetected = it }
        )
    }
    val mediaPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        if (granted[Manifest.permission.CAMERA] == true) {
            errorMessage = null
            audioAvailable = granted[Manifest.permission.RECORD_AUDIO] == true
            audioEnabled = audioAvailable
            isStreaming = true
            motionDetectionActive = true
            motionDetected = false
            publisher.start(audioEnabled = audioAvailable, connectionMode = connectionMode)
            if (!audioAvailable) {
                errorMessage = "Microphone permission was not granted; streaming video only."
            }
        } else {
            errorMessage = "Camera permission is required to stream from this phone."
        }
    }

    DisposableEffect(publisher) {
        onDispose { publisher.stop() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Use Phone as Camera") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = when (connectionMode) {
                    StreamConnectionMode.RELAY_VIA_LAPTOP -> "Media relay: ${Constants.API_BASE_URL}"
                    StreamConnectionMode.P2P_VIA_LAPTOP_SIGNALING -> "Laptop signaling: ${Constants.API_BASE_URL}"
                    StreamConnectionMode.P2P_LOCAL_WIFI -> "Local Wi-Fi signaling (no laptop)"
                },
                style = MaterialTheme.typography.bodyMedium
            )
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StreamConnectionMode.values().forEach { mode ->
                    FilterChip(
                        selected = connectionMode == mode,
                        onClick = { if (!isStreaming) connectionMode = mode },
                        label = { Text(mode.label()) },
                        enabled = !isStreaming
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .padding(2.dp),
                contentAlignment = Alignment.Center
            ) {
                AndroidView(
                    factory = { publisher.getPreviewRenderer() },
                    modifier = Modifier.fillMaxSize()
                )
                if (motionDetectionActive) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(12.dp),
                        shape = RoundedCornerShape(20.dp),
                        color = if (motionDetected) {
                            MaterialTheme.colorScheme.errorContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    ) {
                        Text(
                            text = if (motionDetected) "● Motion detected" else "● No motion",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            color = if (motionDetected) {
                                MaterialTheme.colorScheme.onErrorContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
                if (isStreaming) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        FilledTonalIconButton(
                            onClick = {
                                publisher.switchCamera(
                                    onCameraFacingChanged = { isFrontCamera = it },
                                    onSwitchFailed = { errorMessage = it }
                                )
                            },
                            modifier = Modifier.size(56.dp),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Cameraswitch,
                                contentDescription = if (isFrontCamera) "Switch to back camera" else "Switch to front camera"
                            )
                        }
                        if (audioAvailable) {
                            FilledTonalIconButton(
                                onClick = {
                                    audioEnabled = !audioEnabled
                                    publisher.setAudioEnabled(audioEnabled)
                                },
                                modifier = Modifier.size(56.dp),
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = if (audioEnabled) {
                                        MaterialTheme.colorScheme.secondaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.errorContainer
                                    },
                                    contentColor = if (audioEnabled) {
                                        MaterialTheme.colorScheme.onSecondaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onErrorContainer
                                    }
                                )
                            ) {
                                Icon(
                                    imageVector = if (audioEnabled) Icons.Filled.Mic else Icons.Filled.MicOff,
                                    contentDescription = if (audioEnabled) "Mute microphone" else "Unmute microphone"
                                )
                            }
                        }
                    }
                }
            }
            Text("Status: $status", style = MaterialTheme.typography.titleMedium)
            errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Text(
                when (connectionMode) {
                    StreamConnectionMode.RELAY_VIA_LAPTOP -> "Keep this screen open while streaming. Start the laptop relay first; media passes through the laptop."
                    StreamConnectionMode.P2P_VIA_LAPTOP_SIGNALING -> "Keep this screen open. Start the laptop service for signaling; media should flow directly between phones."
                    StreamConnectionMode.P2P_LOCAL_WIFI -> "Keep this screen open and both phones on the same home Wi-Fi. This phone advertises itself; no laptop is needed."
                },
                style = MaterialTheme.typography.bodySmall
            )
            Button(
                onClick = {
                    mediaPermissionLauncher.launch(
                        arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
                    )
                },
                enabled = !isStreaming,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (connectionMode == StreamConnectionMode.RELAY_VIA_LAPTOP) "Start Camera + Audio Stream" else "Start ${connectionMode.label()} Camera")
            }
            Button(onClick = { navController.popBackStack() }, modifier = Modifier.fillMaxWidth()) {
                Text("Stop and Return")
            }
        }
    }
}

private fun StreamConnectionMode.label(): String = when (this) {
    StreamConnectionMode.RELAY_VIA_LAPTOP -> "Laptop Relay"
    StreamConnectionMode.P2P_VIA_LAPTOP_SIGNALING -> "Laptop P2P"
    StreamConnectionMode.P2P_LOCAL_WIFI -> "Local Wi-Fi"
}
