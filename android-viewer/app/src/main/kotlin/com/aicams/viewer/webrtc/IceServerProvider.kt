package com.aicams.viewer.webrtc

import com.aicams.viewer.utils.Constants
import org.webrtc.PeerConnection

/** Builds provider-neutral ICE configuration from build-time STUN/TURN settings. */
internal object IceServerProvider {
    fun create(): List<PeerConnection.IceServer> {
        val servers = Constants.STUN_SERVERS.map { url ->
            PeerConnection.IceServer.builder(url).createIceServer()
        }.toMutableList()

        if (Constants.TURN_SERVERS.isNotEmpty()) {
            servers += PeerConnection.IceServer.builder(Constants.TURN_SERVERS)
                .setUsername(Constants.TURN_USERNAME)
                .setPassword(Constants.TURN_PASSWORD)
                .createIceServer()
        }
        return servers
    }
}
