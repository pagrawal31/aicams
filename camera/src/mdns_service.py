"""
mDNS service discovery module
Advertises camera service on local network
"""

from zeroconf import ServiceInfo, Zeroconf
import socket
import logging

logger = logging.getLogger(__name__)


class MDNSService:
    """mDNS service advertisement"""
    
    def __init__(self, device_name, service_port, device_info=None):
        self.device_name = device_name
        self.service_port = service_port
        self.device_info = device_info or {}
        self.service_info = None
        self.zeroconf = None
        self._registered = False
    
    def _get_local_ip(self) -> str:
        """Get local IP address"""
        try:
            # Create a socket to determine the IP address
            s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
            s.connect(("8.8.8.8", 80))
            ip = s.getsockname()[0]
            s.close()
            return ip
        except Exception as e:
            logger.warning(f"Could not determine local IP: {e}")
            return "127.0.0.1"
    
    def start(self) -> bool:
        """Start mDNS service"""
        if self.zeroconf is not None and self._registered:
            logger.info("mDNS service is already registered")
            return True
        
        try:
            local_ip = self._get_local_ip()
            logger.info(f"Local IP: {local_ip}")
            
            # Create service info
            service_name = f"{self.device_name}._camera-stream._tcp.local."
            
            # Prepare TXT properties
            txt_props = {}
            for key, value in self.device_info.items():
                txt_props[key] = str(value)
            
            self.service_info = ServiceInfo(
                "_camera-stream._tcp.local.",
                service_name,
                addresses=[socket.inet_aton(local_ip)],
                port=self.service_port,
                properties=txt_props,
                server=f"{self.device_name}.local."
            )
            
            # Register with Zeroconf
            self.zeroconf = Zeroconf()
            self.zeroconf.register_service(self.service_info)
            self._registered = True
            
            logger.info(f"✓ mDNS service registered: {service_name}")
            logger.info(f"  Address: {local_ip}:{self.service_port}")
            logger.info(f"  Properties: {txt_props}")
            
            return True
        
        except Exception as e:
            message = str(e) if str(e) else "duplicate service name, network issue, or local discovery unavailable"
            logger.warning(f"mDNS registration failed for {self.device_name}: {message}")
            if self.zeroconf:
                try:
                    self.zeroconf.close()
                except Exception:
                    pass
                self.zeroconf = None
            self.service_info = None
            self._registered = False
            return False
    
    def stop(self):
        """Stop mDNS service"""
        try:
            if self.zeroconf:
                logger.info("Unregistering mDNS service...")
                if self.service_info is not None and self._registered:
                    self.zeroconf.unregister_service(self.service_info)
                self.zeroconf.close()
                self.zeroconf = None
                self.service_info = None
                self._registered = False
                logger.info("✓ mDNS service unregistered")
        except Exception as e:
            logger.warning(f"Error stopping mDNS service: {e}")
