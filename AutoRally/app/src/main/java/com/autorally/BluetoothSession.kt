package com.autorally

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import com.badlogic.gdx.Gdx
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.LinkedBlockingQueue

private val APP_UUID: UUID = UUID.fromString("7d5a1c0e-8f21-4b0a-9a53-3a4a8c1e2b11")

/** Finds nearby Bluetooth devices (and lists already paired ones). */
@SuppressLint("MissingPermission")
class BtScanner(private val ctx: Context) {
    private val adapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private val devices = CopyOnWriteArrayList<BluetoothDevice>()
    @Volatile var scanning = false
    private var registered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            when (i.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val d: BluetoothDevice? = if (Build.VERSION.SDK_INT >= 33)
                        i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    else @Suppress("DEPRECATION") i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    if (d != null && devices.none { it.address == d.address }) devices.add(d)
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> scanning = false
            }
        }
    }

    fun named(): List<BluetoothDevice> = devices.filter { !it.name.isNullOrEmpty() }
    fun nameOf(d: BluetoothDevice): String = d.name ?: d.address

    fun start() {
        val a = adapter ?: return
        devices.clear()
        try { for (d in a.bondedDevices) devices.add(d) } catch (e: Exception) {}
        if (!registered) {
            val f = IntentFilter()
            f.addAction(BluetoothDevice.ACTION_FOUND)
            f.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            ctx.registerReceiver(receiver, f)
            registered = true
        }
        try { if (a.isDiscovering) a.cancelDiscovery() } catch (e: Exception) {}
        scanning = a.startDiscovery()
    }

    fun stop() {
        try { adapter?.cancelDiscovery() } catch (e: Exception) {}
        if (registered) {
            try { ctx.unregisterReceiver(receiver) } catch (e: Exception) {}
            registered = false
        }
        scanning = false
    }
}

/**
 * Bluetooth Classic (RFCOMM) multiplayer. The host phone listens; friends connect to it.
 * The host keeps the room, collects each player's "ME" message and broadcasts a "ROOM" snapshot
 * about 10 times a second during a race. No internet or Wi-Fi is used.
 *
 * Messages (one per line):  HELLO|id|name|veh   ME|name|veh|ready|rid|dist|off|spd|boost|fin|coins
 *                           WELCOME   FULL   ROOM|code|status|track|round|startId|hostId|players
 */
@SuppressLint("MissingPermission")
class BluetoothSession(private val ctx: Context, me: MpPlayer) : MpSession(me) {
    private val adapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var server: BluetoothServerSocket? = null
    private val conns = CopyOnWriteArrayList<Conn>()
    private var clientConn: Conn? = null
    @Volatile private var closed = false
    private var sendT = 0f
    private var dirty = true

    private inner class Conn(val socket: BluetoothSocket) {
        val q = LinkedBlockingQueue<String>()
        var id = ""
        fun send(s: String) { q.offer(s) }
        fun start(onLine: (Conn, String) -> Unit, onClose: (Conn) -> Unit) {
            Thread {
                try {
                    val r = BufferedReader(InputStreamReader(socket.inputStream))
                    while (true) { val l = r.readLine() ?: break; onLine(this, l) }
                } catch (e: Exception) {
                } finally { onClose(this) }
            }.apply { isDaemon = true; start() }
            Thread {
                try {
                    val o = socket.outputStream
                    while (true) { val s = q.take(); o.write((s + "\n").toByteArray()); o.flush() }
                } catch (e: Exception) {}
            }.apply { isDaemon = true; start() }
        }
        fun close() { try { socket.close() } catch (e: Exception) {}; q.offer("") }
    }

    private fun post(r: () -> Unit) = Gdx.app.postRunnable { if (!closed) r() }

    // ---------- host ----------
    fun startHost() {
        isHost = true
        room.hostId = me.id; room.code = makeCode(); room.players[me.id] = me
        ready = true
        val a = adapter
        if (a == null) { fail("This phone has no Bluetooth"); return }
        try {
            server = a.listenUsingInsecureRfcommWithServiceRecord("AutoRally", APP_UUID)
        } catch (e: Exception) { fail("Could not start Bluetooth hosting"); return }
        Thread {
            while (!closed) {
                try {
                    val s = server!!.accept()
                    val c = Conn(s)
                    conns.add(c)
                    c.start({ cc, line -> post { hostLine(cc, line) } }, { cc -> post { hostClosed(cc) } })
                } catch (e: Exception) {
                    if (!closed) post { fail("Bluetooth hosting stopped") }
                    break
                }
            }
        }.apply { isDaemon = true; start() }
    }

