#import "WifiDirect.h"
#import <React/RCTUtils.h>
#import <React/RCTBridge+Private.h>
#import <ReactCommon/RCTTurboModule.h>
#import <React/RCTEventEmitter.h>

static NSString *const kServiceType = @"wifi-direct";

@implementation WifiDirect {
  MCPeerID *_localPeerID;
  MCSession *_session;
  MCNearbyServiceBrowser *_browser;
  MCNearbyServiceAdvertiser *_advertiser;
  NSMutableDictionary<NSString *, MCPeerID *> *_discoveredPeers;
  NSMutableDictionary<NSString *, NSProgress *> *_activeTransfers;
  BOOL _hasListeners;
  BOOL _isInitialized;
}

+ (NSString *)moduleName
{
  return @"WifiDirect";
}

- (std::shared_ptr<facebook::react::TurboModule>)getTurboModule:
    (const facebook::react::ObjCTurboModule::InitParams &)params
{
  return std::make_shared<facebook::react::NativeWifiDirectSpecJSI>(params);
}

#pragma mark - Lifecycle

- (void)initialize:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject
{
  dispatch_async(dispatch_get_main_queue(), ^{
    @try {
      _discoveredPeers = [NSMutableDictionary new];
      _activeTransfers = [NSMutableDictionary new];

      _localPeerID = [[MCPeerID alloc] initWithDisplayName:[UIDevice currentDevice].name];
      _session = [[MCSession alloc] initWithPeer:_localPeerID
                                securityIdentity:nil
                            encryptionPreference:MCEncryptionRequired];
      _session.delegate = self;

      _browser = [[MCNearbyServiceBrowser alloc] initWithPeer:_localPeerID
                                                  serviceType:kServiceType];
      _browser.delegate = self;

      _advertiser = [[MCNearbyServiceAdvertiser alloc] initWithPeer:_localPeerID
                                                      discoveryInfo:nil
                                                        serviceType:kServiceType];
      _advertiser.delegate = self;

      // Start advertising so other devices can find us
      [_advertiser startAdvertisingPeer];

      _isInitialized = YES;
      resolve(@(YES));
    } @catch (NSException *exception) {
      reject(@"INIT_FAILED", exception.reason, nil);
    }
  });
}

- (void)dispose
{
  [_browser stopBrowsingForPeers];
  [_advertiser stopAdvertisingPeer];
  [_session disconnect];
  [_discoveredPeers removeAllObjects];
  [_activeTransfers removeAllObjects];
  _isInitialized = NO;
}

#pragma mark - Discovery

- (void)startDiscovery:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject
{
  if (!_isInitialized) {
    reject(@"NOT_INITIALIZED", @"Call initialize() first", nil);
    return;
  }
  [_browser startBrowsingForPeers];
  resolve(@(YES));
}

- (void)stopDiscovery:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject
{
  if (!_isInitialized) {
    reject(@"NOT_INITIALIZED", @"Call initialize() first", nil);
    return;
  }
  [_browser stopBrowsingForPeers];
  resolve(@(YES));
}

- (void)getAvailablePeers:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject
{
  if (!_isInitialized) {
    reject(@"NOT_INITIALIZED", @"Call initialize() first", nil);
    return;
  }
  NSMutableArray *peersArray = [NSMutableArray new];
  for (NSString *key in _discoveredPeers) {
    MCPeerID *peer = _discoveredPeers[key];
    [peersArray addObject:[self peerToDictionary:peer connected:[_session.connectedPeers containsObject:peer]]];
  }
  NSData *jsonData = [NSJSONSerialization dataWithJSONObject:peersArray options:0 error:nil];
  NSString *jsonString = [[NSString alloc] initWithData:jsonData encoding:NSUTF8StringEncoding];
  resolve(jsonString);
}

#pragma mark - Connection

- (void)connect:(NSString *)deviceAddress resolve:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject
{
  if (!_isInitialized) {
    reject(@"NOT_INITIALIZED", @"Call initialize() first", nil);
    return;
  }
  MCPeerID *peer = _discoveredPeers[deviceAddress];
  if (!peer) {
    reject(@"PEER_NOT_FOUND", @"Peer not found. Run discovery first.", nil);
    return;
  }
  [_browser invitePeer:peer toSession:_session withContext:nil timeout:30];
  resolve(@(YES));
}

- (void)cancelConnect:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject
{
  // MultipeerConnectivity doesn't have a direct cancel invite API
  // Disconnecting the session effectively cancels pending connections
  [_session disconnect];
  // Reinitialize session for future connections
  _session = [[MCSession alloc] initWithPeer:_localPeerID
                            securityIdentity:nil
                        encryptionPreference:MCEncryptionRequired];
  _session.delegate = self;
  resolve(@(YES));
}

