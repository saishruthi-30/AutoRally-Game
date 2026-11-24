package com.autorally

const val MAX_PLAYERS = 6
private const val CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

fun makeCode(): String {
    val r = java.util.Random()
    return String(CharArray(4) { CODE_CHARS[r.nextInt(CODE_CHARS.length)] })
}

/** Names travel inside text messages, so keep them to safe characters. */
fun cleanName(s: String): String = s.filter { it.isLetterOrDigit() || it == ' ' || it == '_' || it == '-' }.trim().take(12)

/** One racer in a multiplayer room. fin: -1 = still racing, -2 = left, >= 0 = finish time in seconds. */
class MpPlayer(var id: String, var name: String, var veh: Int) {
    var ready = false
    var rid = 0            // which race (room.startId) the numbers below belong to
    var dist = 0f
    var off = 0f
    var spd = 0f
    var boost = false
    var fin = -1f
    var coins = 0
    var vDist = 0f         // smoothed values used for drawing
    var vOff = 0f
}

class RoomState {
    var code = ""
    var hostId = ""
    var status = "lobby"   // "lobby" or "race"
    var track = 0
    var round = 1
    var startId = 0        // goes up by one every time the host starts a race
    val players = LinkedHashMap<String, MpPlayer>()
}

/**
 * A multiplayer session (Bluetooth or online). All fields are read and written on the game thread;
 * network threads hand their results over with Gdx.app.postRunnable.
 */
abstract class MpSession(val me: MpPlayer) {
    val room = RoomState()
    var isHost = false
    var ready = false                 // joined the room and received its state
    @Volatile var error: String? = null

    abstract fun tick(dt: Float, racing: Boolean)
    abstract fun pushNow()
    abstract fun close()
    protected open fun pushMeta() { pushNow() }

    protected fun fail(msg: String) { if (error == null) error = msg }

    fun hostStart(track: Int) {
        room.status = "race"; room.startId++; room.track = track
        for (p in room.players.values) {
            p.ready = false
            if (p.id != me.id) { p.fin = -1f; p.dist = 0f; p.vDist = 0f; p.vOff = 0f }
        }
        pushMeta(); pushNow()
    }

    fun hostToLobby() { room.status = "lobby"; room.round++; pushMeta(); pushNow() }

    fun hostSetTrack(t: Int) { room.track = t; pushMeta(); pushNow() }
}
