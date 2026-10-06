# Android Viewer Component

## Overview
Native Android app for viewing live camera streams and receiving event notifications from AI Cams system.

## Features
- Live camera streaming via WebRTC
- Local network camera discovery (mDNS)
- Real-time event notifications
- Event history and snapshots
- Responsive Material Design UI

## Project Structure

```
android-viewer/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── kotlin/
│   │   │   │   └── com/aicams/viewer/
│   │   │   │       ├── MainActivity.kt
│   │   │   │       ├── ui/
│   │   │   │       │   ├── screens/
│   │   │   │       │   │   ├── LoginScreen.kt
│   │   │   │       │   │   ├── DashboardScreen.kt
│   │   │   │       │   │   ├── CameraDetailScreen.kt
│   │   │   │       │   │   └── EventsScreen.kt
│   │   │   │       │   └── components/
│   │   │   │       │       ├── StreamViewer.kt
│   │   │   │       │       ├── CameraCard.kt
│   │   │   │       │       └── EventCard.kt
│   │   │   │       ├── network/
│   │   │   │       │   ├── ApiService.kt
│   │   │   │       │   ├── WebRTCManager.kt
│   │   │   │       │   └── MDNSDiscovery.kt
│   │   │   │       ├── data/
│   │   │   │       │   ├── models/
│   │   │   │       │   ├── local/
│   │   │   │       │   └── remote/
│   │   │   │       ├── viewmodel/
│   │   │   │       │   ├── LoginViewModel.kt
│   │   │   │       │   ├── DashboardViewModel.kt
│   │   │   │       │   └── CameraViewModel.kt
│   │   │   │       └── utils/
│   │   │   │           ├── Constants.kt
│   │   │   │           ├── Extensions.kt
│   │   │   │           └── PreferenceManager.kt
│   │   │   ├── res/
│   │   │   │   ├── layout/
│   │   │   │   ├── values/
│   │   │   │   ├── drawable/
│   │   │   │   └── mipmap/
│   │   │   └── AndroidManifest.xml
│   │   └── test/
│   │       └── kotlin/
│   ├── build.gradle.kts
│   └── proguard-rules.pro
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── local.properties.template
└── README.md
```

## Tech Stack

- **Language**: Kotlin
- **Architecture**: MVVM + Clean Architecture
- **UI Framework**: Jetpack Compose
- **Networking**: Retrofit + OkHttp
- **WebRTC**: WebRTC Android SDK
- **mDNS Discovery**: NSD (Network Service Discovery)
- **Database**: Room
- **State Management**: Hilt + Flow

## Setup Instructions

### Prerequisites
- Android Studio 2022.1.1+
- Android SDK 28+
- Kotlin 1.8+
- JDK 11+

### 1. Clone Repository
```bash
cd /Users/Anvi/Desktop/code/aicams/android-viewer
```

### 2. Setup Android Project
```bash
# Copy local.properties template
cp local.properties.template local.properties

# Edit local.properties and add SDK path:
# sdk.dir=/Users/Anvi/Library/Android/sdk
```

### 3. Open in Android Studio
```bash
# From Android Studio: File → Open → select android-viewer folder
```

### 4. Build and Run
```bash
# Build debug APK
./gradlew assembleDebug

# Run on emulator/device
./gradlew installDebug

# Or use Android Studio's Run button
```

## Architecture

### MVVM Pattern
```
View (Compose UI)
    ↓
ViewModel (State Management)
    ↓
Repository (Data Access)
    ↓
Data Sources (Local/Remote)
```

### Layers

1. **Presentation Layer** (UI)
   - Jetpack Compose screens
   - ViewModels for state management
   - Theme and styling

2. **Domain Layer** (Business Logic)
   - Use cases
   - Interfaces/contracts
   - Models

3. **Data Layer** (Data Access)
   - Repositories
   - API service (Retrofit)
   - Local database (Room)
   - Preferences

4. **Network Layer**
   - WebRTC peer connections
   - mDNS discovery
   - Signaling communication

## Key Components

### WebRTC Manager
Manages WebRTC peer connections and media streams.

```kotlin
class WebRTCManager {
    fun createPeerConnection(cameraId: String): RTCPeerConnection
    fun handleRemoteStreamAdd(stream: MediaStream)
    fun handleRemoteStreamRemove(stream: MediaStream)
    fun sendIceCandidate(candidate: IceCandidate)
    fun createOffer()
    fun handleAnswer(answer: SessionDescription)
}
```

