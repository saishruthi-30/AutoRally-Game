package com.autorally

import android.bluetooth.BluetoothDevice
import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.Preferences
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.ScreenUtils
import com.badlogic.gdx.utils.viewport.ExtendViewport
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private enum class State {
    MENU, MODE, AI_SETUP, SEL_VEHICLE, SEL_TRACK, MISSIONS, PROFILE,
    MP_MENU, BT_FIND, MP_LOBBY, MP_RESULT, LEADERBOARD,
    COUNTDOWN, RACE, PAUSED, FINISH
}

private const val PLAYER_Y = 170f
private const val STEP = 6f
private const val DAILY_REWARD = 100

private val DARK = Color(0.1f, 0.1f, 0.12f, 1f)
private val YELLOW = Color(1f, 0.8f, 0.1f, 1f)
private val ORANGE = Color(1f, 0.5f, 0.1f, 1f)
private val CYAN = Color(0.2f, 0.85f, 0.95f, 1f)
private val LIME = Color(0.4f, 1f, 0.4f, 1f)
private val RED = Color(0.95f, 0.3f, 0.3f, 1f)
private val BG1 = Color(0.09f, 0.11f, 0.2f, 1f)
private val BG2 = Color(0.14f, 0.17f, 0.3f, 1f)
private val BTN_GREEN = Color(0.2f, 0.7f, 0.3f, 1f)
private val BTN_BLUE = Color(0.2f, 0.45f, 0.85f, 1f)
private val BTN_PURPLE = Color(0.55f, 0.3f, 0.8f, 1f)
private val BTN_ORANGE = Color(0.9f, 0.5f, 0.1f, 1f)
private val BTN_GRAY = Color(0.35f, 0.37f, 0.45f, 1f)
private val BTN_LOCKED = Color(0.4f, 0.4f, 0.42f, 1f)

private class Btn(
    val x: Float, val y: Float, val w: Float, val h: Float,
    val label: String, val col: Color, val action: () -> Unit
) {
    fun hit(px: Float, py: Float) = px in x..(x + w) && py in y..(y + h)
}

/** A surprise item that appears during a race (roadblocks, coin rain). abs = distance from the start. */
private class Ev(val abs: Float, val off: Float, val kind: Kind) { var taken = false }

class AutoRallyGame(private val platform: Platform) : ApplicationAdapter() {
    private lateinit var shapes: ShapeRenderer
    private lateinit var batch: SpriteBatch
    private lateinit var font: BitmapFont
    private lateinit var sfx: Sfx
    private lateinit var prefs: Preferences
    private lateinit var vr: VehicleRenderer
    private lateinit var scenery: SceneryRenderer
    private lateinit var track: Track
    private lateinit var previewTrack: Track
    private val layout = GlyphLayout()
    private val view = ExtendViewport(480f, 800f)
    private val tmp = Vector2()
    private val rnd = java.util.Random()

    private var state = State.MENU
    private var buttons: List<Btn> = emptyList()

    // race state
    private var clock = 0f
    private var dist = 0f
    private var px = 240f
    private var speed = 0f
    private var steerVis = 0f
    private var coins = 0
    private var crashes = 0
    private var boostsGot = 0
    private var boostT = 0f
    private var hitT = 0f
    private var raceT = 0f
    private var postT = 0f
    private var lap = 1
    private var countdownT = 0f
    private var lastCount = 0
    private var goT = 0f
    private var lapMsgT = 0f
    private var finishT = 0f
    private var bonus = 0
    private var newBest = false
    private var best = 0f
    private var aiMode = false
    private var diff = Diff.MEDIUM
    private var ais = ArrayList<Ai>()
    private var playerRank = 1
    private var evT = 0f
    private val evs = ArrayList<Ev>()
    private var bannerText = ""
    private var bannerColor = RED
    private var bannerT = 0f
    private var resultLines = ArrayList<Pair<String, Color>>()
    private var confettiAt = -99f

    // progress (saved locally)
    private var totalCoins = 0
    private var points = 0
    private var races = 0
    private var cleanRaces = 0
    private var missionsDone = 0
    private var coinsLife = 0
    private var boostsLife = 0
    private var aiWins = 0
    private var ach = mutableSetOf<String>()
    private var mdone = mutableSetOf<String>()
    private var ownedV = mutableSetOf("auto")
    private var ownedT = mutableSetOf("village")
    private var dailyDay = 0L
    private var dailyProg = 0
    private var dailyClaimed = false
    private var selV = 0
    private var selT = 0
    private var vIdx = 0
    private var tIdx = 0
    private var toastText = ""
    private var toastT = 0f

    // multiplayer
    private var mp: MpSession? = null
    private var mpKind = "ONLINE"          // "BT" or "ONLINE"
    private var playerName = ""
    private var playerId = ""
    private var seenStartId = 0
    private var scanner: BtScanner? = null
    private var btListSize = -1
    private var btScanning = false
    private val roomBoard = LinkedHashMap<String, RB>()
    private var roundFinal = false
    private var mpFinishClock = 0f
    private var mpRewardLines = ArrayList<Pair<String, Color>>()
    private var backPressT = 0f
    private var lastSig = ""
    private var history = mutableListOf<String>()
    private var lbTab = 0
    private var lbTrack = 0
    private var globalRows: List<Pair<String, Float>> = emptyList()
    private var globalMsg = ""
    @Volatile var pendingJoinCode: String? = null

    private val vw: Float get() = view.worldWidth
    private val vh: Float get() = view.worldHeight
    private val veh: VehicleDef get() = VEHICLES[selV]

    override fun create() {
        shapes = ShapeRenderer()
        batch = SpriteBatch()
        font = BitmapFont()
        font.region.texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
        sfx = Sfx()
        vr = VehicleRenderer(shapes)
        scenery = SceneryRenderer(shapes)
        prefs = Gdx.app.getPreferences("autorally")
        totalCoins = prefs.getInteger("totalCoins", 0)
        points = prefs.getInteger("points", 0)
        races = prefs.getInteger("races", 0)
        cleanRaces = prefs.getInteger("cleanRaces", 0)
        missionsDone = prefs.getInteger("missionsDone", 0)
        coinsLife = prefs.getInteger("coinsLife", 0)
        boostsLife = prefs.getInteger("boostsLife", 0)
        aiWins = prefs.getInteger("aiWins", 0)
        sfx.muted = prefs.getBoolean("muted", false)
        ach = csv("ach", "")
        mdone = csv("mdone", "")
        ownedV = csv("unlockedV", "auto")
        ownedT = csv("unlockedT", "village")
        dailyDay = prefs.getLong("dailyDay", 0L)
        dailyProg = prefs.getInteger("dailyProg", 0)
        dailyClaimed = prefs.getBoolean("dailyClaimed", false)
        diff = try { Diff.valueOf(prefs.getString("aiDiff", "MEDIUM")) } catch (e: Exception) { Diff.MEDIUM }
        selV = VEHICLES.indexOfFirst { it.id == prefs.getString("selV", "auto") }.coerceAtLeast(0)
        selT = TRACKS.indexOfFirst { it.id == prefs.getString("selT", "village") }.coerceAtLeast(0)
        if (VEHICLES[selV].id !in ownedV) selV = 0
        if (TRACKS[selT].id !in ownedT) selT = 0
        playerName = prefs.getString("playerName", "")
        playerId = prefs.getString("playerId", "")
        if (playerId.isEmpty()) {
            playerId = java.util.UUID.randomUUID().toString().replace("-", "").take(10)
            prefs.putString("playerId", playerId); prefs.flush()
        }
        history = prefs.getString("mpHistory", "").split("~").filter { it.isNotEmpty() }.toMutableList()
        vIdx = selV; tIdx = selT
        track = Track(TRACKS[selT])
        previewTrack = track
        ensureDaily()
        refreshBest()
        Gdx.input.setCatchKey(Input.Keys.BACK, true)
        Gdx.input.inputProcessor = object : InputAdapter() {
            override fun touchDown(x: Int, y: Int, pointer: Int, button: Int): Boolean {
                tmp.set(x.toFloat(), y.toFloat()); view.unproject(tmp)
                val b = buttons.firstOrNull { it.hit(tmp.x, tmp.y) }
                when {
                    b != null -> b.action()
                    inMute(tmp) -> toggleMute()
                    state == State.RACE && inPause(tmp) -> go(State.PAUSED)
                }
                return true
            }
            override fun keyDown(keycode: Int): Boolean {
                when (keycode) {
                    Input.Keys.BACK, Input.Keys.ESCAPE, Input.Keys.P -> back()
                    Input.Keys.M -> toggleMute()
                    Input.Keys.LEFT -> browse(-1)
                    Input.Keys.RIGHT -> browse(1)
                    Input.Keys.SPACE, Input.Keys.ENTER -> primary()
                }
                return true
            }
        }
        rebuild()
    }

    override fun resize(width: Int, height: Int) { view.update(width, height, true); rebuild() }

    override fun pause() { if (state == State.RACE && mp == null) go(State.PAUSED) } // calls, app switch

    // ---------- saving ----------
    private fun csv(key: String, def: String): MutableSet<String> =
        prefs.getString(key, def).split(",").filter { it.isNotEmpty() }.toMutableSet()

    private fun bestOf(id: String): Float =
        prefs.getFloat("best_$id", if (id == "village") prefs.getFloat("best", 0f) else 0f)

    private fun refreshBest() { best = bestOf(TRACKS[selT].id) }

    private fun saveProgress() {
        prefs.putInteger("totalCoins", totalCoins)
        prefs.putInteger("points", points)
        prefs.putInteger("races", races)
        prefs.putInteger("cleanRaces", cleanRaces)
        prefs.putInteger("missionsDone", missionsDone)
        prefs.putInteger("coinsLife", coinsLife)
        prefs.putInteger("boostsLife", boostsLife)
        prefs.putInteger("aiWins", aiWins)
        prefs.putString("ach", ach.joinToString(","))
        prefs.putString("mdone", mdone.joinToString(","))
        prefs.putString("unlockedV", ownedV.joinToString(","))
        prefs.putString("unlockedT", ownedT.joinToString(","))
        prefs.putString("selV", VEHICLES[selV].id)
        prefs.putString("selT", TRACKS[selT].id)
        prefs.putLong("dailyDay", dailyDay)
        prefs.putInteger("dailyProg", dailyProg)
        prefs.putBoolean("dailyClaimed", dailyClaimed)
        prefs.putString("mpHistory", history.joinToString("~"))
        prefs.flush()
    }

    private fun toast(t: String) { toastText = t; toastT = 1.8f }
    private fun banner(t: String, c: Color) { bannerText = t; bannerColor = c; bannerT = 2f; sfx.play(sfx.beep) }

