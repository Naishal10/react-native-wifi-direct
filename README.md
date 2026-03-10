# react-native-wifi-direct

WiFi Direct (P2P) for React Native. Supports peer discovery, connection, file transfer, and data messaging.

- **Android**: Uses the native WiFi Direct (Wi-Fi P2P) API
- **iOS**: Uses MultipeerConnectivity framework

> **Note**: Android WiFi Direct and iOS Multipeer Connectivity use different protocols. An Android device **cannot** discover or connect to an iOS device. Each platform operates within its own ecosystem.

## Requirements

- React Native >= 0.74 (New Architecture / Turbo Modules)
- Android: minSdkVersion 24
- iOS: 14.0+
- Physical devices required (WiFi Direct does not work on emulators/simulators)

## Installation

```sh
npm i @np10/react-native-wifi-direct
# or
yarn add @np10/react-native-wifi-direct
```

### iOS

```sh
cd ios && pod install
```

### Android Permissions

Add these to your `android/app/src/main/AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
<uses-permission android:name="android.permission.CHANGE_WIFI_STATE" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.NEARBY_WIFI_DEVICES"
  android:usesPermissionFlags="neverForLocation"
  android:minSdkVersion="33" />
<uses-permission android:name="android.permission.INTERNET" />

<uses-feature
  android:name="android.hardware.wifi.direct"
  android:required="true" />
```

You must also request `ACCESS_FINE_LOCATION` (and `NEARBY_WIFI_DEVICES` on Android 13+) at runtime before calling `initialize()`.

### iOS Permissions

Add to your `Info.plist`:

```xml
<key>NSLocalNetworkUsageDescription</key>
<string>This app uses the local network to discover and communicate with nearby devices.</string>
<key>NSBonjourServices</key>
<array>
  <string>_wifi-direct._tcp</string>
  <string>_wifi-direct._udp</string>
</array>
```

## Usage

```typescript
import WifiDirect, { DeviceStatus } from '@np10/react-native-wifi-direct';

// Initialize
await WifiDirect.initialize();

// Listen for events
const sub = WifiDirect.onPeersUpdated((data) => {
  console.log('Peers:', data.devices);
});

// Discover peers
await WifiDirect.startDiscovery();

// Get available peers
const peers = await WifiDirect.getAvailablePeers();

// Connect to a peer
await WifiDirect.connect(peers[0].deviceAddress);

// Listen for connection changes
WifiDirect.onConnectionInfoUpdated((data) => {
  console.log('Connection:', data.connectionInfo);
});

// Send a message
await WifiDirect.sendData('Hello!', targetAddress, false);

// Receive messages
WifiDirect.onDataReceived((data) => {
  console.log(`${data.senderName}: ${data.data}`);
});

// Send a file
const transferId = await WifiDirect.sendFile('/path/to/file.pdf', targetAddress);

// Track file transfer progress
WifiDirect.onFileTransferUpdate((update) => {
  console.log(`${update.fileName}: ${Math.round(update.progress * 100)}%`);
});

// Disconnect
await WifiDirect.disconnect();

// Cleanup
sub.remove();
WifiDirect.dispose();
```

## API Reference

### Lifecycle

| Method | Returns | Description |
|--------|---------|-------------|
| `initialize()` | `Promise<boolean>` | Initialize WiFi Direct. Must be called first. |
| `dispose()` | `void` | Cleanup all resources. Call when done. |

### Discovery

| Method | Returns | Description |
|--------|---------|-------------|
| `startDiscovery()` | `Promise<boolean>` | Start scanning for nearby peers. |
| `stopDiscovery()` | `Promise<boolean>` | Stop scanning. |
| `getAvailablePeers()` | `Promise<WifiDirectDevice[]>` | Get list of discovered peers. |

### Connection

