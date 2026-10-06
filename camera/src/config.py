"""
Configuration management for camera component
"""

import os
from dotenv import load_dotenv

# Load environment variables
load_dotenv()


class Config:
    """Configuration class"""
    
    # Video settings
    CAMERA_DEVICE = int(os.getenv('CAMERA_DEVICE', '0'))
    VIDEO_RESOLUTION = (
        int(os.getenv('CAMERA_RESOLUTION_WIDTH', '1280')),
        int(os.getenv('CAMERA_RESOLUTION_HEIGHT', '720'))
    )
    VIDEO_FPS = int(os.getenv('CAMERA_FPS', '30'))
    VIDEO_CODEC = os.getenv('VIDEO_CODEC', 'h264')
    
    # Device settings
    DEVICE_NAME = os.getenv('DEVICE_NAME', 'HomeCamera-01')
    DEVICE_ID = os.getenv('DEVICE_ID', 'camera-001')
    DEVICE_TYPE = 'camera'
    
    # Network settings
    SERVICE_PORT = int(os.getenv('SERVICE_PORT', '8000'))
    SERVICE_TYPE = os.getenv('SERVICE_TYPE', '_camera-stream._tcp')
    MDNS_TTL = 4500  # Time to live for mDNS
    AUDIO_DEVICE = os.getenv('AUDIO_DEVICE', 'none:1')
    
    # Server settings (Phase 2)
    SERVER_URL = os.getenv('SERVER_URL', None)  # Optional server for registration
    API_KEY = os.getenv('CAMERA_API_KEY', None)
    
    # WebRTC settings
    STUN_SERVERS = os.getenv('STUN_SERVERS', 'stun:stun.l.google.com:19302,stun:stun1.l.google.com:19302').split(',')
    TURN_SERVERS = os.getenv('TURN_SERVERS', '').split(',') if os.getenv('TURN_SERVERS') else []
    
    # Performance settings
    FRAME_BUFFER_SIZE = 30  # Circular buffer size
    ENCODING_QUALITY = 85  # JPEG quality for snapshots (1-100)
    
    # Logging
    LOG_LEVEL = os.getenv('LOG_LEVEL', 'INFO')
    
    @classmethod
    def validate(cls):
        """Validate configuration"""
        if cls.CAMERA_DEVICE < 0:
            raise ValueError("CAMERA_DEVICE must be >= 0")
        if cls.VIDEO_FPS <= 0 or cls.VIDEO_FPS > 60:
            raise ValueError("VIDEO_FPS must be between 1 and 60")
        if cls.SERVICE_PORT < 1024 or cls.SERVICE_PORT > 65535:
            raise ValueError("SERVICE_PORT must be between 1024 and 65535")
        return True
    
    @classmethod
    def to_dict(cls):
        """Convert config to dictionary"""
        return {
            'device_name': cls.DEVICE_NAME,
            'device_id': cls.DEVICE_ID,
            'resolution': f"{cls.VIDEO_RESOLUTION[0]}x{cls.VIDEO_RESOLUTION[1]}",
            'fps': cls.VIDEO_FPS,
            'service_port': cls.SERVICE_PORT,
            'stun_servers': cls.STUN_SERVERS
        }
