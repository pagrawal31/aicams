# API Documentation

Complete API reference for the AI Cams system.

## Base URL

```
Development: http://localhost:3000
Production: https://api.aicams.example.com
```

## Authentication

All authenticated endpoints require a Bearer token in the Authorization header:

```
Authorization: Bearer <JWT_TOKEN>
```

Tokens are obtained through login and expire after 24 hours.

---

## Authentication Endpoints

### Register User

Create a new user account.

```http
POST /api/auth/signup
Content-Type: application/json

{
  "email": "user@example.com",
  "password": "SecurePassword123!",
  "name": "John Doe"
}
```

**Response: 201 Created**
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "user": {
    "id": "user-123",
    "email": "user@example.com",
    "name": "John Doe",
    "created_at": "2024-01-01T12:00:00Z"
  }
}
```

**Error: 400 Bad Request**
```json
{
  "error": "Invalid email or password too weak"
}
```

### Login

Authenticate user and get JWT token.

```http
POST /api/auth/login
Content-Type: application/json

{
  "email": "user@example.com",
  "password": "SecurePassword123!"
}
```

**Response: 200 OK**
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "user": {
    "id": "user-123",
    "email": "user@example.com",
    "name": "John Doe"
  }
}
```

**Error: 401 Unauthorized**
```json
{
  "error": "Invalid credentials"
}
```

### Logout

Invalidate current token (client-side implementation).

```javascript
// Remove token from localStorage
localStorage.removeItem('auth_token');
// Redirect to login
window.location.href = '/login';
```

### Refresh Token

Get new token before expiration.

```http
POST /api/auth/refresh
Authorization: Bearer <CURRENT_TOKEN>
```

**Response: 200 OK**
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

---

## Device Management Endpoints

### Register Camera

Register a new camera device.

```http
POST /api/devices/register
Authorization: Bearer <DEVICE_TOKEN>
Content-Type: application/json

{
  "device_id": "camera-001",
  "name": "Living Room Camera",
  "type": "camera",
  "capabilities": ["video", "audio"],
  "resolution": "1280x720",
  "fps": 30
}
```

**Response: 201 Created**
```json
{
  "device_id": "camera-001",
  "auth_token": "device-token-xyz...",
  "server_url": "http://localhost:3000",
  "registered_at": "2024-01-01T12:00:00Z"
}
```

### Get Device Details

Retrieve camera device information.

```http
GET /api/devices/:device_id
Authorization: Bearer <TOKEN>
```

**Response: 200 OK**
```json
{
  "id": "camera-001",
  "name": "Living Room Camera",
  "owner_id": "user-123",
  "type": "camera",
  "capabilities": ["video", "audio"],
  "resolution": "1280x720",
  "fps": 30,
  "is_online": true,
  "last_heartbeat": "2024-01-01T12:05:00Z",
  "created_at": "2024-01-01T12:00:00Z"
}
```

### List User's Devices

Get all cameras owned by user.

```http
GET /api/devices
Authorization: Bearer <USER_TOKEN>
```

**Query Parameters:**
- `skip` (int): Pagination offset, default 0
- `limit` (int): Max results, default 20
- `online_only` (bool): Only online devices

**Response: 200 OK**
```json
{
  "devices": [
    {
      "id": "camera-001",
      "name": "Living Room",
      "is_online": true,
      "owner_id": "user-123"
    },
    {
      "id": "camera-002",
      "name": "Bedroom",
      "is_online": false,
      "owner_id": "user-123"
    }
  ],
  "total": 2,
  "skip": 0,
  "limit": 20
}
```

### Update Device

Update camera settings.

```http
PUT /api/devices/:device_id
Authorization: Bearer <OWNER_TOKEN>
Content-Type: application/json

{
  "name": "Updated Living Room Camera",
  "resolution": "1920x1080"
}
```

**Response: 200 OK**
```json
{
  "id": "camera-001",
  "name": "Updated Living Room Camera",
  "resolution": "1920x1080"
}
```

### Delete Device

Remove a camera device.

```http
DELETE /api/devices/:device_id
Authorization: Bearer <OWNER_TOKEN>
```

**Response: 204 No Content**

### Device Heartbeat

Keep device registration alive (called by camera every 60s).

```http
POST /api/devices/:device_id/heartbeat
Authorization: Bearer <DEVICE_TOKEN>
```

**Response: 200 OK**
```json
{
  "status": "ok",
  "timestamp": "2024-01-01T12:05:00Z"
}
```

---

## Permission Management Endpoints

### Grant Access

Give user permission to view camera.

