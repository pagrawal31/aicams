# Home-Safety Video Streaming + Edge AI System
## Phased Implementation Plan

---

## Overview
A distributed home safety system that captures video on-device, streams to authorized viewers, and performs real-time edge AI analysis for event detection and alerting.

---

## Phase 1: Local P2P Video Streaming (No Server)

### Goal
Enable video streaming from a recording device to viewers on the same local Wi-Fi network without requiring a cloud server.

### Architecture
```
Camera/Device (Producer)
  ↓ (Video Frames)
Local Network (Wi-Fi)
  ↓
Viewer Devices (Consumers) on same Wi-Fi
```

### Key Components

#### 1.1 Video Capture
- **Technology**: OpenCV or FFmpeg
- **Input**: Webcam, USB camera, or built-in device camera
- **Output**: Raw frame stream (H.264/H.265 encoded)
- **Resolution**: Start with 720p@30fps
- **Buffer**: Circular buffer to handle frame drops

#### 1.2 P2P Network Discovery
- **Technology**: mDNS (Multicast DNS) / Bonjour
- **Service Name**: `_camera-stream._tcp.local.`
- **Information Advertised**: 
  - Device name
  - Local IP address
  - Port number
  - Capabilities (resolution, framerate)

#### 1.3 Streaming Protocol (Direct)
- **Technology**: WebRTC (for viewer browser compatibility) OR RTMP/RTSP (for simplicity)
- **Option A (Recommended)**: WebRTC
  - Pros: Works in web browsers, P2P capable, low latency
  - Cons: Slightly more complex setup
  - Use: Simple signaling server on local network (lightweight)
- **Option B (Alternative)**: RTMP/RTSP
  - Pros: Simpler, well-established
  - Cons: Less interactive, higher latency

#### 1.4 Viewer Application
- **Technology**: Web-based (HTML5 + JavaScript)
- **Features**:
  - Discover available cameras on local network
  - Connect and view stream
  - Display video in real-time
  - Basic controls (pause, resolution selection)

#### 1.5 Network Assumptions
- Both camera and viewer on same local Wi-Fi network
- No firewall blocks between them
- Local subnet allows mDNS discovery

### Implementation Steps

1. **Setup Development Environment**
   - Install FFmpeg, Python (with OpenCV), Node.js
   - Choose framework: Express.js or Flask for signaling server

2. **Implement Video Capture**
   - Create simple script to capture from camera
   - Test frame rate and quality
   - Implement circular buffer for stability

3. **Implement mDNS Discovery**
   - Broadcast camera service on local network
   - Test discovery from multiple devices

4. **Choose & Setup Streaming Protocol**
   - If WebRTC: Setup lightweight signaling server (Node.js + Socket.io)
   - If RTMP: Use FFmpeg to stream to RTMP server

5. **Build Viewer Web App**
   - Create simple web interface
   - Connect to streaming source
   - Display video stream

6. **Testing**
   - Test on multiple devices on same Wi-Fi
   - Measure latency
   - Test network resilience

### Success Criteria
- ✅ Camera discovered automatically on local network
- ✅ Viewer can stream video within 1-2 seconds
- ✅ Latency < 500ms
- ✅ Multiple viewers can connect simultaneously

---

## Phase 2: Signaling Server (Local or Cloud)

### Goal
Add a signaling and coordination server to enable features beyond local P2P and prepare for multi-network scenarios.

### Architecture
```
Camera/Device (Producer)
  ↓ (Register + Stream)
Signaling Server (Local Network or Cloud)
  ↓ (Connection Info)
Viewer Devices (Can be remote)
```

### Key Components

#### 2.1 Signaling Server
- **Technology**: Node.js + Express + Socket.io
- **Responsibilities**:
  - Camera device registration & heartbeat
  - Viewer authentication & authorization
  - Distribute camera connection info
  - Relay WebRTC SDP offers/answers for initial P2P setup
  - Fallback relay (if P2P not possible)

#### 2.2 Device Management
- **Features**:
  - Store camera metadata (ID, name, owner, capabilities)
  - Device status tracking (online/offline)
  - Admin dashboard to manage devices

#### 2.3 Authentication & Authorization
- **Technology**: JWT tokens
- **Flow**:
  - Camera authenticates with server (API key or password)
  - Viewer authenticates (username/password or social login)
  - Viewer gets token to access specific cameras

#### 2.4 Database
- **Technology**: SQLite (local) or PostgreSQL (cloud)
- **Tables**:
  - `devices` (cameras)
  - `users` (viewers)
  - `permissions` (who can view which camera)
  - `sessions` (active connections)