    // ---------- daily challenge and achievements ----------
    private fun today() = System.currentTimeMillis() / 86_400_000L
    private fun dailyType() = (today() % 3).toInt()
    private fun dailyTarget() = when (dailyType()) { 0 -> 2; 1 -> 50; else -> 1 }
    private fun dailyText() = when (dailyType()) {
        0 -> "Finish 2 races today"
        1 -> "Collect 50 coins today"
        else -> "Complete a mission today"
    }

    private fun ensureDaily() {
        if (dailyDay != today()) { dailyDay = today(); dailyProg = 0; dailyClaimed = false; saveProgress() }
    }

    private fun earned(id: String): Boolean = when (id) {
        "first" -> races >= 1
        "coins100" -> coinsLife >= 100
        "clean" -> cleanRaces >= 1
        "boost10" -> boostsLife >= 10
        "mission1" -> missionsDone >= 1
        "garage" -> ownedV.size >= 3
        "tracks" -> ownedT.size >= 3
        "aiwin" -> aiWins >= 1
        "mission5" -> mdone.size >= TRACKS.size
        "expert" -> prefs.getBoolean("expertWin", false)
        else -> false
    }

    private fun checkAchievements(): List<String> {
        val n = ArrayList<String>()
        for (a in ACHIEVEMENTS) if (a.id !in ach && earned(a.id)) {
            ach.add(a.id); totalCoins += a.reward; n.add(a.name)
        }
        if (n.isNotEmpty()) { sfx.play(sfx.buy); confettiAt = clock }
        return n
    }

    // ---------- navigation ----------
    private fun go(s: State) { state = s; rebuild() }

    private fun toggleMute() {
        sfx.muted = !sfx.muted
        prefs.putBoolean("muted", sfx.muted); prefs.flush()
    }

    private fun primary() {
        when (state) {
            State.MENU -> go(State.MODE)
            State.AI_SETUP -> { aiMode = true; startRace() }
            State.FINISH -> if (finishT > 0.8f) startRace()
            State.PAUSED -> go(State.RACE)
            State.SEL_VEHICLE -> actionV()
            State.SEL_TRACK -> actionT()
            else -> {}
        }
    }

    private fun back() {
        when (state) {
            State.RACE -> if (mp != null) leaveRacePrompt() else go(State.PAUSED)
            State.COUNTDOWN -> if (mp != null) leaveRacePrompt() else go(State.MENU)
            State.PAUSED -> go(State.RACE)
            State.AI_SETUP, State.MP_MENU -> go(State.MODE)
            State.BT_FIND -> { scanner?.stop(); scanner = null; go(State.MP_MENU) }
            State.MP_LOBBY -> leaveMp()
            State.MP_RESULT -> backToLobby()
            State.MENU -> Gdx.app.exit()
            else -> go(State.MENU)
        }
    }

    private fun browse(d: Int) {
        if (state == State.SEL_VEHICLE) browseV(d) else if (state == State.SEL_TRACK) browseT(d)
    }

    private fun browseV(d: Int) { vIdx = (vIdx + d + VEHICLES.size) % VEHICLES.size; rebuild() }

    private fun browseT(d: Int) {
        tIdx = (tIdx + d + TRACKS.size) % TRACKS.size
        previewTrack = Track(TRACKS[tIdx])
        rebuild()
    }

    private fun openVehicles() { vIdx = selV; go(State.SEL_VEHICLE) }
    private fun openTracks() { tIdx = selT; previewTrack = Track(TRACKS[tIdx]); go(State.SEL_TRACK) }

    private fun unlocked(name: String) {
        val a = checkAchievements()
        saveProgress(); sfx.play(sfx.buy); confettiAt = clock
        toast(if (a.isEmpty()) "$name UNLOCKED!" else "$name UNLOCKED!  ACHIEVEMENT: ${a[0]}")
        rebuild()
    }

    private fun actionV() {
        val v = VEHICLES[vIdx]
        if (v.id in ownedV) {
            selV = vIdx; saveProgress(); go(State.MENU)
        } else if (totalCoins >= v.price) {
            totalCoins -= v.price; ownedV.add(v.id); unlocked(v.name.uppercase())
        } else toast("Need ${v.price - totalCoins} more coins")
    }

    private fun actionT() {
        val t = TRACKS[tIdx]
        if (t.id in ownedT) {
            selT = tIdx; track = Track(t); refreshBest(); saveProgress(); go(State.MENU)
        } else if (totalCoins >= t.price) {
            totalCoins -= t.price; ownedT.add(t.id); unlocked(t.name.uppercase())
        } else toast("Need ${t.price - totalCoins} more coins")
    }

    private fun cardY(i: Int) = vh - 130f - (i + 1) * 118f - i * 14f

    private fun rivalVehicle(i: Int): VehicleDef {
        val pool = VEHICLES.filter { it.id != veh.id }
        return pool[(i * 2 + selV) % pool.size]
    }

    private val rivalNames = listOf("Raju", "Priya", "Kabir")

    private fun rebuild() {
        val cx = vw / 2f
        val l = ArrayList<Btn>()
        val back = Btn(cx - 150f, 50f, 300f, 56f, "BACK", BTN_GRAY) { go(State.MENU) }
        when (state) {
            State.MENU -> {
                val y = vh * 0.30f
                l.add(Btn(cx - 150f, y, 300f, 76f, "PLAY", BTN_GREEN) { go(State.MODE) })
                l.add(Btn(cx - 150f, y - 70f, 146f, 56f, "VEHICLE", BTN_BLUE) { openVehicles() })
                l.add(Btn(cx + 4f, y - 70f, 146f, 56f, "TRACK", BTN_PURPLE) { openTracks() })
                l.add(Btn(cx - 150f, y - 136f, 146f, 56f, "MISSIONS", BTN_ORANGE) { ensureDaily(); go(State.MISSIONS) })
                l.add(Btn(cx + 4f, y - 136f, 146f, 56f, "PROFILE", BTN_GRAY) { go(State.PROFILE) })
                l.add(Btn(cx - 150f, 38f, 300f, 52f, "LEADERBOARD", BTN_BLUE) { go(State.LEADERBOARD) })
            }
            State.MODE -> {
                val w = vw - 40f; val h = 118f
                l.add(Btn(20f, cardY(0), w, h, "", BTN_GREEN) { aiMode = false; startRace() })
                l.add(Btn(20f, cardY(1), w, h, "", BTN_BLUE) { openMp("BT") })
                l.add(Btn(20f, cardY(2), w, h, "", BTN_PURPLE) { openMp("ONLINE") })
                l.add(Btn(20f, cardY(3), w, h, "", BTN_ORANGE) { go(State.AI_SETUP) })
                l.add(back)
            }
            State.AI_SETUP -> {
                val d = Diff.values()
                for (i in d.indices) {
                    val c = i % 2; val r = i / 2
                    l.add(Btn(cx - 150f + c * 154f, vh * 0.66f - r * 66f, 146f, 56f, d[i].label,
                        if (d[i] == diff) BTN_GREEN else BTN_GRAY) {
                        diff = d[i]; prefs.putString("aiDiff", diff.name); prefs.flush(); rebuild()
                    })
                }
                l.add(Btn(cx - 150f, 150f, 300f, 64f, "START RACE", BTN_GREEN) { aiMode = true; startRace() })
                l.add(Btn(cx - 150f, 50f, 300f, 56f, "BACK", BTN_GRAY) { go(State.MODE) })
            }
            State.SEL_VEHICLE -> {
                val v = VEHICLES[vIdx]
                val owned = v.id in ownedV
                val py = vh * 0.62f
                l.add(Btn(12f, py - 35f, 60f, 70f, "<", BTN_BLUE) { browseV(-1) })
                l.add(Btn(vw - 72f, py - 35f, 60f, 70f, ">", BTN_BLUE) { browseV(1) })
                val col = if (owned) BTN_GREEN else if (totalCoins >= v.price) BTN_ORANGE else BTN_LOCKED
                l.add(Btn(cx - 150f, 130f, 300f, 64f, if (owned) "SELECT" else "UNLOCK  ${v.price} COINS", col) { actionV() })
                l.add(back)
            }
            State.SEL_TRACK -> {
                val t = TRACKS[tIdx]
                val owned = t.id in ownedT
                val rowY = vh * 0.40f + vh * 0.30f + 75f
                l.add(Btn(12f, rowY - 30f, 60f, 60f, "<", BTN_PURPLE) { browseT(-1) })
                l.add(Btn(vw - 72f, rowY - 30f, 60f, 60f, ">", BTN_PURPLE) { browseT(1) })
                val col = if (owned) BTN_GREEN else if (totalCoins >= t.price) BTN_ORANGE else BTN_LOCKED
                l.add(Btn(cx - 150f, 130f, 300f, 64f, if (owned) "SELECT" else "UNLOCK  ${t.price} COINS", col) { actionT() })
                l.add(back)
            }
            State.MP_MENU -> {
                val y0 = vh * 0.60f
                if (mpKind == "BT") {
                    l.add(Btn(cx - 150f, y0, 300f, 60f, "HOST A GAME", BTN_GREEN) { ensureName { startBtHost() } })
                    l.add(Btn(cx - 150f, y0 - 76f, 300f, 60f, "FIND A GAME", BTN_BLUE) { ensureName { openFind() } })
                    l.add(Btn(cx - 150f, y0 - 152f, 300f, 56f, "CHANGE NAME", BTN_GRAY) { changeName() })
                } else {
                    l.add(Btn(cx - 150f, y0, 300f, 60f, "CREATE ROOM", BTN_GREEN) { if (hasServer()) ensureName { startOnlineHost() } else toast("Set the server first") })
                    l.add(Btn(cx - 150f, y0 - 76f, 300f, 60f, "JOIN WITH CODE", BTN_BLUE) { if (hasServer()) ensureName { askJoinCode() } else toast("Set the server first") })
                    l.add(Btn(cx - 150f, y0 - 152f, 300f, 56f, "SET SERVER", BTN_ORANGE) { setServer() })
                    l.add(Btn(cx - 150f, y0 - 220f, 300f, 56f, "CHANGE NAME", BTN_GRAY) { changeName() })
                }
                l.add(Btn(cx - 150f, 50f, 300f, 56f, "BACK", BTN_GRAY) { go(State.MODE) })
            }
            State.BT_FIND -> {
                val sc = scanner
                if (sc != null) for ((i, d) in sc.named().take(6).withIndex())
                    l.add(Btn(30f, vh - 190f - i * 62f, vw - 60f, 52f, sc.nameOf(d), BTN_BLUE) { connectTo(d) })
                l.add(Btn(cx - 150f, 130f, 300f, 56f, "SCAN AGAIN", BTN_PURPLE) { scanner?.start(); btListSize = -1 })
                l.add(Btn(cx - 150f, 50f, 300f, 56f, "BACK", BTN_GRAY) { scanner?.stop(); scanner = null; go(State.MP_MENU) })
            }
            State.MP_LOBBY -> {
                val m = mp
                if (m != null) {
                    l.add(Btn(12f, 316f, 56f, 50f, "<", BTN_BLUE) { lobbyVeh(-1) })
                    l.add(Btn(vw - 68f, 316f, 56f, 50f, ">", BTN_BLUE) { lobbyVeh(1) })
                    if (m.isHost) {
                        l.add(Btn(12f, 252f, 56f, 50f, "<", BTN_PURPLE) { lobbyTrack(-1) })
                        l.add(Btn(vw - 68f, 252f, 56f, 50f, ">", BTN_PURPLE) { lobbyTrack(1) })
                        l.add(Btn(cx - 150f, 170f, 300f, 64f, "START RACE", if (canStart()) BTN_GREEN else BTN_LOCKED) { hostStartRace() })
                    } else {
                        l.add(Btn(cx - 150f, 170f, 300f, 64f, if (m.me.ready) "READY! (TAP TO CANCEL)" else "I AM READY",
                            if (m.me.ready) BTN_ORANGE else BTN_GREEN) { toggleReady() })
                    }
                    l.add(Btn(cx - 150f, 100f, 146f, 50f, "INVITE", BTN_BLUE) { invite() })
                    l.add(Btn(cx + 4f, 100f, 146f, 50f, "LEAVE", BTN_GRAY) { leaveMp() })
                } else l.add(back)
            }
            State.MP_RESULT -> {
                l.add(Btn(cx - 150f, 110f, 300f, 56f, "BACK TO LOBBY", BTN_GREEN) { backToLobby() })
                l.add(Btn(cx - 150f, 46f, 300f, 50f, "LEAVE ROOM", BTN_GRAY) { leaveMp() })
            }
            State.LEADERBOARD -> {
                l.add(Btn(20f, vh - 150f, 215f, 50f, "MY RACES", if (lbTab == 0) BTN_GREEN else BTN_GRAY) { lbTab = 0; rebuild() })
                l.add(Btn(245f, vh - 150f, 215f, 50f, "GLOBAL (ONLINE)", if (lbTab == 1) BTN_GREEN else BTN_GRAY) { lbTab = 1; loadGlobal(); rebuild() })
                if (lbTab == 1) {
                    l.add(Btn(12f, vh - 215f, 56f, 44f, "<", BTN_PURPLE) { lbTrack = (lbTrack + TRACKS.size - 1) % TRACKS.size; loadGlobal(); rebuild() })
                    l.add(Btn(vw - 68f, vh - 215f, 56f, 44f, ">", BTN_PURPLE) { lbTrack = (lbTrack + 1) % TRACKS.size; loadGlobal(); rebuild() })
                }
                l.add(back)
            }
            State.MISSIONS, State.PROFILE -> l.add(back)
            State.PAUSED -> {
                l.add(Btn(cx - 150f, vh * 0.40f, 300f, 64f, "RESUME", BTN_GREEN) { go(State.RACE) })
                l.add(Btn(cx - 150f, vh * 0.40f - 80f, 300f, 56f, "QUIT TO MENU", BTN_GRAY) { go(State.MENU) })
            }
            State.FINISH -> {
                l.add(Btn(cx - 150f, vh * 0.22f, 300f, 64f, "RACE AGAIN", BTN_GREEN) { if (finishT > 0.5f) startRace() })
                l.add(Btn(cx - 150f, vh * 0.22f - 80f, 300f, 56f, "MENU", BTN_GRAY) { go(State.MENU) })
            }
            else -> {}
        }
        buttons = l
    }

