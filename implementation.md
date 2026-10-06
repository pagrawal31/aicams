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