- (void)disconnect:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject
{
  [_session disconnect];
  _session = [[MCSession alloc] initWithPeer:_localPeerID
                            securityIdentity:nil
                        encryptionPreference:MCEncryptionRequired];
  _session.delegate = self;
  resolve(@(YES));
}

- (void)getConnectionInfo:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject
{
  BOOL isConnected = _session.connectedPeers.count > 0;
  NSString *firstPeerAddress = @"";
  if (isConnected) {
    firstPeerAddress = _session.connectedPeers.firstObject.displayName;
  }
  NSDictionary *info = @{
    @"groupOwnerAddress": firstPeerAddress,
    @"isGroupOwner": @(NO), // Multipeer doesn't have explicit group owner concept
    @"groupFormed": @(isConnected)
  };
  NSData *jsonData = [NSJSONSerialization dataWithJSONObject:info options:0 error:nil];
  NSString *jsonString = [[NSString alloc] initWithData:jsonData encoding:NSUTF8StringEncoding];
  resolve(jsonString);
}

#pragma mark - Group Management

- (void)createGroup:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject
{
  // On iOS, "creating a group" is equivalent to starting to advertise
  if (!_isInitialized) {
    reject(@"NOT_INITIALIZED", @"Call initialize() first", nil);
    return;
  }
  [_advertiser startAdvertisingPeer];
  resolve(@(YES));
}

- (void)removeGroup:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject
{
  [_advertiser stopAdvertisingPeer];
  [_session disconnect];
  _session = [[MCSession alloc] initWithPeer:_localPeerID
                            securityIdentity:nil
                        encryptionPreference:MCEncryptionRequired];
  _session.delegate = self;
  resolve(@(YES));
}

- (void)getGroupInfo:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject
{
  NSMutableArray *clientsArray = [NSMutableArray new];
  for (MCPeerID *peer in _session.connectedPeers) {
    [clientsArray addObject:[self peerToDictionary:peer connected:YES]];
  }
  NSDictionary *info = @{
    @"networkName": kServiceType,
    @"passphrase": @"",
    @"isGroupOwner": @(NO),
    @"ownerAddress": _localPeerID.displayName ?: @"",
    @"clients": clientsArray
  };
  NSData *jsonData = [NSJSONSerialization dataWithJSONObject:info options:0 error:nil];
  NSString *jsonString = [[NSString alloc] initWithData:jsonData encoding:NSUTF8StringEncoding];
  resolve(jsonString);
}

#pragma mark - File Transfer

- (void)sendFile:(NSString *)filePath targetAddress:(NSString *)targetAddress resolve:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject
{
  if (_session.connectedPeers.count == 0) {
    reject(@"NOT_CONNECTED", @"No connected peers", nil);
    return;
  }

  NSURL *fileURL = [NSURL fileURLWithPath:filePath];
  NSString *fileName = [fileURL lastPathComponent];
  NSString *transferId = [[NSUUID UUID] UUIDString];

  // Find target peer
  MCPeerID *targetPeer = nil;
  for (MCPeerID *peer in _session.connectedPeers) {
    if ([peer.displayName isEqualToString:targetAddress]) {
      targetPeer = peer;
      break;
    }
  }

  if (!targetPeer) {
    // If no specific target found, send to first connected peer
    targetPeer = _session.connectedPeers.firstObject;
  }

  NSProgress *progress = [_session sendResourceAtURL:fileURL
                                            withName:fileName
                                              toPeer:targetPeer
                                   withCompletionHandler:^(NSError *error) {
    [self->_activeTransfers removeObjectForKey:transferId];
    if (error) {
      [self sendFileTransferEvent:transferId progress:0.0 status:@"failed" fileName:fileName error:error.localizedDescription];
    } else {
      [self sendFileTransferEvent:transferId progress:1.0 status:@"completed" fileName:fileName error:nil];
    }
  }];

  if (progress) {
    _activeTransfers[transferId] = progress;
    [self sendFileTransferEvent:transferId progress:0.0 status:@"started" fileName:fileName error:nil];

    // Observe progress
    [progress addObserver:self forKeyPath:@"fractionCompleted" options:NSKeyValueObservingOptionNew context:(__bridge void *)transferId];
  }

  resolve(transferId);
}