    private fun inRace() = state == State.COUNTDOWN || state == State.RACE || state == State.PAUSED || state == State.FINISH

    private fun inMute(p: Vector2) =
        (inRace() || state == State.MENU) && p.x in (vw - 68f)..(vw - 4f) && p.y in (vh - 128f)..(vh - 64f)

    private fun inPause(p: Vector2) = mp == null && p.x in 4f..68f && p.y in (vh - 128f)..(vh - 64f)

    private fun isScreen() = !(state == State.MENU || inRace())

    // ---------- game logic ----------
    private fun startRace() {
        track = Track(TRACKS[selT]); refreshBest()
        dist = 0f; speed = 0f; coins = 0; crashes = 0; boostsGot = 0; boostT = 0f; hitT = 0f
        raceT = 0f; postT = 0f; lap = 1; steerVis = 0f
        px = vw / 2f + track.curve(0f)
        countdownT = 3f; lastCount = 4; newBest = false
        evs.clear(); evT = 10f; bannerT = 0f
        ais = ArrayList()
        if (aiMode) {
            val prefsL = floatArrayOf(-0.5f, 0.5f, 0f)
            val greed = floatArrayOf(1.0f, 0.6f, 1.4f)
            val offs = floatArrayOf(-70f, 70f, 0f)
            val dists = floatArrayOf(0f, 0f, -45f)
            for (i in 0 until 3) {
                val a = Ai(rivalNames[i], rivalVehicle(i), prefsL[i], greed[i])
                a.off = offs[i]; a.dist = dists[i]
                ais.add(a)
            }
        }
        go(State.COUNTDOWN)
    }

    private fun wrapD(tr: Track, d: Float): Float {
        var x = d
        if (x < -tr.lapLen / 2f) x += tr.lapLen
        if (x > tr.lapLen / 2f) x -= tr.lapLen
        return x
    }

    private fun update(dt: Float) {
        clock += dt
        goT -= dt; lapMsgT -= dt; toastT -= dt; bannerT -= dt
        updateMp(dt)
        when (state) {
            State.MENU -> dist += 90f * dt   // the track scrolls slowly behind the menu
            State.COUNTDOWN -> {
                countdownT -= dt
                val n = ceil(countdownT).toInt()
                if (n != lastCount && n > 0) { lastCount = n; sfx.play(sfx.beep) }
                if (countdownT <= 0f) { goT = 0.8f; sfx.play(sfx.go); go(State.RACE) }
            }
            State.RACE -> { race(dt); updateAis(dt) }
            State.FINISH -> { finishT += dt; postT += dt; updateAis(dt) }
            else -> {}
        }
    }

    private fun updateAis(dt: Float) {
        if (ais.isEmpty()) return
        val pOff = px - (vw / 2f + track.curve(dist))
        val total = LAPS * track.lapLen
        for (a in ais) {
            a.update(dt, track, diff, dist, pOff, ais)
            if (a.finishT < 0f && a.dist >= total) a.finishT = raceT + postT
        }
    }

    private fun spawnEvent() {
        val total = LAPS * track.lapLen
        if (dist < 600f || dist > total - 1100f) return
        val half = track.def.roadHalf
        if (rnd.nextBoolean()) {
            val o = half * 0.45f
            evs.add(Ev(dist + 900f, -o, Kind.CONE)); evs.add(Ev(dist + 900f, o, Kind.BARREL))
            banner("ROADBLOCK AHEAD!", RED)
        } else {
            for (i in 0 until 8) evs.add(Ev(dist + 700f + i * 45f, sin(i * 0.9f) * (half - 55f), Kind.COIN))
            banner("COIN RAIN!", CYAN)
        }
    }

    private fun hitObstacle() {
        hitT = 1.2f; speed *= 0.35f; boostT = 0f; crashes++; sfx.play(sfx.crash)
    }

    private fun race(dt: Float) {
        val td = track.def
        val cx = vw / 2f
        var steer = 0f
        if (Gdx.input.isKeyPressed(Input.Keys.LEFT) || Gdx.input.isKeyPressed(Input.Keys.A)) steer -= 1f
        if (Gdx.input.isKeyPressed(Input.Keys.RIGHT) || Gdx.input.isKeyPressed(Input.Keys.D)) steer += 1f
        for (i in 0 until 5) if (Gdx.input.isTouched(i)) {
            tmp.set(Gdx.input.getX(i).toFloat(), Gdx.input.getY(i).toFloat()); view.unproject(tmp)
            if (!inMute(tmp) && !inPause(tmp)) steer += if (tmp.x < cx) -1f else 1f
        }
        steer = steer.coerceIn(-1f, 1f)
        steerVis += (steer - steerVis) * min(1f, dt * 8f)

        val v = veh
        val onRoad = abs(px - (cx + track.curve(dist))) < td.roadHalf - 4f
        val maxSp = when { boostT > 0f -> v.maxSpeed * 1.5f; onRoad -> v.maxSpeed; else -> v.offroad }
        speed = if (speed < maxSp) min(maxSp, speed + v.accel * dt) else max(maxSp, speed - 400f * dt)
        px = (px + steer * v.handling * (0.8f + 0.4f * min(1f, speed / 300f)) * dt).coerceIn(16f, vw - 16f)
        dist += speed * dt; raceT += dt; boostT -= dt; hitT -= dt

        evT -= dt
        if (evT <= 0f) { evT = 12f + rnd.nextFloat() * 10f; if (mp == null) spawnEvent() }

        val newLap = min(LAPS, (dist / track.lapLen).toInt() + 1)
        if (newLap != lap) { lap = newLap; track.resetLap(); lapMsgT = 1.3f; sfx.play(sfx.lap) }

        val pos = dist % track.lapLen
        for (it in track.items) {
            if (it.taken) continue
            if (abs(wrapD(track, it.s - pos)) > 26f) continue
            val dx = abs(px - (cx + track.curve(it.s) + it.off))
            when (it.kind) {
                Kind.COIN -> if (dx < 9f + v.halfW) { it.taken = true; coins++; sfx.play(sfx.coin) }
                Kind.BOOST -> if (dx < 13f + v.halfW) { it.taken = true; boostT = 2.5f; boostsGot++; sfx.play(sfx.boost) }
                else -> if (hitT <= 0f && dx < 11f + v.halfW) { it.taken = true; hitObstacle() }
            }
        }
        for (e in evs) {
            if (e.taken || abs(e.abs - dist) > 26f) continue
            val dx = abs(px - (cx + track.curve(e.abs) + e.off))
            if (e.kind == Kind.COIN) {
                if (dx < 9f + v.halfW) { e.taken = true; coins++; sfx.play(sfx.coin) }
            } else if (hitT <= 0f && dx < 11f + v.halfW) { e.taken = true; hitObstacle() }
        }
        evs.removeAll { it.abs < dist - 100f }

        // bumping into rivals: the car behind can't pass through, and they push apart
        for (a in ais) {
            val dy = a.dist - dist
            if (abs(dy) > 36f) continue
            val dx = (cx + track.curve(a.dist) + a.off) - px
            if (abs(dx) < v.halfW + a.v.halfW) {
                if (dy > 0f) speed = min(speed, a.speed * 0.97f) else a.speed = min(a.speed, speed * 0.97f)
                val push = (if (dx >= 0f) 1f else -1f) * 70f * dt
                a.off += push; px -= push
            }
        }
        if (dist >= LAPS * track.lapLen) { if (mp != null) finishMpRace() else finishRace() }
    }

