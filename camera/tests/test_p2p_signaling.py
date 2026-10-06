import asyncio
import sys
from pathlib import Path

from aiohttp import web
from aiohttp.test_utils import TestClient, TestServer

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'src'))

from webrtc_server import WebRTCServer


def test_p2p_signaling_forwards_offer_and_answer():
    async def run_test():
        relay = WebRTCServer(video_source=None)
        app = web.Application()
        app.router.add_get('/p2p/signaling', relay.handle_p2p_signaling)
        client = TestClient(TestServer(app))
        await client.start_server()
        try:
            camera = await client.ws_connect('/p2p/signaling?role=camera&deviceId=test-phone')
            assert (await camera.receive_json())['type'] == 'ready'

            viewer = await client.ws_connect('/p2p/signaling?role=viewer&deviceId=test-viewer')
            assert (await viewer.receive_json())['type'] == 'ready'
            await viewer.send_json({'type': 'offer', 'deviceId': 'test-phone', 'sdp': 'offer-sdp'})

            camera_offer = await camera.receive_json()
            viewer_notice = await viewer.receive_json()
            assert camera_offer['type'] == 'offer'
            assert camera_offer['sdp'] == 'offer-sdp'
            assert camera_offer['sessionId'] == viewer_notice['sessionId']

            await camera.send_json({
                'type': 'answer',
                'sessionId': camera_offer['sessionId'],
                'sdp': 'answer-sdp',
            })
            answer = await viewer.receive_json()
            assert answer['type'] == 'answer'
            assert answer['sdp'] == 'answer-sdp'
        finally:
            await client.close()

    asyncio.run(run_test())


def test_p2p_signaling_reports_camera_offline():
    async def run_test():
        relay = WebRTCServer(video_source=None)
        app = web.Application()
        app.router.add_get('/p2p/signaling', relay.handle_p2p_signaling)
        client = TestClient(TestServer(app))
        await client.start_server()
        try:
            viewer = await client.ws_connect('/p2p/signaling?role=viewer&deviceId=test-viewer')
            assert (await viewer.receive_json())['type'] == 'ready'
            await viewer.send_json({'type': 'offer', 'deviceId': 'missing-camera', 'sdp': 'offer-sdp'})
            response = await viewer.receive_json()
            assert response['type'] == 'error'
            assert response['message'] == 'P2P camera is offline'
        finally:
            await client.close()

    asyncio.run(run_test())


def test_motion_state_is_forwarded_and_replayed_to_late_subscribers():
    async def run_test():
        relay = WebRTCServer(video_source=None)
        app = web.Application()
        app.router.add_get('/p2p/signaling', relay.handle_p2p_signaling)
        client = TestClient(TestServer(app))
        await client.start_server()
        try:
            camera = await client.ws_connect('/p2p/signaling?role=camera&deviceId=motion-phone')
            assert (await camera.receive_json())['type'] == 'ready'

            viewer = await client.ws_connect('/p2p/signaling?role=viewer&deviceId=viewer-one')
            assert (await viewer.receive_json())['type'] == 'ready'
            await viewer.send_json({'type': 'motion_subscribe', 'deviceId': 'motion-phone'})

            await camera.send_json({'type': 'motion', 'deviceId': 'motion-phone', 'detected': True})
            live_state = await viewer.receive_json()
            assert live_state == {
                'type': 'motion',
                'deviceId': 'motion-phone',
                'detected': True,
            }

            late_viewer = await client.ws_connect('/p2p/signaling?role=viewer&deviceId=viewer-two')
            assert (await late_viewer.receive_json())['type'] == 'ready'
            await late_viewer.send_json({'type': 'motion_subscribe', 'deviceId': 'motion-phone'})
            cached_state = await late_viewer.receive_json()
            assert cached_state['type'] == 'motion'
            assert cached_state['detected'] is True
        finally:
            await client.close()

    asyncio.run(run_test())