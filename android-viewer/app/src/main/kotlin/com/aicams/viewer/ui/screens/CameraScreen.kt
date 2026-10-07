package com.aicams.viewer.ui.screens

import android.app.Activity
import android.graphics.Bitmap
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.NavHostController
import android.util.Log
import com.aicams.viewer.utils.Constants
import com.aicams.viewer.webrtc.WebRTCClient
import com.aicams.viewer.webrtc.StreamConnectionMode
import java.text.DateFormat
import java.util.Date

private const val TAG = "AICAMsCameraScreen"
private const val MAX_VISIBLE_MOTION_SNAPSHOTS = 3

private data class ReceivedMotionSnapshot(val timestampMs: Long, val bitmap: Bitmap)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen(
    navController: NavHostController,
    cameraName: String = "Living Room",
    cameraLocation: String = "Main Floor",
    cameraBaseUrl: String = Constants.API_BASE_URL,
    isInPipMode: Boolean = false,
    onConfigurePip: (Any, Boolean) -> Unit = { _, _ -> },
    onClearPip: (Any) -> Unit = {},
    onEnterPip: (Any) -> Boolean = { false }
) {
    var connectionState by remember { mutableStateOf("connecting") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var connectionMode by remember { mutableStateOf(StreamConnectionMode.RELAY_VIA_LAPTOP) }
    var motionDetected by remember { mutableStateOf<Boolean?>(null) }
    var receiverAudioEnabled by remember { mutableStateOf(true) }
    var isFullscreen by remember { mutableStateOf(false) }
    val motionSnapshots = remember { mutableStateListOf<ReceivedMotionSnapshot>() }
    val context = LocalContext.current
    val view = LocalView.current
    val activity = context as? Activity
    val motionTone = remember(context) { ToneGenerator(AudioManager.STREAM_ALARM, 60) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val pipOwner = remember { Any() }

    DisposableEffect(view) {
        val wasKeepingScreenOn = view.keepScreenOn
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = wasKeepingScreenOn }
    }

    LaunchedEffect(connectionState) {
        onConfigurePip(pipOwner, connectionState == "connected")
    }

    DisposableEffect(pipOwner) {
        onDispose { onClearPip(pipOwner) }
    }

    LaunchedEffect(isInPipMode) {
        if (isInPipMode) isFullscreen = false
    }

    DisposableEffect(motionTone) {
        onDispose { motionTone.release() }
    }

    DisposableEffect(activity, isFullscreen) {
        val window = activity?.window
        if (window != null) {
            val controller = WindowCompat.getInsetsController(window, view)
            if (isFullscreen) {
                controller.systemBarsBehavior =
                    androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            if (window != null) {
                WindowCompat.getInsetsController(window, view).show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    BackHandler(enabled = isFullscreen) { isFullscreen = false }

    Log.d(TAG, "CameraScreen opened: cameraName=$cameraName, location=$cameraLocation, baseUrl=$cameraBaseUrl")

    val client = remember(context, connectionMode) {
        WebRTCClient(
            context = context.applicationContext,
            cameraBaseUrl = cameraBaseUrl,
            onConnectionStateChanged = { state ->
                Log.d(TAG, "Connection state changed: $state")
                if (state == "CONNECTED") errorMessage = null
                connectionState = when (state) {
                    "CONNECTED" -> "connected"
                    "FAILED", "DISCONNECTED" -> "failed"
                    else -> "connecting"
                }
            },
            onStreamReady = {
                Log.d(TAG, "Remote stream ready; attaching renderer")
                errorMessage = null
                connectionState = "connected"
            },
            onError = { throwable ->
                Log.e(TAG, "WebRTC error while connecting to camera", throwable)
                errorMessage = throwable.message ?: "Unable to connect to camera stream"
                connectionState = "failed"
            },
            onMotionChanged = { detected ->
                mainHandler.post {
                    Log.i(TAG, "Received camera motion state: detected=$detected")
                    motionDetected = detected
                    if (detected) motionTone.startTone(ToneGenerator.TONE_PROP_BEEP, 220)
                }
            },
            onMotionSnapshot = { timestampMs, bitmap ->
                val existingIndex = motionSnapshots.indexOfFirst { it.timestampMs == timestampMs }
                if (existingIndex >= 0) {
                    motionSnapshots[existingIndex] = ReceivedMotionSnapshot(timestampMs, bitmap)
                } else {
                    motionSnapshots.add(0, ReceivedMotionSnapshot(timestampMs, bitmap))
                    motionSnapshots.sortByDescending { it.timestampMs }
                }
                while (motionSnapshots.size > MAX_VISIBLE_MOTION_SNAPSHOTS) {
                    motionSnapshots.removeAt(motionSnapshots.lastIndex)
                }
            }
        )
    }

    LaunchedEffect(client) {
        Log.d(TAG, "Starting WebRTC client for camera screen")
        errorMessage = null
        connectionState = "connecting"
        client.start(connectionMode = connectionMode)
    }

    DisposableEffect(client) {
        onDispose {
            Log.d(TAG, "Disposing CameraScreen and stopping WebRTC client")
            client.stop()
        }
    }

    Scaffold(
        topBar = {
            if (!isFullscreen && !isInPipMode) {
                TopAppBar(
                    title = { Text(cameraName) },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(if (isFullscreen || isInPipMode) Modifier else Modifier.padding(paddingValues).padding(16.dp)),
            verticalArrangement = if (isFullscreen || isInPipMode) Arrangement.Top else Arrangement.spacedBy(16.dp)
        ) {
            if (!isFullscreen && !isInPipMode) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StreamConnectionMode.values().forEach { mode ->
                        FilterChip(
                            selected = connectionMode == mode,
                            onClick = { connectionMode = mode },
                            label = { Text(mode.label()) }
                        )
                    }
                }
                Text(
                    text = when (connectionMode) {
                        StreamConnectionMode.RELAY_VIA_LAPTOP -> "Laptop relay carries the media stream."
                        StreamConnectionMode.P2P_VIA_LAPTOP_SIGNALING -> "Laptop handles signaling only; media should flow directly between phones."
                        StreamConnectionMode.P2P_LOCAL_WIFI -> "No laptop needed: discover the camera on the same Wi-Fi; media flows phone-to-phone."
                    },
                    style = MaterialTheme.typography.bodySmall
                )
            }
            ViewerVideoPanel(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .then(if (isFullscreen || isInPipMode) Modifier else Modifier.clip(RoundedCornerShape(18.dp))),
                client = client,
                connectionState = connectionState,
                cameraName = cameraName,
                errorMessage = errorMessage,
                motionDetected = motionDetected,
                receiverAudioEnabled = receiverAudioEnabled,
                isFullscreen = isFullscreen,
                isInPipMode = isInPipMode,
                onToggleAudio = {
                    receiverAudioEnabled = !receiverAudioEnabled
                    client.setRemoteAudioEnabled(receiverAudioEnabled)
                },
                onToggleFullscreen = { isFullscreen = !isFullscreen },
                onEnterPip = { onEnterPip(pipOwner) }
            )

            if (!isFullscreen && !isInPipMode && connectionMode != StreamConnectionMode.RELAY_VIA_LAPTOP && motionSnapshots.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Recent motion snapshots", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        motionSnapshots.forEach { snapshot ->
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Image(
                                    bitmap = snapshot.bitmap.asImageBitmap(),
                                    contentDescription = "Motion snapshot at ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(snapshot.timestampMs))}",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(width = 88.dp, height = 54.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                )
                                Text(
                                    DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(snapshot.timestampMs)),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
            }

            if (!isFullscreen && !isInPipMode) {
                Text(
                    text = "Location: $cameraLocation",
                    style = MaterialTheme.typography.bodyLarge
                )

                Text(
                    text = when (connectionState) {
                        "connected" -> "Status: Live"
                        "failed" -> "Status: Not connected"
                        else -> "Status: Connecting"
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = when (connectionState) {
                        "failed" -> MaterialTheme.colorScheme.error
                        "connected" -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.primary
                    }
                )

                if (connectionState == "failed") {
                    Button(
                        onClick = {
                            errorMessage = null
                            connectionState = "connecting"
                            client.start(connectionMode = connectionMode)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Retry")
                    }
                }

                Button(
                    onClick = { navController.popBackStack() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Back to Cameras")
                }
            }
        }
    }
}

private fun StreamConnectionMode.label(): String = when (this) {
    StreamConnectionMode.RELAY_VIA_LAPTOP -> "Laptop Relay"
    StreamConnectionMode.P2P_VIA_LAPTOP_SIGNALING -> "Laptop P2P"
    StreamConnectionMode.P2P_LOCAL_WIFI -> "Local Wi-Fi"
}

@Composable
private fun ViewerVideoPanel(
    modifier: Modifier,
    client: WebRTCClient,
    connectionState: String,
    cameraName: String,
    errorMessage: String?,
    motionDetected: Boolean?,
    receiverAudioEnabled: Boolean,
    isFullscreen: Boolean,
    isInPipMode: Boolean,
    onToggleAudio: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onEnterPip: () -> Unit
) {
    Box(
        modifier = modifier.background(
            if (connectionState == "failed") Color(0xFF2B1A1A) else Color(0xFF1E1E1E)
        ),
        contentAlignment = Alignment.Center
    ) {
        if (connectionState == "connected") {
            AndroidView(
                factory = { client.getRemoteRenderer() },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (connectionState == "failed") "Connection failed" else "Live Camera",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = if (connectionState == "failed") {
                        errorMessage ?: "Unable to connect to the camera. Check the camera and signaling service."
                    } else {
                        "Connecting to $cameraName..."
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }

        if (connectionState == "connected" && !isInPipMode) {
            Surface(
                modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
                shape = RoundedCornerShape(20.dp),
                color = if (motionDetected == true) {
                    MaterialTheme.colorScheme.errorContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            ) {
                Text(
                    text = when (motionDetected) {
                        true -> "● Motion detected"
                        false -> "● No motion"
                        null -> "● Waiting for motion status"
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    color = if (motionDetected == true) MaterialTheme.colorScheme.onErrorContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }

        if (!isInPipMode) {
            Row(
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                FilledTonalIconButton(
                    onClick = onToggleAudio,
                    enabled = connectionState == "connected",
                    modifier = Modifier.size(52.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = if (receiverAudioEnabled) MaterialTheme.colorScheme.secondaryContainer
                        else MaterialTheme.colorScheme.errorContainer,
                        contentColor = if (receiverAudioEnabled) MaterialTheme.colorScheme.onSecondaryContainer
                        else MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Icon(
                        imageVector = if (receiverAudioEnabled) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff,
                        contentDescription = if (receiverAudioEnabled) "Mute received audio" else "Unmute received audio"
                    )
                }
                FilledTonalIconButton(
                    onClick = onToggleFullscreen,
                    enabled = connectionState == "connected",
                    modifier = Modifier.size(52.dp)
                ) {
                    Icon(
                        imageVector = if (isFullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                        contentDescription = if (isFullscreen) "Exit fullscreen" else "Enter fullscreen"
                    )
                }
                FilledTonalIconButton(
                    onClick = onEnterPip,
                    enabled = connectionState == "connected",
                    modifier = Modifier.size(52.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.PictureInPictureAlt,
                        contentDescription = "Enter picture-in-picture"
                    )
                }
            }
        }
    }
}