    private fun missionSuccess(): Boolean {
        val m = MISSIONS.getValue(track.def.id)
        return when (m.kind) {
            MKind.DELIVER -> raceT <= m.target
            MKind.COINS -> coins >= m.target
            MKind.CLEAN -> crashes == 0
            MKind.BOOSTS -> boostsGot >= m.target
        }
    }

    private fun finishRace() {
        val td = track.def
        dist = LAPS * track.lapLen - 1f
        finishT = 0f
        playerRank = 1 + ais.count { it.finishT >= 0f }
        bonus = 20 + 10 * td.difficulty
        newBest = best <= 0f || raceT < best
        if (newBest) { best = raceT; prefs.putFloat("best_${td.id}", best) }

        val m = MISSIONS.getValue(td.id)
        val missionOk = missionSuccess()
        val mBonus = if (missionOk) 40 + 15 * td.difficulty else 0
        val rankBonus = if (aiMode) (intArrayOf(60, 35, 20, 0)[playerRank - 1] * diff.reward).toInt() else 0
        val pts = coins * 10 + bonus * 5 + (if (missionOk) 300 else 0) + (if (aiMode) (4 - playerRank) * 100 else 0)

        totalCoins += coins + bonus + mBonus + rankBonus
        points += pts
        races++; coinsLife += coins; boostsLife += boostsGot
        if (crashes == 0) cleanRaces++
        if (missionOk) { missionsDone++; mdone.add(td.id) }
        if (aiMode && playerRank == 1) {
            aiWins++
            if (diff == Diff.EXPERT) prefs.putBoolean("expertWin", true)
        }

        ensureDaily()
        var dailyDone = false
        if (!dailyClaimed) {
            dailyProg += when (dailyType()) { 0 -> 1; 1 -> coins; else -> if (missionOk) 1 else 0 }
            if (dailyProg >= dailyTarget()) { dailyClaimed = true; dailyDone = true; totalCoins += DAILY_REWARD }
        }
        val newAch = checkAchievements()
        saveProgress()

        val lines = ArrayList<Pair<String, Color>>()
        lines.add(Pair("TIME  ${fmt(raceT)}   BEST  ${fmt(best)}", Color.WHITE))
        if (newBest) lines.add(Pair("NEW BEST TIME!", LIME))
        lines.add(if (missionOk) Pair("MISSION COMPLETE!  +$mBonus coins", LIME) else Pair("Mission failed: ${m.title}", RED))
        if (aiMode) lines.add(Pair("POSITION $playerRank of ${ais.size + 1}   +$rankBonus coins", if (playerRank == 1) LIME else Color.WHITE))
        lines.add(Pair("Coins +$coins   Finish bonus +$bonus", YELLOW))
        lines.add(Pair("Points +$pts   Total coins $totalCoins", Color.WHITE))
        if (dailyDone) lines.add(Pair("DAILY CHALLENGE DONE!  +$DAILY_REWARD coins", CYAN))
        if (newAch.isNotEmpty()) lines.add(Pair("ACHIEVEMENT: " + newAch.joinToString(", "), CYAN))
        resultLines = lines
        if (missionOk || newBest || (aiMode && playerRank == 1)) confettiAt = clock
        sfx.play(sfx.win)
        go(State.FINISH)
    }

    private fun positionNow() = 1 + ais.count { it.dist > dist }

    // ---------- drawing ----------
    override fun render() {
        update(min(Gdx.graphics.deltaTime, 0.05f))
        val screen = isScreen()
        val bg = if (screen) BG1 else track.def.grass1
        ScreenUtils.clear(bg.r, bg.g, bg.b, 1f)
        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        view.apply()
        shapes.projectionMatrix = view.camera.combined
        batch.projectionMatrix = view.camera.combined
        shapes.begin(ShapeType.Filled)
        if (screen) {
            shapes.color = BG2; shapes.rect(0f, vh - 90f, vw, 90f)
            when (state) {
                State.SEL_VEHICLE -> drawVehicleSelect()
                State.SEL_TRACK -> drawTrackSelect()
                State.MODE -> drawModeIcons()
                State.AI_SETUP -> drawRivals()
                State.MP_LOBBY -> drawLobbyShapes()
                else -> {}
            }
        } else {
            drawWorld()
            drawOverlay()
        }
        drawConfetti()
        drawButtons()
        shapes.end()
        batch.begin()
        drawText()
        batch.end()
    }

    private fun rc(l: Float, y: Float, w: Float, h: Float, cl: Float, cr: Float) {
        val a = max(l, cl); val b = min(l + w, cr)
        if (b > a) shapes.rect(a, y, b - a, h)
    }

    /** Draws grass, rumble strips, road and dashes. Used by the race view and the track preview. */
    private fun roadStrips(
        tr: Track, sOff: Float, cx: Float, baseY: Float, yFrom: Float, yTo: Float,
        k: Float, step: Float, cl: Float, cr: Float
    ) {
        val td = tr.def
        var y = yFrom
        while (y < yTo) {
            val s = sOff + (y - baseY) / k
            val c = cx + tr.curve(s) * k
            val h = min(step, yTo - y)
            shapes.color = if ((floor(s / 60f).toInt() and 1) == 0) td.grass1 else td.grass2
            rc(cl, y, cr - cl, h, cl, cr)
            shapes.color = if ((floor(s / 30f).toInt() and 1) == 0) td.rumbleA else td.rumbleB
            rc(c - (td.roadHalf + 10f) * k, y, (2f * td.roadHalf + 20f) * k, h, cl, cr)
            shapes.color = td.road
            rc(c - td.roadHalf * k, y, 2f * td.roadHalf * k, h, cl, cr)
            if ((floor(s / 40f).toInt() and 3) == 0) {
                shapes.color = Color.WHITE
                rc(c - 3f * k, y, 6f * k, h, cl, cr)
            }
            y += step
        }
    }

    private fun decorPass(tr: Track, sOff: Float, cx: Float, baseY: Float, k: Float, yMin: Float, yMax: Float) {
        val pos = sOff % tr.lapLen
        for (d in tr.decor) {
            val y = baseY + wrapD(tr, d.s - pos) * k
            if (y < yMin - 30f * k || y > yMax + 30f * k) continue
            scenery.draw(d, cx + (tr.curve(d.s) + d.x) * k, y, k)
        }
    }

    private fun drawItem(kind: Kind, ix: Float, iy: Float) {
        when (kind) {
            Kind.CONE -> {
                shapes.color = ORANGE; shapes.triangle(ix - 13f, iy - 12f, ix + 13f, iy - 12f, ix, iy + 16f)
                shapes.color = Color.WHITE; shapes.rect(ix - 7f, iy - 2f, 14f, 5f)
            }
            Kind.BARREL -> {
                shapes.color = Color(0.7f, 0.2f, 0.15f, 1f); shapes.circle(ix, iy, 16f)
                shapes.color = Color.LIGHT_GRAY; shapes.circle(ix, iy, 9f)
                shapes.color = Color(0.7f, 0.2f, 0.15f, 1f); shapes.circle(ix, iy, 5f)
            }
            Kind.COIN -> {
                shapes.color = YELLOW; shapes.circle(ix, iy, 11f)
                shapes.color = Color(0.9f, 0.6f, 0.05f, 1f); shapes.circle(ix, iy, 6f)
            }
            Kind.BOOST -> {
                shapes.color = CYAN; shapes.circle(ix, iy, 17f)
                shapes.color = Color.WHITE; shapes.triangle(ix - 9f, iy - 9f, ix + 9f, iy - 9f, ix, iy + 11f)
            }
        }
    }

    private fun drawWorld() {
        val cx = vw / 2f
        val td = track.def
        roadStrips(track, dist, cx, PLAYER_Y, 0f, vh, 1f, STEP, 0f, vw)
        val base = floor(dist / track.lapLen).toInt()
        for (kk in base..base + 1) {          // start / finish lines
            val ly = PLAYER_Y + (kk * track.lapLen - dist)
            if (ly < -20f || ly > vh) continue
            val lx = cx + track.curve(0f) - td.roadHalf
            val n = (2f * td.roadHalf / 14f).toInt()
            for (i in 0 until n) for (row in 0..1) {
                shapes.color = if (((i + row) and 1) == 0) Color.WHITE else DARK
                shapes.rect(lx + i * 14f, ly + row * 14f, 14f, 14f)
            }
        }
        decorPass(track, dist, cx, PLAYER_Y, 1f, 0f, vh)
        val pos = dist % track.lapLen
        for (it in track.items) {
            if (it.taken && (it.kind == Kind.COIN || it.kind == Kind.BOOST)) continue
            val iy = PLAYER_Y + wrapD(track, it.s - pos)
            if (iy < -30f || iy > vh + 30f) continue
            drawItem(it.kind, cx + track.curve(it.s) + it.off, iy)
        }
        for (e in evs) {
            if (e.taken && e.kind == Kind.COIN) continue
            val iy = PLAYER_Y + (e.abs - dist)
            if (iy < -30f || iy > vh + 30f) continue
            drawItem(e.kind, cx + track.curve(e.abs) + e.off, iy)
        }
        for (a in ais) {
            val ay = PLAYER_Y + (a.dist - dist)
            if (ay < -70f || ay > vh + 70f) continue
            vr.draw(a.v, cx + track.curve(a.dist) + a.off, ay, 1f, a.steerVis, abs(a.dist), clock, a.boostT > 0f, false)
        }
        drawRemotes(cx)
        if (inRace() && !(hitT > 0f && (hitT * 12f).toInt() % 2 == 0))
            vr.draw(veh, px, PLAYER_Y, 1f, steerVis, dist, clock, boostT > 0f, speed > veh.maxSpeed * 0.9f)
    }

    private fun drawOverlay() {
        if (state == State.MENU || state == State.PAUSED || state == State.FINISH) {
            shapes.setColor(0f, 0f, 0f, if (state == State.MENU) 0.45f else 0.6f)
            shapes.rect(0f, 0f, vw, vh)
        }
        if (state == State.MENU) {
            val cx = vw / 2f; val py = vh * 0.60f
            shapes.setColor(0f, 0f, 0f, 0.35f); shapes.circle(cx, py, 100f)
            vr.draw(veh, cx, py - 6f, 2.3f, sin(clock * 1.5f) * 0.5f, clock * 60f, clock, false, true)
        }
        if (inRace()) {
            shapes.setColor(0f, 0f, 0f, 0.4f); shapes.rect(0f, vh - 56f, vw, 56f)
            if (mp == null) {
                shapes.setColor(0f, 0f, 0f, 0.5f); shapes.rect(8f, vh - 124f, 52f, 52f)
                shapes.color = Color.WHITE
                shapes.rect(23f, vh - 112f, 7f, 28f); shapes.rect(38f, vh - 112f, 7f, 28f)
            }
        }
        val mx = vw - 60f; val my = vh - 124f   // mute button
        shapes.setColor(0f, 0f, 0f, 0.5f); shapes.rect(mx, my, 52f, 52f)
        shapes.color = Color.WHITE
        shapes.rect(mx + 10f, my + 21f, 9f, 10f)
        shapes.triangle(mx + 19f, my + 21f, mx + 19f, my + 31f, mx + 30f, my + 40f)
        shapes.triangle(mx + 19f, my + 21f, mx + 30f, my + 12f, mx + 30f, my + 40f)
        if (sfx.muted) { shapes.color = RED; shapes.rectLine(mx + 8f, my + 8f, mx + 44f, my + 44f, 5f) }
        else { shapes.rect(mx + 35f, my + 20f, 3f, 12f); shapes.rect(mx + 41f, my + 16f, 3f, 20f) }
    }

