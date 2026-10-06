import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'src'))

from mdns_service import MDNSService


def test_start_and_stop_registers_service():
    service = MDNSService('HomeCamera-01', 8000, {'device_id': 'camera-001'})

    assert service.start() is True
    assert service.service_info is not None
    assert service.zeroconf is not None

    service.stop()

    assert service.zeroconf is None
    assert service.service_info is None


def test_duplicate_service_name_fails_cleanly():
    first = MDNSService('HomeCamera-01', 8000, {'device_id': 'camera-001'})
    second = MDNSService('HomeCamera-01', 8000, {'device_id': 'camera-001'})

    assert first.start() is True
    assert second.start() is False

    first.stop()
    second.stop()