- (void)cancelFileTransfer:(NSString *)transferId resolve:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject
{
  NSProgress *progress = _activeTransfers[transferId];
  if (progress) {
    [progress cancel];
    [_activeTransfers removeObjectForKey:transferId];
    resolve(@(YES));
  } else {
    resolve(@(NO));
  }
}

- (void)observeValueForKeyPath:(NSString *)keyPath ofObject:(id)object change:(NSDictionary *)change context:(void *)context
{
  if ([keyPath isEqualToString:@"fractionCompleted"]) {
    NSProgress *progress = (NSProgress *)object;
    NSString *transferId = (__bridge NSString *)context;
    [self sendFileTransferEvent:transferId progress:progress.fractionCompleted status:@"progress" fileName:@"" error:nil];
  }
}

#pragma mark - Data / Messaging

- (void)sendData:(NSString *)data targetAddress:(NSString *)targetAddress isBase64:(BOOL)isBase64 resolve:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject
{
  if (_session.connectedPeers.count == 0) {
    reject(@"NOT_CONNECTED", @"No connected peers", nil);
    return;
  }

  NSData *messageData = [data dataUsingEncoding:NSUTF8StringEncoding];
  if (!messageData) {
    reject(@"INVALID_DATA", @"Could not encode data", nil);
    return;
  }

  // Wrap data with metadata
  NSDictionary *wrapper = @{
    @"senderId": _localPeerID.displayName ?: @"",
    @"senderName": _localPeerID.displayName ?: @"",
    @"data": data,
    @"isBase64": @(isBase64)
  };
  NSData *wrappedData = [NSJSONSerialization dataWithJSONObject:wrapper options:0 error:nil];

  NSArray<MCPeerID *> *targets = _session.connectedPeers;
  // If targetAddress specified, filter to that peer
  for (MCPeerID *peer in _session.connectedPeers) {
    if ([peer.displayName isEqualToString:targetAddress]) {
      targets = @[peer];
      break;
    }
  }

  NSError *error = nil;
  [_session sendData:wrappedData toPeers:targets withMode:MCSessionSendDataReliable error:&error];
  if (error) {
    reject(@"SEND_DATA_FAILED", error.localizedDescription, error);
  } else {
    resolve(@(YES));
  }
}

#pragma mark - Event Listener Management

- (void)addListener:(NSString *)eventName
{
  _hasListeners = YES;
}

- (void)removeListeners:(double)count
{
  // No-op: managed by NativeEventEmitter
}

#pragma mark - MCSessionDelegate

- (void)session:(MCSession *)session peer:(MCPeerID *)peerID didChangeState:(MCSessionState)state
{
  NSDictionary *connectionInfo = @{
    @"groupOwnerAddress": peerID.displayName ?: @"",
    @"isGroupOwner": @(NO),
    @"groupFormed": @(state == MCSessionStateConnected)
  };
  NSData *jsonData = [NSJSONSerialization dataWithJSONObject:connectionInfo options:0 error:nil];
  NSString *jsonString = [[NSString alloc] initWithData:jsonData encoding:NSUTF8StringEncoding];

  [self sendEventWithName:@"onConnectionInfoUpdated" body:@{@"connectionInfo": jsonString}];

  // Also send peers updated
  [self emitPeersUpdated];
}

- (void)session:(MCSession *)session didReceiveData:(NSData *)data fromPeer:(MCPeerID *)peerID
{
  NSDictionary *wrapper = [NSJSONSerialization JSONObjectWithData:data options:0 error:nil];
  if (wrapper) {
    [self sendEventWithName:@"onDataReceived" body:wrapper];
  } else {
    // Raw data fallback
    NSString *dataString = [[NSString alloc] initWithData:data encoding:NSUTF8StringEncoding];
    [self sendEventWithName:@"onDataReceived" body:@{
      @"senderId": peerID.displayName ?: @"",
      @"senderName": peerID.displayName ?: @"",
      @"data": dataString ?: @"",
      @"isBase64": @(NO)
    }];
  }
}

- (void)session:(MCSession *)session didReceiveStream:(NSInputStream *)stream withName:(NSString *)streamName fromPeer:(MCPeerID *)peerID
{
  // Not used in current implementation
}

- (void)session:(MCSession *)session didStartReceivingResourceWithName:(NSString *)resourceName fromPeer:(MCPeerID *)peerID withProgress:(NSProgress *)progress
{
  NSString *transferId = [[NSUUID UUID] UUIDString];
  [self sendFileTransferEvent:transferId progress:0.0 status:@"started" fileName:resourceName error:nil];
}

