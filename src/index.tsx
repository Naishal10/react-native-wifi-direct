import { NativeEventEmitter } from 'react-native';
import NativeWifiDirect from './NativeWifiDirect';
import {
  WifiDirectEvent,
  type WifiDirectDevice,
  type ConnectionInfo,
  type GroupInfo,
  type FileTransferUpdate,
  type DataReceived,
} from './types';

export {
  WifiDirectEvent,
  DeviceStatus,
  TransferStatus,
} from './types';

export type {
  WifiDirectDevice,
  ConnectionInfo,
  GroupInfo,
  FileTransferUpdate,
  DataReceived,
} from './types';

const eventEmitter = new NativeEventEmitter(NativeWifiDirect);

const WifiDirect = {
  // Lifecycle
  initialize: (): Promise<boolean> => NativeWifiDirect.initialize(),
  dispose: (): void => NativeWifiDirect.dispose(),

  // Discovery
  startDiscovery: (): Promise<boolean> => NativeWifiDirect.startDiscovery(),
  stopDiscovery: (): Promise<boolean> => NativeWifiDirect.stopDiscovery(),
  getAvailablePeers: async (): Promise<WifiDirectDevice[]> => {
    const json = await NativeWifiDirect.getAvailablePeers();
    return JSON.parse(json) as WifiDirectDevice[];
  },

  // Connection
  connect: (deviceAddress: string): Promise<boolean> =>
    NativeWifiDirect.connect(deviceAddress),
  cancelConnect: (): Promise<boolean> => NativeWifiDirect.cancelConnect(),
  disconnect: (): Promise<boolean> => NativeWifiDirect.disconnect(),
  getConnectionInfo: async (): Promise<ConnectionInfo> => {
    const json = await NativeWifiDirect.getConnectionInfo();
    return JSON.parse(json) as ConnectionInfo;
  },

  // Group Management
  createGroup: (): Promise<boolean> => NativeWifiDirect.createGroup(),
  removeGroup: (): Promise<boolean> => NativeWifiDirect.removeGroup(),
  getGroupInfo: async (): Promise<GroupInfo> => {
    const json = await NativeWifiDirect.getGroupInfo();
    return JSON.parse(json) as GroupInfo;
  },

  // File Transfer
  sendFile: (filePath: string, targetAddress: string): Promise<string> =>
    NativeWifiDirect.sendFile(filePath, targetAddress),
  cancelFileTransfer: (transferId: string): Promise<boolean> =>
    NativeWifiDirect.cancelFileTransfer(transferId),

  // Data / Messaging
  sendData: (
    data: string,
    targetAddress: string,
    isBase64: boolean = false
  ): Promise<boolean> =>
    NativeWifiDirect.sendData(data, targetAddress, isBase64),

  // Event Subscriptions
  onPeersUpdated: (
    callback: (data: { devices: WifiDirectDevice[] }) => void
  ) => eventEmitter.addListener(WifiDirectEvent.PEERS_UPDATED, callback),

  onConnectionInfoUpdated: (
    callback: (data: { connectionInfo: ConnectionInfo }) => void
  ) =>
    eventEmitter.addListener(
      WifiDirectEvent.CONNECTION_INFO_UPDATED,
      callback
    ),

  onThisDeviceChanged: (
    callback: (data: { device: WifiDirectDevice }) => void
  ) =>
    eventEmitter.addListener(WifiDirectEvent.THIS_DEVICE_CHANGED, callback),

  onFileTransferUpdate: (callback: (data: FileTransferUpdate) => void) =>
    eventEmitter.addListener(WifiDirectEvent.FILE_TRANSFER_UPDATE, callback),

  onDataReceived: (callback: (data: DataReceived) => void) =>
    eventEmitter.addListener(WifiDirectEvent.DATA_RECEIVED, callback),
};

export default WifiDirect;