    private fun drawConfetti() {
        val t = clock - confettiAt
        if (t < 0f || t > 4.5f) return
        for (i in 0 until 40) {
            val x = ((i * 97) % 100) / 100f * vw + sin(t * 3f + i) * 12f
            val y = vh + 20f - (t * (140f + (i * 37) % 120) + (i * 53) % 200)
            if (y < -10f || y > vh + 10f) continue
            shapes.color = when (i % 4) { 0 -> YELLOW; 1 -> CYAN; 2 -> LIME; else -> ORANGE }
            if (i % 2 == 0) shapes.circle(x, y, 7f) else shapes.rect(x, y, 9f, 9f)
        }
    }

    private fun lockIcon(x: Float, y: Float) {
        shapes.color = Color.LIGHT_GRAY; shapes.circle(x, y + 14f, 13f)
        shapes.color = Color(0.1f, 0.1f, 0.15f, 1f); shapes.circle(x, y + 14f, 8f)
        shapes.color = Color.LIGHT_GRAY; shapes.rect(x - 18f, y - 16f, 36f, 28f)
        shapes.color = Color(0.1f, 0.1f, 0.15f, 1f); shapes.circle(x, y - 3f, 4f); shapes.rect(x - 2f, y - 12f, 4f, 9f)
    }

    private fun bar(y: Float, v: Float, c: Color) {
        shapes.setColor(0f, 0f, 0f, 0.4f); shapes.rect(190f, y, 230f, 14f)
        shapes.color = c; shapes.rect(190f, y, 230f * v.coerceIn(0.05f, 1f), 14f)
    }

    private fun drawVehicleSelect() {
        val cx = vw / 2f; val py = vh * 0.62f
        val v = VEHICLES[vIdx]
        shapes.setColor(0.2f, 0.24f, 0.38f, 1f); shapes.circle(cx, py, 125f)
        shapes.setColor(0.27f, 0.32f, 0.5f, 1f); shapes.circle(cx, py, 105f)
        vr.draw(v, cx, py - 12f, 2.6f, sin(clock * 1.5f) * 0.6f, clock * 60f, clock, false, true)
        if (v.id !in ownedV) {
            shapes.setColor(0f, 0f, 0f, 0.6f); shapes.circle(cx, py, 125f)
            lockIcon(cx, py)
        }
        bar(vh * 0.36f, v.maxSpeed / 480f, CYAN)
        bar(vh * 0.36f - 35f, v.accel / 270f, LIME)
        bar(vh * 0.36f - 70f, v.handling / 350f, YELLOW)
    }

    private fun drawTrackSelect() {
        val t = TRACKS[tIdx]
        val owned = t.id in ownedT
        val cx = vw / 2f
        val panelX = 40f; val panelW = vw - 80f
        val panelY = vh * 0.40f; val panelH = vh * 0.30f
        shapes.color = Color.WHITE; shapes.rect(panelX - 4f, panelY - 4f, panelW + 8f, panelH + 8f)
        val k = 0.5f
        val sOff = (clock * 220f) % previewTrack.lapLen
        val baseY = panelY + 30f
        roadStrips(previewTrack, sOff, cx, baseY, panelY, panelY + panelH, k, 4f, panelX, panelX + panelW)
        decorPass(previewTrack, sOff, cx, baseY, k, panelY + 10f, panelY + panelH - 10f)
        vr.draw(veh, cx + previewTrack.curve(sOff) * k, baseY, 0.9f, 0f, sOff, clock, false, false)
        if (!owned) {
            shapes.setColor(0f, 0f, 0f, 0.6f); shapes.rect(panelX, panelY, panelW, panelH)
            lockIcon(cx, panelY + panelH / 2f)
        }
        for (i in 0 until 5) {
            shapes.color = if (i < t.difficulty) YELLOW else BTN_GRAY
            shapes.circle(cx - 60f + i * 30f, panelY + panelH + 28f, 9f)
        }
    }

    private fun drawModeIcons() {
        val ix = vw - 62f
        var y = cardY(0) + 48f   // single player: a coin
        shapes.color = YELLOW; shapes.circle(ix, y, 24f)
        shapes.color = Color(0.9f, 0.6f, 0.05f, 1f); shapes.circle(ix, y, 14f)
        y = cardY(1) + 48f       // bluetooth: blue disc with a B-like shape
        shapes.color = Color(0.2f, 0.45f, 0.9f, 1f); shapes.circle(ix, y, 24f)
        shapes.color = Color.WHITE; shapes.rect(ix - 8f, y - 12f, 5f, 24f)
        shapes.triangle(ix - 3f, y + 12f, ix + 10f, y + 4f, ix - 3f, y - 2f)
        shapes.triangle(ix - 3f, y - 12f, ix + 10f, y - 4f, ix - 3f, y + 2f)
        y = cardY(2) + 48f       // online: a globe
        shapes.color = CYAN; shapes.circle(ix, y, 24f)
        shapes.color = Color(0.1f, 0.5f, 0.7f, 1f); shapes.rect(ix - 24f, y - 2f, 48f, 4f); shapes.rect(ix - 2f, y - 24f, 4f, 48f)
        y = cardY(3) + 48f       // AI: a robot head
        shapes.color = Color(0.7f, 0.4f, 0.95f, 1f); shapes.rect(ix - 22f, y - 18f, 44f, 36f)
        shapes.color = Color.WHITE; shapes.circle(ix - 9f, y + 3f, 6f); shapes.circle(ix + 9f, y + 3f, 6f)
        shapes.color = DARK; shapes.circle(ix - 9f, y + 3f, 3f); shapes.circle(ix + 9f, y + 3f, 3f)
        shapes.rect(ix - 8f, y - 12f, 16f, 3f); shapes.rect(ix - 2f, y + 18f, 4f, 8f)
    }

    private fun drawRivals() {
        val cx = vw / 2f
        for (i in 0 until 3) {
            val x = cx + (i - 1) * 120f
            shapes.setColor(0.2f, 0.24f, 0.38f, 1f); shapes.circle(x, 355f, 52f)
            vr.draw(rivalVehicle(i), x, 350f, 1.2f, 0f, clock * 60f, clock, false, false)
        }
    }

    private fun drawButtons() {
        for (b in buttons) {
            shapes.setColor(b.col.r * 0.55f, b.col.g * 0.55f, b.col.b * 0.55f, 1f)
            shapes.rect(b.x, b.y - 5f, b.w, b.h)
            shapes.color = b.col; shapes.rect(b.x, b.y, b.w, b.h)
            shapes.setColor(1f, 1f, 1f, 0.15f); shapes.rect(b.x, b.y + b.h * 0.55f, b.w, b.h * 0.45f)
        }
    }

    private fun fmt(t: Float): String {
        val m = (t / 60f).toInt()
        return String.format(java.util.Locale.US, "%d:%05.2f", m, t - m * 60f)
    }

    private fun text(t: String, x: Float, y: Float, scale: Float, c: Color, align: Int = Align.center) {
        font.data.setScale(scale)
        layout.setText(font, t, Color.BLACK, 0f, align, false)
        font.draw(batch, layout, x + 2f, y - 2f)
        layout.setText(font, t, c, 0f, align, false)
        font.draw(batch, layout, x, y)
    }

    private fun centerText(t: String, x: Float, cy: Float, scale: Float, c: Color) {
        font.data.setScale(scale)
        layout.setText(font, t, c, 0f, Align.center, false)
        text(t, x, cy + layout.height / 2f, scale, c)
    }

    private fun objective(): Pair<String, Color> {
        val m = MISSIONS.getValue(track.def.id)
        return when (m.kind) {
            MKind.DELIVER -> {
                val left = m.target - raceT
                if (left >= 0f) Pair("PARCEL: ${(left / 60f).toInt()}:${String.format(java.util.Locale.US, "%02d", (left % 60f).toInt())} left",
                    if (left < 10f) RED else CYAN)
                else Pair("PARCEL IS LATE!", RED)
            }
            MKind.COINS -> Pair("COINS $coins/${m.target}", if (coins >= m.target) LIME else CYAN)
            MKind.CLEAN -> if (crashes == 0) Pair("NO CRASHES: OK", LIME) else Pair("CRASHED - MISSION FAILED", RED)
            MKind.BOOSTS -> Pair("BOOSTS $boostsGot/${m.target}", if (boostsGot >= m.target) LIME else CYAN)
        }
    }

