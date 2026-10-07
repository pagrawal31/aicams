# Recent Implementation Prompts and Results

Chronological log of the latest implementation requests and their outcomes. This supplements, rather than replaces, [IMPLEMENTATION_PLAN.md](IMPLEMENTATION_PLAN.md).

## 1. Add laptop-free signaling for home Wi-Fi

**Prompt:** Add local-network peer-to-peer signaling without removing either existing laptop mode; show all available options.

**Result:** Added three selectable modes on the camera and viewer screens:
- **Laptop Relay:** existing laptop media-relay path.
- **Laptop P2P:** existing laptop signaling path with direct WebRTC media.
- **Local Wi-Fi:** camera phone advertises a TCP signaling socket through Android NSD/mDNS; the viewer discovers and connects locally. WebRTC carries the media directly.

Updated setup instructions. Android unit tests and build passed, and the app was installed on both connected phones at that time. Local Wi-Fi requires both phones on a network that permits device-to-device traffic.

## 2. Fix Local Wi-Fi motion delivery and viewer fullscreen crash

**Prompt:** Motion state was not reaching the viewer in Local Wi-Fi mode, the beep was not audible, and entering fullscreen crashed the viewer.

**Result:** Device logs showed local socket writes were attempted on the Android main thread and rejected with `NetworkOnMainThreadException`. Local signaling writes were moved to background writer queues. The viewer logs received motion-state changes, updates its badge, and plays a short alarm-stream beep on motion detection.

The fullscreen crash was caused by attaching the same `SurfaceViewRenderer` to a second Android view parent. Fullscreen now resizes the existing video view instead of creating a second `AndroidView`.

Android tests/build passed, and the fix was installed on both connected phones.

## 3. Decide snapshot behavior before background monitoring

**Prompt:** Capture snapshots when motion is detected, retain them on the camera device, and send recent snapshots when a viewer connects.

**Result:** Agreed on event-triggered JPEG thumbnails, a bounded local history, and sending snapshots when a direct viewer connects. The laptop media-relay mode remains separate.

## 4. Implement snapshots for direct connection modes

**Prompt:** Implement motion snapshots for the direct modes.

**Result:** Added app-private snapshot storage on the camera phone. A motion-start event captures a 320×180 JPEG thumbnail; storage retains at most three snapshots and prunes snapshots older than 24 hours. In **Laptop P2P** and **Local Wi-Fi**, the viewer requests the recent snapshots over a WebRTC data channel. Chunked transfer bounds message size, and new snapshots are pushed while the direct viewer remains connected. The viewer displays received thumbnails with capture times. **Laptop Relay** behavior remains unchanged and does not currently transfer snapshots.

Added unit coverage for snapshot chunking, payload reconstruction, and size limits. The most recent Android unit-test/build run succeeded. A live two-phone snapshot-transfer check remains pending; the latest snapshot build has not yet been confirmed installed on both phones.

## 5. Add Picture-in-Picture to both Android roles

**Prompt:** Implement Picture-in-Picture (PiP) mode on both sides of the app.

**Response:** Added activity-level PiP support for both Android roles: the remote-camera viewer and the phone-camera publisher. Each active media screen has a PiP button and supports automatic PiP when the user presses or swipes Home. The PiP layout keeps the existing WebRTC renderer mounted, hides non-video controls, and preserves the live stream. PiP is disabled on login/dashboard screens and until media is active. Android 12+ uses auto-enter and seamless resize; older supported Android versions enter through `onUserLeaveHint`.

Updated the activity manifest for PiP and configuration changes, added feature checks for unsupported devices, and declared camera/microphone hardware optional so viewer-only devices remain compatible. Unit tests, debug APK assembly, and Android lint passed. Physical-device PiP testing remains recommended because device settings can disable PiP per app.

## 6. Prevent false Local Wi-Fi timeout after connection

**Prompt:** Even when connected in Local Wi-Fi mode, the viewer displays a timeout message containing milliseconds.

**Response:** Separated WebRTC media health from local discovery/signaling health. Once direct WebRTC media is connected, a late NSD or TCP signaling failure no longer replaces the healthy stream with a failure screen. Successful connection or stream callbacks now clear stale error text. Genuine pre-connection failures still appear, but the raw socket exception such as `after 8000ms` is replaced with a clear message asking the user to verify that both phones are on the same Wi-Fi and retry.

Unit tests, debug APK assembly, and Android lint passed after the fix.

## 7. Keep this implementation log updated

**Prompt:** Update completed work in `implementation.md` with the prompt and response, and always do that after every prompt response.

**Response:** Added the PiP work, Local Wi-Fi timeout fix, and this documentation request to the chronological log. For subsequent requests in this conversation, each completed prompt and its response will be appended to this file.

## 8. Prevent automatic screen sleep during camera use

**Prompt:** Both the camera and receiver screens go to sleep after some time, which breaks the connection; prevent this on both sides.

**Response:** Enabled Android's keep-screen-on behavior while the relevant media screens are in use. The receiver keeps the display awake while its camera-viewing screen is open, including while connecting. The phone-camera side enables keep-screen-on once publishing starts. Each screen restores the view's previous setting when it closes, so the dashboard and the rest of the device retain their normal timeout behavior. No additional Android permission is required.

