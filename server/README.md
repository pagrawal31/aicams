# Server Component (Phase 2)

## Overview
This component is the signaling and coordination server that manages device registration, user authentication, and facilitates WebRTC connections between cameras and viewers.

## Features
- Device registration and management
- User authentication (JWT-based)
- Permission and authorization system
- WebRTC signaling server
- Event and alert relay
- Database management

## File Structure
```
server/
├── src/
│   ├── index.js          # Express server entry point
│   ├── api/
│   │   ├── devices.js    # Device registration/management APIs
│   │   ├── users.js      # User authentication APIs
│   │   ├── auth.js       # Login/token APIs
│   │   ├── streams.js    # WebRTC signaling APIs
│   │   └── events.js     # Event/alert APIs
│   ├── services/
│   │   ├── auth.js       # JWT token management
│   │   ├── db.js         # Database service
│   │   ├── signaling.js  # WebRTC signaling logic
│   │   └── permissions.js # Permission checking
│   ├── models/
│   │   ├── Device.js
│   │   ├── User.js
│   │   └── Event.js
│   ├── middleware/
│   │   ├── auth.js       # JWT verification
│   │   └── errorHandler.js
│   ├── config.js
│   └── db/
│       └── schema.sql
├── package.json
├── Dockerfile
├── .dockerignore
└── README.md
```

## Configuration

Edit `.env` file (copy from `.env.example`):

```env
# Server
NODE_ENV=development
PORT=3000
HOST=0.0.0.0

# Database
DB_PATH=./data/aicams.db

# JWT
JWT_SECRET=your-secret-key-change-this
JWT_EXPIRES_IN=24h

# CORS
CORS_ORIGIN=http://localhost:5173,http://localhost:3000

# Signaling
SIGNALING_PORT=3000
STUN_SERVERS=stun:stun.l.google.com:19302,stun:stun1.l.google.com:19302
```

## Development Setup

### 1. Install Dependencies
```bash
cd server
npm install
```

### 2. Setup Database
```bash
npm run db:init
```

### 3. Start Development Server
```bash
npm run dev
```

Expected output:
```
[INFO] Server starting...
[INFO] Database initialized
[INFO] JWT Secret configured
[INFO] Server listening on http://localhost:3000
[INFO] WebSocket signaling ready
```

## API Endpoints

### Device APIs

#### Register Device
```
POST /api/devices/register
Content-Type: application/json

{
  "device_id": "camera-001",
  "name": "Living Room Camera",
  "type": "camera",
  "capabilities": ["video", "audio"]
}

Response: { device_id, auth_token, ...}
```

#### Heartbeat
```
POST /api/devices/:device_id/heartbeat
Authorization: Bearer <auth_token>
```

#### List Devices (Admin)
```
GET /api/devices
Authorization: Bearer <user_token>
```

### User APIs

#### Sign Up
```
POST /api/auth/signup
{
  "email": "user@example.com",
  "password": "secure-password",
  "name": "John Doe"
}
```

#### Login
```
POST /api/auth/login
{
  "email": "user@example.com",
  "password": "secure-password"
}

Response: { token, user: {...} }
```

#### Get User Profile
```
GET /api/users/profile
Authorization: Bearer <token>
```

### Permission APIs

#### Grant Access
```
POST /api/permissions
{
  "user_id": "user-123",
  "device_id": "camera-001",
  "permission": "view"
}
```

#### Check Permission
```
GET /api/permissions/check?user_id=user-123&device_id=camera-001
```

### WebRTC Signaling APIs

#### Create WebRTC Offer
```
POST /api/streams/:device_id/offer
Authorization: Bearer <token>

Response: { offer_sdp, session_id }
```

#### Submit WebRTC Answer
```
POST /api/streams/:device_id/answer
{
  "session_id": "session-123",
  "answer_sdp": "..."
}
```

#### ICE Candidate
```
POST /api/streams/:device_id/ice-candidate
{
  "session_id": "session-123",
  "candidate": "..."
}
```

### Event APIs

