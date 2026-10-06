"""
AI Cams - Camera Component
Main entry point for the camera device
"""

import asyncio
import sys
import signal
import logging
from pathlib import Path

from config import Config
from video_capture import VideoCapture
from mdns_service import MDNSService
from webrtc_server import WebRTCServer

# Setup logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s',
    handlers=[
        logging.StreamHandler(sys.stdout),
        logging.FileHandler('logs/camera.log', mode='a')
    ]
)
logger = logging.getLogger(__name__)


class CameraApp:
    """Main camera application"""
    
    def __init__(self):
        self.config = Config()
        self.video_capture = None
        self.mdns_service = None
        self.webrtc_server = None
        self._running = False
    
    async def initialize(self):
        """Initialize camera components"""
        logger.info("Initializing AI Cams Camera Component...")
        
        # Initialize video capture
        logger.info("Setting up video capture...")
        self.video_capture = VideoCapture(
            device_id=self.config.CAMERA_DEVICE,
            resolution=self.config.VIDEO_RESOLUTION,
            fps=self.config.VIDEO_FPS
        )
        
        if not self.video_capture.initialize():
            logger.warning("Host webcam unavailable; continuing in phone-camera relay mode")
            self.video_capture = None
        else:
            logger.info(f"✓ Video capture initialized: {self.config.VIDEO_RESOLUTION} @ {self.config.VIDEO_FPS}fps")

            # Advertise only the laptop's own camera, if available.
            logger.info("Starting mDNS service discovery...")
            self.mdns_service = MDNSService(
                device_name=self.config.DEVICE_NAME,
                service_port=self.config.SERVICE_PORT,
                device_info={
                    'device_id': self.config.DEVICE_ID,
                    'capabilities': ['video'],
                    'resolution': f"{self.config.VIDEO_RESOLUTION[0]}x{self.config.VIDEO_RESOLUTION[1]}",
                    'fps': str(self.config.VIDEO_FPS)
                }
            )

            if not self.mdns_service.start():
                logger.warning("mDNS advertisement failed. Continuing with direct WebRTC streaming without local discovery.")
            else:
                logger.info(f"✓ mDNS service advertised: {self.config.DEVICE_NAME}._camera-stream._tcp.local")
        
        # Initialize WebRTC server
        logger.info("Starting WebRTC server...")
        self.webrtc_server = WebRTCServer(
            video_source=self.video_capture,
            port=self.config.SERVICE_PORT,
            audio_device=self.config.AUDIO_DEVICE
        )
        
        if not await self.webrtc_server.initialize():
            logger.error("Failed to initialize WebRTC server")
            return False
        
        logger.info(f"✓ WebRTC server listening on ws://0.0.0.0:{self.config.SERVICE_PORT}")
        
        return True
    
    async def run(self):
        """Run camera application"""
        self._running = True
        logger.info("Camera component ready for connections")
        
        try:
            # Run WebRTC server
            await self.webrtc_server.run()
        except KeyboardInterrupt:
            logger.info("Received interrupt signal")
        except Exception as e:
            logger.error(f"Error running camera: {e}", exc_info=True)
        finally:
            await self.shutdown()
    
    async def shutdown(self):
        """Cleanup and shutdown"""
        logger.info("Shutting down camera component...")
        self._running = False
        
        if self.webrtc_server:
            await self.webrtc_server.shutdown()
        
        if self.mdns_service:
            self.mdns_service.stop()
        
        if self.video_capture:
            self.video_capture.release()
        
        logger.info("Camera component shutdown complete")
    
    def handle_signal(self, signum, frame):
        """Handle system signals"""
        logger.info(f"Received signal {signum}")
        asyncio.create_task(self.shutdown())


async def main():
    """Main entry point"""
    # Create logs directory
    Path('logs').mkdir(exist_ok=True)
    
    app = CameraApp()
    
    # Setup signal handlers
    signal.signal(signal.SIGINT, app.handle_signal)
    signal.signal(signal.SIGTERM, app.handle_signal)
    
    # Initialize and run
    if await app.initialize():
        await app.run()
    else:
        logger.error("Failed to initialize camera application")
        sys.exit(1)


if __name__ == '__main__':
    asyncio.run(main())
