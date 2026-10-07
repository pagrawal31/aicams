package com.aicams.viewer

import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
    private val isInPipMode = androidx.compose.runtime.mutableStateOf(false)
    private var pipOwner: Any? = null
    private var pipEnabled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val inPipMode by isInPipMode
            AICAMsViewerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(
                        isInPipMode = inPipMode,
                        onConfigurePip = ::configurePictureInPicture,
                        onClearPip = ::clearPictureInPicture,
                        onEnterPip = ::enterPictureInPicture
                    )
                }
            }
        }
    }

    fun configurePictureInPicture(owner: Any, enabled: Boolean) {
        pipOwner = owner
        pipEnabled = enabled
        if (supportsPictureInPicture()) {
            setPictureInPictureParams(buildPipParams(enabled))
        }
    }

    fun clearPictureInPicture(owner: Any) {
        if (pipOwner !== owner) return
        pipOwner = null
        pipEnabled = false
        if (supportsPictureInPicture()) {
            setPictureInPictureParams(buildPipParams(false))
        }
    }

    fun enterPictureInPicture(owner: Any): Boolean {
        if (
            pipOwner !== owner ||
            !pipEnabled ||
            !supportsPictureInPicture() ||
            isInPictureInPictureMode
        ) return false
        return enterPictureInPictureMode(buildPipParams(true))
    }

    private fun supportsPictureInPicture(): Boolean =
        packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    private fun buildPipParams(enabled: Boolean): PictureInPictureParams {
        return PictureInPictureParams.Builder()
            .setAspectRatio(Rational(16, 9))
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setAutoEnterEnabled(enabled)
                    setSeamlessResizeEnabled(true)
                }
            }
            .build()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Android 12+ uses auto-enter for a smoother swipe-to-home animation.
        if (
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S &&
            pipEnabled &&
            supportsPictureInPicture() &&
            !isInPictureInPictureMode
        ) {
            enterPictureInPictureMode(buildPipParams(true))
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPipMode.value = isInPictureInPictureMode
    }
}

@Composable
fun AppNavigation(
    isInPipMode: Boolean,
    onConfigurePip: (Any, Boolean) -> Unit,
    onClearPip: (Any) -> Unit,
    onEnterPip: (Any) -> Boolean
) {
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
            PhoneCameraScreen(
                navController = navController,
                isInPipMode = isInPipMode,
                onConfigurePip = onConfigurePip,
                onClearPip = onClearPip,
                onEnterPip = onEnterPip
            )
        }
        composable("camera") {
            CameraScreen(
                navController = navController,
                isInPipMode = isInPipMode,
                onConfigurePip = onConfigurePip,
                onClearPip = onClearPip,
                onEnterPip = onEnterPip
            )
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
                cameraBaseUrl = cameraBaseUrl,
                isInPipMode = isInPipMode,
                onConfigurePip = onConfigurePip,
                onClearPip = onClearPip,
                onEnterPip = onEnterPip
            )
        }
    }
}