#### Send Event Alert
```
POST /api/events
Authorization: Bearer <device_token>

{
  "device_id": "camera-001",
  "event_type": "person_detected",
  "confidence": 0.95,
  "snapshot_url": "...",
  "timestamp": "2024-01-01T12:00:00Z"
}
```

#### Get Events for Device
```
GET /api/events?device_id=camera-001&limit=50
Authorization: Bearer <token>
```

## Database Schema

### Devices Table
```sql
CREATE TABLE devices (
  id TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  type TEXT NOT NULL,
  owner_id TEXT NOT NULL,
  capabilities JSON,
  last_heartbeat DATETIME,
  is_online BOOLEAN,
  created_at DATETIME,
  FOREIGN KEY (owner_id) REFERENCES users(id)
);
```

### Users Table
```sql
CREATE TABLE users (
  id TEXT PRIMARY KEY,
  email TEXT UNIQUE NOT NULL,
  password_hash TEXT NOT NULL,
  name TEXT,
  created_at DATETIME
);
```

### Permissions Table
```sql
CREATE TABLE permissions (
  id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL,
  device_id TEXT NOT NULL,
  permission_level TEXT,
  created_at DATETIME,
  FOREIGN KEY (user_id) REFERENCES users(id),
  FOREIGN KEY (device_id) REFERENCES devices(id),
  UNIQUE(user_id, device_id)
);
```

### Events Table
```sql
CREATE TABLE events (
  id TEXT PRIMARY KEY,
  device_id TEXT NOT NULL,
  event_type TEXT NOT NULL,
  confidence FLOAT,
  snapshot_path TEXT,
  data JSON,
  created_at DATETIME,
  FOREIGN KEY (device_id) REFERENCES devices(id)
);
```

## Scripts

```bash
# Development
npm run dev          # Start with hot reload

# Production
npm start           # Start production server

# Database
npm run db:init     # Initialize database
npm run db:reset    # Reset database (dev only)
npm run db:migrate  # Run migrations

# Testing
npm test           # Run tests
npm run test:watch # Watch mode
npm run lint       # ESLint

# Docker
npm run docker:build
npm run docker:run
```

## Phase 2 TODO

- [ ] Setup Express server and middleware
- [ ] Implement JWT authentication
- [ ] Create SQLite database and schema
- [ ] Implement device registration API
- [ ] Implement user authentication API
- [ ] Implement permission system
- [ ] Setup WebRTC signaling
- [ ] Implement event relay
- [ ] Error handling and logging
- [ ] Unit and integration tests
- [ ] API documentation (Swagger/OpenAPI)

## Architecture

```
┌──────────────┐         ┌─────────────────────────────────┐
│   Cameras    │         │   Signaling Server              │
│              │─────────│─ Device Registration            │
│              │         │─ JWT Auth                       │
└──────────────┘         │─ WebRTC Signaling               │
                         │─ Permission Management          │
┌──────────────┐         │─ Event Relay                    │
│   Viewers    │─────────│                                 │
│              │         └──────────────────────────────────┘
└──────────────┘                    │
                          ┌─────────┴──────────┐
                          │                    │
                        ┌──────┐          ┌─────────┐
                        │Users │          │Devices  │
                        │ DB   │          │  DB     │
                        └──────┘          └─────────┘
                          │                    │
                        ┌──────────────────────┴─┐
                        │   Permissions Table    │
                        └──────────────────────────┘
```

## Security Considerations

- ✅ All API endpoints require authentication (JWT)
- ✅ Permission checks on all resource access
- ✅ Password hashing (bcrypt)
- ✅ CORS protection
- ✅ Rate limiting (to be implemented)
- ✅ Input validation
- ✅ SQL injection protection (parameterized queries)

## Performance

- SQLite suitable for MVP (upgrade to PostgreSQL for Phase 4)
- In-memory WebRTC session cache
- Event queue for async processing
- Connection pooling for database

## Deployment

See [DEPLOYMENT.md](../docs/DEPLOYMENT.md) for production deployment guide.

## Next Steps

After completing Phase 2, proceed to Phase 3 (Edge AI Analysis).
