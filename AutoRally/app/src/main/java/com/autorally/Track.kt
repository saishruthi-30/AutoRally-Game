package com.autorally

import com.badlogic.gdx.graphics.Color
import java.util.Random
import kotlin.math.PI
import kotlin.math.sin

const val LAPS = 3

enum class Kind { CONE, BARREL, COIN, BOOST }

/** s = distance along the lap, off = sideways offset from road centre. */
class Item(val s: Float, val off: Float, val kind: Kind) {
    var taken = false
}

/** Everything that makes a track unique. Add a new TrackDef to TRACKS to add a track. */
class TrackDef(
    val id: String, val name: String, val blurb: String, val difficulty: Int, val price: Int,
    val grass1: Color, val grass2: Color, val road: Color, val rumbleA: Color, val rumbleB: Color,
    val roadHalf: Float, val lapLen: Float,
    val a1: Float, val f1: Int, val a2: Float, val f2: Int,   // road curve: two sine waves
    val obsPct: Int, val coinPct: Int, val gatePct: Int,       // obstacle / coin-line / obstacle-gate chances
    val decor: List<DKind>, val decorGap: Float, val seed: Long
)

private val WHITE = Color(1f, 1f, 1f, 1f)
private val RED = Color(0.85f, 0.2f, 0.2f, 1f)

val TRACKS = listOf(
    TrackDef("village", "Village Road", "Dusty lanes, huts and haystacks", 1, 0,
        Color(0.38f, 0.66f, 0.30f, 1f), Color(0.34f, 0.60f, 0.27f, 1f), Color(0.55f, 0.45f, 0.32f, 1f),
        Color(0.78f, 0.68f, 0.48f, 1f), Color(0.62f, 0.52f, 0.36f, 1f),
        125f, 6000f, 70f, 2, 30f, 5, 25, 40, 0, listOf(DKind.TREE, DKind.HUT, DKind.HAY), 120f, 11L),
    TrackDef("country", "Countryside", "Golden fields and rolling bends", 2, 80,
        Color(0.55f, 0.72f, 0.28f, 1f), Color(0.50f, 0.67f, 0.25f, 1f), Color(0.28f, 0.30f, 0.34f, 1f),
        RED, WHITE,
        115f, 6500f, 90f, 2, 35f, 4, 32, 38, 0, listOf(DKind.TREE, DKind.HAY), 110f, 22L),
    TrackDef("city", "City Streets", "Tall blocks and tight corners", 3, 160,
        Color(0.42f, 0.44f, 0.48f, 1f), Color(0.38f, 0.40f, 0.44f, 1f), Color(0.15f, 0.16f, 0.20f, 1f),
        Color(0.95f, 0.80f, 0.10f, 1f), Color(0.12f, 0.12f, 0.14f, 1f),
        110f, 6000f, 60f, 3, 30f, 7, 35, 35, 15, listOf(DKind.BUILDING, DKind.LAMP), 100f, 33L),
    TrackDef("highway", "Highway", "Wide, fast and full of roadworks", 4, 250,
        Color(0.62f, 0.62f, 0.45f, 1f), Color(0.58f, 0.58f, 0.42f, 1f), Color(0.20f, 0.20f, 0.22f, 1f),
        WHITE, Color(0.55f, 0.55f, 0.58f, 1f),
        140f, 8000f, 40f, 1, 15f, 3, 42, 30, 25, listOf(DKind.SIGN, DKind.LAMP), 160f, 44L),
    TrackDef("mountain", "Mountain Pass", "Narrow, twisty and rocky", 5, 400,
        Color(0.50f, 0.50f, 0.46f, 1f), Color(0.45f, 0.45f, 0.42f, 1f), Color(0.30f, 0.30f, 0.32f, 1f),
        RED, WHITE,
        100f, 5500f, 90f, 3, 35f, 5, 40, 30, 25, listOf(DKind.ROCK, DKind.PINE), 90f, 55L)
)

class Track(val def: TrackDef) {
    val lapLen = def.lapLen
    val items = ArrayList<Item>()
    val decor = ArrayList<Decor>()

    /** Sideways road centre at distance s. Periodic in lapLen, so the road loops seamlessly. */
    fun curve(s: Float): Float {
        val t = (s / lapLen) * 2f * PI.toFloat()
        return def.a1 * sin(def.f1 * t) + def.a2 * sin(def.f2 * t + 1f)
    }

    init {
        val r = Random(def.seed)
        var s = 500f
        while (s < lapLen - 400f) {
            val roll = r.nextInt(100)
            if (roll < def.obsPct) {
                if (r.nextInt(100) < def.gatePct) { // two obstacles with a gap in the middle
                    val o = def.roadHalf * 0.45f
                    items.add(Item(s, -o, Kind.CONE)); items.add(Item(s, o, Kind.BARREL))
                } else {
                    val off = (r.nextFloat() * 2f - 1f) * (def.roadHalf - 35f)
                    items.add(Item(s, off, if (r.nextBoolean()) Kind.CONE else Kind.BARREL))
                }
            } else if (roll < def.obsPct + def.coinPct) {
                val off = (r.nextFloat() * 2f - 1f) * (def.roadHalf - 45f)
                for (k in 0 until 5) items.add(Item(s + k * 45f, off, Kind.COIN))
                s += 200f
            }
            s += 170f
        }
        for (f in floatArrayOf(0.25f, 0.5f, 0.75f)) items.add(Item(lapLen * f, 0f, Kind.BOOST))

        var d = 0f
        while (d < lapLen) {
            for (side in intArrayOf(-1, 1)) {
                val kind = def.decor[r.nextInt(def.decor.size)]
                var off = def.roadHalf + 30f + r.nextFloat() * 70f
                if (kind == DKind.BUILDING) off += 30f
                decor.add(Decor(d + r.nextFloat() * 40f, side * off, kind, r.nextInt(6)))
            }
            d += def.decorGap
        }
    }

    fun resetLap() = items.forEach { it.taken = false }
}
