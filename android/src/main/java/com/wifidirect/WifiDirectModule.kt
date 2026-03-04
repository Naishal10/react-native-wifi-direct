package com.wifidirect

import com.facebook.react.bridge.ReactApplicationContext

class WifiDirectModule(reactContext: ReactApplicationContext) :
  NativeWifiDirectSpec(reactContext) {

  override fun multiply(a: Double, b: Double): Double {
    return a * b
  }

  companion object {
    const val NAME = NativeWifiDirectSpec.NAME
  }
}
