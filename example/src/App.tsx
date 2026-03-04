import React, { useState, useEffect, useCallback } from 'react';
import {
  Text,
  View,
  StyleSheet,
  TouchableOpacity,
  FlatList,
  TextInput,
  Alert,
  SafeAreaView,
  Platform,
  PermissionsAndroid,
} from 'react-native';
import WifiDirect, {
  type WifiDirectDevice,
  type ConnectionInfo,
  type DataReceived,
  DeviceStatus,
} from 'react-native-wifi-direct';

async function requestAndroidPermissions() {
  if (Platform.OS !== 'android') return true;
  try {
    const permissions = [
      PermissionsAndroid.PERMISSIONS.ACCESS_FINE_LOCATION!,
    ];
    // Android 13+ needs NEARBY_WIFI_DEVICES
    if (Platform.Version >= 33) {
      permissions.push('android.permission.NEARBY_WIFI_DEVICES' as any);
    }
    const results = await PermissionsAndroid.requestMultiple(permissions);
    return Object.values(results).every(
      (r) => r === PermissionsAndroid.RESULTS.GRANTED
    );
  } catch {
    return false;
  }
}

export default function App() {
  const [initialized, setInitialized] = useState(false);
  const [discovering, setDiscovering] = useState(false);
  const [peers, setPeers] = useState<WifiDirectDevice[]>([]);
  const [connectionInfo, setConnectionInfo] = useState<ConnectionInfo | null>(
    null
  );
  const [messages, setMessages] = useState<DataReceived[]>([]);
  const [messageInput, setMessageInput] = useState('');

  useEffect(() => {
    const sub1 = WifiDirect.onPeersUpdated((data) => {
      const devices =
        typeof data.devices === 'string'
          ? JSON.parse(data.devices)
          : data.devices;
      setPeers(devices);
    });

    const sub2 = WifiDirect.onConnectionInfoUpdated((data) => {
      const info =
        typeof data.connectionInfo === 'string'
          ? JSON.parse(data.connectionInfo)
          : data.connectionInfo;
      setConnectionInfo(info);
    });

    const sub3 = WifiDirect.onDataReceived((data) => {
      setMessages((prev) => [...prev, data]);
    });

    const sub4 = WifiDirect.onFileTransferUpdate((data) => {
      if (data.status === 'completed') {
        Alert.alert('Transfer Complete', `File: ${data.fileName}`);
      } else if (data.status === 'failed') {
        Alert.alert('Transfer Failed', data.error || 'Unknown error');
      }
    });

    return () => {
      sub1.remove();
      sub2.remove();
      sub3.remove();
      sub4.remove();
      WifiDirect.dispose();
    };
  }, []);

  const handleInitialize = useCallback(async () => {
    const granted = await requestAndroidPermissions();
    if (!granted) {
      Alert.alert('Permissions Required', 'Location permission is needed for WiFi Direct.');
      return;
    }
    try {
      await WifiDirect.initialize();
      setInitialized(true);
    } catch (e: any) {
      Alert.alert('Init Failed', e.message);
    }
  }, []);

  const handleToggleDiscovery = useCallback(async () => {
    try {
      if (discovering) {
        await WifiDirect.stopDiscovery();
        setDiscovering(false);
      } else {
        await WifiDirect.startDiscovery();
        setDiscovering(true);
      }
    } catch (e: any) {
      Alert.alert('Discovery Error', e.message);
    }
  }, [discovering]);

  const handleConnect = useCallback(async (device: WifiDirectDevice) => {
    try {
      await WifiDirect.connect(device.deviceAddress);
    } catch (e: any) {
      Alert.alert('Connect Error', e.message);
    }
  }, []);

  const handleDisconnect = useCallback(async () => {
    try {
      await WifiDirect.disconnect();
      setConnectionInfo(null);
    } catch (e: any) {
      Alert.alert('Disconnect Error', e.message);
    }
  }, []);

  const handleSendMessage = useCallback(async () => {
    if (!messageInput.trim() || !connectionInfo?.groupOwnerAddress) return;
    try {
      await WifiDirect.sendData(
        messageInput,
        connectionInfo.groupOwnerAddress,
        false
      );
      setMessageInput('');
    } catch (e: any) {
      Alert.alert('Send Error', e.message);
    }
  }, [messageInput, connectionInfo]);

  const statusLabel = (status: number) => {
    switch (status) {
      case DeviceStatus.CONNECTED:
        return 'Connected';
      case DeviceStatus.INVITED:
        return 'Invited';
      case DeviceStatus.FAILED:
        return 'Failed';
      case DeviceStatus.AVAILABLE:
        return 'Available';
      default:
        return 'Unavailable';
    }
  };

  const isConnected = connectionInfo?.groupFormed === true;

  if (!initialized) {
    return (
      <SafeAreaView style={styles.container}>
        <Text style={styles.title}>WiFi Direct</Text>
        <Text style={styles.subtitle}>
          {Platform.OS === 'android'
            ? 'Uses Android WiFi Direct'
            : 'Uses iOS Multipeer Connectivity'}
        </Text>
        <TouchableOpacity style={styles.button} onPress={handleInitialize}>
          <Text style={styles.buttonText}>Initialize</Text>
        </TouchableOpacity>
      </SafeAreaView>
    );
  }

  return (
    <SafeAreaView style={styles.container}>
      <Text style={styles.title}>WiFi Direct</Text>

      {/* Discovery Section */}
      <TouchableOpacity
        style={[styles.button, discovering && styles.buttonActive]}
        onPress={handleToggleDiscovery}
      >
        <Text style={styles.buttonText}>
          {discovering ? 'Stop Discovery' : 'Start Discovery'}
        </Text>
      </TouchableOpacity>

      {/* Connection Status */}
      {isConnected && (
        <View style={styles.connectedBanner}>
          <Text style={styles.connectedText}>
            Connected to: {connectionInfo?.groupOwnerAddress}
          </Text>
          <TouchableOpacity onPress={handleDisconnect}>
            <Text style={styles.disconnectText}>Disconnect</Text>
          </TouchableOpacity>
        </View>
      )}

      {/* Peers List */}
      <Text style={styles.sectionTitle}>
        Nearby Devices ({peers.length})
      </Text>
      <FlatList
        data={peers}
        keyExtractor={(item) => item.deviceAddress}
        style={styles.list}
        renderItem={({ item }) => (
          <TouchableOpacity
            style={styles.peerItem}
            onPress={() => handleConnect(item)}
          >
            <Text style={styles.peerName}>
              {item.deviceName || 'Unknown Device'}
            </Text>
            <Text style={styles.peerAddress}>{item.deviceAddress}</Text>
            <Text style={styles.peerStatus}>{statusLabel(item.status)}</Text>
          </TouchableOpacity>
        )}
        ListEmptyComponent={
          <Text style={styles.emptyText}>
            {discovering ? 'Searching...' : 'Tap "Start Discovery" to find devices'}
          </Text>
        }
      />

      {/* Messaging Section */}
      {isConnected && (
        <View style={styles.messagingSection}>
          <Text style={styles.sectionTitle}>Messages</Text>
          <FlatList
            data={messages}
            keyExtractor={(_, i) => String(i)}
            style={styles.messageList}
            renderItem={({ item }) => (
              <View style={styles.messageItem}>
                <Text style={styles.messageSender}>{item.senderName}:</Text>
                <Text>{item.data}</Text>
              </View>
            )}
          />
          <View style={styles.inputRow}>
            <TextInput
              style={styles.input}
              value={messageInput}
              onChangeText={setMessageInput}
              placeholder="Type a message..."
              onSubmitEditing={handleSendMessage}
            />
            <TouchableOpacity style={styles.sendButton} onPress={handleSendMessage}>
              <Text style={styles.buttonText}>Send</Text>
            </TouchableOpacity>
          </View>
        </View>
      )}
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    padding: 16,
    backgroundColor: '#f5f5f5',
  },
  title: {
    fontSize: 24,
    fontWeight: 'bold',
    textAlign: 'center',
    marginTop: 16,
  },
  subtitle: {
    fontSize: 14,
    color: '#666',
    textAlign: 'center',
    marginBottom: 24,
  },
  button: {
    backgroundColor: '#007AFF',
    padding: 14,
    borderRadius: 10,
    alignItems: 'center',
    marginVertical: 8,
  },
  buttonActive: {
    backgroundColor: '#FF3B30',
  },
  buttonText: {
    color: '#fff',
    fontWeight: '600',
    fontSize: 16,
  },
  connectedBanner: {
    backgroundColor: '#34C759',
    padding: 12,
    borderRadius: 10,
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginVertical: 8,
  },
  connectedText: {
    color: '#fff',
    fontWeight: '600',
    flex: 1,
  },
  disconnectText: {
    color: '#fff',
    fontWeight: '700',
    textDecorationLine: 'underline',
  },
  sectionTitle: {
    fontSize: 18,
    fontWeight: '600',
    marginTop: 16,
    marginBottom: 8,
  },
  list: {
    maxHeight: 200,
  },
  peerItem: {
    backgroundColor: '#fff',
    padding: 14,
    borderRadius: 10,
    marginBottom: 8,
  },
  peerName: {
    fontSize: 16,
    fontWeight: '600',
  },
  peerAddress: {
    fontSize: 12,
    color: '#888',
    marginTop: 2,
  },
  peerStatus: {
    fontSize: 12,
    color: '#007AFF',
    marginTop: 4,
  },
  emptyText: {
    textAlign: 'center',
    color: '#999',
    padding: 20,
  },
  messagingSection: {
    flex: 1,
    marginTop: 8,
  },
  messageList: {
    flex: 1,
    maxHeight: 150,
  },
  messageItem: {
    backgroundColor: '#fff',
    padding: 10,
    borderRadius: 8,
    marginBottom: 4,
  },
  messageSender: {
    fontWeight: '600',
    fontSize: 12,
    color: '#007AFF',
  },
  inputRow: {
    flexDirection: 'row',
    marginTop: 8,
    gap: 8,
  },
  input: {
    flex: 1,
    backgroundColor: '#fff',
    borderRadius: 10,
    padding: 12,
    fontSize: 16,
  },
  sendButton: {
    backgroundColor: '#007AFF',
    padding: 12,
    borderRadius: 10,
    justifyContent: 'center',
  },
});
