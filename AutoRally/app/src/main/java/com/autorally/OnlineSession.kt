package com.autorally

import com.badlogic.gdx.Gdx
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Tiny HTTP helper for the Firebase Realtime Database REST API. Call from background threads only. */
object Net {
    fun request(method: String, url: String, body: String? = null): String {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = method
            c.connectTimeout = 5000
            c.readTimeout = 5000
            if (body != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json")
                c.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) throw IOException("HTTP $code $text")
            return text
        } finally {
            c.disconnect()
        }
    }
}

/** Online best times per track. */
object Leaderboards {
    fun fetch(base: String, trackId: String): List<Pair<String, Float>> {
        val txt = Net.request("GET", "$base/leaderboard/$trackId.json").trim()
        if (txt == "null" || txt.isEmpty()) return emptyList()
        val o = JSONObject(txt)
        val rows = ArrayList<Pair<String, Float>>()
        val keys = o.keys()
        while (keys.hasNext()) {
            val e = o.optJSONObject(keys.next()) ?: continue
            rows.add(Pair(e.optString("name", "?"), e.optDouble("time", 9999.0).toFloat()))
        }
        return rows.sortedBy { it.second }.take(10)
    }

    fun submit(base: String, trackId: String, playerId: String, name: String, time: Float) {
        val cur = Net.request("GET", "$base/leaderboard/$trackId/$playerId.json").trim()
        val old = if (cur == "null" || cur.isEmpty()) Float.MAX_VALUE else JSONObject(cur).optDouble("time", 1e9).toFloat()
        if (time < old) {
            val o = JSONObject().put("name", name).put("time", time.toDouble())
            Net.request("PUT", "$base/leaderboard/$trackId/$playerId.json", o.toString())
        }
    }
}

private class SnapPlayer(
    val id: String, val name: String, val veh: Int, val ready: Boolean, val rid: Int, val dist: Float,
    val off: Float, val spd: Float, val boost: Boolean, val fin: Float, val coins: Int, val gone: Boolean
)

private class Snap(val host: String, val status: String, val track: Int, val round: Int, val startId: Int, val players: List<SnapPlayer>)

/**
 * Online multiplayer through a Firebase Realtime Database (REST, no extra SDK).
 * Layout:  rooms/CODE/meta (host, status, track, round, startId)   rooms/CODE/players/ID (one entry per player)
 * Each phone writes only its own entry about 5 times a second and reads the whole room.
 * A heartbeat counter (hb) tells everyone whether a player is still connected; failed requests are retried
 * for about 40 seconds before the session gives up, so short connection drops recover by themselves.
 */
