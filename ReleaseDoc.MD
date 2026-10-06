# ReleaseDoc

This document tracks project work in version order, with the newest entries first.

---

## Version 0.2.5 - 2026-09-10
### Mac microphone audio publishing
- Added optional Mac microphone capture using aiortc `MediaPlayer` in [camera/src/webrtc_server.py](camera/src/webrtc_server.py).
- WebRTC camera peers now receive the microphone audio track alongside video.
- Added `AUDIO_DEVICE` configuration with a Mac AVFoundation default of `none:1`.
- Added `MediaRelay` so multiple viewers can subscribe to the shared microphone source independently.
- Audio capture failures fall back to video-only streaming.

---

## Version 0.2.4 - 2026-09-09
### Camera process exits after shutdown
- Updated [camera/src/webrtc_server.py](camera/src/webrtc_server.py) to stop its run loop through an asyncio shutdown event.
- The camera process now returns from `asyncio.run(main())` after `Camera component shutdown complete` is logged instead of leaving the server loop alive.

---

## Version 0.2.3 - 2026-09-02
### Camera backend WebRTC timestamp fix
- Fixed the Python camera backend crash caused by setting `video_frame.time_base = None` in [camera/src/webrtc_server.py](camera/src/webrtc_server.py).
- Replaced it with a valid AVRational time base using `Fraction(1, 90000)`, which is required by aiortc / av for RTP frame timestamps.
- This addresses the runtime error: `AttributeError: 'NoneType' object has no attribute 'numerator'` during frame encoding.

---

## Version 0.2.2 - 2026-09-02
### Native WebRTC init fix and debug bypass
- Fixed the native WebRTC initialization issue causing the crash: `UnsatisfiedLinkError` on `SoftwareVideoDecoderFactory.nativeCreateFactory()`.
- Added explicit `PeerConnectionFactory.initialize(...)` before creating the Android WebRTC factory in [android-viewer/app/src/main/kotlin/com/aicams/viewer/webrtc/WebRTCClient.kt](android-viewer/app/src/main/kotlin/com/aicams/viewer/webrtc/WebRTCClient.kt).
- Bypassed the login gate for development by setting the app start destination directly to the dashboard in [android-viewer/app/src/main/kotlin/com/aicams/viewer/MainActivity.kt](android-viewer/app/src/main/kotlin/com/aicams/viewer/MainActivity.kt).
- This is intended for the local debugging workflow only; login can be restored later before release.

---

## Version 0.2.1 - 2026-09-02
### Android viewer debugging instrumentation
- Added app-level debug logging to the feed opening flow in [android-viewer/app/src/main/kotlin/com/aicams/viewer/ui/screens/CameraScreen.kt](android-viewer/app/src/main/kotlin/com/aicams/viewer/ui/screens/CameraScreen.kt).
- Added WebRTC lifecycle logs in [android-viewer/app/src/main/kotlin/com/aicams/viewer/webrtc/WebRTCClient.kt](android-viewer/app/src/main/kotlin/com/aicams/viewer/webrtc/WebRTCClient.kt) for:
  - screen entry
  - WebRTC client start
  - ICE server setup
  - offer creation
  - offer request to camera backend
  - remote description handling
  - ICE candidate transmission
  - stream attach
  - stop/dispose path
- Verified the app still compiles successfully with the new logging: `./gradlew :app:compileDebugKotlin --console=plain` returned BUILD SUCCESSFUL.
- Added the recommended reproduction command for runtime crash debugging:
  - `adb logcat -v time -s "AICAMsCameraScreen:*" "AICAMsWebRTC:*" "*:E"`
- Current status: logs are in place and the app is instrumented for debugging, but the crash fix is not yet fully confirmed until we reproduce the feed-open path successfully on the emulator/device.

---

## Version 0.2.0 - 2026-09-02
### Android WebRTC viewer implemented
- Implemented actual Android viewer using Kotlin + Jetpack Compose + WebRTC SDK.
- Added the WebRTC client logic for offer creation, remote answer handling, ICE candidate exchange, and renderer binding.
- Confirmed the Android app builds with the published WebRTC SDK artifact.
- Fixed build issues related to invalid legacy WebRTC API usage and mismatched dependency versions.
- Added an initial UI flow for camera selection and viewer screen.

---

## Version 0.1.5 - 2026-09-02
### Camera backend stabilization
- Stabilized the Python camera app startup and port handling.
- Fixed stale port issue on port 8000 and ensured camera app lifecycle is more predictable.
- Kept the local Wi-Fi / emulator-friendly network configuration for Android viewer connectivity.

---

## Version 0.1.4 - 2026-09-02
### Android app foundation and navigation
- Created Android viewer project structure.
- Implemented login, dashboard, and camera detail navigation flow.
- Registered app dependencies and aligned Hilt/Compose setup.
- Rebuilt app state for local-home-safety monitoring use case.

---

## Version 0.1.3 - 2026-09-02
### Camera component implementation
- Built initial Python camera stack using OpenCV, aiortc, aiohttp, and av.
- Added local camera capture and WebRTC signaling server for stream delivery.
- Prepared the backend for viewer connection over local IP / emulator networking.

---

## Version 0.1.2 - 2026-09-02
### Product planning and architecture
- Defined MVP scope for a home-safety camera system with local streaming.
- Split work into camera app and Android viewer app.
- Planned initial implementation around local Wi-Fi and direct network communication.

---

## Version 0.1.1 - 2026-09-02
### Initial project setup
- Created workspace and repository structure for the AIcams project.
- Began implementation of camera + Android viewer MVP for home security use case.

---

## Version 0.1.0 - 2026-09-02
### Initial release baseline
- Project initialized for home safety streaming MVP.
- Core objective: allow a camera device in the home to stream live video to an Android viewer app.
