#import <WifiDirectSpec/WifiDirectSpec.h>
#import <MultipeerConnectivity/MultipeerConnectivity.h>

@interface WifiDirect : NSObject <NativeWifiDirectSpec, MCSessionDelegate, MCNearbyServiceBrowserDelegate, MCNearbyServiceAdvertiserDelegate>

@end
