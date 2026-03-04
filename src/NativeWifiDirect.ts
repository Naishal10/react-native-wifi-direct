import {
  TurboModuleRegistry,
  type TurboModule,
} from 'react-native';

export interface Spec extends TurboModule {
  // Lifecycle
  initialize(): Promise<boolean>;
  dispose(): void;

  // Discovery
  startDiscovery(): Promise<boolean>;
  stopDiscovery(): Promise<boolean>;
  getAvailablePeers(): Promise<string>; // JSON-encoded WifiDirectDevice[]

  // Connection
  connect(deviceAddress: string): Promise<boolean>;
  cancelConnect(): Promise<boolean>;
  disconnect(): Promise<boolean>;
  getConnectionInfo(): Promise<string>; // JSON-encoded ConnectionInfo

  // Group Management
  createGroup(): Promise<boolean>;
  removeGroup(): Promise<boolean>;
  getGroupInfo(): Promise<string>; // JSON-encoded group info

  // File Transfer
  sendFile(filePath: string, targetAddress: string): Promise<string>; // returns transferId
  cancelFileTransfer(transferId: string): Promise<boolean>;

  // Data / Messaging
  sendData(
    data: string,
    targetAddress: string,
    isBase64: boolean
  ): Promise<boolean>;

  // Event registration
  addListener(eventName: string): void;
  removeListeners(count: number): void;
}

export default TurboModuleRegistry.getEnforcing<Spec>('WifiDirect');
