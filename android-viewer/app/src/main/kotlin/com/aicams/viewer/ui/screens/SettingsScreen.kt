package com.aicams.viewer.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.aicams.viewer.settings.CameraBatterySettings
import com.aicams.viewer.settings.CameraBatterySettingsStore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavHostController) {
    val context = LocalContext.current
    val store = remember(context) { CameraBatterySettingsStore(context) }
    var settings by remember { mutableStateOf(store.load()) }

    fun update(transform: (CameraBatterySettings) -> CameraBatterySettings) {
        settings = transform(settings)
        store.save(settings)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Camera battery", style = MaterialTheme.typography.titleLarge)
            SettingSwitch(
                title = "Battery Saver profile",
                description = "Enable the selected camera-side power savings below.",
                checked = settings.enabled,
                onCheckedChange = { enabled -> update { it.copy(enabled = enabled) } }
            )
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            SettingSwitch(
                title = "Reduced streaming quality",
                description = "Use 640×360 at 15 fps instead of 1280×720 at 24 fps.",
                checked = settings.reduceVideoQuality,
                enabled = settings.enabled,
                onCheckedChange = { value -> update { it.copy(reduceVideoQuality = value) } }
            )
            SettingSwitch(
                title = "Low-power waiting mode",
                description = "Use 320×180 at 3 fps until a viewer connects.",
                checked = settings.lowPowerWhileWaiting,
                enabled = settings.enabled,
                onCheckedChange = { value -> update { it.copy(lowPowerWhileWaiting = value) } }
            )
            SettingSwitch(
                title = "Disable local preview",
                description = "Do not render camera frames on the camera phone.",
                checked = settings.disableLocalPreview,
                enabled = settings.enabled,
                onCheckedChange = { value -> update { it.copy(disableLocalPreview = value) } }
            )
            SettingSwitch(
                title = "Microphone off by default",
                description = "Start video-only; audio can still be enabled from the camera screen.",
                checked = settings.microphoneOffByDefault,
                enabled = settings.enabled,
                onCheckedChange = { value -> update { it.copy(microphoneOffByDefault = value) } }
            )
            SettingSwitch(
                title = "Allow screen sleep",
                description = "Keep streaming with a foreground-service notification instead of holding the display on.",
                checked = settings.allowScreenSleep,
                enabled = settings.enabled,
                onCheckedChange = { value -> update { it.copy(allowScreenSleep = value) } }
            )
            SettingSwitch(
                title = "Limit video bitrate",
                description = "Cap outgoing video near 500 kbps to reduce encoder and Wi-Fi work.",
                checked = settings.limitVideoBitrate,
                enabled = settings.enabled,
                onCheckedChange = { value -> update { it.copy(limitVideoBitrate = value) } }
            )
            Text(
                "Changes apply the next time the phone camera is started.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.38f)
            )
        }
        Spacer(Modifier.width(16.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}