- (void)session:(MCSession *)session didFinishReceivingResourceWithName:(NSString *)resourceName fromPeer:(MCPeerID *)peerID atURL:(NSURL *)localURL withError:(NSError *)error
{
  NSString *transferId = [[NSUUID UUID] UUIDString];
  if (error) {
    [self sendFileTransferEvent:transferId progress:0.0 status:@"failed" fileName:resourceName error:error.localizedDescription];
  } else {
    // Move file to documents directory
    NSString *documentsPath = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, YES).firstObject;
    NSString *destPath = [documentsPath stringByAppendingPathComponent:resourceName];
    [[NSFileManager defaultManager] moveItemAtURL:localURL toURL:[NSURL fileURLWithPath:destPath] error:nil];
    [self sendFileTransferEvent:transferId progress:1.0 status:@"completed" fileName:resourceName error:nil];
  }
}

#pragma mark - MCNearbyServiceBrowserDelegate

- (void)browser:(MCNearbyServiceBrowser *)browser foundPeer:(MCPeerID *)peerID withDiscoveryInfo:(NSDictionary<NSString *, NSString *> *)info
{
  _discoveredPeers[peerID.displayName] = peerID;
  [self emitPeersUpdated];
}

- (void)browser:(MCNearbyServiceBrowser *)browser lostPeer:(MCPeerID *)peerID
{
  [_discoveredPeers removeObjectForKey:peerID.displayName];
  [self emitPeersUpdated];
}

- (void)browser:(MCNearbyServiceBrowser *)browser didNotStartBrowsingForPeers:(NSError *)error
{
  // Could emit an error event here in the future
}

#pragma mark - MCNearbyServiceAdvertiserDelegate

- (void)advertiser:(MCNearbyServiceAdvertiser *)advertiser didReceiveInvitationFromPeer:(MCPeerID *)peerID withContext:(NSData *)context invitationHandler:(void (^)(BOOL, MCSession *))invitationHandler
{
  // Auto-accept invitations
  invitationHandler(YES, _session);
}

- (void)advertiser:(MCNearbyServiceAdvertiser *)advertiser didNotStartAdvertisingPeer:(NSError *)error
{
  // Could emit an error event here in the future
}

#pragma mark - Helpers

- (void)emitPeersUpdated
{
  NSMutableArray *peersArray = [NSMutableArray new];
  for (NSString *key in _discoveredPeers) {
    MCPeerID *peer = _discoveredPeers[key];
    [peersArray addObject:[self peerToDictionary:peer connected:[_session.connectedPeers containsObject:peer]]];
  }
  NSData *jsonData = [NSJSONSerialization dataWithJSONObject:peersArray options:0 error:nil];
  NSString *jsonString = [[NSString alloc] initWithData:jsonData encoding:NSUTF8StringEncoding];
  [self sendEventWithName:@"onPeersUpdated" body:@{@"devices": jsonString}];
}

- (NSDictionary *)peerToDictionary:(MCPeerID *)peer connected:(BOOL)connected
{
  return @{
    @"deviceName": peer.displayName ?: @"",
    @"deviceAddress": peer.displayName ?: @"", // iOS uses display name as identifier
    @"isGroupOwner": @(NO),
    @"status": @(connected ? 0 : 3), // 0 = connected, 3 = available
    @"primaryDeviceType": @"iOS"
  };
}

- (void)sendFileTransferEvent:(NSString *)transferId progress:(double)progress status:(NSString *)status fileName:(NSString *)fileName error:(NSString *)error
{
  NSMutableDictionary *body = [NSMutableDictionary dictionaryWithDictionary:@{
    @"transferId": transferId,
    @"progress": @(progress),
    @"status": status,
    @"fileName": fileName
  }];
  if (error) {
    body[@"error"] = error;
  }
  [self sendEventWithName:@"onFileTransferUpdate" body:body];
}

- (void)sendEventWithName:(NSString *)eventName body:(NSDictionary *)body
{
  if (!_hasListeners) return;

  __weak typeof(self) weakSelf = self;
  dispatch_async(dispatch_get_main_queue(), ^{
    __strong typeof(weakSelf) strongSelf = weakSelf;
    if (!strongSelf) return;

    RCTBridge *bridge = [RCTBridge currentBridge];
    if (bridge) {
      [bridge enqueueJSCall:@"RCTDeviceEventEmitter"
                     method:@"emit"
                       args:@[eventName, body ?: @{}]
                 completion:NULL];
    }
  });
}

- (NSArray<NSString *> *)supportedEvents
{
  return @[
    @"onPeersUpdated",
    @"onConnectionInfoUpdated",
    @"onThisDeviceChanged",
    @"onFileTransferUpdate",
    @"onDataReceived"
  ];
}

@end