    private fun drawText() {
        val cx = vw / 2f
        val top = vh - 14f
        if (inRace()) {
            text("COINS $coins", 14f, top, 1.4f, YELLOW, Align.left)
            text("LAP $lap/$LAPS", cx, top, 1.4f, Color.WHITE)
            text(fmt(raceT), vw - 14f, top, 1.4f, Color.WHITE, Align.right)
            if (mp != null) {
                text("RACE FOR 1ST PLACE!", cx, vh - 68f, 1.0f, CYAN)
                text("POSITION ${mpPosition()}/${mpActive()}", cx, vh - 92f, 1.1f, Color.WHITE)
                val r = mp!!.room
                for (p in r.players.values) {
                    if (p.id == playerId || p.rid != r.startId || p.fin == -2f) continue
                    val py = PLAYER_Y + (p.vDist - dist)
                    if (py < 0f || py > vh - 140f) continue
                    text(p.name, cx + track.curve(p.vDist) + p.vOff, py + 52f, 0.8f, Color.WHITE)
                }
            } else {
                val o = objective()
                text(o.first, cx, vh - 68f, 1.0f, o.second)
                if (aiMode) text("POSITION ${positionNow()}/${ais.size + 1}", cx, vh - 92f, 1.1f, Color.WHITE)
            }
            if (boostT > 0f) text("BOOST!", cx, vh - 120f, 1.6f, CYAN)
            if (bannerT > 0f) text(bannerText, cx, vh * 0.72f, 1.9f, bannerColor)
            for (a in ais) {
                val ay = PLAYER_Y + (a.dist - dist)
                if (ay < 0f || ay > vh - 140f) continue
                text(a.name, cx + track.curve(a.dist) + a.off, ay + 52f, 0.8f, Color.WHITE)
            }
        }
        when (state) {
            State.MENU -> {
                text("AUTO RALLY", cx, vh * 0.86f, 3.2f, YELLOW)
                text("3 laps. Grab coins. Dodge cones and barrels.", cx, vh * 0.86f - 60f, 1.0f, Color.WHITE)
                text("COINS $totalCoins", 14f, top, 1.4f, YELLOW, Align.left)
                val b = if (best > 0f) "BEST ${fmt(best)}" else "no best time yet"
                text("${TRACKS[selT].name.uppercase()}  -  $b", cx, vh * 0.30f + 76f + 34f, 1.1f, Color.WHITE)
            }
            State.MODE -> {
                text("CHOOSE MODE", cx, vh - 30f, 1.8f, Color.WHITE)
                val names = listOf("SINGLE PLAYER", "BLUETOOTH MULTIPLAYER", "ONLINE MULTIPLAYER", "AI COMPETITION")
                val l1 = listOf("Play alone and beat your own records.", "Race nearby friends over Bluetooth.",
                    "Create or join rooms with a code or link.", "Race smart computer opponents.")
                val l2 = listOf("Works offline.", "No internet or Wi-Fi needed.", "Needs internet.", "Easy to Expert. Works offline.")
                val badge = listOf("OFFLINE", "OFFLINE - BLUETOOTH", "NEEDS INTERNET", "OFFLINE")
                val bc = listOf(LIME, LIME, CYAN, LIME)
                for (i in 0 until 4) {
                    val y = cardY(i) + 118f
                    text(names[i], 36f, y - 14f, 1.25f, Color.WHITE, Align.left)
                    text(badge[i], 36f, y - 90f, 0.9f, bc[i], Align.left)
                    text(l1[i], 36f, y - 46f, 0.85f, Color.WHITE, Align.left)
                    text(l2[i], 36f, y - 68f, 0.85f, Color.LIGHT_GRAY, Align.left)
                }
            }
            State.AI_SETUP -> {
                text("AI COMPETITION", cx, vh - 30f, 1.8f, Color.WHITE)
                text("Vehicle: ${veh.name}   Track: ${TRACKS[selT].name}", cx, vh - 100f, 1.0f, Color.LIGHT_GRAY)
                text("DIFFICULTY", cx, vh * 0.66f + 84f, 1.2f, CYAN)
                text(diff.blurb, cx, vh * 0.66f - 90f, 1.0f, Color.WHITE)
                text("YOUR RIVALS", cx, 440f, 1.1f, CYAN)
                for (i in 0 until 3) text("${rivalNames[i]}\n${rivalVehicle(i).name}", cx + (i - 1) * 120f, 300f, 0.85f, Color.WHITE)
            }
            State.SEL_VEHICLE -> {
                val v = VEHICLES[vIdx]; val py = vh * 0.62f
                text("CHOOSE VEHICLE", cx, vh - 30f, 1.8f, Color.WHITE)
                text("COINS $totalCoins", vw - 14f, vh - 36f, 1.2f, YELLOW, Align.right)
                text(v.name.uppercase(), cx, py + 160f, 2.2f, YELLOW)
                text(v.desc, cx, py - 140f, 0.95f, Color.LIGHT_GRAY)
                text("SPEED", 40f, vh * 0.36f + 16f, 1.0f, Color.WHITE, Align.left)
                text("ACCEL", 40f, vh * 0.36f - 19f, 1.0f, Color.WHITE, Align.left)
                text("HANDLING", 40f, vh * 0.36f - 54f, 1.0f, Color.WHITE, Align.left)
            }
            State.SEL_TRACK -> {
                val t = TRACKS[tIdx]
                val panelY = vh * 0.40f; val panelH = vh * 0.30f
                text("CHOOSE TRACK", cx, vh - 30f, 1.8f, Color.WHITE)
                text("COINS $totalCoins", vw - 14f, vh - 36f, 1.2f, YELLOW, Align.right)
                centerText(t.name.uppercase(), cx, panelY + panelH + 75f, 1.8f, YELLOW)
                text(t.blurb, cx, panelY - 18f, 1.0f, Color.LIGHT_GRAY)
                text("MISSION: ${MISSIONS.getValue(t.id).title}", cx, panelY - 42f, 0.95f, CYAN)
                val b = bestOf(t.id)
                text(if (b > 0f) "BEST  ${fmt(b)}" else "No best time yet", cx, panelY - 68f, 1.2f, Color.WHITE)
            }
            State.MISSIONS -> {
                text("MISSIONS", cx, vh - 30f, 1.8f, Color.WHITE)
                text("DAILY CHALLENGE", 24f, vh - 100f, 1.2f, CYAN, Align.left)
                text(dailyText(), 24f, vh - 130f, 1.0f, Color.WHITE, Align.left)
                text(if (dailyClaimed) "DONE  +$DAILY_REWARD coins" else "$dailyProg/${dailyTarget()}  reward $DAILY_REWARD",
                    vw - 24f, vh - 130f, 1.0f, if (dailyClaimed) LIME else YELLOW, Align.right)
                for (i in TRACKS.indices) {
                    val t = TRACKS[i]
                    val y = vh - 215f - i * 62f
                    val done = t.id in mdone
                    text("${i + 1}. ${t.name}${if (t.id in ownedT) "" else " (locked)"}", 24f, y, 1.2f, YELLOW, Align.left)
                    text(MISSIONS.getValue(t.id).title, 24f, y - 24f, 0.95f, Color.LIGHT_GRAY, Align.left)
                    text(if (done) "DONE" else "TO DO", vw - 24f, y, 1.2f, if (done) LIME else Color.LIGHT_GRAY, Align.right)
                }
                text("Every race has a mission. Finish it for bonus coins.", cx, 135f, 0.9f, Color.LIGHT_GRAY)
            }
            State.PROFILE -> {
                text("PROFILE", cx, vh - 30f, 1.8f, Color.WHITE)
                val stats = listOf("Coins: $totalCoins", "Points: $points", "Races finished: $races",
                    "Missions completed: $missionsDone", "Coins collected (all time): $coinsLife", "AI races won: $aiWins")
                for (i in stats.indices) text(stats[i], 24f, vh - 100f - i * 28f, 1.1f, Color.WHITE, Align.left)
                text("ACHIEVEMENTS  ${ach.size}/${ACHIEVEMENTS.size}", 24f, vh - 285f, 1.2f, CYAN, Align.left)
                for (i in ACHIEVEMENTS.indices) {
                    val a = ACHIEVEMENTS[i]
                    val got = a.id in ach
                    text("${if (got) "[x]" else "[ ]"} ${a.name} - ${a.desc}", 24f, vh - 320f - i * 26f, 0.85f,
                        if (got) LIME else Color.LIGHT_GRAY, Align.left)
                }
            }
            State.MP_MENU -> textMpMenu()
            State.BT_FIND -> textBtFind()
            State.MP_LOBBY -> textLobby()
            State.MP_RESULT -> textResult()
            State.LEADERBOARD -> textLeaderboard()
            State.COUNTDOWN -> {
                text("${max(1, ceil(countdownT).toInt())}", cx, vh * 0.6f, 6f, Color.WHITE)
                hints()
            }
            State.RACE -> {
                if (goT > 0f) text("GO!", cx, vh * 0.6f, 5f, LIME)
                if (lapMsgT > 0f) text(if (lap == LAPS) "FINAL LAP!" else "LAP $lap", cx, vh * 0.62f, 3f, YELLOW)
                if (raceT < 4f) hints()
            }
            State.PAUSED -> text("PAUSED", cx, vh * 0.6f, 3.2f, Color.WHITE)
            State.FINISH -> {
                text("FINISH!", cx, vh * 0.93f, 3.0f, YELLOW)
                var y = vh * 0.85f
                for (p in resultLines) { text(p.first, cx, y, 1.0f, p.second); y -= 30f }
                if (aiMode) {
                    y -= 8f
                    text("STANDINGS", cx, y, 1.1f, CYAN); y -= 28f
                    class Row(val name: String, val t: Float, val d: Float)
                    val rows = ArrayList<Row>()
                    rows.add(Row("YOU", raceT, Float.MAX_VALUE))
                    for (a in ais) rows.add(Row(a.name, a.finishT, a.dist))
                    rows.sortWith(compareBy<Row> { if (it.t >= 0f) 0 else 1 }.thenBy { if (it.t >= 0f) it.t else -it.d })
                    for ((i, r) in rows.withIndex()) {
                        val s = if (r.t >= 0f) fmt(r.t) else "racing..."
                        text("${i + 1}. ${r.name}   $s", cx, y, 1.0f, if (r.name == "YOU") YELLOW else Color.WHITE)
                        y -= 26f
                    }
                }
            }
            else -> {}
        }
        for (b in buttons) if (b.label.isNotEmpty())
            centerText(b.label, b.x + b.w / 2f, b.y + b.h / 2f, if (b.label.length <= 1) 2.2f else 1.3f, Color.WHITE)
        if (toastT > 0f) text(toastText, cx, vh - 70f, 1.3f, YELLOW)
    }


    // ================= multiplayer (Bluetooth and online) =================
    private class RB(var name: String) { var pts = 0; var wins = 0; var best = -1f; var coins = 0 }

    private val pointsTable = intArrayOf(10, 7, 5, 3, 2, 1)
    private val rankCoins = intArrayOf(80, 50, 30, 15)

    private fun serverUrl() = Config.cleanUrl(prefs.getString("fbUrl", Config.DEFAULT_FIREBASE_URL))
    private fun hasServer() = serverUrl().startsWith("https://")

    private fun ask(title: String, def: String, cb: (String) -> Unit) {
        Gdx.input.getTextInput(object : Input.TextInputListener {
            override fun input(text: String?) { Gdx.app.postRunnable { cb(text ?: "") } }
            override fun canceled() {}
        }, title, def, "")
    }

    private fun saveName(t: String): Boolean {
        val n = cleanName(t)
        if (n.isEmpty()) { toast("Please type a name"); return false }
        playerName = n; prefs.putString("playerName", n); prefs.flush()
        return true
    }

    private fun ensureName(next: () -> Unit) {
        if (playerName.isNotEmpty()) { next(); return }
        ask("Your player name", "") { t -> if (saveName(t)) next() }
    }

    private fun changeName() { ask("Your player name", playerName) { t -> if (saveName(t)) toast("Name saved") } }

    private fun setServer() {
        ask("Firebase database URL", serverUrl().ifEmpty { "https://" }) { t ->
            val u = Config.cleanUrl(t)
            if (u.startsWith("https://")) { prefs.putString("fbUrl", u); prefs.flush(); toast("Server saved") }
            else toast("The URL must start with https://")
        }
    }

    private fun openMp(kind: String) {
        if (kind == "BT" && !platform.bluetoothAvailable()) { toast("This phone has no Bluetooth"); return }
        mpKind = kind
        go(State.MP_MENU)
    }

    private fun newMe() = MpPlayer(playerId, playerName, selV)

    private fun beginSession(s: MpSession) {
        mp = s; roomBoard.clear(); seenStartId = s.room.startId; roundFinal = false; lastSig = ""
        go(State.MP_LOBBY)
    }

