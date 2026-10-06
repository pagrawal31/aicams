"""
WebRTC server for streaming video
Uses aiortc library for WebRTC implementation
"""

import asyncio
import logging
from fractions import Fraction
from aiohttp import web
from aiortc import RTCPeerConnection, RTCSessionDescription, VideoStreamTrack
from aiortc.contrib.media import MediaPlayer, MediaRelay
import cv2
import json
import numpy as np
import uuid

logger = logging.getLogger(__name__)


class CameraVideoTrack(VideoStreamTrack):
    """Custom video track that reads from camera"""
    
    def __init__(self, video_source):
        super().__init__()
        self.video_source = video_source
        self.counter = 0
    
    async def recv(self):
        """Receive video frames"""
        from av import VideoFrame
        
        # Get frame from video source
        frame = self.video_source.get_frame()
        
        if frame is None:
            # Return black frame if no frame available
            frame = np.zeros((720, 1280, 3), dtype="uint8")
        
        # Convert BGR to RGB (OpenCV uses BGR by default)
        frame_rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
        
        # Create VideoFrame from numpy array
        video_frame = VideoFrame.from_ndarray(frame_rgb, format="rgb24")
        
        # Set timestamp using a valid AVRational time base required by aiortc/av
        self.counter += 1
        video_frame.pts = self.counter
        video_frame.time_base = Fraction(1, 90000)

        return video_frame


