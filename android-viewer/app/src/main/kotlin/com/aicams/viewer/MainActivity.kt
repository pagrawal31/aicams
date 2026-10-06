package com.aicams.viewer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.aicams.viewer.ui.screens.CameraScreen
import com.aicams.viewer.ui.screens.DashboardScreen
import com.aicams.viewer.ui.screens.LoginScreen
import com.aicams.viewer.ui.screens.PhoneCameraScreen
import com.aicams.viewer.ui.theme.AICAMsViewerTheme
import dagger.hilt.android.AndroidEntryPoint
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AICAMsViewerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                }
            }
        }
    }
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    
    NavHost(
        navController = navController,
        startDestination = "dashboard"
    ) {
        composable("login") {
            LoginScreen(navController)
        }
        composable("dashboard") {
            DashboardScreen(navController)
        }
        composable("phone-camera") {
            PhoneCameraScreen(navController)
        }
        composable("camera") {
            CameraScreen(navController)
        }
        composable("camera/{cameraName}/{cameraLocation}/{cameraBaseUrl}") { backStackEntry ->
            val cameraName = backStackEntry.arguments?.getString("cameraName")?.let {
                URLDecoder.decode(it, StandardCharsets.UTF_8.toString())
            } ?: "Living Room"
            val cameraLocation = backStackEntry.arguments?.getString("cameraLocation")?.let {
                URLDecoder.decode(it, StandardCharsets.UTF_8.toString())
            } ?: "Main Floor"
            val cameraBaseUrl = backStackEntry.arguments?.getString("cameraBaseUrl")?.let {
                URLDecoder.decode(it, StandardCharsets.UTF_8.toString())
            } ?: "http://10.0.2.2:8000"
            CameraScreen(
                navController = navController,
                cameraName = cameraName,
                cameraLocation = cameraLocation,
                cameraBaseUrl = cameraBaseUrl
            )
        }
    }
}