#### 2.5 Viewer Application (Enhanced)
- Login capability
- View multiple cameras
- Permission-based camera access

### Implementation Steps

1. **Setup Signaling Server**
   - Create Node.js/Express server
   - Implement Socket.io for WebRTC signaling
   - Create API endpoints for device registration

2. **Implement Authentication**
   - JWT token generation and validation
   - Device registration API
   - User login/signup endpoints

3. **Implement Device Management**
   - Store device info in database
   - Heartbeat mechanism to track online status
   - Admin interface to manage devices

4. **Update Camera Client**
   - Register with signaling server
   - Send heartbeats periodically
   - Report status changes

5. **Update Viewer Application**
   - Add login
   - Fetch list of accessible cameras
   - Connect via signaling server

6. **Testing**
   - Test camera registration
   - Test viewer authentication
   - Test connection flow
   - Test multi-camera scenarios

### Success Criteria
- ✅ Camera registers with server
- ✅ Viewer authenticates and retrieves camera list
- ✅ Connection established via signaling server
- ✅ Works across different network segments
- ✅ Graceful handling of offline cameras

---

## Phase 3: Edge AI Analysis & Event Detection

### Goal
Analyze video frames on the recording device in real-time and generate alerts for detected events.

### Architecture
```
Video Stream
  ↓
Frame Processor (On Camera Device)
  ↓ (AI Model)
Event Detector
  ↓
Alert Handler → Notifications, Logs, Remote Server
```

### Key Components

#### 3.1 Edge AI Models
- **Technology**: TensorFlow Lite, PyTorch Mobile, or ONNX Runtime
- **Models to Implement**:
  - **Person Detection**: Detect presence of people
  - **Motion Detection**: Track movement in frame
  - **Anomaly Detection**: Unusual activity (e.g., object left behind, intrusion)
  - **Object Detection**: Specific items (weapons, packages, etc.)
  - **Action Recognition**: Specific behaviors (running, falling, etc.)

#### 3.2 Frame Processing Pipeline
- **Capture**: Read frame from video stream
- **Preprocess**: Resize, normalize (optimized for edge device)
- **Inference**: Run AI model (optimized for speed)
- **Post-process**: Parse results, filter low-confidence detections
- **Event Generation**: Create event objects if thresholds met

#### 3.3 Event Detection Logic
- **Rules Engine**:
  - Person detected in area → Alert
  - Sustained motion for X seconds → Alert
  - Specific objects detected → Alert
  - Multiple events in short time → Escalate alert
- **Configurability**: Allow custom rules per camera

#### 3.4 Alert Handler
- **Local Alerts**:
  - System notifications on camera device
  - Local log files
- **Remote Alerts**:
  - Send to signaling server
  - Push notifications to viewers
  - Email notifications
  - Webhook integrations

#### 3.5 Storage
- **Frame Snapshots**: Store frames where events detected
- **Event Logs**: Timestamp, event type, confidence, snapshot
- **Retention**: Configurable (e.g., 7 days local, older in cloud)

### Implementation Steps

1. **Choose AI Models**
   - Evaluate lightweight models (MobileNet, YOLOv5 Lite, etc.)
   - Consider device hardware (CPU/GPU/TPU)
   - Download pre-trained models

2. **Setup Edge AI Runtime**
   - Install TensorFlow Lite or PyTorch Mobile
   - Test model inference speed
   - Optimize for device (quantization if needed)

3. **Implement Frame Processing Pipeline**
   - Read frames from video stream
   - Implement preprocessing
   - Run inference
   - Handle output

4. **Implement Event Detection**
   - Define event rules
   - Implement detection logic
   - Add confidence thresholds
   - Test with sample videos

5. **Implement Alert Handler**
   - Local logging
   - Send alerts to signaling server
   - Queue system for reliability

6. **Implement Snapshot Storage**
   - Save frames with alerts
   - Implement cleanup/retention policy
   - Create web interface to view snapshots

7. **Testing**
   - Test various scenarios (person entering, motion, objects)
   - Measure inference time impact on streaming
   - Test alert delivery
   - Optimize for false positive/negatives

### Success Criteria
- ✅ Models run on device at 2-5 FPS (without impacting video streaming)
- ✅ Alerts generated within 1-2 seconds of event
- ✅ Alerts delivered to viewers in real-time
- ✅ Snapshots saved successfully
- ✅ < 20% false positive rate

---

## Phase 4: Cloud Integration & Advanced Features

### Goal
Add cloud backup, advanced analytics, and scalability for multiple homes/devices.

### Key Components

