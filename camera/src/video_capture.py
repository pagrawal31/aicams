"""
Video capture module using OpenCV
"""

import cv2
import numpy as np
from collections import deque
from threading import Thread, Lock
import logging
import time

logger = logging.getLogger(__name__)


class FrameBuffer:
    """Circular buffer for video frames"""
    
    def __init__(self, max_size=30):
        self.buffer = deque(maxlen=max_size)
        self.lock = Lock()
        self.frame_count = 0
        self.dropped_frames = 0
    
    def put(self, frame):
        """Add frame to buffer"""
        with self.lock:
            # If buffer is full, a frame is automatically dropped
            if len(self.buffer) == self.buffer.maxlen:
                self.dropped_frames += 1
            self.buffer.append(frame)
            self.frame_count += 1
    
    def get(self):
        """Get latest frame from buffer"""
        with self.lock:
            if len(self.buffer) > 0:
                return self.buffer[-1].copy()
            return None
    
    def get_all(self):
        """Get all frames from buffer"""
        with self.lock:
            return list(self.buffer)
    
    def get_stats(self):
        """Get buffer statistics"""
        with self.lock:
            return {
                'total_frames': self.frame_count,
                'dropped_frames': self.dropped_frames,
                'buffer_size': len(self.buffer),
                'buffer_capacity': self.buffer.maxlen
            }


class VideoCapture:
    """Video capture from camera using OpenCV"""
    
    def __init__(self, device_id=0, resolution=(1280, 720), fps=30):
        self.device_id = device_id
        self.resolution = resolution
        self.target_fps = fps
        self.frame_interval = 1.0 / fps
        
        self.capture = None
        self.frame_buffer = FrameBuffer()
        self.capture_thread = None
        self._running = False
        self._lock = Lock()
        
        # Performance metrics
        self.frame_time_ms = 0
        self.actual_fps = 0
        self.last_frame_time = time.time()
        self.fps_counter = 0
        self.fps_timer = time.time()
    
    def initialize(self) -> bool:
        """Initialize video capture"""
        try:
            logger.info(f"Initializing camera device {self.device_id}...")
            
            self.capture = cv2.VideoCapture(self.device_id)
            
            if not self.capture.isOpened():
                logger.error(f"Failed to open camera device {self.device_id}")
                return False
            
            # Set resolution
            self.capture.set(cv2.CAP_PROP_FRAME_WIDTH, self.resolution[0])
            self.capture.set(cv2.CAP_PROP_FRAME_HEIGHT, self.resolution[1])
            
            # Set FPS
            self.capture.set(cv2.CAP_PROP_FPS, self.target_fps)
            
            # Set buffer size (reduce to 1 for lower latency)
            self.capture.set(cv2.CAP_PROP_BUFFERSIZE, 1)
            
            # Get actual properties
            actual_width = int(self.capture.get(cv2.CAP_PROP_FRAME_WIDTH))
            actual_height = int(self.capture.get(cv2.CAP_PROP_FRAME_HEIGHT))
            actual_fps = self.capture.get(cv2.CAP_PROP_FPS)
            
            logger.info(f"✓ Camera initialized: {actual_width}x{actual_height} @ {actual_fps}fps")
            
            # Start capture thread
            self._running = True
            self.capture_thread = Thread(target=self._capture_loop, daemon=True)
            self.capture_thread.start()
            
            return True
        
        except Exception as e:
            logger.error(f"Error initializing video capture: {e}")
            return False
    
    def _capture_loop(self):
        """Main capture loop (runs in separate thread)"""
        logger.info("Capture thread started")
        
        while self._running:
            try:
                start_time = time.time()
                ret, frame = self.capture.read()
                
                if not ret:
                    logger.warning("Failed to read frame")
                    continue
                
                # Add frame to buffer
                self.frame_buffer.put(frame)
                
                # Calculate performance metrics
                self.fps_counter += 1
                elapsed = time.time() - self.fps_timer
                
                if elapsed >= 1.0:  # Update every second
                    self.actual_fps = self.fps_counter / elapsed
                    self.fps_counter = 0
                    self.fps_timer = time.time()
                
                # Frame capture time
                self.frame_time_ms = (time.time() - start_time) * 1000
                
                # Sleep to match target FPS
                sleep_time = self.frame_interval - (time.time() - start_time)
                if sleep_time > 0:
                    time.sleep(sleep_time)
            
            except Exception as e:
                logger.error(f"Error in capture loop: {e}")
        
        logger.info("Capture thread stopped")
    
    def get_frame(self) -> np.ndarray:
        """Get latest frame"""
        return self.frame_buffer.get()
    
    def get_frame_jpeg(self, quality=85) -> bytes:
        """Get latest frame as JPEG bytes"""
        frame = self.frame_buffer.get()
        if frame is None:
            return None
        
        ret, jpeg = cv2.imencode('.jpg', frame, [cv2.IMWRITE_JPEG_QUALITY, quality])
        if ret:
            return jpeg.tobytes()
        return None
    
    def get_stats(self) -> dict:
        """Get capture statistics"""
        stats = self.frame_buffer.get_stats()
        stats.update({
            'actual_fps': round(self.actual_fps, 2),
            'frame_time_ms': round(self.frame_time_ms, 2),
            'resolution': f"{self.resolution[0]}x{self.resolution[1]}",
            'target_fps': self.target_fps
        })
        return stats
    
    def release(self):
        """Release camera and stop capture"""
        logger.info("Releasing video capture...")
        
        self._running = False
        
        if self.capture_thread:
            self.capture_thread.join(timeout=2)
        
        if self.capture:
            self.capture.release()
        
        logger.info("Video capture released")
