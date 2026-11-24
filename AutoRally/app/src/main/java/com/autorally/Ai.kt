package com.autorally

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * A computer-controlled racer. It drives the real track: it looks ahead for obstacles and steers
 * around them, goes for coins and boosts, overtakes slower cars, and (below Expert) sometimes
 * makes mistakes. off = sideways position relative to the road centre.
 */
class Ai(val name: String, val v: VehicleDef, private val lanePref: Float, private val greed: Float) {
    var dist = 0f
    var off = 0f
    var speed = 0f
    var hitT = 0f
    var boostT = 0f
    var steerVis = 0f
    var finishT = -1f
    var coins = 0
    private var seen: Item? = null
    private var ignore = false
    private val rnd = java.util.Random(name.hashCode().toLong())

    private fun wrap(tr: Track, d: Float): Float {
        var x = d
        if (x < -tr.lapLen / 2f) x += tr.lapLen
        if (x > tr.lapLen / 2f) x -= tr.lapLen
        return x
    }

    fun update(dt: Float, tr: Track, diff: Diff, pDist: Float, pOff: Float, all: List<Ai>) {
        val td = tr.def
        val pos = dist % tr.lapLen
        val maxOff = td.roadHalf - 16f
        var target = lanePref * td.roadHalf * 0.7f
        var conflict = false
        var nearest = Float.MAX_VALUE
        var coinItem: Item? = null
        var coinD = Float.MAX_VALUE
        var boostItem: Item? = null

        for (item in tr.items) {
            val d = wrap(tr, item.s - pos)
            if (d < 0f || d > diff.look) continue
            when (item.kind) {
                Kind.CONE, Kind.BARREL -> {
                    val gap = 38f + v.halfW
                    if (abs(item.off - off) < 36f + v.halfW && d < nearest) {
                        nearest = d; conflict = true
                        if (seen !== item) { seen = item; ignore = rnd.nextFloat() < diff.err }
                        val l = item.off - gap
                        val r = item.off + gap
                        val pick = when {
                            l < -maxOff -> r
                            r > maxOff -> l
                            abs(l - off) < abs(r - off) -> l
                            else -> r
                        }
                        if (!ignore) target = pick.coerceIn(-maxOff, maxOff)
                    }
                }
                Kind.COIN -> if (!item.taken && d < coinD && abs(item.off - off) < 80f * greed) { coinD = d; coinItem = item }
                Kind.BOOST -> if (!item.taken && boostItem == null) boostItem = item
            }
        }
        val b = boostItem
        val c = coinItem
        if (!conflict) {
            if (b != null) target = b.off else if (c != null) target = c.off
            // overtaking: move out from behind a slower car
            fun consider(od: Float, oo: Float) {
                val gap = od - dist
                if (gap in 0f..110f && abs(oo - off) < 30f)
                    target = (off + (if (off >= oo) 48f else -48f)).coerceIn(-maxOff, maxOff)
            }
            consider(pDist, pOff)
            for (o in all) if (o !== this) consider(o.dist, o.off)
        }

        val step = v.handling * 0.75f * dt
        val delta = target - off
        off = (off + delta.coerceIn(-step, step)).coerceIn(-maxOff, maxOff)
        steerVis += (delta.coerceIn(-1f, 1f) - steerVis) * min(1f, dt * 8f)

        // gentle rubber-banding (not on Expert) so races stay close and fun
        val rb = if (diff == Diff.EXPERT) 1f else 1f + ((pDist - dist) / 4000f).coerceIn(-0.05f, 0.07f)
        val top = (if (boostT > 0f) v.maxSpeed * 1.5f else v.maxSpeed) * diff.speed * rb
        speed = if (speed < top) min(top, speed + v.accel * dt) else max(top, speed - 400f * dt)
        dist += speed * dt
        boostT -= dt; hitT -= dt

        for (item in tr.items) {
            if (abs(wrap(tr, item.s - (dist % tr.lapLen))) > 26f) continue
            val dx = abs(item.off - off)
            when (item.kind) {
                Kind.COIN -> if (!item.taken && dx < 9f + v.halfW) { item.taken = true; coins++ }
                Kind.BOOST -> if (!item.taken && dx < 13f + v.halfW) { item.taken = true; boostT = 2.5f }
                else -> if (hitT <= 0f && dx < 11f + v.halfW) { hitT = 1.2f; speed *= 0.35f; boostT = 0f }
            }
        }
    }
}
