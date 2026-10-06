package com.aicams.viewer.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.aicams.viewer.data.CoordinatorService
import com.aicams.viewer.data.RegisterCameraRequest
import com.aicams.viewer.utils.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class Camera(
    val id: String,
    val name: String,
    val location: String,
    val isOnline: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(navController: NavHostController) {
    val context = LocalContext.current
    val service = remember { CoordinatorService() }
    var cameras by remember {
        mutableStateOf(listOf(
            Camera("cam-1", "Living Room", "Main Floor", true),
            Camera("cam-2", "Bedroom", "Second Floor", false),
            Camera("cam-3", "Garage", "Garage", true)
        ))
    }
    var isLoading by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        isLoading = true
        try {
            val networkCameras = withContext(Dispatchers.IO) { service.listCameras() }
            cameras = networkCameras.map { device ->
                Camera(
                    id = device.id,
                    name = device.name,
                    location = device.location,
                    isOnline = device.status == "online"
                )
            }
        } catch (_: Throwable) {
            cameras = listOf(
                Camera("cam-1", "Living Room", "Main Floor", true),
                Camera("cam-2", "Bedroom", "Second Floor", false),
                Camera("cam-3", "Garage", "Garage", true)
            )
        } finally {
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Cameras") },
                actions = {
                    IconButton(onClick = { navController.navigate("phone-camera") }) {
                        Text("📷", style = MaterialTheme.typography.titleLarge)
                    }
                    IconButton(onClick = { navController.navigate("login") }) {
                        Icon(Icons.Filled.ExitToApp, "Logout")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                scope.launch {
                    try {
                        val localIp = "10.0.2.2"
                        val camera = withContext(Dispatchers.IO) {
                            service.registerCamera(
                                RegisterCameraRequest(
                                    deviceId = "camera-${System.currentTimeMillis()}",
                                    name = "My Phone Camera",
                                    location = "Home",
                                    ip = localIp,
                                    port = 8000,
                                    deviceType = "camera",
                                    capabilities = listOf("video")
                                )
                            )
                        }
                        cameras = cameras + Camera(
                            id = camera.id,
                            name = camera.name,
                            location = camera.location,
                            isOnline = true
                        )
                    } catch (_: Throwable) {
                        // no-op for local MVP debug mode
                    }
                }
            }) {
                Icon(Icons.Filled.Add, "Add Camera")
            }
        }
    ) { paddingValues ->
        if (isLoading && cameras.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (cameras.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text(
                        text = "No Cameras",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        text = "Tap + to add a camera device",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(cameras) { camera ->
                    CameraCard(camera = camera, onClick = {
                        val route = listOf(
                            URLEncoder.encode(camera.name, StandardCharsets.UTF_8.toString()),
                            URLEncoder.encode(camera.location, StandardCharsets.UTF_8.toString()),
                            URLEncoder.encode(Constants.API_BASE_URL, StandardCharsets.UTF_8.toString())
                        ).joinToString("/")
                        navController.navigate("camera/$route")
                    })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraCard(camera: Camera, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp),
        shape = RoundedCornerShape(12.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status indicator
            Surface(
                modifier = Modifier
                    .size(60.dp)
                    .padding(end = 16.dp),
                shape = RoundedCornerShape(8.dp),
                color = if (camera.isOnline) 
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                else 
                    MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = if (camera.isOnline) "●" else "○",
                        color = if (camera.isOnline) 
                            MaterialTheme.colorScheme.primary 
                        else 
                            MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.displaySmall
                    )
                }
            }
            
            // Camera info
            Column(
                modifier = Modifier
                    .weight(1f)
            ) {
                Text(
                    text = camera.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = camera.location,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
                Text(
                    text = if (camera.isOnline) "Online" else "Offline",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (camera.isOnline) 
                        MaterialTheme.colorScheme.primary 
                    else 
                        MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}
