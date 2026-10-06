import express from 'express';
import cors from 'cors';

const app = express();
const port = Number(process.env.PORT || 3000);

app.use(cors());
app.use(express.json({ limit: '2mb' }));

const cameras = new Map();

function makeCameraRecord(payload) {
  const deviceId = payload.deviceId || payload.device_id || `camera-${Date.now()}`;
  const name = payload.name || 'Home Camera';
  const location = payload.location || 'Main Floor';
  const ip = payload.ip || payload.host || '127.0.0.1';
  const port = Number(payload.port || 8000);

  return {
    id: deviceId,
    name,
    location,
    ip,
    port,
    baseUrl: `http://${ip}:${port}`,
    status: 'online',
    updatedAt: new Date().toISOString(),
    capabilities: payload.capabilities || ['video'],
    deviceType: payload.deviceType || payload.type || 'camera'
  };
}

app.get('/health', (_req, res) => {
  res.json({ ok: true, timestamp: new Date().toISOString() });
});

app.get('/cameras', (_req, res) => {
  const list = [...cameras.values()].map((camera) => ({
    id: camera.id,
    name: camera.name,
    location: camera.location,
    ip: camera.ip,
    port: camera.port,
    baseUrl: camera.baseUrl,
    status: camera.status,
    capabilities: camera.capabilities
  }));

  res.json({ cameras: list });
});

app.post('/register-camera', (req, res) => {
  const camera = makeCameraRecord(req.body || {});
  cameras.set(camera.id, camera);

  res.status(200).json({
    ok: true,
    camera,
    message: 'Camera registered successfully'
  });
});

app.post('/offer', (req, res) => {
  const { targetId, offer } = req.body || {};

  if (!targetId || !offer) {
    return res.status(400).json({ error: 'targetId and offer are required' });
  }

  const camera = cameras.get(targetId);
  if (!camera) {
    return res.status(404).json({ error: 'camera not found' });
  }

  return res.json({
    ok: true,
    targetId,
    targetUrl: camera.baseUrl,
    message: 'Offer accepted by coordinator'
  });
});

app.post('/answer', (req, res) => {
  const { sessionId, answer } = req.body || {};
  if (!sessionId || !answer) {
    return res.status(400).json({ error: 'sessionId and answer are required' });
  }

  res.json({ ok: true, sessionId, answer });
});

app.post('/ice-candidate', (req, res) => {
  const { sessionId, candidate } = req.body || {};
  if (!sessionId || !candidate) {
    return res.status(400).json({ error: 'sessionId and candidate are required' });
  }

  res.json({ ok: true, sessionId, candidate });
});

app.listen(port, '0.0.0.0', () => {
  console.log(`AI Cams coordinator server listening on http://0.0.0.0:${port}`);
});
