# Setup Instructions

Complete step-by-step guide to setup the AI Cams MVP on your development machine.

## Prerequisites

### Required Software
- **Python 3.8+** - For camera component
- **Node.js 16+** - For server and viewer
- **Git** - For version control
- **Docker & Docker Compose** (optional) - For containerized deployment

### Hardware Requirements
- **Camera/Webcam** - Connected to your computer
- **Minimum 4GB RAM** - For running all components
- **Wi-Fi Network** - For testing P2P connectivity (same network)

### Check Installation

```bash
# Check Python
python3 --version

# Check Node.js
node --version
npm --version

# Check Git
git --version

# Check Docker (optional)
docker --version
docker-compose --version
```

## Project Setup

### 1. Clone Repository
```bash
cd /Users/Anvi/Desktop/code
git clone <repository-url>
cd aicams
```

### 2. Create .env File
```bash
cp .env.example .env
```

Edit `.env` and update critical values:
```env
JWT_SECRET=your-unique-secret-key-min-32-chars
DEVICE_NAME=MyHomeCamera
DEVICE_ID=camera-001
```

### 3. Setup Camera Component

#### Install Dependencies
```bash
cd camera
python3 -m venv venv
source venv/bin/activate  # On Windows: venv\Scripts\activate
pip install -r requirements.txt
```

#### Test Camera Detection
```python
python3 -c "import cv2; print(cv2.__version__)"
# Should print: 4.8.1.78 or similar
```

#### Verify Webcam
```bash
python3 << 'EOF'
import cv2
cap = cv2.VideoCapture(0)
if cap.isOpened():
    print("✓ Camera found")
    ret, frame = cap.read()
    if ret:
        print(f"✓ Camera working: {frame.shape}")
    cap.release()
else:
    print("✗ Camera not found. Check USB connection.")
EOF
```

### 4. Setup Server Component

#### Install Dependencies
```bash
cd ../server
npm install
```

#### Initialize Database
```bash
npm run db:init
```

#### Test Server Startup
```bash
npm run dev
# Expected output: Server listening on http://localhost:3000
# Press Ctrl+C to stop
```

### 5. Setup Viewer Component

#### Install Dependencies
```bash
cd ../viewer
npm install
```

#### Test Viewer Startup
```bash
npm run dev
# Expected output: VITE v5.0.0 ready in XXX ms
# Open http://localhost:5173 in browser
# Press Ctrl+C to stop
```

## Running All Components

### Option 1: Run Locally (Recommended for Development)

**Terminal 1 - Camera Component**
```bash
cd camera
source venv/bin/activate
python3 src/main.py
```

**Terminal 2 - Server Component**
```bash
cd server
npm run dev
```

**Terminal 3 - Viewer Component**
```bash
cd viewer
npm run dev
```

Then:
1. Open `http://localhost:5173` in browser
2. Create account and login
3. Register camera via server API
4. View stream from viewer

### Option 2: Run with Docker Compose

```bash
# From project root
docker-compose up -d

# View logs
docker-compose logs -f

# Stop all services
docker-compose down
```

Access:
- Viewer: http://localhost:5173
- Server API: http://localhost:3000

## Configuration

### Camera Configuration (camera/src/config.py)

```python
# Video Settings
VIDEO_RESOLUTION = (1280, 720)  # 720p
VIDEO_FPS = 30
VIDEO_CODEC = 'h264'

# Device Settings
DEVICE_NAME = 'HomeCamera-01'
DEVICE_ID = 'camera-001'
SERVICE_PORT = 8000

# Server Settings (Phase 2)
SERVER_URL = 'http://localhost:3000'
API_KEY = None  # Set after registration
```

### Server Configuration (server/.env)

```env
# Server Port
PORT=3000

# Database
DB_PATH=./data/aicams.db

# JWT
JWT_SECRET=your-secure-key
JWT_EXPIRES_IN=24h

# CORS
CORS_ORIGIN=http://localhost:5173
```

### Viewer Configuration (viewer/src/config.js)

```javascript
export const API_BASE_URL = 'http://localhost:3000';
export const SIGNALING_URL = 'ws://localhost:3000';
```

## Testing Workflow

### Test 1: Server Startup
```bash
cd server && npm run dev
# Should see: "Server listening on http://localhost:3000"
```

### Test 2: Camera Discovery
```bash
curl http://localhost:3000/api/devices
# Should return: { "devices": [] }
```

### Test 3: User Registration
```bash
curl -X POST http://localhost:3000/api/auth/signup \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@example.com",
    "password": "Test123!",
    "name": "Test User"
  }'
# Should return: { "token": "...", "user": {...} }
```

### Test 4: Viewer Login
1. Open http://localhost:5173
2. Click "Sign Up"
3. Register with test@example.com / Test123!
4. Login
5. Dashboard should load (empty, no cameras yet)

### Test 5: Camera Registration (Future)
Register camera device via API (after Phase 2 implementation)

## Troubleshooting

### Camera not detected
```bash
# Check camera device
ls /dev/video*    # Linux/Mac
# On Mac, try:
system_profiler SPCameraDataType
```

### Port already in use
```bash
# Find process using port
lsof -i :3000    # Server
lsof -i :5173    # Viewer
lsof -i :8000    # Camera

# Kill process
kill -9 <PID>
```

### Database errors
```bash
# Reset database
cd server && npm run db:reset
```

### Module not found errors
```bash
# Reinstall dependencies
cd camera && pip install -r requirements.txt --force-reinstall
cd ../server && npm install --force
cd ../viewer && npm install --force
```

### WebRTC connection fails
- Ensure all three components are running
- Check browser console for errors (F12)
- Verify firewall doesn't block connections
- Try on same machine first (localhost)

## Performance Tuning

### Reduce Camera Latency
```python
# In camera/src/config.py
VIDEO_FPS = 15  # Reduce from 30 to 15
VIDEO_RESOLUTION = (640, 480)  # Reduce resolution
```

### Server Optimization
```bash
# Use production mode
NODE_ENV=production npm start
```

### Viewer Performance
- Close browser tabs
- Disable browser extensions
- Check network bandwidth

## Next Steps

1. ✅ Setup complete
2. ⬜ Start Phase 1 implementation
3. ⬜ Implement video capture (camera)
4. ⬜ Implement WebRTC signaling (server)
5. ⬜ Test streaming
6. ⬜ Proceed to Phase 2

## Getting Help

- Check component-specific README files
- Review [IMPLEMENTATION_PLAN.md](../IMPLEMENTATION_PLAN.md)
- Check error logs in `logs/` directory
- See [Troubleshooting](#troubleshooting) section

## Additional Resources

- [OpenCV Python Docs](https://docs.opencv.org/master/d6/d00/tutorial_py_root.html)
- [WebRTC Documentation](https://webrtc.org/getting-started/overview)
- [Express.js Guide](https://expressjs.com/)
- [React Documentation](https://react.dev/)
