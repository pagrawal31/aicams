# Getting Started: Camera + Android Viewer

Quick setup guide for running the camera component and Android viewer app.

## Prerequisites

### For Camera Component (on Mac with webcam)
- Python 3.8+
- Webcam or USB camera
- Same Wi-Fi network

### For Android Viewer
- Android Studio 2022.1.1+
- Android SDK 28+
- Android phone/emulator with Android 9+
- Same Wi-Fi network as camera

## Part 1: Setup Camera Component

### Step 1: Install Python Dependencies

```bash
cd /Users/Anvi/Desktop/code/aicams/camera

# Create virtual environment
python3 -m venv venv
source venv/bin/activate

# Install dependencies
pip install -r requirements.txt
```

### Step 2: Configure Camera

Edit `.env` file in project root:

```bash
cd ../
cp .env.example .env
```

Update these values:
```env
CAMERA_DEVICE=0
CAMERA_RESOLUTION_WIDTH=1280
CAMERA_RESOLUTION_HEIGHT=720
CAMERA_FPS=30
DEVICE_NAME=HomeCamera-01
DEVICE_ID=camera-001
SERVICE_PORT=8000
```

### Step 3: Start Camera Component

```bash
cd camera
source venv/bin/activate
python3 src/main.py
```

Expected output:
```
[INFO] Initializing AI Cams Camera Component...
[INFO] Setting up video capture...
✓ Video capture initialized: 1280x720 @ 30fps
[INFO] Starting mDNS service discovery...
✓ mDNS service advertised: HomeCamera-01._camera-stream._tcp.local
[INFO] Starting WebRTC server...
✓ WebRTC server listening on ws://0.0.0.0:8000
[INFO] Camera component ready for connections
```

**Keep this terminal open!**

## Part 2: Setup Android Viewer

### Step 1: Open Project in Android Studio

```bash
# From Android Studio menu:
# File → Open → select /Users/Anvi/Desktop/code/aicams/android-viewer
```

### Step 2: Configure Local SDK Path

Copy template and add your Android SDK path:

```bash
cd /Users/Anvi/Desktop/code/aicams/android-viewer
cp local.properties.template local.properties
```

Edit `local.properties`:
```properties
sdk.dir=/Users/Anvi/Library/Android/sdk
```

### Step 3: Sync Gradle

In Android Studio:
- Click "Sync Now" when prompted
- Wait for gradle build to complete

### Step 4: Update API Configuration

Edit `app/src/main/kotlin/com/aicams/viewer/utils/Constants.kt`:

Get your Mac's local IP address:
```bash
ipconfig getifaddr en0  # Replace en0 with your network interface if needed
```

Update Constants.kt:
```kotlin
const val API_BASE_URL = "http://192.168.x.x:8000"  // Laptop's LAN IP for physical phones
```

For an Android emulator, use `http://10.0.2.2:8000` to reach the laptop. Physical phones must use the laptop's LAN IP. This MVP exchanges SDP with the Python service over HTTP; `SIGNALING_URL` is not used. The optional Node coordinator uses port 3000 and is separate from the camera media relay.

### Step 5: Build & Run

**On Emulator:**
```bash
# In Android Studio:
# Run → Run 'app' (or press Ctrl+R)
```

**On Physical Device:**
1. Connect device via USB
2. Enable USB debugging in Developer Settings
3. Run → Run 'app'

Expected behavior:
- App opens with login screen
- Click "Login" (demo credentials work)
- Dashboard shows camera list

## Part 3: Test Camera Discovery

### Option 1: Manual Testing

**From Android App:**
1. Open app on phone/emulator
2. Go to Dashboard
3. Tap "+" to add camera
4. App should discover "HomeCamera-01" via mDNS

**From Terminal (Testing mDNS):**
```bash
# On Mac, list mDNS services
dns-sd -B _camera-stream._tcp local.

# Should show:
# HomeCamera-01._camera-stream._tcp.local.
```

### Option 2: Automated Test

```bash
# In camera directory
python3 << 'EOF'
from zeroconf import ServiceBrowser, Zeroconf
import time

def on_service_state_change(zeroconf, service_type, name, state_change):
    print(f"Service {name} changed to {state_change.name}")

zeroconf = Zeroconf()
ServiceBrowser(zeroconf, "_camera-stream._tcp.local.", handlers=[on_service_state_change])

print("Searching for cameras...")
time.sleep(5)
zeroconf.close()
EOF
```

## Part 4: Test Android Camera Streaming (Relay or Direct P2P)

The Android app supports three modes. Keep the publisher screen open while streaming; background/lock-screen streaming is not implemented yet. The Node coordinator is not required for these tests.

1. Put the laptop and both Android phones on the same non-guest Wi-Fi network.
2. On the laptop, get its current Wi-Fi IP (`ipconfig getifaddr en0` on macOS).
3. In `android-viewer/app/src/main/kotlin/com/aicams/viewer/utils/Constants.kt`, set `API_BASE_URL` to `http://<laptop-ip>:8000` (for example `http://192.168.1.25:8000`). Use the laptop's IP, not `localhost` or `10.0.2.2`.
4. Start the Python relay on the laptop. It can run without a laptop webcam:

   ```bash
   cd camera
   source venv/bin/activate
   python3 src/main.py
   ```