    private fun startBtHost() {
        platform.prepareBluetooth(true) { ok, msg ->
            if (!ok) { toast(msg); return@prepareBluetooth }
            val s = BluetoothSession(platform.context, newMe())
            s.startHost()
            beginSession(s)
        }
    }

    private fun openFind() {
        platform.prepareBluetooth(false) { ok, msg ->
            if (!ok) { toast(msg); return@prepareBluetooth }
            scanner?.stop()
            scanner = BtScanner(platform.context).also { it.start() }
            btListSize = -1
            go(State.BT_FIND)
        }
    }

    private fun connectTo(d: BluetoothDevice) {
        scanner?.stop(); scanner = null
        val s = BluetoothSession(platform.context, newMe())
        s.connect(d)
        beginSession(s)
    }

    private fun startOnlineHost() {
        if (!hasServer()) { toast("Set the server first"); return }
        val s = OnlineSession(serverUrl(), newMe(), true, null)
        s.start()
        beginSession(s)
    }

    private fun joinOnline(code: String) {
        if (!hasServer()) { toast("Set the server first"); return }
        val c = code.trim().uppercase()
        if (c.length != 4) { toast("Room codes have 4 characters"); return }
        val s = OnlineSession(serverUrl(), newMe(), false, c)
        s.start()
        beginSession(s)
    }

    private fun askJoinCode() { ask("Enter the room code", "") { t -> joinOnline(t) } }

    private fun leaveMp(msg: String? = null) {
        mp?.close(); mp = null
        scanner?.stop(); scanner = null
        roomBoard.clear()
        track = Track(TRACKS[selT]); refreshBest()
        go(State.MODE)
        if (msg != null) toast(msg)
    }

    private fun leaveRacePrompt() {
        if (backPressT > 0f) leaveMp("You left the race")
        else { backPressT = 2f; toast("Press back again to leave the race") }
    }

    private fun backToLobby() {
        val m = mp ?: return
        if (m.isHost && m.room.status == "race") m.hostToLobby()
        go(State.MP_LOBBY)
    }

    private fun nextOwned(cur: Int, d: Int): Int {
        var i = cur
        repeat(VEHICLES.size) {
            i = (i + d + VEHICLES.size) % VEHICLES.size
            if (VEHICLES[i].id in ownedV) return i
        }
        return cur
    }

    private fun lobbyVeh(d: Int) {
        val m = mp ?: return
        m.me.veh = nextOwned(m.me.veh, d); selV = m.me.veh
        saveProgress(); m.pushNow(); rebuild()
    }

    private fun toggleReady() {
        val m = mp ?: return
        m.me.ready = !m.me.ready; m.pushNow(); rebuild()
    }

    private fun canStart(): Boolean {
        val m = mp ?: return false
        return m.room.players.size >= 2 && m.room.players.values.all { it.id == m.me.id || it.ready }
    }

    private fun hostStartRace() {
        val m = mp ?: return
        if (!canStart()) { toast(if (m.room.players.size < 2) "Wait for a friend to join" else "Everyone must press READY"); return }
        m.hostStart(m.room.track)
    }

    private fun lobbyTrack(d: Int) {
        val m = mp ?: return
        m.hostSetTrack((m.room.track + d + TRACKS.size) % TRACKS.size)
        rebuild()
    }

    private fun invite() {
        val m = mp ?: return
        val code = m.room.code
        platform.share(
            if (mpKind == "ONLINE") "Join my Auto Rally race! Room code: $code\nOr open this link: autorally://join/$code"
            else "Join my Auto Rally game over Bluetooth! Open Auto Rally > Play > Bluetooth Multiplayer > Find a Game. Room code: $code"
        )
    }

    private fun lobbySig(m: MpSession): String =
        "${m.ready}${m.room.track}${m.room.hostId}" + m.room.players.values.joinToString(",") { "${it.id}${it.veh}${it.ready}" }

    private fun updateMp(dt: Float) {
        backPressT -= dt
        val join = pendingJoinCode
        if (join != null && mp == null && !inRace()) {
            pendingJoinCode = null
            mpKind = "ONLINE"
            ensureName { joinOnline(join) }
        }
        if (state == State.BT_FIND) {
            val n = scanner?.named()?.size ?: 0
            val sc = scanner?.scanning ?: false
            if (n != btListSize || sc != btScanning) { btListSize = n; btScanning = sc; rebuild() }
        }
        val m = mp ?: return
        if (state == State.RACE) {
            val me = m.me
            me.dist = min(dist, LAPS * track.lapLen); me.off = px - (vw / 2f + track.curve(dist))
            me.spd = speed; me.boost = boostT > 0f; me.coins = coins
        }
        m.tick(dt, state == State.RACE || state == State.COUNTDOWN || state == State.MP_RESULT)
        val err = m.error
        if (err != null) { leaveMp(err); return }
        val r = m.room
        if (r.status == "race" && r.startId != seenStartId && (state == State.MP_LOBBY || state == State.MP_RESULT)) {
            seenStartId = r.startId
            startMpRace()
            return
        }
        if (state == State.MP_LOBBY) {
            val sig = lobbySig(m)
            if (sig != lastSig) { lastSig = sig; rebuild() }
        }
        for (p in r.players.values) updateRemote(p, dt)
        if (state == State.MP_RESULT && !roundFinal) checkRoundDone()
    }

    private fun updateRemote(p: MpPlayer, dt: Float) {
        val r = mp?.room ?: return
        if (p.id == playerId || p.rid != r.startId) return
        val total = LAPS * track.lapLen
        val target = if (p.fin >= 0f) total else p.dist
        p.vDist += p.spd * dt
        p.vDist += (target - p.vDist) * min(1f, dt * 6f)
        if (p.vDist > total) p.vDist = total
        p.vOff += (p.off - p.vOff) * min(1f, dt * 8f)
    }

    private fun startMpRace() {
        val m = mp ?: return
        track = Track(TRACKS[m.room.track])
        dist = 0f; speed = 0f; coins = 0; crashes = 0; boostsGot = 0; boostT = 0f; hitT = 0f
        raceT = 0f; postT = 0f; lap = 1; steerVis = 0f
        px = vw / 2f + track.curve(0f)
        countdownT = 3f; lastCount = 4; newBest = false
        evs.clear(); ais = ArrayList(); aiMode = false; bannerT = 0f
        val me = m.me
        me.rid = m.room.startId; me.fin = -1f; me.dist = 0f; me.off = 0f; me.spd = 0f
        me.boost = false; me.coins = 0; me.ready = false
        for (p in m.room.players.values) { p.vDist = 0f; p.vOff = 0f }
        roundFinal = false
        m.pushNow()
        go(State.COUNTDOWN)
    }

    private fun finishMpRace() {
        val m = mp ?: return
        val total = LAPS * track.lapLen
        dist = total - 1f
        val me = m.me
        me.fin = raceT; me.dist = total; me.coins = coins; me.spd = 0f
        m.pushNow()
        mpFinishClock = clock
        roundFinal = false
        mpRewardLines = ArrayList()
        sfx.play(sfx.win)
        go(State.MP_RESULT)
    }

    private fun minTime(trackIdx: Int) = LAPS * TRACKS[trackIdx].lapLen / (VEHICLES.maxOf { it.maxSpeed } * 1.5f) * 0.8f

    /** A finish time counts only if it belongs to this race and is humanly possible. */
    private fun validFin(m: MpSession, p: MpPlayer) = p.rid == m.room.startId && p.fin >= minTime(m.room.track)

    private fun standings(m: MpSession): List<MpPlayer> = m.room.players.values.sortedWith(
        compareBy<MpPlayer> { if (validFin(m, it)) 0 else if (it.fin == -2f) 2 else 1 }
            .thenBy { if (validFin(m, it)) it.fin else -it.vDist }
    )

    private fun checkRoundDone() {
        val m = mp ?: return
        val allDone = m.room.players.values.all { it.fin == -2f || (it.rid == m.room.startId && it.fin >= 0f) }
        if (allDone || clock - mpFinishClock > 25f) finalizeRound(m)
    }

    private fun finalizeRound(m: MpSession) {
        roundFinal = true
        val st = standings(m)
        val n = st.size
        var myRank = n
        for ((i, p) in st.withIndex()) {
            val ok = validFin(m, p)
            val rb = roomBoard.getOrPut(p.id) { RB(p.name) }
            rb.name = p.name
            if (ok) {
                rb.pts += pointsTable[min(i, 5)]
                if (i == 0 && n > 1) rb.wins++
                if (rb.best < 0f || p.fin < rb.best) rb.best = p.fin
            }
            rb.coins += p.coins
            if (p.id == playerId) myRank = i + 1
        }
        val me = m.me
        val ok = validFin(m, me)
        val tr = TRACKS[m.room.track]
        val myPts = if (ok) pointsTable[min(myRank - 1, 5)] else 0
        val rankBonus = if (ok) rankCoins[min(myRank - 1, 3)] else 0
        val gain = if (ok) me.coins + rankBonus + 20 else me.coins
        totalCoins += gain; points += myPts * 10
        races++; coinsLife += me.coins
        if (ok && crashes == 0) cleanRaces++
        history.add("${if (mpKind == "ONLINE") "ONLINE" else "BLUETOOTH"}|${m.room.code}|$myRank/$n|${if (ok) fmt(me.fin) else "DNF"}|${tr.name}|$myPts")
        while (history.size > 20) history.removeAt(0)
        if (mpKind == "ONLINE" && ok && hasServer()) {
            val url = serverUrl(); val tid = tr.id; val t = me.fin; val name = playerName; val id = playerId
            Thread { try { Leaderboards.submit(url, tid, id, name, t) } catch (e: Exception) {} }.apply { isDaemon = true; start() }
        }
        val newAch = checkAchievements()
        saveProgress()
        val lines = ArrayList<Pair<String, Color>>()
        lines.add(if (ok) Pair("You finished #$myRank of $n", if (myRank == 1) LIME else Color.WHITE)
                  else Pair("Did not finish - no rank reward", RED))
        lines.add(Pair("Coins +$gain   Points +${myPts * 10}", YELLOW))
        if (newAch.isNotEmpty()) lines.add(Pair("ACHIEVEMENT: " + newAch.joinToString(", "), CYAN))
        mpRewardLines = lines
        if (ok && myRank == 1 && n > 1) confettiAt = clock
    }

    private fun mpPosition(): Int {
        val r = mp?.room ?: return 1
        return 1 + r.players.values.count { it.id != playerId && it.rid == r.startId && it.fin != -2f && it.vDist > dist }
    }

    private fun mpActive(): Int {
        val r = mp?.room ?: return 1
        return r.players.values.count { it.id == playerId || (it.rid == r.startId && it.fin != -2f) }
    }

