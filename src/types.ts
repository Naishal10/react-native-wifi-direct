export interface WifiDirectDevice {
  deviceName: string;
  deviceAddress: string;
  isGroupOwner: boolean;
  status: DeviceStatus;
  primaryDeviceType: string;
}

export interface ConnectionInfo {
  groupOwnerAddress: string;
  isGroupOwner: boolean;
  groupFormed: boolean;
}

export interface GroupInfo {
  networkName: string;
  passphrase: string;
  isGroupOwner: boolean;
  ownerAddress: string;
  clients: WifiDirectDevice[];
}

export interface FileTransferUpdate {
  transferId: string;
  progress: number; // 0.0 - 1.0
  status: TransferStatus;
  fileName: string;
  error?: string;
}

export interface DataReceived {
  senderId: string;
  senderName: string;
  data: string; // base64-encoded for binary, plain for text
  isBase64: boolean;
}

export enum DeviceStatus {
  CONNECTED = 0,
  INVITED = 1,
  FAILED = 2,
  AVAILABLE = 3,
  UNAVAILABLE = 4,
}

export enum TransferStatus {
  STARTED = 'started',
  PROGRESS = 'progress',
  COMPLETED = 'completed',
  FAILED = 'failed',
}

export enum WifiDirectEvent {
  PEERS_UPDATED = 'onPeersUpdated',
  CONNECTION_INFO_UPDATED = 'onConnectionInfoUpdated',
  THIS_DEVICE_CHANGED = 'onThisDeviceChanged',
  FILE_TRANSFER_UPDATE = 'onFileTransferUpdate',
  DATA_RECEIVED = 'onDataReceived',
}