### mDNS Discovery
Discovers cameras on local network.

```kotlin
class MDNSDiscovery {
    fun startDiscovery(onCameraFound: (Camera) -> Unit)
    fun stopDiscovery()
    fun resolveCameraService(serviceName: String): Camera
}
```

### API Service
Communicates with signaling server.

```kotlin
interface ApiService {
    @POST("/api/auth/login")
    suspend fun login(credentials: LoginRequest): LoginResponse
    
    @GET("/api/devices")
    suspend fun getCameras(): List<Camera>
    
    @POST("/api/streams/{deviceId}/offer")
    suspend fun createOffer(@Path("deviceId") id: String): OfferResponse
}
```

## Screens

### 1. Login Screen
- Email/password input
- Sign up option
- Biometric authentication (future)

### 2. Dashboard Screen
- List of accessible cameras
- Camera status (online/offline)
- Quick access to camera details
- Pull-to-refresh

### 3. Camera Detail Screen
- Live video stream
- Event history
- Snapshots
- Camera controls

### 4. Events Screen
- Event timeline
- Snapshot gallery
- Event filtering
- Export options

## Permissions Required

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.CHANGE_NETWORK_STATE" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
```

## Build Variants

- **Debug**: Full logging, no obfuscation
- **Release**: Production build, ProGuard obfuscation

## Testing

### Unit Tests
```bash
./gradlew test
```

### UI Tests (Instrumented)
```bash
./gradlew connectedAndroidTest
```

## Release Build

### Sign APK
```bash
./gradlew assembleRelease
# or use Android Studio's Build → Generate Signed Bundle / APK
```

## Dependencies

See `app/build.gradle.kts` for complete list. Key dependencies:

```
Kotlin Coroutines
Jetpack Compose
Retrofit + OkHttp
Room Database
Hilt Dependency Injection
WebRTC Android SDK
Coil (Image Loading)
```

## Development Guidelines

- **Language**: Kotlin (100%)
- **UI Framework**: Jetpack Compose
- **Architecture**: MVVM with Clean Architecture
- **State Management**: ViewModel + Flow
- **Error Handling**: Try-catch + Result sealed classes
- **Logging**: Android Log + Timber

## Performance Targets

- Stream latency: < 1 second
- App startup: < 2 seconds
- Memory usage: < 150MB
- Battery impact: < 10% per hour (streaming)

## Troubleshooting

### Build Issues
```bash
# Clean build
./gradlew clean build

# Clear gradle cache
rm -rf ~/.gradle/caches

# Sync with gradle files
./gradlew --refresh-dependencies
```

### Runtime Issues
- Check logcat: `adb logcat | grep aicams`
- Verify camera server running on same network
- Check device mDNS capabilities

## Phase 1 TODO

- [ ] Setup Kotlin project structure
- [ ] Implement Retrofit API client
- [ ] Implement mDNS discovery
- [ ] Setup WebRTC with Jetpack Compose UI
- [ ] Create Compose screens
- [ ] Implement login/authentication
- [ ] Implement camera discovery and connection
- [ ] Display live stream
- [ ] Handle errors and edge cases
- [ ] Add unit and UI tests
- [ ] Performance optimization

## Phase 2 TODO (Future)

- [ ] Push notifications
- [ ] Event history UI
- [ ] Snapshot gallery
- [ ] Recording functionality
- [ ] Device sharing
- [ ] Permission management

## Next Steps

1. ✅ Project structure created
2. ⬜ Setup Android project in Android Studio
3. ⬜ Implement core network layers
4. ⬜ Build UI screens
5. ⬜ Implement WebRTC streaming
6. ⬜ Test on device

## Contributing

See [DEVELOPMENT.md](../docs/DEVELOPMENT.md) for guidelines.

## License

MIT

## Resources

- [WebRTC Android Documentation](https://webrtc.org/getting-started/overview)
- [Jetpack Compose Tutorial](https://developer.android.com/compose)
- [Kotlin Coroutines Guide](https://kotlinlang.org/docs/coroutines-overview.html)
- [Android Architecture Guide](https://developer.android.com/topic/architecture)