    private fun drawRemotes(cx: Float) {
        val r = mp?.room ?: return
        for (p in r.players.values) {
            if (p.id == playerId || p.rid != r.startId || p.fin == -2f) continue
            val y = PLAYER_Y + (p.vDist - dist)
            if (y < -70f || y > vh + 70f) continue
            vr.draw(VEHICLES[p.veh.coerceIn(0, VEHICLES.size - 1)], cx + track.curve(p.vDist) + p.vOff, y, 1f, 0f,
                abs(p.vDist), clock, p.boost, false)
        }
    }

    private fun loadGlobal() {
        if (!hasServer()) { globalMsg = "Set the server (Online Multiplayer > SET SERVER) to see global times"; globalRows = emptyList(); return }
        globalMsg = "Loading..."; globalRows = emptyList()
        val url = serverUrl(); val tid = TRACKS[lbTrack].id
        Thread {
            try {
                val rows = Leaderboards.fetch(url, tid)
                Gdx.app.postRunnable {
                    if (TRACKS[lbTrack].id == tid) { globalRows = rows; globalMsg = if (rows.isEmpty()) "No online times yet" else "" }
                }
            } catch (e: Exception) {
                Gdx.app.postRunnable { globalMsg = "Could not load. Check the internet and the server URL." }
            }
        }.apply { isDaemon = true; start() }
    }

    // ---------- multiplayer screens: shapes ----------
    private fun drawLobbyShapes() {
        val m = mp ?: return
        for ((i, p) in m.room.players.values.withIndex()) {
            val yb = vh - 160f - i * 44f
            shapes.setColor(0.17f, 0.21f, 0.36f, 1f); shapes.rect(12f, yb, vw - 24f, 40f)
            if (p.id == m.me.id) { shapes.color = YELLOW; shapes.rect(12f, yb, 4f, 40f) }
            vr.draw(VEHICLES[p.veh.coerceIn(0, VEHICLES.size - 1)], 46f, yb + 20f, 0.42f, 0f, clock * 60f, clock, false, false)
        }
        shapes.setColor(0.17f, 0.21f, 0.36f, 1f)
        shapes.rect(76f, 316f, vw - 152f, 50f)
        shapes.rect(76f, 252f, vw - 152f, 50f)
    }

    // ---------- multiplayer screens: text ----------
    private fun textMpMenu() {
        val cx = vw / 2f
        val bt = mpKind == "BT"
        text(if (bt) "BLUETOOTH MULTIPLAYER" else "ONLINE MULTIPLAYER", cx, vh - 30f, 1.5f, Color.WHITE)
        text(if (bt) "Race friends nearby. No internet or Wi-Fi needed." else "Race friends anywhere. Needs internet.",
            cx, vh - 110f, 0.95f, Color.LIGHT_GRAY)
        text("Playing as: ${if (playerName.isEmpty()) "(you will be asked)" else playerName}", cx, vh - 140f, 1.0f, CYAN)
        if (bt) text("Both phones need Bluetooth on. The host taps HOST, friends tap FIND.", cx, vh * 0.60f + 100f, 0.85f, Color.LIGHT_GRAY)
        else text(if (hasServer()) "Server is set." else "Server not set. Tap SET SERVER first.", cx, vh * 0.60f + 100f, 0.95f,
            if (hasServer()) LIME else YELLOW)
    }

    private fun textBtFind() {
        val cx = vw / 2f
        text("FIND A GAME", cx, vh - 30f, 1.8f, Color.WHITE)
        text(if (scanner?.scanning == true) "Scanning... tap the phone that is hosting" else "Tap the phone that is hosting, or scan again",
            cx, vh - 100f, 0.95f, Color.LIGHT_GRAY)
        if (btListSize <= 0) text("No devices yet. Your friend must tap HOST A GAME.", cx, vh - 200f, 0.95f, YELLOW)
    }

    private fun textLobby() {
        val m = mp ?: return
        val cx = vw / 2f
        val r = m.room
        text(if (m.ready) "LOBBY" else "CONNECTING...", cx, vh - 30f, 1.6f, Color.WHITE)
        if (m.ready) text("ROOM CODE  ${r.code}", cx, vh - 62f, 2.0f, YELLOW)
        text(if (mpKind == "BT") "Bluetooth game" else "Online game  -  round ${r.round}", cx, vh - 108f, 0.9f, Color.LIGHT_GRAY)
        for ((i, p) in r.players.values.withIndex()) {
            val yb = vh - 160f - i * 44f
            val me = p.id == m.me.id
            text(p.name + if (me) " (you)" else "", 76f, yb + 34f, 1.0f, if (me) YELLOW else Color.WHITE, Align.left)
            text(VEHICLES[p.veh.coerceIn(0, VEHICLES.size - 1)].name, 76f, yb + 16f, 0.75f, Color.LIGHT_GRAY, Align.left)
            val host = p.id == r.hostId
            text(if (host) "HOST" else if (p.ready) "READY" else "waiting", vw - 24f, yb + 26f, 0.95f,
                if (host) CYAN else if (p.ready) LIME else Color.LIGHT_GRAY, Align.right)
        }
        centerText("MY VEHICLE: ${VEHICLES[m.me.veh.coerceIn(0, VEHICLES.size - 1)].name.uppercase()}", cx, 341f, 1.0f, Color.WHITE)
        centerText("TRACK: ${TRACKS[r.track.coerceIn(0, TRACKS.size - 1)].name.uppercase()}", cx, 277f, 1.0f, Color.WHITE)
        if (!m.isHost && m.ready) text("The host picks the track and starts the race.", cx, 242f, 0.8f, Color.LIGHT_GRAY)
    }

    private fun textResult() {
        val m = mp ?: return
        val cx = vw / 2f
        text("ROUND ${m.room.round} RESULTS", cx, vh - 30f, 1.6f, Color.WHITE)
        text(TRACKS[m.room.track.coerceIn(0, TRACKS.size - 1)].name, cx, vh - 62f, 1.0f, Color.LIGHT_GRAY)
        text("PLAYER", 20f, vh - 92f, 0.85f, CYAN, Align.left)
        text("TIME", 290f, vh - 92f, 0.85f, CYAN, Align.right)
        text("COINS", 365f, vh - 92f, 0.85f, CYAN, Align.right)
        text("PTS", 450f, vh - 92f, 0.85f, CYAN, Align.right)
        for ((i, p) in standings(m).withIndex()) {
            val y = vh - 118f - i * 24f
            val ok = validFin(m, p)
            val col = if (p.id == playerId) YELLOW else Color.WHITE
            text("${i + 1}. ${p.name}", 20f, y, 0.95f, col, Align.left)
            text(when {
                ok -> fmt(p.fin)
                p.fin == -2f -> "LEFT"
                p.rid == m.room.startId && p.fin >= 0f -> "INVALID"
                else -> "racing..."
            }, 290f, y, 0.9f, col, Align.right)
            text("${p.coins}", 365f, y, 0.9f, col, Align.right)
            text(if (ok) "${pointsTable[min(i, 5)]}" else "0", 450f, y, 0.9f, col, Align.right)
        }
        val by = vh - 284f
        text("ROOM LEADERBOARD", 20f, by, 1.1f, CYAN, Align.left)
        text("PLAYER", 20f, by - 24f, 0.8f, Color.LIGHT_GRAY, Align.left)
        text("PTS", 300f, by - 24f, 0.8f, Color.LIGHT_GRAY, Align.right)
        text("WINS", 370f, by - 24f, 0.8f, Color.LIGHT_GRAY, Align.right)
        text("BEST", 450f, by - 24f, 0.8f, Color.LIGHT_GRAY, Align.right)
        val rows = roomBoard.values.sortedWith(compareByDescending<RB> { it.pts }.thenBy { if (it.best < 0f) Float.MAX_VALUE else it.best })
        for ((i, rb) in rows.withIndex()) {
            val y = by - 48f - i * 22f
            text("${i + 1}. ${rb.name}", 20f, y, 0.9f, Color.WHITE, Align.left)
            text("${rb.pts}", 300f, y, 0.9f, Color.WHITE, Align.right)
            text("${rb.wins}", 370f, y, 0.9f, Color.WHITE, Align.right)
            text(if (rb.best >= 0f) fmt(rb.best) else "--", 450f, y, 0.9f, Color.WHITE, Align.right)
        }
        if (!roundFinal) text("Waiting for the other racers...", cx, 336f, 1.0f, YELLOW)
        else for ((i, l) in mpRewardLines.withIndex()) text(l.first, cx, 336f - i * 24f, 1.0f, l.second)
    }

    private fun textLeaderboard() {
        val cx = vw / 2f
        text("LEADERBOARD", cx, vh - 30f, 1.8f, Color.WHITE)
        if (lbTab == 0) {
            text("BEST TIMES ON THIS PHONE", 20f, vh - 175f, 1.1f, CYAN, Align.left)
            for ((i, t) in TRACKS.withIndex()) {
                val b = bestOf(t.id)
                text(t.name, 20f, vh - 205f - i * 24f, 0.95f, Color.WHITE, Align.left)
                text(if (b > 0f) fmt(b) else "--", vw - 20f, vh - 205f - i * 24f, 0.95f, Color.WHITE, Align.right)
            }
            text("MULTIPLAYER RACES", 20f, vh - 340f, 1.1f, CYAN, Align.left)
            if (history.isEmpty()) text("No multiplayer races yet.", 20f, vh - 370f, 0.9f, Color.LIGHT_GRAY, Align.left)
            for ((i, h) in history.asReversed().take(10).withIndex()) {
                val f = h.split("|")
                if (f.size >= 6) text("${f[0]} ${f[1]}  #${f[2]}  ${f[3]}  ${f[4]}", 20f, vh - 370f - i * 24f, 0.8f, Color.LIGHT_GRAY, Align.left)
            }
        } else {
            centerText(TRACKS[lbTrack].name.uppercase(), cx, vh - 193f, 1.3f, YELLOW)
            if (globalMsg.isNotEmpty()) text(globalMsg, cx, vh - 290f, 0.9f, Color.LIGHT_GRAY)
            for ((i, r) in globalRows.withIndex()) {
                text("${i + 1}. ${r.first}", 30f, vh - 250f - i * 26f, 1.0f, if (r.first == playerName) YELLOW else Color.WHITE, Align.left)
                text(fmt(r.second), vw - 30f, vh - 250f - i * 26f, 1.0f, Color.WHITE, Align.right)
            }
        }
    }

    private fun hints() {
        text("< TOUCH LEFT", vw * 0.27f, 80f, 1.1f, Color.WHITE)
        text("TOUCH RIGHT >", vw * 0.73f, 80f, 1.1f, Color.WHITE)
        text("Stay on the road, grab coins, dodge cones!", vw / 2f, 120f, 1.0f, Color.LIGHT_GRAY)
    }

    override fun dispose() {
        mp?.close(); scanner?.stop()
        shapes.dispose(); batch.dispose(); font.dispose(); sfx.dispose()
    }
}