```http
POST /api/permissions
Authorization: Bearer <OWNER_TOKEN>
Content-Type: application/json

{
  "user_id": "user-456",
  "device_id": "camera-001",
  "permission": "view"
}
```

**Permission Levels:**
- `view` - Can watch live stream and history
- `admin` - Can manage device and users

**Response: 201 Created**
```json
{
  "id": "perm-123",
  "user_id": "user-456",
  "device_id": "camera-001",
  "permission": "view",
  "created_at": "2024-01-01T12:00:00Z"
}
```

### Check Permission

Verify user access to device.

```http
GET /api/permissions/check
Authorization: Bearer <TOKEN>

Query: ?user_id=user-456&device_id=camera-001
```

**Response: 200 OK**
```json
{
  "has_permission": true,
  "permission_level": "view"
}
```

### List Permissions

Get all device shares.

```http
GET /api/devices/:device_id/permissions
Authorization: Bearer <OWNER_TOKEN>
```

**Response: 200 OK**
```json
{
  "permissions": [
    {
      "user_id": "user-456",
      "user_email": "viewer@example.com",
      "permission": "view",
      "created_at": "2024-01-01T12:00:00Z"
    }
  ]
}
```

### Revoke Access

Remove user access to camera.

```http
DELETE /api/permissions/:permission_id
Authorization: Bearer <OWNER_TOKEN>
```

**Response: 204 No Content**

---

## WebRTC Streaming Endpoints

### Create Stream Offer

Request WebRTC offer to view stream.

```http
POST /api/streams/:device_id/offer
Authorization: Bearer <VIEWER_TOKEN>
Content-Type: application/json

{
  "peer_id": "viewer-session-abc"
}
```

**Response: 200 OK**
```json
{
  "session_id": "session-xyz",
  "offer_sdp": "v=0\r\no=...",
  "stun_servers": [
    "stun:stun.l.google.com:19302",
    "stun:stun1.l.google.com:19302"
  ]
}
```

### Submit Answer

Send WebRTC answer to establish connection.

```http
POST /api/streams/:device_id/answer
Authorization: Bearer <VIEWER_TOKEN>
Content-Type: application/json

{
  "session_id": "session-xyz",
  "answer_sdp": "v=0\r\no=..."
}
```

**Response: 200 OK**
```json
{
  "status": "connected",
  "timestamp": "2024-01-01T12:00:00Z"
}
```

### Send ICE Candidate

Relay ICE candidates for connection establishment.

```http
POST /api/streams/:device_id/ice-candidate
Authorization: Bearer <VIEWER_TOKEN>
Content-Type: application/json

{
  "session_id": "session-xyz",
  "candidate": "candidate:1 1 udp..."
}
```

**Response: 200 OK**
```json
{
  "status": "received"
}
```

### Close Stream

Terminate stream session.

```http
DELETE /api/streams/:device_id/sessions/:session_id
Authorization: Bearer <VIEWER_TOKEN>
```

**Response: 204 No Content**

---

## Event & Alert Endpoints

### Send Event Alert

Camera sends alert when event detected (Phase 3).

```http
POST /api/events
Authorization: Bearer <DEVICE_TOKEN>
Content-Type: application/json

{
  "device_id": "camera-001",
  "event_type": "person_detected",
  "confidence": 0.95,
  "metadata": {
    "person_count": 1,
    "location": "center",
    "pose_detected": true
  },
  "snapshot_url": "http://camera-001/snapshots/2024-01-01-12-00-00.jpg",
  "timestamp": "2024-01-01T12:00:00Z"
}
```

**Event Types:**
- `person_detected` - Person present in frame
- `motion_detected` - Movement detected
- `object_detected` - Specific object found
- `anomaly_detected` - Unusual activity
- `sound_detected` - Audio event (future)

**Response: 201 Created**
```json
{
  "event_id": "event-xyz",
  "status": "received",
  "timestamp": "2024-01-01T12:00:00Z"
}
```

### Get Events for Device

Retrieve event history.

```http
GET /api/events
Authorization: Bearer <TOKEN>

Query Parameters:
  ?device_id=camera-001
  &event_type=person_detected
  &start_date=2024-01-01T00:00:00Z
  &end_date=2024-01-02T00:00:00Z
  &limit=50
  &skip=0
```

**Response: 200 OK**
```json
{
  "events": [
    {
      "id": "event-123",
      "device_id": "camera-001",
      "event_type": "person_detected",
      "confidence": 0.95,
      "snapshot_url": "...",
      "created_at": "2024-01-01T12:00:00Z"
    }
  ],
  "total": 245,
  "skip": 0,
  "limit": 50
}
```

