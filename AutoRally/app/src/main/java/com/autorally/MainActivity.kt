package com.autorally

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.android.AndroidApplication
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration

@SuppressLint("MissingPermission")
class MainActivity : AndroidApplication(), Platform {
    private lateinit var game: AutoRallyGame
    private var btCb: ((Boolean, String) -> Unit)? = null
    private var btHost = false

    override val context: Context get() = this

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val config = AndroidApplicationConfiguration().apply {
            useAccelerometer = false
            useCompass = false
            useImmersiveMode = true
        }
        game = AutoRallyGame(this)
        initialize(game, config)
        handleLink(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleLink(intent)
    }

    /** Invitation links look like autorally://join/ABCD */
    private fun handleLink(i: Intent?) {
        val d = i?.data ?: return
        if (d.scheme != "autorally") return
        val code = d.lastPathSegment ?: d.getQueryParameter("code") ?: return
        game.pendingJoinCode = code.trim().uppercase()
    }

    // ---------- Platform ----------
    override fun bluetoothAvailable(): Boolean =
        BluetoothAdapter.getDefaultAdapter() != null && packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)

    override fun prepareBluetooth(host: Boolean, cb: (Boolean, String) -> Unit) {
        runOnUiThread { btCb = cb; btHost = host; stepPermissions() }
    }

    override fun share(text: String) {
        runOnUiThread {
            val i = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }
            startActivity(Intent.createChooser(i, "Invite friends"))
        }
    }

    private fun neededPermissions(): List<String> =
        if (Build.VERSION.SDK_INT >= 31) listOf(
            Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_ADVERTISE
        ) else listOf(Manifest.permission.ACCESS_FINE_LOCATION)

    private fun stepPermissions() {
        if (Build.VERSION.SDK_INT >= 23) {
            val missing = neededPermissions().filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
            if (missing.isNotEmpty()) { requestPermissions(missing.toTypedArray(), REQ_PERM); return }
        }
        stepEnable()
    }

    private fun stepEnable() {
        val a = BluetoothAdapter.getDefaultAdapter()
        if (a == null) { done(false, "This phone has no Bluetooth"); return }
        if (!a.isEnabled) startActivityForResult(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), REQ_ENABLE)
        else stepDiscoverable()
    }

    private fun stepDiscoverable() {
        if (btHost) {
            val i = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
            i.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
            startActivityForResult(i, REQ_DISC)
        } else done(true, "")
    }

    private fun done(ok: Boolean, msg: String) {
        val cb = btCb
        btCb = null
        Gdx.app.postRunnable { cb?.invoke(ok, msg) }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQ_PERM) return
        if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) stepEnable()
        else done(false, "Bluetooth permission was denied")
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQ_ENABLE ->
                if (BluetoothAdapter.getDefaultAdapter()?.isEnabled == true) stepDiscoverable()
                else done(false, "Bluetooth is turned off")
            REQ_DISC ->
                if (resultCode == RESULT_CANCELED) done(false, "Your phone must be discoverable so friends can find it")
                else done(true, "")
        }
    }

    private companion object {
        const val REQ_PERM = 77
        const val REQ_ENABLE = 78
        const val REQ_DISC = 79
    }
}
