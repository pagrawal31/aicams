# AI Cams - Home Safety Video Streaming + Edge AI

A distributed home safety system featuring real-time video streaming, P2P connectivity, and edge AI analysis for event detection.

## Project Structure

```
aicams/
├── IMPLEMENTATION_PLAN.md    # Detailed phased implementation plan
├── README.md                 # This file
├── docker-compose.yml        # Docker orchestration (Phase 2+)
├── .env.example              # Environment variables template
│
├── camera/                   # Phase 1: Video capture & streaming
│   ├── src/
│   │   ├── main.py           # Camera application entry point
│   │   ├── video_capture.py  # Video capture module
│   │   ├── mdns_service.py   # mDNS discovery
│   │   ├── webrtc_client.py  # WebRTC streaming
│   │   └── config.py         # Configuration
│   ├── requirements.txt
│   ├── Dockerfile
│   └── README.md
│
├── server/                   # Phase 2: Signaling server
│   ├── src/
│   │   ├── index.js          # Server entry point
│   │   ├── api/
│   │   │   ├── devices.js    # Device registration APIs
│   │   │   ├── users.js      # User auth APIs
│   │   │   └── streams.js    # Streaming APIs
│   │   ├── services/
│   │   │   ├── auth.js       # JWT authentication
│   │   │   ├── db.js         # Database service
│   │   │   └── signaling.js  # WebRTC signaling
│   │   └── config.js
│   ├── package.json
│   ├── Dockerfile
│   └── README.md
│
├── android-viewer/            # Phase 1+: Android viewer app
│   ├── app/
│   │   ├── src/main/
│   │   │   ├── kotlin/com/aicams/viewer/
│   │   │   │   ├── MainActivity.kt
│   │   │   │   ├── ui/screens/
│   │   │   │   ├── ui/theme/
│   │   │   │   ├── network/
│   │   │   │   ├── data/
│   │   │   │   └── utils/
│   │   │   ├── res/
│   │   │   └── AndroidManifest.xml
│   │   ├── build.gradle.kts
│   │   └── proguard-rules.pro
│   ├── build.gradle.kts
│   ├── settings.gradle.kts
│   ├── gradle.properties
│   ├── local.properties.template
│   └── README.md
│
├── shared/                   # Shared utilities and types
│   ├── constants.js
│   ├── types.js
│   └── utils.js
│
├── tests/                    # Testing suite
│   ├── unit/
│   ├── integration/
│   └── e2e/
│
└── docs/                     # Documentation
    ├── API.md               # API documentation
    ├── DEPLOYMENT.md        # Deployment guide
    └── SETUP.md            # Setup instructions
```

## MVP Roadmap

### Phase 1: Local P2P Video Streaming (Weeks 1-3)
- [x] Plan created
- [ ] Video capture from camera
- [ ] mDNS service discovery
- [ ] WebRTC signaling server (lightweight, local)
- [ ] Android viewer application
- [ ] Real-time streaming validation

### Phase 2: Signaling Server (Weeks 4-5)
- [ ] Device registration API
- [ ] User authentication (JWT)
- [ ] Camera management
- [ ] Permission system
- [ ] Enhanced viewer app with login

### Phase 3: Edge AI Analysis (Weeks 6-8)
- [ ] Setup TensorFlow Lite
- [ ] Person detection model
- [ ] Frame processing pipeline
- [ ] Event detection and alerts
- [ ] Snapshot storage
- [ ] Alert delivery to viewers

### Testing & Polish (Weeks 9-11)
- [ ] Comprehensive testing
- [ ] Performance optimization
- [ ] Documentation
- [ ] Deployment readiness

## Quick Start

### Prerequisites
- Python 3.8+
- Node.js 16+
- Docker & Docker Compose
- OpenCV capable device (webcam or camera)

### Setup (Development)

1. **Clone repository**
   ```bash
   cd /Users/Anvi/Desktop/code/aicams
   ```

2. **Setup Camera Component**
   ```bash
   cd camera
   python -m venv venv
   source venv/bin/activate
   pip install -r requirements.txt
   ```

3. **Setup Server Component**
   ```bash
   cd ../server
   npm install
   ```

4. **Setup Android Viewer Component**
   ```bash
   cd ../android-viewer
   cp local.properties.template local.properties
   # Edit local.properties and set your Android SDK path
   ```

5. **Create .env file**
   ```bash
   cp .env.example .env
   ```

6. **Run components**
   - Camera: `cd camera && python src/main.py`
   - Server: `cd server && npm start`
   - Viewer: Open `android-viewer/` in Android Studio and run on emulator/device

## Technology Stack

| Component | Technology | Version |
|-----------|-----------|---------|
| Camera | Python + OpenCV | 3.8+ / 4.8+ |
| Streaming | WebRTC + FFmpeg | - / 4.4+ |
| Server | Node.js + Express | 16+ |
| Database | SQLite | 3 |
| AI Runtime | TensorFlow Lite | 2.12+ |
| Mobile App | Kotlin + Jetpack Compose | 1.9+ / Latest |

## API Overview

### Camera → Server
- `POST /api/devices/register` - Register camera
- `POST /api/devices/{id}/heartbeat` - Keep-alive
- `POST /api/events` - Send event alerts

### Viewer → Server  
- `POST /api/auth/login` - User login
- `GET /api/cameras` - List accessible cameras
- `GET /api/cameras/{id}/stream` - WebRTC offer endpoint
- `GET /api/events/{cameraId}` - Get event history

## Contributing

See [DEVELOPMENT.md](docs/DEVELOPMENT.md) for development guidelines.

## License

MIT

## Support

For issues and questions, please create an issue in the repository.