    private fun hostLine(c: Conn, line: String) {
        try {
            val p = line.split("|")
            when (p[0]) {
                "HELLO" -> {
                    val id = p[1]
                    if (room.players.size >= MAX_PLAYERS || room.status == "race" || room.players.containsKey(id)) {
                        c.send("FULL"); return
                    }
                    c.id = id
                    room.players[id] = MpPlayer(id, cleanName(p[2]), p[3].toIntOrNull() ?: 0)
                    c.send("WELCOME"); dirty = true
                }
                "ME" -> {
                    val pl = room.players[c.id] ?: return
                    pl.name = cleanName(p[1]); pl.veh = p[2].toIntOrNull() ?: 0
                    pl.ready = p[3] == "1"; pl.rid = p[4].toIntOrNull() ?: 0
                    pl.dist = p[5].toFloat(); pl.off = p[6].toFloat(); pl.spd = p[7].toFloat()
                    pl.boost = p[8] == "1"; pl.fin = p[9].toFloat(); pl.coins = p[10].toIntOrNull() ?: 0
                }
            }
        } catch (e: Exception) { /* ignore a malformed line */ }
    }

    private fun hostClosed(c: Conn) {
        conns.remove(c)
        c.close()
        val pl = room.players[c.id] ?: return
        if (room.status == "race") { if (pl.fin < 0f) pl.fin = -2f } else room.players.remove(c.id)
        dirty = true
    }

    private fun snapshot(): String {
        val sb = StringBuilder("ROOM|${room.code}|${room.status}|${room.track}|${room.round}|${room.startId}|${room.hostId}|")
        var first = true
        for (p in room.players.values) {
            if (!first) sb.append(';')
            first = false
            sb.append(p.id).append(',').append(p.name).append(',').append(p.veh).append(',')
                .append(if (p.ready) 1 else 0).append(',').append(p.rid).append(',').append(p.dist).append(',')
                .append(p.off).append(',').append(p.spd).append(',').append(if (p.boost) 1 else 0).append(',')
                .append(p.fin).append(',').append(p.coins)
        }
        return sb.toString()
    }

    // ---------- client ----------
    fun connect(dev: BluetoothDevice) {
        isHost = false
        val a = adapter
        if (a == null) { fail("This phone has no Bluetooth"); return }
        Thread {
            try {
                a.cancelDiscovery()
                val s = dev.createInsecureRfcommSocketToServiceRecord(APP_UUID)
                s.connect()
                val c = Conn(s)
                clientConn = c
                c.start({ _, line -> post { clientLine(line) } }, { _ -> post { fail(if (ready) "Disconnected from the host" else "Could not join") } })
                c.send("HELLO|${me.id}|${me.name}|${me.veh}")
            } catch (e: Exception) {
                post { fail("Could not connect. Is that phone hosting Auto Rally?") }
            }
        }.apply { isDaemon = true; start() }
    }

    private fun clientLine(line: String) {
        try {
            if (line == "FULL") { fail("The room is full or the race already started"); return }
            if (!line.startsWith("ROOM|")) return
            val p = line.split("|", limit = 8)
            room.code = p[1]; room.status = p[2]; room.track = p[3].toInt()
            room.round = p[4].toInt(); room.startId = p[5].toInt(); room.hostId = p[6]
            val np = LinkedHashMap<String, MpPlayer>()
            if (p[7].isNotEmpty()) for (e in p[7].split(";")) {
                val f = e.split(",")
                val id = f[0]
                if (id == me.id) { np[id] = me; continue }
                val pl = room.players[id] ?: MpPlayer(id, f[1], f[2].toIntOrNull() ?: 0)
                pl.name = f[1]; pl.veh = f[2].toIntOrNull() ?: 0; pl.ready = f[3] == "1"; pl.rid = f[4].toIntOrNull() ?: 0
                pl.dist = f[5].toFloat(); pl.off = f[6].toFloat(); pl.spd = f[7].toFloat()
                pl.boost = f[8] == "1"; pl.fin = f[9].toFloat(); pl.coins = f[10].toIntOrNull() ?: 0
                np[id] = pl
            }
            if (!np.containsKey(me.id)) np[me.id] = me
            room.players.clear(); room.players.putAll(np)
            ready = true
        } catch (e: Exception) { /* ignore a malformed snapshot */ }
    }

    // ---------- shared ----------
    override fun tick(dt: Float, racing: Boolean) {
        if (closed) return
        sendT += dt
        if (sendT >= (if (racing) 0.1f else 0.3f) || dirty) {
            sendT = 0f; dirty = false
            if (isHost) {
                val s = snapshot()
                for (c in conns) if (c.id.isNotEmpty()) c.send(s)
            } else if (ready) {
                clientConn?.send("ME|${me.name}|${me.veh}|${if (me.ready) 1 else 0}|${me.rid}|${me.dist}|${me.off}|${me.spd}|${if (me.boost) 1 else 0}|${me.fin}|${me.coins}")
            }
        }
    }

    override fun pushNow() { dirty = true }

    override fun close() {
        if (closed) return
        closed = true
        try { server?.close() } catch (e: Exception) {}
        for (c in conns) c.close()
        clientConn?.close()
    }
}