| Method | Returns | Description |
|--------|---------|-------------|
| `connect(deviceAddress)` | `Promise<boolean>` | Connect to a peer by address. |
| `cancelConnect()` | `Promise<boolean>` | Cancel a pending connection. |
| `disconnect()` | `Promise<boolean>` | Disconnect from the current peer. |
| `getConnectionInfo()` | `Promise<ConnectionInfo>` | Get current connection details. |

### Group Management

| Method | Returns | Description |
|--------|---------|-------------|
| `createGroup()` | `Promise<boolean>` | Create a WiFi Direct group (become group owner). |
| `removeGroup()` | `Promise<boolean>` | Remove the current group. |
| `getGroupInfo()` | `Promise<GroupInfo>` | Get group details including connected clients. |

### File Transfer

| Method | Returns | Description |
|--------|---------|-------------|
| `sendFile(filePath, targetAddress)` | `Promise<string>` | Send a file. Returns a `transferId` for tracking. |
| `cancelFileTransfer(transferId)` | `Promise<boolean>` | Cancel an in-progress file transfer. |

### Data Messaging

| Method | Returns | Description |
|--------|---------|-------------|
| `sendData(data, targetAddress, isBase64?)` | `Promise<boolean>` | Send text or base64-encoded binary data. |

### Events

| Event | Payload | Description |
|-------|---------|-------------|
| `onPeersUpdated` | `{ devices: WifiDirectDevice[] }` | Peer list changed. |
| `onConnectionInfoUpdated` | `{ connectionInfo: ConnectionInfo }` | Connection state changed. |
| `onThisDeviceChanged` | `{ device: WifiDirectDevice }` | This device's info changed. |
| `onFileTransferUpdate` | `FileTransferUpdate` | File transfer progress/status. |
| `onDataReceived` | `DataReceived` | Incoming data from a peer. |

All event subscriptions return an `EmitterSubscription` with a `.remove()` method.

### Types

```typescript
interface WifiDirectDevice {
  deviceName: string;
  deviceAddress: string;
  isGroupOwner: boolean;
  status: DeviceStatus;
  primaryDeviceType: string;
}

interface ConnectionInfo {
  groupOwnerAddress: string;
  isGroupOwner: boolean;
  groupFormed: boolean;
}

interface FileTransferUpdate {
  transferId: string;
  progress: number; // 0.0 - 1.0
  status: 'started' | 'progress' | 'completed' | 'failed';
  fileName: string;
  error?: string;
}

interface DataReceived {
  senderId: string;
  senderName: string;
  data: string;
  isBase64: boolean;
}

enum DeviceStatus {
  CONNECTED = 0,
  INVITED = 1,
  FAILED = 2,
  AVAILABLE = 3,
  UNAVAILABLE = 4,
}
```

## Platform Differences

| Feature | Android | iOS |
|---------|---------|-----|
| Protocol | WiFi Direct (Wi-Fi P2P) | Multipeer Connectivity |
| Cross-platform | Android-to-Android only | iOS-to-iOS only |
| Group owner | Explicit via `createGroup()` | Implicit (advertiser role) |
| Device address | MAC address | Display name |
| File transfer | TCP sockets | MCSession resources |
| Data messaging | TCP sockets | MCSession data |
| Background | Limited | Limited |

## Troubleshooting

**Discovery not finding peers?**
- Ensure both devices have WiFi enabled
- Android: Verify location permission is granted at runtime
- iOS: Check that the local network permission dialog was accepted
- Both devices must be relatively close to each other

**Connection failing?**
- Android: Try `createGroup()` on one device first, then `connect()` from the other
- iOS: The advertiser auto-accepts invitations; make sure one device is advertising

**File transfer not working?**
- Ensure devices are connected before sending files
- Android: The group owner acts as the file server; files are received in the app's cache directory
- Check that the file path is valid and accessible

## Contributing

- [Development workflow](CONTRIBUTING.md#development-workflow)
- [Sending a pull request](CONTRIBUTING.md#sending-a-pull-request)
- [Code of conduct](CODE_OF_CONDUCT.md)

## License

MIT
