package com.aicams.viewer.webrtc

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.webrtc.CapturerObserver
import org.json.JSONObject
import org.webrtc.BuiltinAudioDecoderFactoryFactory
import org.webrtc.BuiltinAudioEncoderFactoryFactory
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoSource
import org.webrtc.VideoFrame
import org.webrtc.VideoTrack
import org.webrtc.DataChannel
import com.aicams.viewer.utils.Constants
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "AICAMsPhonePublisher"

/** Captures this Android device's camera and publishes it to the laptop relay. */
class PhoneCameraPublisher(
    context: Context,
    private val serverBaseUrl: String,
    private val onStateChanged: (String) -> Unit,
    private val onError: (Throwable) -> Unit,
    private val onMotionChanged: (Boolean) -> Unit
) {
    private val appContext = context.applicationContext
    private val diagnostics = ResourceDiagnostics(appContext, TAG, "camera")
    private val snapshotStore = MotionSnapshotStore(appContext)
    private val eglBase = EglBase.create()
    private val renderer = SurfaceViewRenderer(context).apply {
        init(eglBase.eglBaseContext, null)
        setEnableHardwareScaler(true)
        setMirror(true)
    }
    private val httpClient = OkHttpClient()
    private val offerSent = AtomicBoolean(false)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val motionDetector = MotionDetector()
    private var lastMotionAnalysisAt = 0L
    private var publishedInitialMotionState = false

    private val factory: PeerConnectionFactory
    private var capturer: CameraVideoCapturer? = null
    private var textureHelper: SurfaceTextureHelper? = null
    private var videoSource: VideoSource? = null
    private var videoTrack: VideoTrack? = null
    private var audioSource: AudioSource? = null
    private var audioTrack: AudioTrack? = null
    private var peerConnection: PeerConnection? = null
    private var localDescriptionReady = false
    private var started = false
    private var frontCamera = false
    private var connectionMode = StreamConnectionMode.RELAY_VIA_LAPTOP
    private var p2pSessionId: String? = null
    private var signalingTransport: SignalingTransport? = null
    private var localSignalingServer: LocalWifiSignalingServer? = null
    private var snapshotsDataChannel: DataChannel? = null

    init {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(appContext)
                .createInitializationOptions()
        )
        factory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase.eglBaseContext))
            .setAudioEncoderFactoryFactory(BuiltinAudioEncoderFactoryFactory())
            .setAudioDecoderFactoryFactory(BuiltinAudioDecoderFactoryFactory())
            .createPeerConnectionFactory()
    }

    fun getPreviewRenderer(): SurfaceViewRenderer = renderer

    @Synchronized
    fun start(
        audioEnabled: Boolean = true,
        connectionMode: StreamConnectionMode = StreamConnectionMode.RELAY_VIA_LAPTOP
    ) {
        if (started) return
        started = true
        this.connectionMode = connectionMode
        diagnostics.start(
            connectionProvider = { peerConnection },
            modeProvider = { connectionMode.name }
        )
        onStateChanged("starting camera")
        Thread({ startCaptureAndNegotiate(audioEnabled) }, "PhoneCameraPublisher").start()
    }

    private fun startCaptureAndNegotiate(audioEnabled: Boolean) {
        try {
            val enumerator = Camera2Enumerator(appContext)
            val cameraName = enumerator.deviceNames.firstOrNull { enumerator.isBackFacing(it) }
                ?: enumerator.deviceNames.firstOrNull()
                ?: throw IllegalStateException("No camera is available on this phone")
            frontCamera = enumerator.isFrontFacing(cameraName)
            val cameraCapturer = enumerator.createCapturer(cameraName, null)
                ?: throw IllegalStateException("Could not open the phone camera")
            capturer = cameraCapturer

            val source = factory.createVideoSource(false)
            videoSource = source
            val helper = SurfaceTextureHelper.create("PhoneCameraCapture", eglBase.eglBaseContext)
                ?: throw IllegalStateException("Could not initialize camera texture")
            textureHelper = helper
            val sourceObserver = source.capturerObserver
            cameraCapturer.initialize(helper, appContext, object : CapturerObserver {
                override fun onCapturerStarted(success: Boolean) {
                    sourceObserver.onCapturerStarted(success)
                }

                override fun onCapturerStopped() {
                    sourceObserver.onCapturerStopped()
                }

                override fun onFrameCaptured(frame: VideoFrame) {
                    analyzeMotion(frame)
                    sourceObserver.onFrameCaptured(frame)
                }
            })
            cameraCapturer.startCapture(1280, 720, 24)

            val track = factory.createVideoTrack("phone-camera-video", source)
            videoTrack = track
            track.setEnabled(true)
            track.addSink(renderer)

            if (audioEnabled) {
                val microphoneSource = factory.createAudioSource(MediaConstraints())
                audioSource = microphoneSource
                val microphoneTrack = factory.createAudioTrack("phone-camera-audio", microphoneSource)
                audioTrack = microphoneTrack
                microphoneTrack.setEnabled(true)
            }

            when (connectionMode) {
                StreamConnectionMode.RELAY_VIA_LAPTOP -> {
                    signalingTransport = createLaptopSignalingTransport()
                    startRelayPublishing(track)
                }
                StreamConnectionMode.P2P_VIA_LAPTOP_SIGNALING -> {
                    signalingTransport = createLaptopSignalingTransport()
                    onStateChanged("registering through laptop signaling")
                }
                StreamConnectionMode.P2P_LOCAL_WIFI -> {
                    val localServer = LocalWifiSignalingServer(
                        context = appContext,
                        onMessage = ::handleP2PSignalingMessage,
                        onState = { message ->
                            onStateChanged(message)
                            if (message.startsWith("local signaling error") || message.startsWith("local discovery failed")) {
                                onError(IllegalStateException(message))
                            }
                        }
                    )
                    localSignalingServer = localServer
                    signalingTransport = localServer
                    localServer.start()
                }
            }
        } catch (error: Throwable) {
            fail(error)
        }
    }

    private fun createLaptopSignalingTransport() = P2PSignalingClient(
        baseUrl = serverBaseUrl,
        role = "camera",
        deviceId = Constants.P2P_DEVICE_ID,
        onMessage = ::handleP2PSignalingMessage,
        onState = { signalingState ->
            if (signalingState == "signaling connected" && connectionMode == StreamConnectionMode.P2P_VIA_LAPTOP_SIGNALING) {
                onStateChanged("waiting for P2P viewer")
            } else if (signalingState.startsWith("signaling error")) {
                Log.w(TAG, signalingState)
                if (connectionMode != StreamConnectionMode.RELAY_VIA_LAPTOP) {
                    onError(IllegalStateException(signalingState))
                }
            }
        }
    )

    private fun createPeerConnection(): PeerConnection {
        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer()
        )
        return factory.createPeerConnection(PeerConnection.RTCConfiguration(iceServers), createPeerObserver())
            ?: throw IllegalStateException("Could not create WebRTC peer connection")
    }

    private fun startRelayPublishing(track: VideoTrack) {
        val connection = createPeerConnection()
        peerConnection = connection
        connection.addTrack(track, listOf("phone-camera"))
        audioTrack?.let { connection.addTrack(it, listOf("phone-camera")) }

        onStateChanged("connecting to laptop relay")
        connection.createOffer(object : SdpObserver {
            override fun onCreateSuccess(description: SessionDescription?) {
                if (description == null) {
                    fail(IllegalStateException("WebRTC created an empty offer"))
                    return
                }
                connection.setLocalDescription(object : SdpObserver {
                    override fun onSetSuccess() {
                        localDescriptionReady = true
                        sendOfferWhenIceReady(connection)
                    }
                    override fun onSetFailure(error: String?) = fail(IllegalStateException(error ?: "Could not set local offer"))
                    override fun onCreateSuccess(description: SessionDescription?) = Unit
                    override fun onCreateFailure(error: String?) = fail(IllegalStateException(error ?: "Could not create local offer"))
                }, description)
            }
            override fun onSetSuccess() = Unit
            override fun onSetFailure(error: String?) = fail(IllegalStateException(error ?: "Could not set offer"))
            override fun onCreateFailure(error: String?) = fail(IllegalStateException(error ?: "Could not create offer"))
        }, MediaConstraints())
    }

    private fun handleP2PSignalingMessage(message: JSONObject) {
        when (message.optString("type")) {
            "offer" -> {
                p2pSessionId = message.optString("sessionId")
                answerP2POffer(message.optString("sdp"))
            }
            "motion_subscribe" -> {
                signalingTransport?.send(
                    JSONObject()
                        .put("type", "motion")
                        .put("deviceId", Constants.P2P_DEVICE_ID)
                        .put("detected", motionDetector.isMotionDetected())
                )
            }
            "close" -> {
                if (message.optString("sessionId") == p2pSessionId) {
                    peerConnection?.close()
                    peerConnection = null
                    snapshotsDataChannel?.dispose()
                    snapshotsDataChannel = null
                    p2pSessionId = null
                    onStateChanged(if (connectionMode == StreamConnectionMode.P2P_LOCAL_WIFI) "local camera ready for viewer" else "waiting for P2P viewer")
                }
            }
            "peer_disconnected" -> {
                peerConnection?.close()
                peerConnection = null
                snapshotsDataChannel?.dispose()
                snapshotsDataChannel = null
                p2pSessionId = null
                onStateChanged("waiting for P2P viewer")
            }
            "error" -> onError(IllegalStateException(message.optString("message", "P2P signaling failed")))
        }
    }

    private fun answerP2POffer(sdp: String) {
        try {
            localDescriptionReady = false
            offerSent.set(false)
            val connection = createPeerConnection()
            peerConnection = connection
            connection.addTrack(videoTrack ?: throw IllegalStateException("Camera video track is unavailable"), listOf("phone-camera"))
            audioTrack?.let { connection.addTrack(it, listOf("phone-camera")) }
            connection.setRemoteDescription(object : SdpObserver {
                override fun onSetSuccess() {
                    connection.createAnswer(object : SdpObserver {
                        override fun onCreateSuccess(description: SessionDescription?) {
                            if (description == null) {
                                fail(IllegalStateException("WebRTC created an empty P2P answer"))
                                return
                            }
                            connection.setLocalDescription(object : SdpObserver {
                                override fun onSetSuccess() {
                                    localDescriptionReady = true
                                    sendP2PAnswerWhenIceReady(connection)
                                }
                                override fun onSetFailure(error: String?) = fail(IllegalStateException(error ?: "Could not set P2P answer"))
                                override fun onCreateSuccess(description: SessionDescription?) = Unit
                                override fun onCreateFailure(error: String?) = fail(IllegalStateException(error ?: "Could not create P2P answer"))
                            }, description)
                        }
                        override fun onSetSuccess() = Unit
                        override fun onSetFailure(error: String?) = fail(IllegalStateException(error ?: "Could not set P2P offer"))
                        override fun onCreateFailure(error: String?) = fail(IllegalStateException(error ?: "Could not create P2P answer"))
                    }, MediaConstraints())
                }
                override fun onSetFailure(error: String?) = fail(IllegalStateException(error ?: "Could not apply P2P offer"))
                override fun onCreateSuccess(description: SessionDescription?) = Unit
                override fun onCreateFailure(error: String?) = Unit
            }, SessionDescription(SessionDescription.Type.OFFER, sdp))
            onStateChanged("connecting phone directly")
        } catch (error: Throwable) {
            fail(error)
        }
    }

    fun switchCamera(onCameraFacingChanged: (Boolean) -> Unit, onSwitchFailed: (String) -> Unit) {
        val activeCapturer = capturer
        if (activeCapturer == null) {
            onSwitchFailed("Start the camera stream before switching cameras.")
            return
        }
        activeCapturer.switchCamera(object : CameraVideoCapturer.CameraSwitchHandler {
            override fun onCameraSwitchDone(isFrontCamera: Boolean) {
                frontCamera = isFrontCamera
                onCameraFacingChanged(isFrontCamera)
            }

            override fun onCameraSwitchError(errorDescription: String?) {
                onSwitchFailed(errorDescription ?: "Unable to switch cameras on this phone.")
            }
        })
    }

    fun setAudioEnabled(enabled: Boolean) {
        audioTrack?.setEnabled(enabled)
    }

    private fun analyzeMotion(frame: VideoFrame) {
        val now = System.currentTimeMillis()
        if (now - lastMotionAnalysisAt < MOTION_ANALYSIS_INTERVAL_MS) return
        lastMotionAnalysisAt = now

        val i420Buffer = frame.buffer.toI420() ?: return
        try {
            val sample = ByteArray(MOTION_SAMPLE_COLUMNS * MOTION_SAMPLE_ROWS)
            val luma = i420Buffer.dataY
            val width = i420Buffer.width
            val height = i420Buffer.height
            val stride = i420Buffer.strideY
            for (row in 0 until MOTION_SAMPLE_ROWS) {
                val y = ((row + 1) * height / (MOTION_SAMPLE_ROWS + 1)).coerceAtMost(height - 1)
                for (column in 0 until MOTION_SAMPLE_COLUMNS) {
                    val x = ((column + 1) * width / (MOTION_SAMPLE_COLUMNS + 1)).coerceAtMost(width - 1)
                    sample[row * MOTION_SAMPLE_COLUMNS + column] = luma.get(y * stride + x)
                }
            }
            val detected = motionDetector.update(sample)
            val stateToPublish = detected ?: if (!publishedInitialMotionState) false else null
            publishedInitialMotionState = true
            if (detected == true) {
                snapshotStore.capture(frame) { snapshot ->
                    snapshotsDataChannel?.takeIf { it.state() == DataChannel.State.OPEN }?.let { channel ->
                        sendSnapshots(channel, listOf(snapshot))
                    }
                }
            }
            stateToPublish?.let { motionState ->
                Log.i(TAG, "Motion state changed: detected=$motionState mode=${connectionMode.name}")
                mainHandler.post {
                    onMotionChanged(motionState)
                    val sent = signalingTransport?.send(
                        JSONObject()
                            .put("type", "motion")
                            .put("deviceId", Constants.P2P_DEVICE_ID)
                            .put("detected", motionState)
                    ) ?: false
                    Log.i(TAG, "Motion state signaling queued=$sent")
                }
            }
        } catch (error: Exception) {
            Log.w(TAG, "Unable to analyze camera frame for motion", error)
        } finally {
            i420Buffer.release()
        }
    }

    private fun createPeerObserver() = object : PeerConnection.Observer {
        override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {
            if (state == PeerConnection.IceGatheringState.COMPLETE) {
                peerConnection?.let {
                    if (connectionMode != StreamConnectionMode.RELAY_VIA_LAPTOP) sendP2PAnswerWhenIceReady(it) else sendOfferWhenIceReady(it)
                }
            }
        }
        override fun onConnectionChange(state: PeerConnection.PeerConnectionState?) {
            when (state) {
                PeerConnection.PeerConnectionState.CONNECTED -> onStateChanged(
                    when (connectionMode) {
                        StreamConnectionMode.RELAY_VIA_LAPTOP -> "streaming to laptop relay"
                        StreamConnectionMode.P2P_VIA_LAPTOP_SIGNALING -> "streaming directly (laptop signaling)"
                        StreamConnectionMode.P2P_LOCAL_WIFI -> "streaming directly (local Wi-Fi)"
                    }
                )
                PeerConnection.PeerConnectionState.FAILED,
                PeerConnection.PeerConnectionState.DISCONNECTED,
                PeerConnection.PeerConnectionState.CLOSED -> onStateChanged("connection failed")
                else -> Unit
            }
        }
        override fun onIceCandidate(candidate: IceCandidate?) = Unit
        override fun onSignalingChange(state: PeerConnection.SignalingState?) = Unit
        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) = Unit
        override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
        override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) = Unit
        override fun onAddStream(stream: org.webrtc.MediaStream?) = Unit
        override fun onRemoveStream(stream: org.webrtc.MediaStream?) = Unit
        override fun onDataChannel(channel: org.webrtc.DataChannel?) {
            channel ?: return
            snapshotsDataChannel?.dispose()
            snapshotsDataChannel = channel
            channel.registerObserver(object : DataChannel.Observer {
                override fun onBufferedAmountChange(previousAmount: Long) = Unit
                override fun onStateChange() {
                    Log.i(TAG, "Snapshot data channel state=${channel.state()}")
                }
                override fun onMessage(buffer: DataChannel.Buffer?) {
                    if (buffer == null || buffer.binary) return
                    try {
                        val messageBytes = ByteArray(buffer.data.remaining())
                        buffer.data.get(messageBytes)
                        if (JSONObject(String(messageBytes, Charsets.UTF_8)).optString("type") == MotionSnapshotTransfer.EVENT_REQUEST) {
                            sendRecentSnapshots(channel)
                        }
                    } catch (error: Exception) {
                        Log.w(TAG, "Invalid snapshot transfer request", error)
                    }
                }
            })
        }
        override fun onRenegotiationNeeded() = Unit
        override fun onAddTrack(receiver: org.webrtc.RtpReceiver?, streams: Array<out org.webrtc.MediaStream>?) = Unit
        override fun onTrack(transceiver: org.webrtc.RtpTransceiver?) = Unit
        override fun onStandardizedIceConnectionChange(state: PeerConnection.IceConnectionState?) = Unit
    }

    private fun sendOfferWhenIceReady(connection: PeerConnection) {
        if (!localDescriptionReady || connection.iceGatheringState() != PeerConnection.IceGatheringState.COMPLETE) return
        if (!offerSent.compareAndSet(false, true)) return
        val description = connection.localDescription
            ?: return fail(IllegalStateException("Local WebRTC offer is missing"))
        Thread({
            try {
                val payload = JSONObject()
                    .put("sdp", description.description)
                    .put("type", description.type.canonicalForm())
                val request = Request.Builder()
                    .url("${serverBaseUrl.trimEnd('/')}/publish")
                    .post(payload.toString().toRequestBody("application/json".toMediaType()))
                    .build()
                httpClient.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) throw IOException("Publish request failed (${response.code}): $body")
                    val answerJson = JSONObject(body)
                    val answer = SessionDescription(
                        SessionDescription.Type.fromCanonicalForm(answerJson.getString("type")),
                        answerJson.getString("sdp")
                    )
                    connection.setRemoteDescription(object : SdpObserver {
                        override fun onSetSuccess() = onStateChanged("streaming to laptop")
                        override fun onSetFailure(error: String?) = fail(IllegalStateException(error ?: "Could not apply server answer"))
                        override fun onCreateSuccess(description: SessionDescription?) = Unit
                        override fun onCreateFailure(error: String?) = fail(IllegalStateException(error ?: "Could not apply server answer"))
                    }, answer)
                }
            } catch (error: Throwable) {
                fail(error)
            }
        }, "PhoneCameraSignaling").start()
    }

    private fun sendP2PAnswerWhenIceReady(connection: PeerConnection) {
        if (!localDescriptionReady || connection.iceGatheringState() != PeerConnection.IceGatheringState.COMPLETE) return
        if (!offerSent.compareAndSet(false, true)) return
        val description = connection.localDescription
            ?: return fail(IllegalStateException("Local P2P answer is missing"))
        val sessionId = p2pSessionId ?: return fail(IllegalStateException("P2P signaling session is missing"))
        val sent = signalingTransport?.send(
            JSONObject()
                .put("type", "answer")
                .put("sessionId", sessionId)
                .put("sdp", description.description)
        ) ?: false
        if (!sent) fail(IllegalStateException("Could not send P2P answer through signaling server"))
    }

    private fun sendRecentSnapshots(channel: DataChannel) {
        Thread({
            val snapshots = snapshotStore.latest(MotionSnapshotTransfer.MAX_SNAPSHOTS)
            Log.i(TAG, "Sending ${snapshots.size} stored motion snapshots")
            sendSnapshots(channel, snapshots)
        }, "AICAMsSnapshotTransfer").apply { isDaemon = true }.start()
    }

    private fun sendSnapshots(channel: DataChannel, snapshots: List<MotionSnapshot>) {
        for (message in MotionSnapshotTransfer.messages(snapshots)) {
            if (channel.state() != DataChannel.State.OPEN) return
            val buffer = DataChannel.Buffer(
                ByteBuffer.wrap(message.toJson().toString().toByteArray(Charsets.UTF_8)),
                false
            )
            if (!channel.send(buffer)) {
                Log.w(TAG, "WebRTC snapshot message rejected by data channel")
                return
            }
        }
    }

    private fun fail(error: Throwable) {
        Log.e(TAG, "Phone camera publishing failed", error)
        onStateChanged("error")
        onError(error)
    }

    fun stop() {
        try {
            diagnostics.stop()
            peerConnection?.close()
            peerConnection = null
            snapshotsDataChannel?.dispose()
            snapshotsDataChannel = null
            signalingTransport?.close()
            signalingTransport = null
            localSignalingServer?.close()
            localSignalingServer = null
            videoTrack?.removeSink(renderer)
            videoTrack?.dispose()
            videoTrack = null
            audioTrack?.dispose()
            audioTrack = null
            audioSource?.dispose()
            audioSource = null
            capturer?.stopCapture()
            capturer?.dispose()
            capturer = null
            videoSource?.dispose()
            videoSource = null
            textureHelper?.dispose()
            textureHelper = null
            snapshotStore.close()
            renderer.release()
            eglBase.release()
            factory.dispose()
        } catch (error: Exception) {
            Log.w(TAG, "Error while stopping phone camera publisher", error)
        }
    }

    private companion object {
        const val MOTION_ANALYSIS_INTERVAL_MS = 500L
        const val MOTION_SAMPLE_COLUMNS = 32
        const val MOTION_SAMPLE_ROWS = 18
    }
}
