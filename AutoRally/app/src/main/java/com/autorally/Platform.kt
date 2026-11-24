package com.autorally

import android.content.Context

/** What the game needs from Android (permissions, sharing). Implemented by MainActivity. */
interface Platform {
    val context: Context
    fun bluetoothAvailable(): Boolean
    /** Asks for Bluetooth permissions, turns Bluetooth on and (for a host) makes the phone discoverable. */
    fun prepareBluetooth(host: Boolean, cb: (Boolean, String) -> Unit)
    fun share(text: String)
}
