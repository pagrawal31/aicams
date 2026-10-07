package com.aicams.viewer.webrtc

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.aicams.viewer.utils.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.webrtc.*
import java.io.IOException
import java.util.Base64
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "AICAMsWebRTC"

class WebRTCClient(
    private val context: Context,
    private val cameraBaseUrl: String = Constants.API_BASE_URL,
    private val onConnectionStateChanged: (String) -> Unit = {},
    private val onStreamReady: (SurfaceViewRenderer) -> Unit = {},
    private val onError: (Throwable) -> Unit = {},
    private val onMotionChanged: (Boolean) -> Unit = {},
    private val onMotionSnapshot: (Long, Bitmap) -> Unit = { _, _ -> }
) {
    private val client = OkHttpClient()
    private val diagnostics = ResourceDiagnostics(context, TAG, "viewer")
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var previousAudioMode = AudioManager.MODE_NORMAL
    private var previousSpeakerphoneState = false
    private var audioRouteConfigured = false
    private val eglContext = WebRtcRuntime.eglContext(context)
    private val remoteRenderer = SurfaceViewRenderer(context).apply {
        init(eglContext, null)
        setEnableHardwareScaler(true)
        setMirror(false)
    }

    private val peerConnectionFactory = WebRtcRuntime.factory(context)

    private var peerConnection: PeerConnection? = null
    private var started = false
    private var localDescriptionSet = false
    private var connectionMode = StreamConnectionMode.RELAY_VIA_LAPTOP
    private var p2pSessionId: String? = null
    private var signalingTransport: SignalingTransport? = null
    private val localSessionId = java.util.UUID.randomUUID().toString()
    private var remoteMotionState: Boolean? = null
    @Volatile private var remoteAudioTrack: AudioTrack? = null
    @Volatile private var remoteVideoTrack: VideoTrack? = null
    @Volatile private var remoteAudioEnabled = true
    @Volatile private var snapshotsDataChannel: DataChannel? = null
    private val snapshotRequestSent = AtomicBoolean(false)
    private val incomingSnapshots = ConcurrentHashMap<String, SnapshotAssembly>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val offerSent = AtomicBoolean(false)
    private val mediaConnected = AtomicBoolean(false)
    private val stopped = AtomicBoolean(false)

    private data class SnapshotAssembly(
        val timestampMs: Long,
        val chunks: Array<String?>,
        val receivedIndexes: MutableSet<Int> = mutableSetOf()
    )

    fun getRemoteRenderer(): SurfaceViewRenderer = remoteRenderer

    @Synchronized
    fun start(connectionMode: StreamConnectionMode = StreamConnectionMode.RELAY_VIA_LAPTOP) {
        if (started || stopped.get()) {
            Log.d(TAG, "WebRTC start called but already started; ignoring duplicate call")
            return
        }
        started = true
        this.connectionMode = connectionMode
        diagnostics.start(
            connectionProvider = { peerConnection },
            modeProvider = { connectionMode.name }
        )
        Log.d(TAG, "Starting WebRTC connection to camera baseUrl=$cameraBaseUrl")
        configureSpeakerphoneOutput()

        val onSignalingState: (String) -> Unit = signalingCallback@ { signalingState ->
            if (stopped.get()) return@signalingCallback
            Log.i(TAG, signalingState)
            val isFailure = signalingState.contains("error", ignoreCase = true) ||
                signalingState.contains("failed", ignoreCase = true)
            if (isFailure) {
                if (connectionMode != StreamConnectionMode.RELAY_VIA_LAPTOP && mediaConnected.get()) {
                    // P2P signaling is only needed to establish WebRTC. A late socket/WebSocket
                    // failure must not replace a healthy direct media connection with an error.
                    Log.w(TAG, "Ignoring signaling failure after direct media connected: $signalingState")
                } else {
                    reportError(IllegalStateException(signalingState))
                }
            }
        }
        signalingTransport = when (connectionMode) {
            StreamConnectionMode.P2P_LOCAL_WIFI -> LocalWifiSignalingClient(
                context = context,
                initialMessage = JSONObject()
                    .put("type", "motion_subscribe")
                    .put("deviceId", Constants.P2P_DEVICE_ID),
                onMessage = ::handleP2PSignalingMessage,
                onState = onSignalingState
            )
            StreamConnectionMode.RELAY_VIA_LAPTOP,
            StreamConnectionMode.P2P_VIA_LAPTOP_SIGNALING -> P2PSignalingClient(
                baseUrl = cameraBaseUrl,
                role = "viewer",
                deviceId = java.util.UUID.randomUUID().toString(),
                initialMessage = JSONObject()
                    .put("type", "motion_subscribe")
                    .put("deviceId", Constants.P2P_DEVICE_ID),
                onMessage = ::handleP2PSignalingMessage,
                onState = onSignalingState
            )
        }

        CoroutineScope(Dispatchers.IO).launch {
            if (stopped.get()) return@launch
            try {
                val iceServers = listOf(
                    PeerConnection.IceServer("stun:stun.l.google.com:19302"),
                    PeerConnection.IceServer("stun:stun1.l.google.com:19302")
                )

                Log.d(TAG, "Using ICE servers: ${iceServers.map { it.uri }.joinToString()}")

                val config = PeerConnection.RTCConfiguration(iceServers).apply {
                    // Android can report ICE gathering COMPLETE before NetworkMonitor has
                    // delivered the current Wi-Fi interface. Keep gathering so the interface
                    // arriving a few milliseconds later produces usable host candidates.
                    continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
                }
                val pc = peerConnectionFactory.createPeerConnection(config, object : PeerConnection.Observer {
                    override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {
                        if (stopped.get()) return
                        Log.d(TAG, "Peer connection state changed: ${newState?.name ?: "unknown"}")
                        when (newState) {
                            PeerConnection.PeerConnectionState.CONNECTED -> mediaConnected.set(true)
                            PeerConnection.PeerConnectionState.CLOSED,
                            PeerConnection.PeerConnectionState.DISCONNECTED,
                            PeerConnection.PeerConnectionState.FAILED -> mediaConnected.set(false)
                            else -> Unit
                        }
                        onConnectionStateChanged(newState?.name ?: "unknown")
                    }

                    override fun onIceCandidate(candidate: IceCandidate?) {
                        // The complete gathered SDP is sent as one offer; no trickle endpoint is needed.
                        candidate?.let {
                            Log.i(TAG, "Gathered ICE candidate for ${it.sdpMid}: ${it.sdp.take(120)}")
                            mainHandler.postDelayed({
                                if (!stopped.get()) peerConnection?.let(::sendOfferIfIceReady)
                            }, ICE_CANDIDATE_SETTLE_MS)
                        }
                    }

                    override fun onAddStream(stream: MediaStream?) {
                        if (stopped.get()) return
                        Log.d(TAG, "onAddStream called")
                        val videoTrack = stream?.videoTracks?.firstOrNull() ?: return
                        attachRemoteVideoTrack(videoTrack)
                    }

                    override fun onRemoveStream(stream: MediaStream?) = Unit
                    override fun onDataChannel(channel: DataChannel?) = Unit
                    override fun onRenegotiationNeeded() = Unit
                    override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
                    override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState?) {
                        Log.i(TAG, "ICE connection state changed: ${newState?.name ?: "unknown"}")
                    }
                    override fun onStandardizedIceConnectionChange(newState: PeerConnection.IceConnectionState?) {
                        Log.i(TAG, "Standardized ICE state changed: ${newState?.name ?: "unknown"}")
                    }
                    override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState?) {
                        Log.i(TAG, "ICE gathering state changed: ${newState?.name ?: "unknown"}")
                        if (newState == PeerConnection.IceGatheringState.COMPLETE) {
                            peerConnection?.let(::sendOfferIfIceReady)
                        }
                    }
                    override fun onSignalingChange(newState: PeerConnection.SignalingState?) = Unit
                    override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) = Unit
                    override fun onAddTrack(receiver: RtpReceiver?, mediaStreams: Array<out MediaStream>?) {
                        if (stopped.get()) return
                        Log.d(TAG, "onAddTrack called with ${mediaStreams?.size ?: 0} media streams")
                        when (val track = receiver?.track()) {
                            is VideoTrack -> attachRemoteVideoTrack(track)
                            is AudioTrack -> {
                                remoteAudioTrack = track
                                track.setEnabled(remoteAudioEnabled)
                                Log.i(TAG, "Remote audio track enabled for playback")
                            }
                        }
                    }
                    override fun onTrack(transceiver: RtpTransceiver?) {
                        if (stopped.get()) return
                        when (val track = transceiver?.receiver?.track()) {
                            is AudioTrack -> {
                                remoteAudioTrack = track
                                track.setEnabled(remoteAudioEnabled)
                                Log.i(TAG, "Remote audio track enabled for playback")
                            }
                            is VideoTrack -> attachRemoteVideoTrack(track)
                        }
                    }
                })

                synchronized(this@WebRTCClient) {
                    if (stopped.get()) {
                        pc?.dispose()
                        return@launch
                    }
                    peerConnection = pc
                }
                if (connectionMode != StreamConnectionMode.RELAY_VIA_LAPTOP) {
                    val snapshotChannel = pc?.createDataChannel("motion-snapshots", DataChannel.Init())
                        ?: throw IllegalStateException("Could not create motion snapshot data channel")
                    snapshotsDataChannel = snapshotChannel
                    registerSnapshotDataChannel(snapshotChannel)
                }
                Log.d(TAG, "Peer connection created; adding recv-only audio/video transceivers")
                val transceiverInit = RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY)
                pc?.addTransceiver(MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO, transceiverInit)
                pc?.addTransceiver(MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO, transceiverInit)

                val mediaConstraints = MediaConstraints().apply {
                    mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
                    mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
                }

                pc?.createOffer(object : SdpObserver {
                    override fun onCreateSuccess(desc: SessionDescription?) {
                        desc ?: run {
                            Log.e(TAG, "Offer creation succeeded but description was null")
                            return
                        }
                        Log.d(TAG, "Offer created; sending to ${buildOfferUrl(cameraBaseUrl)}")
                        pc.setLocalDescription(object : SdpObserver {
                            override fun onSetSuccess() {
                                Log.d(TAG, "Local description set successfully")
                                localDescriptionSet = true
                                pc?.let(::sendOfferIfIceReady)
                            }

                            override fun onSetFailure(error: String?) {
                                Log.e(TAG, "Failed to set local description: ${error ?: "unknown error"}")
                                reportError(RuntimeException(error ?: "Failed to set local description"))
                            }

                            override fun onCreateSuccess(desc: SessionDescription?) = Unit
                            override fun onCreateFailure(error: String?) = Unit
                        }, desc)
                    }

                    override fun onSetSuccess() = Unit
                    override fun onSetFailure(error: String?) {
                        Log.e(TAG, "Offer creation failed: ${error ?: "unknown error"}")
                        reportError(RuntimeException(error ?: "Failed to create offer"))
                    }

                    override fun onCreateFailure(error: String?) {
                        Log.e(TAG, "Offer creation failure: ${error ?: "unknown error"}")
                        reportError(RuntimeException(error ?: "Failed to create offer"))
                    }
                }, mediaConstraints)
            } catch (t: Throwable) {
                Log.e(TAG, "Unexpected WebRTC setup exception", t)
                reportError(t)
            }
        }
    }

    private fun sendOfferIfIceReady(pc: PeerConnection) {
        if (!localDescriptionSet) return
        val gatheredOffer = pc.localDescription
        if (gatheredOffer == null) {
            reportError(IllegalStateException("Local WebRTC offer is missing"))
            return
        }
        if (!gatheredOffer.description.contains("a=candidate:")) {
            Log.w(TAG, "ICE gathering completed before a network candidate was available; waiting for Wi-Fi")
            return
        }
        if (!offerSent.compareAndSet(false, true)) return
        logSdpTransport("Local offer", gatheredOffer.description)
        sendOffer(gatheredOffer)
    }

    private fun configureSpeakerphoneOutput() {
        previousAudioMode = audioManager.mode
        previousSpeakerphoneState = audioManager.isSpeakerphoneOn
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val speaker = audioManager.availableCommunicationDevices
                .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
            audioRouteConfigured = speaker != null && audioManager.setCommunicationDevice(speaker)
        }
        if (!audioRouteConfigured) {
            @Suppress("DEPRECATION")
            run { audioManager.isSpeakerphoneOn = true }
            audioRouteConfigured = true
        }
        Log.i(TAG, "Configured WebRTC playback for phone speaker")
    }

    @Synchronized
    private fun attachRemoteVideoTrack(track: VideoTrack) {
        if (stopped.get()) return
        if (remoteVideoTrack?.id() != track.id()) {
            remoteVideoTrack?.removeSink(remoteRenderer)
            remoteVideoTrack = track
            track.setEnabled(true)
            track.addSink(remoteRenderer)
            Log.i(TAG, "Remote video track attached to renderer: id=${track.id()}")
        }
        mediaConnected.set(true)
        onStreamReady(remoteRenderer)
    }

    /** Mutes or unmutes only this viewer's received audio; it does not change the camera's mic. */
    fun setRemoteAudioEnabled(enabled: Boolean) {
        remoteAudioEnabled = enabled
        remoteAudioTrack?.setEnabled(enabled)
        Log.i(TAG, "Remote audio playback ${if (enabled) "enabled" else "muted"}")
    }

    private fun restoreAudioOutput() {
        if (!audioRouteConfigured) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.clearCommunicationDevice()
        }
        @Suppress("DEPRECATION")
        run { audioManager.isSpeakerphoneOn = previousSpeakerphoneState }
        audioManager.mode = previousAudioMode
        audioRouteConfigured = false
    }

    private fun sendOffer(offer: SessionDescription) {
        if (connectionMode != StreamConnectionMode.RELAY_VIA_LAPTOP) {
            val payload = JSONObject()
                    .put("type", "offer")
                    .put("deviceId", Constants.P2P_DEVICE_ID)
                    .put("sdp", offer.description)
            if (connectionMode == StreamConnectionMode.P2P_LOCAL_WIFI) {
                payload.put("sessionId", localSessionId)
            }
            val sent = signalingTransport?.send(payload) ?: false
            if (!sent) reportError(IllegalStateException("Could not send P2P offer to signaling server"))
            return
        }
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val json = JSONObject().apply {
                    put("sdp", offer.description)
                    put("type", offer.type.canonicalForm())
                }

                val offerUrl = buildOfferUrl(cameraBaseUrl)
                Log.d(TAG, "Sending offer to $offerUrl")

                val request = Request.Builder()
                    .url(offerUrl)
                    .post(json.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val errorText = response.body?.string() ?: "empty body"
                        Log.e(TAG, "Offer request failed with code ${response.code}: $errorText")
                        throw IOException("Offer request failed with code ${response.code}: $errorText")
                    }

                    val payload = response.body?.string() ?: "{}"
                    Log.d(TAG, "Offer response received: $payload")
                    val answerJson = JSONObject(payload)
                    val answer = SessionDescription(
                        SessionDescription.Type.ANSWER,
                        answerJson.getString("sdp")
                    )

                    peerConnection?.setRemoteDescription(object : SdpObserver {
                        override fun onSetSuccess() {
                            Log.d(TAG, "Remote description set successfully")
                        }
                        override fun onSetFailure(error: String?) {
                            Log.e(TAG, "Failed to set remote description: ${error ?: "unknown error"}")
                            reportError(RuntimeException(error ?: "Failed to set remote description"))
                        }

                        override fun onCreateSuccess(desc: SessionDescription?) = Unit
                        override fun onCreateFailure(error: String?) = Unit
                    }, answer)
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Error while sending offer", t)
                reportError(t)
            }
        }
    }

    private fun registerSnapshotDataChannel(channel: DataChannel) {
        channel.registerObserver(object : DataChannel.Observer {
            override fun onBufferedAmountChange(previousAmount: Long) = Unit

            override fun onStateChange() {
                Log.i(TAG, "Snapshot data channel state=${channel.state()}")
                if (channel.state() == DataChannel.State.OPEN && snapshotRequestSent.compareAndSet(false, true)) {
                    val request = JSONObject().put("type", MotionSnapshotTransfer.EVENT_REQUEST)
                    if (!channel.send(DataChannel.Buffer(java.nio.ByteBuffer.wrap(request.toString().toByteArray(Charsets.UTF_8)), false))) {
                        snapshotRequestSent.set(false)
                        Log.w(TAG, "Could not request motion snapshots from camera")
                    } else {
                        Log.i(TAG, "Requested recent motion snapshots")
                    }
                }
            }

            override fun onMessage(buffer: DataChannel.Buffer?) {
                if (buffer == null || buffer.binary) return
                try {
                    val bytes = ByteArray(buffer.data.remaining())
                    buffer.data.get(bytes)
                    handleSnapshotMessage(JSONObject(String(bytes, Charsets.UTF_8)))
                } catch (error: Exception) {
                    Log.w(TAG, "Could not decode motion snapshot message", error)
                }
            }
        })
    }

    private fun handleSnapshotMessage(message: JSONObject) {
        val id = message.optString("id")
        when (message.optString("type")) {
            MotionSnapshotTransfer.EVENT_START -> {
                val count = message.optInt("totalChunks", -1)
                if (id.isBlank() || count !in 1..MotionSnapshotTransfer.MAX_CHUNKS_PER_SNAPSHOT) return
                incomingSnapshots[id] = SnapshotAssembly(
                    timestampMs = message.optLong("timestampMs"),
                    chunks = arrayOfNulls(count)
                )
            }
            MotionSnapshotTransfer.EVENT_CHUNK -> {
                val assembly = incomingSnapshots[id] ?: return
                val index = message.optInt("index", -1)
                if (index !in assembly.chunks.indices) return
                val complete = synchronized(assembly) {
                    if (assembly.chunks[index] == null) {
                        assembly.chunks[index] = message.optString("data")
                        assembly.receivedIndexes.add(index)
                    }
                    assembly.receivedIndexes.size == assembly.chunks.size
                }
                if (!complete) return
                incomingSnapshots.remove(id)
                val encoded = assembly.chunks.joinToString(separator = "") { it.orEmpty() }
                if (encoded.length > MotionSnapshotTransfer.MAX_SNAPSHOT_BYTES * 2) return
                val jpeg = Base64.getDecoder().decode(encoded)
                if (jpeg.size > MotionSnapshotTransfer.MAX_SNAPSHOT_BYTES) return
                val bitmap = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size) ?: return
                Log.i(TAG, "Received motion snapshot timestamp=${assembly.timestampMs} bytes=${jpeg.size}")
                mainHandler.post { onMotionSnapshot(assembly.timestampMs, bitmap) }
            }
        }
    }

    private fun handleP2PSignalingMessage(message: JSONObject) {
        if (stopped.get()) return
        when (message.optString("type")) {
            "motion" -> {
                if (message.optString("deviceId") != Constants.P2P_DEVICE_ID) return
                val detected = message.optBoolean("detected")
                Log.i(TAG, "Received camera motion state: detected=$detected")
                if (remoteMotionState != detected) {
                    remoteMotionState = detected
                    onMotionChanged(detected)
                }
            }
            "connecting" -> p2pSessionId = message.optString("sessionId")
            "answer" -> {
                val connection = peerConnection ?: return
                val answer = SessionDescription(SessionDescription.Type.ANSWER, message.optString("sdp"))
                logSdpTransport("Remote answer", answer.description)
                connection.setRemoteDescription(object : SdpObserver {
                    override fun onSetSuccess() {
                        Log.i(TAG, "P2P answer applied; direct media connection is negotiating")
                    }
                    override fun onSetFailure(error: String?) {
                        reportError(IllegalStateException(error ?: "Could not apply P2P answer"))
                    }
                    override fun onCreateSuccess(description: SessionDescription?) = Unit
                    override fun onCreateFailure(error: String?) = Unit
                }, answer)
            }
            "error" -> reportError(IllegalStateException(message.optString("message", "P2P signaling failed")))
            "peer_disconnected" -> onConnectionStateChanged("DISCONNECTED")
        }
    }

    private fun reportError(error: Throwable) {
        if (stopped.get()) return
        if (connectionMode != StreamConnectionMode.RELAY_VIA_LAPTOP && mediaConnected.get()) {
            Log.w(TAG, "Ignoring control-path error after direct media connected", error)
            return
        }
        onError(error)
    }

    private fun logSdpTransport(label: String, sdp: String) {
        val lines = sdp.lineSequence()
            .filter { line ->
                line.startsWith("m=") || line.startsWith("a=mid:") ||
                    line.startsWith("a=send") || line.startsWith("a=recv") ||
                    line.startsWith("a=inactive") || line.startsWith("a=candidate:")
            }
            .toList()
        Log.i(TAG, "$label transport (${lines.size} lines): ${lines.joinToString(" | ")}")
    }

    @Synchronized
    fun stop() {
        if (!stopped.compareAndSet(false, true)) return
        Log.d(TAG, "Stopping WebRTC client")
        mediaConnected.set(false)
        diagnostics.stop()
        try {
            (p2pSessionId ?: if (connectionMode == StreamConnectionMode.P2P_LOCAL_WIFI) localSessionId else null)?.let { sessionId ->
                signalingTransport?.send(JSONObject().put("type", "close").put("sessionId", sessionId))
            }
            signalingTransport?.close()
            signalingTransport = null
            snapshotsDataChannel?.unregisterObserver()
            snapshotsDataChannel?.dispose()
            snapshotsDataChannel = null
            incomingSnapshots.clear()
            remoteVideoTrack?.removeSink(remoteRenderer)
            remoteVideoTrack = null
            remoteAudioTrack = null
            val connection = peerConnection
            peerConnection = null
            connection?.close()
            connection?.dispose()
        } catch (_: Throwable) {
        }
        restoreAudioOutput()
        remoteRenderer.release()
    }

    companion object {
        private const val ICE_CANDIDATE_SETTLE_MS = 300L
        fun buildOfferUrl(baseUrl: String): String = "${baseUrl.trimEnd('/')}/offer"
        fun buildIceCandidateUrl(baseUrl: String): String = "${baseUrl.trimEnd('/')}/ice-candidate"

        fun resolveCameraBaseUrl(host: String): String {
            val target = host.trim().ifEmpty { "10.0.2.2" }
            return "http://${target}:8000"
        }
    }
}
