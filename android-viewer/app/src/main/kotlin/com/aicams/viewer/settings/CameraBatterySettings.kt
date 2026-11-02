package com.aicams.viewer.settings

import android.content.Context

/** User-controlled camera-side power savings. All options are inert until [enabled] is true. */
data class CameraBatterySettings(
    val enabled: Boolean = false,
    val reduceVideoQuality: Boolean = true,
    val lowPowerWhileWaiting: Boolean = true,
    val disableLocalPreview: Boolean = true,
    val microphoneOffByDefault: Boolean = true,
    val allowScreenSleep: Boolean = true,
    val limitVideoBitrate: Boolean = true
) {
    val useReducedVideoQuality: Boolean get() = enabled && reduceVideoQuality
    val useLowPowerWhileWaiting: Boolean get() = enabled && lowPowerWhileWaiting
    val suppressLocalPreview: Boolean get() = enabled && disableLocalPreview
    val startWithMicrophoneOff: Boolean get() = enabled && microphoneOffByDefault
    val permitScreenSleep: Boolean get() = enabled && allowScreenSleep
    val capVideoBitrate: Boolean get() = enabled && limitVideoBitrate
}

class CameraBatterySettingsStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    fun load(): CameraBatterySettings = CameraBatterySettings(
        enabled = preferences.getBoolean(KEY_ENABLED, false),
        reduceVideoQuality = preferences.getBoolean(KEY_REDUCE_VIDEO_QUALITY, true),
        lowPowerWhileWaiting = preferences.getBoolean(KEY_LOW_POWER_WAITING, true),
        disableLocalPreview = preferences.getBoolean(KEY_DISABLE_PREVIEW, true),
        microphoneOffByDefault = preferences.getBoolean(KEY_MICROPHONE_OFF, true),
        allowScreenSleep = preferences.getBoolean(KEY_ALLOW_SCREEN_SLEEP, true),
        limitVideoBitrate = preferences.getBoolean(KEY_LIMIT_BITRATE, true)
    )

    fun save(settings: CameraBatterySettings) {
        preferences.edit()
            .putBoolean(KEY_ENABLED, settings.enabled)
            .putBoolean(KEY_REDUCE_VIDEO_QUALITY, settings.reduceVideoQuality)
            .putBoolean(KEY_LOW_POWER_WAITING, settings.lowPowerWhileWaiting)
            .putBoolean(KEY_DISABLE_PREVIEW, settings.disableLocalPreview)
            .putBoolean(KEY_MICROPHONE_OFF, settings.microphoneOffByDefault)
            .putBoolean(KEY_ALLOW_SCREEN_SLEEP, settings.allowScreenSleep)
            .putBoolean(KEY_LIMIT_BITRATE, settings.limitVideoBitrate)
            .apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "camera_battery_settings"
        const val KEY_ENABLED = "enabled"
        const val KEY_REDUCE_VIDEO_QUALITY = "reduce_video_quality"
        const val KEY_LOW_POWER_WAITING = "low_power_while_waiting"
        const val KEY_DISABLE_PREVIEW = "disable_local_preview"
        const val KEY_MICROPHONE_OFF = "microphone_off_by_default"
        const val KEY_ALLOW_SCREEN_SLEEP = "allow_screen_sleep"
        const val KEY_LIMIT_BITRATE = "limit_video_bitrate"
    }
}
