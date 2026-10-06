# Quick Start Guide

Get the AI Cams MVP up and running in 15 minutes!

## Prerequisites Checklist

- [ ] Python 3.8+ installed
- [ ] Node.js 16+ installed
- [ ] Webcam/camera connected
- [ ] Git installed (optional)

## Installation & Setup (5 minutes)

### 1. Clone or Navigate to Project
```bash
cd /Users/Anvi/Desktop/code/aicams
```

### 2. Setup Environment
```bash
# Copy environment file
cp .env.example .env

# Edit .env and set:
# JWT_SECRET=your-secure-key
# DEVICE_NAME=MyCamera
```

### 3. Install Dependencies

**Camera Component (Terminal 1)**
```bash
cd camera
python3 -m venv venv
source venv/bin/activate
pip install -r requirements.txt
```

**Server Component (Terminal 2)**
```bash
cd ../server
npm install
npm run db:init
```

**Viewer Component (Terminal 3)**
```bash
cd ../viewer
npm install
```

## Running (5 minutes)

**Terminal 1: Start Camera**
```bash
cd camera
source venv/bin/activate
python3 src/main.py
# Wait for: "Ready for connections"
```

**Terminal 2: Start Server**
```bash
cd ../server
npm run dev
# Wait for: "Server listening on http://localhost:3000"
```

**Terminal 3: Start Viewer**
```bash
cd ../viewer
npm run dev
# Opens: http://localhost:5173
```

## First Use (5 minutes)

1. **Open Browser**: Go to `http://localhost:5173`
2. **Sign Up**: Create account (any email/password)
3. **Login**: Use credentials from step 2
4. **Dashboard**: Should show empty (no cameras yet)
5. **Wait**: Camera component should be registering

## Next Steps

- ✅ Setup complete!
- 📖 Read [SETUP.md](docs/SETUP.md) for detailed guide
- 📋 See [IMPLEMENTATION_PLAN.md](IMPLEMENTATION_PLAN.md) for roadmap
- 🔧 Check component README files for development

## Troubleshooting Quick Fixes

| Issue | Solution |
|-------|----------|
| Camera not found | `ls /dev/video*` or check USB cable |
| Port 3000 in use | `lsof -i :3000` then `kill -9 <PID>` |
| npm not found | Install Node.js from nodejs.org |
| Python not found | Install Python from python.org |
| WebRTC fails | Ensure all 3 components running |

## Common Commands

```bash
# Kill port
lsof -i :3000 | tail -1 | awk '{print $2}' | xargs kill -9

# Reset database
cd server && npm run db:reset

# View logs
docker-compose logs -f

# Stop everything
docker-compose down
```

## Docker Option

Run everything in containers:

```bash
docker-compose up -d
# Open http://localhost:5173
docker-compose logs -f
docker-compose down
```

## What's Next?

The MVP consists of 3 phases:

### Phase 1: P2P Streaming (Current)
- [x] Project structure created
- [ ] Implement video capture
- [ ] Implement mDNS discovery
- [ ] Implement WebRTC signaling

### Phase 2: Signaling Server
- [ ] Device registration API
- [ ] Authentication system
- [ ] Permission management

### Phase 3: Edge AI
- [ ] Person detection model
- [ ] Frame analysis pipeline
- [ ] Event alerting

## Support

See [IMPLEMENTATION_PLAN.md](IMPLEMENTATION_PLAN.md) and component README files for detailed documentation.

---

**Happy coding! 🚀**