### Get Event Details

Retrieve specific event information.

```http
GET /api/events/:event_id
Authorization: Bearer <TOKEN>
```

**Response: 200 OK**
```json
{
  "id": "event-123",
  "device_id": "camera-001",
  "event_type": "person_detected",
  "confidence": 0.95,
  "metadata": {
    "person_count": 1,
    "location": "center"
  },
  "snapshot_url": "...",
  "created_at": "2024-01-01T12:00:00Z"
}
```

---

## User Management Endpoints

### Get User Profile

Retrieve current user information.

```http
GET /api/users/profile
Authorization: Bearer <TOKEN>
```

**Response: 200 OK**
```json
{
  "id": "user-123",
  "email": "user@example.com",
  "name": "John Doe",
  "avatar_url": "...",
  "created_at": "2024-01-01T12:00:00Z"
}
```

### Update Profile

Update user information.

```http
PUT /api/users/profile
Authorization: Bearer <TOKEN>
Content-Type: application/json

{
  "name": "Jane Doe",
  "avatar_url": "https://..."
}
```

**Response: 200 OK**
```json
{
  "id": "user-123",
  "name": "Jane Doe",
  "avatar_url": "https://..."
}
```

### Change Password

Update user password.

```http
POST /api/users/change-password
Authorization: Bearer <TOKEN>
Content-Type: application/json

{
  "old_password": "OldPassword123!",
  "new_password": "NewPassword456!"
}
```

**Response: 200 OK**
```json
{
  "status": "password_changed"
}
```

---

## Error Responses

All error responses follow this format:

```json
{
  "error": "Error message",
  "code": "ERROR_CODE",
  "status": 400,
  "timestamp": "2024-01-01T12:00:00Z"
}
```

### Common Error Codes

| Code | HTTP | Description |
|------|------|-------------|
| `INVALID_CREDENTIALS` | 401 | Login failed |
| `UNAUTHORIZED` | 401 | Invalid/missing token |
| `FORBIDDEN` | 403 | No permission |
| `NOT_FOUND` | 404 | Resource not found |
| `CONFLICT` | 409 | Resource already exists |
| `VALIDATION_ERROR` | 400 | Invalid input |
| `INTERNAL_ERROR` | 500 | Server error |

---

## Rate Limiting

API is rate-limited to prevent abuse:

- **Default:** 100 requests per minute per IP
- **Auth endpoints:** 10 requests per minute (stricter)
- **Event submission:** 1000 events per hour per device

Rate limit headers:

```
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 95
X-RateLimit-Reset: 1704110400
```

---

## WebSocket Events (Phase 2+)

Real-time event streaming via WebSocket.

### Connect
```javascript
const ws = new WebSocket('ws://localhost:3000/stream');

ws.onopen = () => {
  ws.send(JSON.stringify({
    type: 'subscribe',
    device_id: 'camera-001',
    token: 'jwt-token'
  }));
};
```

### Receive Events
```javascript
ws.onmessage = (event) => {
  const data = JSON.parse(event.data);
  // { type: 'event', event: {...} }
};
```

---

## Code Examples

### JavaScript/TypeScript

```javascript
// Login
const response = await fetch('/api/auth/login', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({
    email: 'user@example.com',
    password: 'password'
  })
});

const { token } = await response.json();
localStorage.setItem('auth_token', token);

// Get cameras
const cameras = await fetch('/api/devices', {
  headers: { 'Authorization': `Bearer ${token}` }
});

const { devices } = await cameras.json();
```

### Python

```python
import requests

# Login
response = requests.post('http://localhost:3000/api/auth/login', json={
    'email': 'camera@example.com',
    'password': 'device-password'
})
token = response.json()['token']

# Register camera
headers = {'Authorization': f'Bearer {token}'}
response = requests.post('http://localhost:3000/api/devices/register', 
    headers=headers,
    json={
        'device_id': 'camera-001',
        'name': 'Living Room',
        'capabilities': ['video']
    }
)
```

### cURL

```bash
# Register
curl -X POST http://localhost:3000/api/auth/signup \
  -H "Content-Type: application/json" \
  -d '{
    "email": "user@example.com",
    "password": "password",
    "name": "User"
  }'

# Get devices with token
curl -H "Authorization: Bearer TOKEN" \
  http://localhost:3000/api/devices
```

---

## OpenAPI/Swagger

Full OpenAPI 3.0 specification available at:
```
http://localhost:3000/api-docs
```

Interactive API explorer available at:
```
http://localhost:3000/api-docs/ui
```