#### 4.1 Cloud Backend Expansion
- Video clip storage (cloud storage like AWS S3)
- Advanced analytics (pattern detection, trend analysis)
- ML model updates (push new models to edge devices)
- Historical query and replay

#### 4.2 Mobile App
- Native iOS/Android app for push notifications
- Better performance than web app
- Offline caching

#### 4.3 Advanced Features
- **AI Improvements**:
  - Facial recognition (identify family members)
  - Sound detection (glass breaking, alarms)
  - Activity patterns (learning user habits)
- **Analytics Dashboard**:
  - Event statistics and trends
  - Most active times
  - Common alerts
- **Smart Rules**:
  - Time-based rules (alert only when away)
  - ML-based anomaly detection
  - Integration with smart home (turn on lights on alert)

#### 4.4 Multi-User Support
- Household groups and sharing
- Permission levels (owner, viewer, admin)
- Activity logs for each user

#### 4.5 Integration APIs
- Third-party integrations (IFTTT, Home Assistant)
- Webhook for custom automations
- Export data APIs

### Implementation Steps

1. Expand cloud backend (Python + Django/FastAPI)
2. Implement cloud storage integration
3. Build mobile app (React Native or Flutter)
4. Add advanced AI models
5. Create analytics dashboard
6. Implement permission system
7. Add integrations

---

## Phase 5: Optimization & Production Hardening

### Goal
Production-ready system with security, reliability, and scalability.

### Key Areas

#### 5.1 Security
- TLS encryption for all communications
- Device certificate management
- Secure key storage (hardware security modules)
- Rate limiting and DDoS protection
- Regular security audits

#### 5.2 Reliability
- Failover mechanisms
- Database replication
- Log aggregation and monitoring
- Health checks and alerting
- Graceful degradation

#### 5.3 Scalability
- Load balancing for signaling servers
- Database optimization and indexing
- CDN for video distribution (optional)
- Microservices architecture

#### 5.4 Performance Monitoring
- Latency monitoring
- Error rate tracking
- Resource usage (CPU, memory, bandwidth)
- Performance dashboards

#### 5.5 Documentation & Support
- User guides
- Admin documentation
- API documentation
- Support portal

---

## Technology Stack Summary

| Component | Phase | Technology |
|-----------|-------|-----------|
| Video Capture | 1 | OpenCV / FFmpeg |
| Network Discovery | 1 | mDNS |
| Streaming | 1 | WebRTC / RTMP |
| Signaling Server | 2 | Node.js + Express |
| Database | 2 | SQLite / PostgreSQL |
| AI Runtime | 3 | TensorFlow Lite / PyTorch Mobile |
| Cloud Backend | 4 | Python + FastAPI |
| Cloud Storage | 4 | AWS S3 / Google Cloud |
| Mobile App | 4 | React Native / Flutter |
| Web Frontend | 1+ | React / Vue.js |

---

## MVP Definition

**Minimum Viable Product = Phase 1 + Phase 2 + Phase 3**

### MVP Features
1. ✅ Local Wi-Fi P2P streaming (Phase 1)
2. ✅ Device registration & viewer authentication (Phase 2)
3. ✅ Person detection and basic alerts (Phase 3)
4. ✅ Snapshot storage on device
5. ✅ Real-time alert notifications

### MVP Tech Stack
- **Camera Device**: Python + OpenCV + FFmpeg
- **Signaling Server**: Node.js + Express + Socket.io
- **Database**: SQLite (can upgrade to PostgreSQL)
- **AI**: TensorFlow Lite (MobileNet for person detection)
- **Viewer App**: React (web-based)

### Estimated Timeline
- **Phase 1 (P2P Streaming)**: 2-3 weeks
- **Phase 2 (Signaling Server)**: 2 weeks
- **Phase 3 (Edge AI)**: 2-3 weeks
- **Testing & Polish**: 1-2 weeks
- **Total MVP**: ~7-11 weeks

---

## Next Steps

1. ✅ Review this plan
2. ✅ Confirm technology choices
3. ✅ Setup development environment
4. ✅ Start Phase 1 implementation
5. ✅ Iterate and refine

---

## Questions & Decisions

- [ ] Preferred AI models? (YOLO, MobileNet, etc.)
- [ ] Target edge devices? (Raspberry Pi, Jetson Nano, etc.)
- [ ] Maximum latency requirement? (200ms, 500ms, 1s?)
- [ ] Number of simultaneous viewers per camera?
- [ ] Required frame resolution? (720p, 1080p?)
- [ ] Cloud deployment preference? (AWS, GCP, Azure, self-hosted?)
