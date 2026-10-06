# Camera Component (Phase 1)

## Overview
This component handles video capture from the camera device and streams it via WebRTC using P2P connectivity on the local network.

## Features
- Local camera/webcam capture
- mDNS service advertisement for discovery
- WebRTC streaming to authorized viewers
- Frame buffering and error handling
- Configuration management

## File Structure
```
camera/
├── src/
│   ├── main.py           # Entry point
│   ├── video_capture.py  # Video capture logic
│   ├── mdns_service.py   # mDNS discovery
│   ├── webrtc_client.py  # WebRTC setup
│   ├── config.py         # Configuration
│   └── utils/
│       ├── logger.py
│       └── frame_buffer.py
├── requirements.txt
├── Dockerfile
├── .dockerignore
└── README.md
```

## Configuration

Edit `src/config.py` to configure:

```python
# Video settings
VIDEO_RESOLUTION = (1280, 720)  # 720p
VIDEO_FPS = 30
VIDEO_CODEC = 'h264'

# Network settings
DEVICE_NAME = 'HomeCamera-01'
SERVICE_PORT = 8000
SERVICE_TYPE = '_camera-stream._tcp'

# Server settings (Phase 2)
SERVER_URL = 'http://localhost:3000'  # Optional
DEVICE_ID = 'camera-001'
API_KEY = None  # Set after Phase 2
```

## Development Setup

### 1. Create Python Virtual Environment
```bash
cd camera
python -m venv venv
source venv/bin/activate  # On Windows: venv\Scripts\activate
```

### 2. Install Dependencies
```bash
pip install -r requirements.txt
```

### 3. Run Camera Component
```bash
python src/main.py
```

Expected output:
```
[INFO] Initializing camera...
[INFO] Camera initialized: 1280x720 @ 30fps
[INFO] Starting mDNS service...
[INFO] Service advertised: HomeCamera-01._camera-stream._tcp.local
[INFO] Starting WebRTC server...
[INFO] WebRTC server listening on ws://0.0.0.0:8000
[INFO] Ready for connections
```

## Testing

### From Another Device on Same Network

1. **Discover camera:**
   ```python
   # Use mDNS discovery
   from zeroconf import ServiceBrowser, Zeroconf
   
   zeroconf = Zeroconf()
   # Will find HomeCamera-01._camera-stream._tcp.local
   ```

2. **Connect via WebRTC:**
   - See `../viewer` for web-based viewer

## Phase 1 TODO

- [ ] Implement `video_capture.py`
  - [ ] OpenCV camera initialization
  - [ ] Frame encoding (H.264)
  - [ ] Circular buffer implementation
  - [ ] Performance monitoring

- [ ] Implement `mdns_service.py`
  - [ ] mDNS service registration
  - [ ] Service announcement
  - [ ] Graceful shutdown

- [ ] Implement `webrtc_client.py`
  - [ ] WebRTC signaling setup
  - [ ] Offer/answer handling
  - [ ] ICE candidate handling
  - [ ] Media stream transmission

- [ ] Implement `config.py`
  - [ ] Configuration loading from environment
  - [ ] Validation
  - [ ] Default values

- [ ] Error Handling
  - [ ] Camera disconnection handling
  - [ ] Network error recovery
  - [ ] Graceful shutdown

- [ ] Testing
  - [ ] Unit tests for each module
  - [ ] Integration tests
  - [ ] Performance testing

## Dependencies

See `requirements.txt` for complete list. Key dependencies:

- `opencv-python` - Video capture and processing
- `aiortc` - WebRTC implementation
- `zeroconf` - mDNS service discovery
- `python-dotenv` - Environment configuration

## Performance Targets

- Frame capture: < 33ms (for 30fps)
- Frame encoding: < 20ms
- Total latency to viewer: < 500ms
- CPU usage: < 40% on typical hardware

## Troubleshooting

### Camera not found
- Check if camera is connected and accessible
- Try: `python -c "import cv2; print(cv2.__version__)"`

### mDNS service not discoverable
- Ensure device is on same Wi-Fi network
- Check firewall settings (port 5353 for mDNS)

### WebRTC connection fails
- Check firewall allows local network connections
- Verify IP address in configuration

## Next Steps

After completing Phase 1, move to Phase 2 (Signaling Server) for remote access support.