5. Rebuild and install the updated Android app on both phones from Android Studio.
6. Restart the Python relay after updating the code, allowing Python through the laptop firewall on port 8000 if prompted.
7. On the **camera phone**, open the app, tap the video-camera icon in the top bar, choose **Laptop Relay**, **Laptop P2P**, or **Local Wi-Fi**, grant camera/microphone permissions, then tap the start button. Keep this screen open.
8. On the **viewer phone**, open any camera card (the demo **Living Room** card works when no Node coordinator is configured) and choose the same mode. **Laptop Relay** sends media through the laptop. **Laptop P2P** uses the laptop only to exchange signaling; media should flow directly between phones. **Local Wi-Fi** has the camera phone advertise a local signaling socket via Android NSD; no laptop is needed. Both phones must be on the same Wi-Fi with client isolation disabled. Start the camera phone before opening the viewer for either P2P mode.
9. To stop publishing, tap **Stop and Return** on the camera phone.

Both phones need to reach the laptop on port 8000 for the laptop modes; Local Wi-Fi signaling is phone-to-phone. The camera phone sends motion-state changes over its signaling connection in all modes; the viewer shows the state and plays one short beep when motion begins. In either direct P2P mode, the camera keeps up to three low-resolution snapshots from motion-start events in app-private storage and sends them to the viewer over a WebRTC data channel when connected (and sends new snapshots while connected). Laptop Relay mode continues to relay video/audio only; snapshot transfer is not enabled in that mode. Direct P2P uses public STUN servers but has no TURN fallback, so some Wi-Fi/cellular/NAT combinations may not connect; use Laptop Relay as a fallback. The laptop signaling endpoint is unauthenticated and should only be exposed on a trusted network. If streaming fails, check that both phones are on the same Wi-Fi for Local Wi-Fi mode, disable router/AP client isolation, and inspect Android Logcat.

Motion detection is a prototype alert, not a certified child-monitoring or safety system. It can miss movement or trigger on lighting changes; do not use it as the only way to monitor a child.

## Troubleshooting

### Camera not found

1. **Check camera device:**
   ```bash
   python3 << 'EOF'
   import cv2
   cap = cv2.VideoCapture(0)
   if cap.isOpened():
       print("✓ Camera found")
       cap.release()
   else:
       print("✗ Camera not found - check USB connection")
   EOF
   ```

2. **Check camera permission (Mac):**
   - System Settings → Privacy & Security → Camera
   - Allow Terminal/Python access

3. **Try different device ID:**
   Edit `.env`: `CAMERA_DEVICE=1` or `CAMERA_DEVICE=2`

### Android app can't find camera

1. **Check network:**
   ```bash
   # Ensure both on same Wi-Fi
   ping 192.168.x.x  # Your Mac's IP
   ```

2. **Check firewall:**
   - Mac System Settings → Firewall & Network Protection
   - Allow Python and Node apps

3. **Update IP in Android app:**
   - Edit Constants.kt
   - Use correct IP address (not localhost)

4. **Test mDNS discovery:**
   ```bash
   dns-sd -B _camera-stream._tcp local.
   ```

### WebRTC connection fails

1. **Check ports:**
   ```bash
   # Port 8000 should be listening
   lsof -i :8000
   
   # Kill if needed
   kill -9 <PID>
   ```

2. **Check firewall allows port 8000:**
   - System Settings → Firewall
   - Add Python to exceptions

3. **Check both devices on same network:**
   ```bash
   # Get your Mac's IP
   ifconfig | grep "inet " | grep -v 127.0.0.1
   
   # From phone, ping this IP
   ping 192.168.x.x
   ```

### App crashes

1. **Check Android logcat:**
   ```bash
   # In Android Studio:
   # View → Tool Windows → Logcat
   ```

2. **Filter by app:**
   ```bash
   adb logcat | grep aicams
   ```

3. **Common errors:**
   - "Connection refused" → Camera not running
   - "Network unreachable" → Different Wi-Fi network
   - "Permission denied" → Check AndroidManifest.xml permissions

## Architecture

```
┌─────────────────────────────┐
│   Android App               │
│ (Jetpack Compose UI)        │
│ ├─ LoginScreen              │
│ ├─ DashboardScreen          │
│ └─ WebRTC Manager           │
└──────────────┬──────────────┘
               │ (mDNS Discovery)
               │ (WebRTC P2P)
               │
┌──────────────▼──────────────┐
│   Camera Component (Python) │
│ ├─ Video Capture            │
│ ├─ mDNS Service             │
│ ├─ WebRTC Server            │
│ └─ Frame Buffer             │
└─────────────────────────────┘
```

## Next Steps

After confirming P2P streaming works:

1. **Phase 2 Setup:**
   - Setup Node.js signaling server
   - Implement user authentication
   - Add camera registration

2. **Phase 3 Setup:**
   - Setup AI models
   - Implement event detection
   - Add alert notifications

## Performance Tips

### Reduce Latency
- Lower resolution: 640x480 instead of 1280x720
- Lower FPS: 15 instead of 30
- Use 5GHz Wi-Fi if available

### Reduce Bandwidth
- Use software encoding
- Reduce key frame interval
- Enable bitrate adaptation

### Debug Performance
```bash
# From camera terminal
# Watch CPU/memory usage
while true; do
  python3 src/main.py 2>&1 | grep -E "fps|frame|CPU"
  sleep 1
done
```

## Support

For issues:
1. Check logs in camera terminal
2. Check Android logcat (Ctrl+Alt+6 in Android Studio)
3. Ensure both components on same network
4. Check firewall settings
5. Restart both components

## Files & Locations

- Camera: `/Users/Anvi/Desktop/code/aicams/camera/`
- Android: `/Users/Anvi/Desktop/code/aicams/android-viewer/`
- Config: `/Users/Anvi/Desktop/code/aicams/.env`
- Logs: `/Users/Anvi/Desktop/code/aicams/camera/logs/`

---

Happy streaming! 🚀
