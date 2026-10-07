package com.aicams.viewer.webrtc

import android.content.Context
import org.webrtc.BuiltinAudioDecoderFactoryFactory
import org.webrtc.BuiltinAudioEncoderFactoryFactory
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.PeerConnectionFactory

/**
 * Process-wide WebRTC resources.
 *
 * WebRTC's network monitor and worker threads are process-global. Reinitializing and disposing
 * their backing factory as Compose screens enter and leave can race with network/sleep callbacks
 * in native code. These resources therefore live for the lifetime of the application process;
 * individual publishers and viewers still own and dispose their peer connections and tracks.
 */
internal object WebRtcRuntime {
    private lateinit var eglBase: EglBase
    private lateinit var peerConnectionFactory: PeerConnectionFactory

    @Volatile
    private var initialized = false

    @Synchronized
    fun initialize(context: Context) {
        if (initialized) return

        val appContext = context.applicationContext
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(appContext)
                .createInitializationOptions()
        )

        eglBase = EglBase.create()
        peerConnectionFactory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase.eglBaseContext))
            .setAudioEncoderFactoryFactory(BuiltinAudioEncoderFactoryFactory())
            .setAudioDecoderFactoryFactory(BuiltinAudioDecoderFactoryFactory())
            .createPeerConnectionFactory()

        initialized = true
    }

    fun factory(context: Context): PeerConnectionFactory {
        initialize(context)
        return peerConnectionFactory
    }

    fun eglContext(context: Context): EglBase.Context {
        initialize(context)
        return eglBase.eglBaseContext
    }
}