This prevents automatic display timeout. It intentionally does not override a manual power-button lock, and uninterrupted capture after manually locking or fully backgrounding the app would require a foreground camera service. Unit tests, debug APK assembly, and Android lint passed.

## 9. Fix native WebRTC crash around screen sleep

**Prompt:** The app also crashes around the time the screen sleeps.

**Response:** Pulled the Android crash buffers from both connected phones. They showed native `SIGSEGV` failures inside WebRTC rather than an ordinary Compose exception: one on `AudioEncoderQueue` and another in `NetworkMonitor` while handling a connectivity change. The app was initializing and destroying WebRTC's process-global factory/EGL runtime for individual Compose screens, and peer connections were closed without being disposed before their shared native resources were released.

Added a process-lifetime `WebRtcRuntime`, initialized once by `AICAMsApplication`, that owns the shared peer-connection factory and EGL context. Viewer and publisher sessions now dispose their individual peer connections, data channels, tracks, capture resources, and renderers without destroying the process-global runtime. Teardown is idempotent, publisher startup/teardown is synchronized, repeated P2P offers dispose the prior connection, and late callbacks are ignored after a session stops. The earlier keep-screen-on behavior remains active to prevent automatic sleep in normal use.

Unit tests, debug APK assembly, and Android lint passed. The updated APK was installed and verified on one connected phone. The other phone disconnected from wireless ADB during installation and refused immediate reconnection, so its installed version could not be verified; it still needs the current APK installed or confirmed after reconnecting.

## 10. Stop stale 10-second signaling errors from hiding live video

**Prompt:** The viewer again shows `connection failed` with `failed to connect ... after 10000 ms` over the video even though audio continues correctly.

**Response:** The continuing audio proves the direct WebRTC media path is still active. The `10000 ms` value matches OkHttp's default timeout used by laptop/WebSocket signaling, while the Local Wi-Fi TCP path uses a different timeout. This identified a delayed callback from a stopped or previous signaling client—typically after switching modes—as the source of the false failure overlay. The late callback changed shared screen state to failed, removing the video renderer while WebRTC audio continued underneath.

Added explicit closed-state guards to `P2PSignalingClient`, suppressed callbacks after shutdown, prevented sends on closed transports, evicted old connections, and replaced raw internal timeout text with a readable signaling error. `WebRTCClient` now ignores callbacks after it stops and does not let a late P2P control/signaling error replace a healthy direct media connection. All asynchronous error paths now pass through the same session-aware error gate.

Unit tests, debug APK assembly, and Android lint passed. Wireless ADB connectivity to the viewer was restored through its newly advertised endpoint, and the updated APK was successfully installed on both the viewer and camera phones. A fresh Local Wi-Fi connection is required after installation because updating the app stops the prior processes.

## 11. Restore Local Wi-Fi video when audio and motion still work

**Prompt:** The viewer shows no visuals, although audio plays and motion state continues updating.

**Response:** Live device logs showed that camera capture remained healthy, the viewer received audio and motion messages, and a remote video track was announced, but the viewer renderer consistently reported `Frames received: 0`. The publisher was adding its local tracks before applying the viewer's Unified Plan offer. That can create separate transceivers instead of binding video to the video m-line offered by the viewer, producing a partially negotiated session where audio works but video frames are never delivered. The viewer was also attaching the renderer sink from several WebRTC callbacks and declaring the stream ready for audio-only track callbacks.

Changed publisher negotiation to apply the remote offer first, bind camera video and microphone audio explicitly to the offered transceivers, set their directions to send-only, and then create the answer. On the viewer, remote video attachment is now idempotent, an old sink is removed before a replacement is attached, the video track is explicitly enabled, and stream-ready is reported only for a real video track rather than for audio callbacks.

Unit tests, debug APK assembly, and Android lint passed. The updated APK was successfully installed on both phones. Both camera publishing and Local Wi-Fi viewing must be started again because APK installation stops the prior sessions; live frame delivery still needs confirmation after that fresh connection.

## 12. Fix candidate-less WebRTC sessions causing a black receiver

**Prompt:** What is the issue? The receiver still shows a black screen although audio is present, and video worked earlier.

**Response:** A two-phone trace identified the exact transport failure. Camera capture was healthy at approximately 30 FPS and the SDP contained send-only video/audio tracks, but both phones completed their first ICE gathering cycle before Android's WebRTC network monitor reported the active Wi-Fi interface. The offer and answer therefore contained no `a=candidate` addresses. The receiver could create remote audio/video track objects and display a Live state, but the peer connection remained in ICE checking and the renderer received zero video frames.

Enabled continual ICE gathering on viewer and publisher connections, prevented candidate-less offers and answers from being sent, and added a short settling interval after real host/STUN candidates arrive before sending the complete SDP. Also made repeated callbacks for the same remote video track idempotent. This preserves the process-wide WebRTC runtime used to prevent the earlier native sleep crash while removing the network-monitor startup race.

Unit tests, debug APK assembly, and Android lint passed. The corrected APK was installed on both physical phones. A fresh Local Wi-Fi session reached ICE/WebRTC `CONNECTED`; receiver logs showed 119–120 frames received and rendered per four seconds (approximately 30 FPS), and a device screenshot visually confirmed the live camera image, motion status, and recent motion snapshots.