class OnlineSession(
    private val base: String, me: MpPlayer, private val host: Boolean, joinCode: String?
) : MpSession(me) {
    @Volatile private var closed = false
    @Volatile private var meJson = ""
    @Volatile private var metaJson: String? = null
    @Volatile private var code = joinCode ?: ""
    private var sendT = 0f
    private var hb = 0
    private var dirty = false
    private val lastHb = HashMap<String, Int>()          // worker thread only
    private val lastChange = HashMap<String, Long>()     // worker thread only

    init {
        isHost = host
        if (host) room.hostId = me.id
        meJson = buildMe()
    }

    fun start() {
        Thread { run() }.apply { isDaemon = true; start() }
    }

    private fun post(r: () -> Unit) = Gdx.app.postRunnable { if (!closed) r() }

    private fun buildMe(): String = JSONObject()
        .put("name", me.name).put("veh", me.veh).put("ready", me.ready).put("rid", me.rid)
        .put("dist", me.dist.toDouble()).put("off", me.off.toDouble()).put("spd", me.spd.toDouble())
        .put("boost", me.boost).put("fin", me.fin.toDouble()).put("coins", me.coins).toString()

    private fun buildMeta(): String = JSONObject()
        .put("host", room.hostId).put("status", room.status).put("track", room.track)
        .put("round", room.round).put("startId", room.startId).toString()

    private fun get(path: String) = Net.request("GET", "$base/$path")
    private fun put(path: String, body: String) = Net.request("PUT", "$base/$path", body)

    private fun run() {
        try {
            val ok = if (host) createRoom() else joinRoom()
            if (!ok) return
            var fails = 0
            while (!closed) {
                try {
                    val mine = meJson
                    put("rooms/$code/players/${me.id}.json", mine.dropLast(1) + ",\"hb\":${hb++}}")
                    val m = metaJson
                    if (m != null) { put("rooms/$code/meta.json", m); if (metaJson == m) metaJson = null }
                    val txt = get("rooms/$code.json").trim()
                    if (txt == "null") { post { fail("The room was closed") }; return }
                    val snap = parse(txt)
                    post { apply(snap) }
                    fails = 0
                } catch (e: Exception) {
                    if (++fails > 40) { post { fail("Lost connection to the server") }; return }
                }
                Thread.sleep(if (fails > 0) 1000L else 150L)
            }
        } catch (e: Exception) {
            post { fail("Could not reach the server. Check the internet and the server URL.") }
        }
    }

    private fun createRoom(): Boolean {
        var tries = 0
        while (true) {
            val c = makeCode()
            if (get("rooms/$c/meta.json").trim() == "null") { code = c; break }
            if (++tries > 8) { post { fail("Could not create a room, try again") }; return false }
        }
        val c = code
        post { room.code = c }
        put("rooms/$c/meta.json", buildMeta())
        return true
    }

    private fun joinRoom(): Boolean {
        val c = code
        val m = get("rooms/$c/meta.json").trim()
        if (m == "null") { post { fail("Room $c was not found") }; return false }
        val meta = JSONObject(m)
        if (meta.optString("status") == "race") { post { fail("That race has already started") }; return false }
        val ps = get("rooms/$c/players.json").trim()
        if (ps != "null" && JSONObject(ps).length() >= MAX_PLAYERS) { post { fail("That room is full") }; return false }
        val h = meta.optString("host")
        post { room.code = c; room.hostId = h }
        return true
    }

    private fun parse(txt: String): Snap {
        val root = JSONObject(txt)
        val meta = root.getJSONObject("meta")
        val list = ArrayList<SnapPlayer>()
        val ps = root.optJSONObject("players")
        val now = System.currentTimeMillis()
        if (ps != null) {
            val keys = ps.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val o = ps.optJSONObject(k) ?: continue
                val h = o.optInt("hb", 0)
                if (lastHb[k] != h) { lastHb[k] = h; lastChange[k] = now }
                val gone = k != me.id && now - (lastChange[k] ?: now) > 7000L
                list.add(SnapPlayer(k, cleanName(o.optString("name", "?")), o.optInt("veh", 0), o.optBoolean("ready", false),
                    o.optInt("rid", 0), o.optDouble("dist", 0.0).toFloat(), o.optDouble("off", 0.0).toFloat(),
                    o.optDouble("spd", 0.0).toFloat(), o.optBoolean("boost", false), o.optDouble("fin", -1.0).toFloat(),
                    o.optInt("coins", 0), gone))
            }
        }
        return Snap(meta.optString("host"), meta.optString("status", "lobby"), meta.optInt("track", 0),
            meta.optInt("round", 1), meta.optInt("startId", 0), list)
    }

    private fun apply(s: Snap) {
        if (!isHost) {
            room.hostId = s.host; room.status = s.status; room.track = s.track
            room.round = s.round; room.startId = s.startId
        }
        val np = LinkedHashMap<String, MpPlayer>()
        for (sp in s.players) {
            if (sp.id == me.id) { np[me.id] = me; continue }
            if (sp.gone && room.status != "race") continue
            val p = room.players[sp.id] ?: MpPlayer(sp.id, sp.name, sp.veh)
            p.name = sp.name; p.veh = sp.veh; p.ready = sp.ready; p.rid = sp.rid
            p.dist = sp.dist; p.off = sp.off; p.spd = sp.spd; p.boost = sp.boost; p.coins = sp.coins
            p.fin = if (sp.gone && sp.fin < 0f) -2f else sp.fin
            np[sp.id] = p
        }
        if (!np.containsKey(me.id)) np[me.id] = me
        room.players.clear(); room.players.putAll(np)
        ready = true
    }

    override fun tick(dt: Float, racing: Boolean) {
        if (closed) return
        sendT += dt
        if (sendT >= (if (racing) 0.1f else 0.25f) || dirty) { sendT = 0f; dirty = false; meJson = buildMe() }
    }

    override fun pushNow() { dirty = true }

    override fun pushMeta() { if (isHost) metaJson = buildMeta() }

    override fun close() {
        if (closed) return
        closed = true
        val c = code
        val id = me.id
        val h = host
        Thread {
            try {
                if (c.isNotEmpty()) {
                    if (h) Net.request("DELETE", "$base/rooms/$c.json")
                    else Net.request("DELETE", "$base/rooms/$c/players/$id.json")
                }
            } catch (e: Exception) {}
        }.apply { isDaemon = true; start() }
    }
}