class WebRTCServer:
    """WebRTC signaling server for peer connections"""
    
    def __init__(self, video_source, port=8000, audio_device="none:1"):
        self.video_source = video_source
        self.port = port
        self.audio_device = audio_device
        self.audio_player = None
        self.audio_relay = MediaRelay()
        self.video_relay = MediaRelay()
        self.publisher_track = None
        self.publisher_audio_track = None
        self.publisher_pc = None
        self.p2p_cameras = {}
        self.p2p_sessions = {}
        self.motion_subscribers = {}
        self.motion_states = {}
        self.app = None
        self.runner = None
        self.site = None
        self.pcs = set()  # Track peer connections
        self._shutdown_event = asyncio.Event()
        self._shutdown_started = False
    
    async def initialize(self) -> bool:
        """Initialize WebRTC server"""
        try:
            self.app = web.Application()
            
            # Add routes
            self.app.router.add_post('/offer', self.handle_offer)
            self.app.router.add_post('/publish', self.handle_publish)
            self.app.router.add_post('/answer', self.handle_answer)
            self.app.router.add_post('/ice-candidate', self.handle_ice_candidate)
            self.app.router.add_get('/stats', self.handle_stats)
            self.app.router.add_get('/p2p/signaling', self.handle_p2p_signaling)
            
            # Store reference to self
            self.app['server'] = self

            try:
                self.audio_player = MediaPlayer(self.audio_device, format="avfoundation")
                if self.audio_player.audio:
                    logger.info(f"✓ Audio capture initialized from {self.audio_device}")
                else:
                    logger.warning("Audio device opened but no audio track was found; continuing video-only")
                    self.audio_player = None
            except Exception as e:
                logger.warning(f"Audio capture unavailable; continuing video-only: {e}")
            
            logger.info(f"✓ WebRTC server initialized on port {self.port}")
            return True
        
        except Exception as e:
            logger.error(f"Error initializing WebRTC server: {e}")
            return False
    
    async def run(self):
        """Run WebRTC server"""
        try:
            self.runner = web.AppRunner(self.app)
            await self.runner.setup()

            self.site = web.TCPSite(self.runner, '0.0.0.0', self.port)
            await self.site.start()

            logger.info(f"✓ WebRTC server running on ws://0.0.0.0:{self.port}")

            await self._shutdown_event.wait()

        except Exception as e:
            logger.error(f"Error running WebRTC server: {e}")
            return

        finally:
            await self.shutdown()
    
    async def shutdown(self):
        """Shutdown server and cleanup"""
        if self._shutdown_started:
            return
        self._shutdown_started = True
        logger.info("Shutting down WebRTC server...")
        self._shutdown_event.set()

        for pc in self.pcs.copy():
            await pc.close()
        self.publisher_track = None
        self.publisher_audio_track = None
        self.publisher_pc = None

        if self.audio_player and self.audio_player.audio:
            self.audio_player.audio.stop()
        self.audio_player = None

        try:
            if self.site and self.runner is not None:
                await self.site.stop()
        except RuntimeError:
            logger.warning("WebRTC site already cleaned up or not registered; skipping shutdown")

        if self.runner:
            await self.runner.cleanup()
            self.runner = None

        self.site = None
        logger.info("✓ WebRTC server shutdown complete")
    
    async def handle_publish(self, request):
        """Accept an Android phone's outgoing camera stream."""
        try:
            data = await request.json()
            if not data.get("sdp") or data.get("type") != "offer":
                return web.json_response({"error": "A valid SDP offer is required"}, status=400)

            if self.publisher_pc:
                await self.publisher_pc.close()
                self.pcs.discard(self.publisher_pc)
            self.publisher_track = None
            self.publisher_audio_track = None

            pc = RTCPeerConnection()
            self.pcs.add(pc)

            self.publisher_pc = pc

            async def on_connectionstatechange():
                logger.info(f"Phone publisher connection state is {pc.connectionState}")
                if pc.connectionState in ("failed", "closed"):
                    await pc.close()
                    self.pcs.discard(pc)

                    if pc is self.publisher_pc:
                        self.publisher_pc = None
                        self.publisher_track = None
                        self.publisher_audio_track = None

            @pc.on("track")
            def on_track(track):
                if track.kind == "video":
                    logger.info("Received video track from Android camera")
                    self.publisher_track = track
                elif track.kind == "audio":
                    logger.info("Received microphone audio track from Android camera")
                    self.publisher_audio_track = track

            pc.add_listener("connectionstatechange", on_connectionstatechange)
            offer = RTCSessionDescription(sdp=data["sdp"], type=data["type"])
            await pc.setRemoteDescription(offer)
            answer = await pc.createAnswer()
            await pc.setLocalDescription(answer)

            logger.info("Android camera publishing offer accepted")
            return web.json_response({
                "sdp": pc.localDescription.sdp,
                "type": pc.localDescription.type
            })
        except Exception as e:
            logger.error(f"Error handling camera publish offer: {e}")
            return web.json_response({"error": str(e)}, status=400)

    async def handle_p2p_signaling(self, request):
        """Rendezvous WebSocket for direct phone-to-phone WebRTC signaling only."""
        role = request.query.get("role", "")
        device_id = request.query.get("deviceId", "")
        if role not in ("camera", "viewer") or (role == "camera" and not device_id):
            return web.json_response({"error": "role and camera deviceId are required"}, status=400)

        websocket = web.WebSocketResponse(heartbeat=25)
        await websocket.prepare(request)
        viewer_id = str(uuid.uuid4()) if role == "viewer" else None
        subscribed_device_id = None
        if role == "camera":
            previous = self.p2p_cameras.get(device_id)
            if previous is not None and not previous.closed:
                await previous.close(code=4001, message=b"camera reconnected")
            self.p2p_cameras[device_id] = websocket
            logger.info("P2P camera registered: %s", device_id)
            await websocket.send_json({"type": "ready", "deviceId": device_id})
        else:
            await websocket.send_json({"type": "ready", "viewerId": viewer_id})

        try:
            async for message in websocket:
                if message.type != web.WSMsgType.TEXT:
                    continue
                try:
                    payload = json.loads(message.data)
                except json.JSONDecodeError:
                    await websocket.send_json({"type": "error", "message": "Invalid signaling JSON"})
                    continue

                message_type = payload.get("type")
                if role == "viewer" and message_type == "motion_subscribe":
                    target_id = payload.get("deviceId", "")
                    if not target_id:
                        await websocket.send_json({"type": "error", "message": "deviceId is required for motion subscription"})
                        continue
                    if subscribed_device_id is not None:
                        self.motion_subscribers.get(subscribed_device_id, set()).discard(websocket)
                    subscribed_device_id = target_id
                    self.motion_subscribers.setdefault(target_id, set()).add(websocket)
                    latest = self.motion_states.get(target_id)
                    if latest is not None:
                        await websocket.send_json({
                            "type": "motion",
                            "deviceId": target_id,
                            "detected": latest,
                        })
                elif role == "camera" and message_type == "motion":
                    detected = payload.get("detected")
                    if not isinstance(detected, bool):
                        await websocket.send_json({"type": "error", "message": "detected must be a boolean"})
                        continue
                    self.motion_states[device_id] = detected
                    event = {"type": "motion", "deviceId": device_id, "detected": detected}
                    subscribers = self.motion_subscribers.get(device_id, set()).copy()
                    for subscriber in subscribers:
                        if subscriber.closed:
                            self.motion_subscribers[device_id].discard(subscriber)
                        else:
                            try:
                                await subscriber.send_json(event)
                            except Exception:
                                self.motion_subscribers[device_id].discard(subscriber)
                elif role == "viewer" and message_type == "offer":
                    target_id = payload.get("deviceId", "")
                    camera_socket = self.p2p_cameras.get(target_id)
                    if camera_socket is None or camera_socket.closed:
                        await websocket.send_json({"type": "error", "message": "P2P camera is offline"})
                        continue
                    active = any(session["device_id"] == target_id for session in self.p2p_sessions.values())
                    if active:
                        await websocket.send_json({"type": "error", "message": "P2P camera is already in use"})
                        continue
                    session_id = str(uuid.uuid4())
                    self.p2p_sessions[session_id] = {
                        "device_id": target_id,
                        "viewer": websocket,
                        "camera": camera_socket,
                    }
                    await camera_socket.send_json({
                        "type": "offer",
                        "sessionId": session_id,
                        "sdp": payload.get("sdp", ""),
                    })
                    await websocket.send_json({"type": "connecting", "sessionId": session_id})
                elif role == "camera" and message_type == "answer":
                    session = self.p2p_sessions.get(payload.get("sessionId", ""))
                    if session is None or session["camera"] is not websocket:
                        await websocket.send_json({"type": "error", "message": "P2P session expired"})
                        continue
                    await session["viewer"].send_json({
                        "type": "answer",
                        "sessionId": payload.get("sessionId"),
                        "sdp": payload.get("sdp", ""),
                    })
                elif role == "camera" and message_type == "error":
                    session = self.p2p_sessions.get(payload.get("sessionId", ""))
                    if session is not None:
                        await session["viewer"].send_json({"type": "error", "message": payload.get("message", "Camera negotiation failed")})
                elif message_type == "close":
                    session = self.p2p_sessions.pop(payload.get("sessionId", ""), None)
                    if session is not None:
                        peer = session["camera"] if role == "viewer" else session["viewer"]
                        if not peer.closed:
                            await peer.send_json({"type": "peer_disconnected", "sessionId": payload.get("sessionId")})
        finally:
            if role == "viewer" and subscribed_device_id is not None:
                subscribers = self.motion_subscribers.get(subscribed_device_id)
                if subscribers is not None:
                    subscribers.discard(websocket)
                    if not subscribers:
                        self.motion_subscribers.pop(subscribed_device_id, None)
            if role == "camera" and self.p2p_cameras.get(device_id) is websocket:
                self.p2p_cameras.pop(device_id, None)
                logger.info("P2P camera disconnected: %s", device_id)
            expired_sessions = [
                session_id for session_id, session in self.p2p_sessions.items()
                if (role == "viewer" and session["viewer"] is websocket)
                or (role == "camera" and session["camera"] is websocket)
            ]
            for session_id in expired_sessions:
                session = self.p2p_sessions.pop(session_id)
                peer = session["camera"] if role == "viewer" else session["viewer"]
                if not peer.closed:
                    try:
                        await peer.send_json({"type": "peer_disconnected", "sessionId": session_id})
                    except Exception:
                        pass
        return websocket

    async def handle_offer(self, request):
        """Handle a viewer offer and relay the phone or host camera track."""
        pc = None
        try:
            data = await request.json()
            if not data.get("sdp") or data.get("type") != "offer":
                return web.json_response({"error": "A valid SDP offer is required"}, status=400)

            if self.publisher_track is not None:
                pc = RTCPeerConnection()
                self.pcs.add(pc)
                pc.addTrack(self.video_relay.subscribe(self.publisher_track))
                logger.info("Relaying Android camera track to viewer")
                if self.publisher_audio_track is not None:
                    pc.addTrack(self.audio_relay.subscribe(self.publisher_audio_track))
                    logger.info("Relaying Android microphone audio to viewer")
                elif self.audio_player and self.audio_player.audio:
                    pc.addTrack(self.audio_relay.subscribe(self.audio_player.audio))
            elif self.video_source is not None:
                pc = RTCPeerConnection()
                self.pcs.add(pc)
                pc.addTrack(CameraVideoTrack(self.video_source))
                if self.audio_player and self.audio_player.audio:
                    pc.addTrack(self.audio_relay.subscribe(self.audio_player.audio))
            else:
                return web.json_response({"error": "No camera is currently publishing"}, status=503)

            async def on_connectionstatechange():
                logger.info(f"Viewer connection state is {pc.connectionState}")
                if pc.connectionState in ("failed", "closed"):
                    await pc.close()
                    self.pcs.discard(pc)

            pc.add_listener("connectionstatechange", on_connectionstatechange)
            await pc.setRemoteDescription(RTCSessionDescription(sdp=data["sdp"], type=data["type"]))
            answer = await pc.createAnswer()
            await pc.setLocalDescription(answer)
            logger.info("Viewer offer received and answer created")

            return web.json_response({"sdp": pc.localDescription.sdp, "type": pc.localDescription.type})
        except Exception as e:
            if pc is not None:
                await pc.close()
                self.pcs.discard(pc)
            logger.error(f"Error handling viewer offer: {e}")
            return web.json_response({"error": str(e)}, status=400)
    
    async def handle_answer(self, request):
        """Handle WebRTC answer"""
        try:
            data = await request.json()
            logger.info("WebRTC answer received")
            return web.json_response({"status": "ok"})
        
        except Exception as e:
            logger.error(f"Error handling answer: {e}")
            return web.json_response({"error": str(e)}, status=400)
    
    async def handle_ice_candidate(self, request):
        """Handle ICE candidate"""
        try:
            data = await request.json()
            logger.debug("ICE candidate received")
            return web.json_response({"status": "ok"})
        
        except Exception as e:
            logger.error(f"Error handling ICE candidate: {e}")
            return web.json_response({"error": str(e)}, status=400)
    
    async def handle_stats(self, request):
        """Get server statistics"""
        try:
            stats = self.video_source.get_stats() if self.video_source is not None else {}
            stats['active_connections'] = len(self.pcs)
            stats['phone_camera_publishing'] = self.publisher_track is not None
            stats['phone_audio_publishing'] = self.publisher_audio_track is not None
            
            return web.json_response(stats)
        
        except Exception as e:
            logger.error(f"Error getting stats: {e}")
            return web.json_response({"error": str(e)}, status=400)
