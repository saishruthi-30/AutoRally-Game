package com.autorally

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import kotlin.math.sin

enum class VKind { AUTO, SCOOTER, HATCH, JEEP, SPORTS, SUPERBIKE }

/** Stats are per vehicle. Add a VehicleDef (and a draw branch below) to add a vehicle. */
class VehicleDef(
    val id: String, val name: String, val kind: VKind, val price: Int,
    val maxSpeed: Float, val accel: Float, val handling: Float,
    val halfW: Float,      // half width used for collisions
    val offroad: Float,    // top speed on grass
    val desc: String
)

val VEHICLES = listOf(
    VehicleDef("auto", "Auto Rickshaw", VKind.AUTO, 0, 330f, 160f, 280f, 15f, 120f, "Balanced and cheerful. Your trusty ride."),
    VehicleDef("scooter", "Scooter", VKind.SCOOTER, 60, 300f, 230f, 340f, 9f, 140f, "Tiny and nimble. Slips past anything."),
    VehicleDef("hatch", "Hatchback", VKind.HATCH, 120, 350f, 175f, 270f, 16f, 110f, "A solid little car with decent pace."),
    VehicleDef("jeep", "Jeep", VKind.JEEP, 200, 340f, 150f, 250f, 18f, 235f, "Slow to start but shrugs off the grass."),
    VehicleDef("sports", "Sports Car", VKind.SPORTS, 350, 430f, 230f, 265f, 16f, 100f, "Fast and flashy. Stay on the road!"),
    VehicleDef("superbike", "Superbike", VKind.SUPERBIKE, 450, 470f, 260f, 330f, 9f, 130f, "Top speed and sharp turns. Hold tight.")
)

private val DARK = Color(0.1f, 0.1f, 0.12f, 1f)
private val GREY = Color(0.45f, 0.45f, 0.5f, 1f)
private val SKIN = Color(0.87f, 0.65f, 0.45f, 1f)
private val YEL = Color(1f, 0.8f, 0.1f, 1f)
private val GREEN = Color(0.1f, 0.5f, 0.25f, 1f)
private val KHAKI = Color(0.7f, 0.65f, 0.4f, 1f)
private val RED = Color(0.85f, 0.2f, 0.2f, 1f)
private val RED_DK = Color(0.6f, 0.1f, 0.12f, 1f)
private val BLUE = Color(0.2f, 0.4f, 0.85f, 1f)
private val LBLUE = Color(0.45f, 0.65f, 0.95f, 1f)
private val GLASS = Color(0.25f, 0.5f, 0.65f, 1f)
private val OLIVE = Color(0.35f, 0.45f, 0.2f, 1f)
private val OLIVE_DK = Color(0.25f, 0.33f, 0.15f, 1f)
private val BROWN = Color(0.45f, 0.3f, 0.15f, 1f)
private val WHITE = Color(1f, 1f, 1f, 1f)
private val ORANGE = Color(1f, 0.5f, 0.1f, 1f)
private val HEADLIGHT = Color(1f, 0.95f, 0.6f, 1f)
private val PINK = Color(0.95f, 0.45f, 0.65f, 1f)
private val NAVY = Color(0.12f, 0.2f, 0.5f, 1f)
private val SHIRT = Color(0.2f, 0.55f, 0.7f, 1f)

/** Draws any vehicle top-down, facing up, with its rider. Origin = vehicle centre. */
class VehicleRenderer(private val sh: ShapeRenderer) {
    private var ox = 0f
    private var oy = 0f
    private var k = 1f

    private fun rect(c: Color, x: Float, y: Float, w: Float, h: Float) { sh.color = c; sh.rect(ox + x * k, oy + y * k, w * k, h * k) }
    private fun circ(c: Color, x: Float, y: Float, r: Float) { sh.color = c; sh.circle(ox + x * k, oy + y * k, r * k) }
    private fun tri(c: Color, x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) {
        sh.color = c
        sh.triangle(ox + x1 * k, oy + y1 * k, ox + x2 * k, oy + y2 * k, ox + x3 * k, oy + y3 * k)
    }

    /** Wheel with tread marks that roll as the vehicle moves (ph = distance travelled). */
    private fun wheel(x: Float, y: Float, w: Float, h: Float, ph: Float) {
        rect(DARK, x - w / 2f, y - h / 2f, w, h)
        var yy = y + h / 2f - 1.5f - (ph % 6f)
        while (yy > y - h / 2f) { rect(GREY, x - w / 2f + 1f, yy, w - 2f, 1.5f); yy -= 6f }
    }

    fun draw(
        v: VehicleDef, x: Float, y: Float, scale: Float,
        steer: Float, ph: Float, clock: Float, boost: Boolean, fast: Boolean
    ) {
        ox = x; oy = y; k = scale
        val lean = steer * 6f
        val bob = sin(clock * 18f) * 0.7f
        when (v.kind) {
            VKind.AUTO -> {
                ox += sin(clock * 50f) * 0.8f * k           // engine vibration
                if (boost) flame(-28f, clock)
                wheel(-17f, -20f, 8f, 16f, ph); wheel(17f, -20f, 8f, 16f, ph); wheel(0f, 30f, 8f, 13f, ph)
                rect(YEL, -15f, -26f, 30f, 40f)
                rect(GREEN, -15f, -26f, 30f, 12f)
                rect(KHAKI, -8f, -10f, 16f, 12f)
                circ(SKIN, 0f, 5f + bob, 6f); circ(RED, 0f, 6.5f + bob, 4.5f)   // driver with red cap
                rect(DARK, -10f, 14f, 20f, 4f)
                rect(YEL, -9f, 18f, 18f, 8f)
            }
            VKind.SCOOTER -> {
                if (boost) flame(-26f, clock)
                wheel(0f, 24f, 5f, 14f, ph); wheel(0f, -22f, 6f, 15f, ph)
                rect(RED, -5f, -18f, 10f, 36f)
                rect(DARK, -5f, -12f, 10f, 12f)
                rect(GREY, -13f + lean * 0.3f, 14f, 26f, 3f)
                circ(HEADLIGHT, lean * 0.3f, 21f, 2.5f)
                rect(PINK, -2f + lean, -16f, 4f, 10f + sin(clock * 30f) * 3f)   // flapping scarf
                rect(SHIRT, -7f + lean, -8f, 14f, 14f)
                rect(SKIN, -9f + lean, 5f, 4f, 9f); rect(SKIN, 5f + lean, 5f, 4f, 9f)
                circ(WHITE, lean * 1.2f, 10f, 6f)
                rect(DARK, lean * 1.2f - 3f, 11f, 6f, 2.5f)
            }
            VKind.HATCH -> {
                oy += sin(clock * 14f) * 0.6f * k            // suspension bounce
                if (boost) flame(-30f, clock)
                wheel(-16f, -20f, 7f, 13f, ph); wheel(16f, -20f, 7f, 13f, ph)
                wheel(-16f, 18f, 7f, 13f, ph); wheel(16f, 18f, 7f, 13f, ph)
                rect(BLUE, -14f, -28f, 28f, 56f)
                rect(LBLUE, -11f, -14f, 22f, 24f)
                rect(GLASS, -11f, 12f, 22f, 6f); rect(GLASS, -11f, -20f, 22f, 5f)
                rect(DARK, -6f, -3f, 12f, 10f)               // sunroof
                circ(SKIN, 0f, 3f + bob, 4.5f); circ(YEL, 0f, 4.5f + bob, 3.5f)
                rect(HEADLIGHT, -12f, 26f, 5f, 3f); rect(HEADLIGHT, 7f, 26f, 5f, 3f)
                val p = (clock * 3f) % 1f                    // exhaust puff
                circ(Color(0.8f, 0.8f, 0.8f, 1f - p), 0f, -31f - p * 14f, 2f + p * 3f)
            }
            VKind.JEEP -> {
                oy += sin(clock * 11f) * 1.2f * k            // bouncy suspension
                if (boost) flame(-34f, clock)
                wheel(-19f, -18f, 9f, 16f, ph); wheel(19f, -18f, 9f, 16f, ph)
                wheel(-19f, 20f, 9f, 16f, ph); wheel(19f, 20f, 9f, 16f, ph)
                rect(OLIVE, -16f, -30f, 32f, 60f)
                rect(OLIVE_DK, -14f, 10f, 28f, 18f)
                rect(DARK, -16f, -6f, 32f, 3f)               // roll bar
                circ(DARK, 0f, -34f, 7f); circ(GREY, 0f, -34f, 3.5f)   // spare tyre
                rect(KHAKI, -9f, -16f, 18f, 14f)
                circ(SKIN, 0f, 3f + bob, 5f)
                rect(BROWN, -8f, 5f + bob, 16f, 3f); rect(BROWN, -4.5f, 7f + bob, 9f, 3.5f)  // hat
                rect(HEADLIGHT, -12f, 27f, 6f, 3f); rect(HEADLIGHT, 6f, 27f, 6f, 3f)
            }
            VKind.SPORTS -> {
                if (boost) flame(-32f, clock)
                if (fast && ((clock * 20f).toInt() and 1) == 0) {   // exhaust pops
                    tri(ORANGE, -10f, -32f, -6f, -32f, -8f, -41f); tri(ORANGE, 6f, -32f, 10f, -32f, 8f, -41f)
                }
                wheel(-17f, -20f, 8f, 14f, ph); wheel(17f, -20f, 8f, 14f, ph)
                wheel(-17f, 20f, 8f, 14f, ph); wheel(17f, 20f, 8f, 14f, ph)
                rect(RED, -15f, -30f, 30f, 60f)
                tri(RED, -15f, 30f, 15f, 30f, 0f, 40f)
                rect(WHITE, -2f, -30f, 4f, 70f * 0.9f)
                rect(DARK, -9f, -8f, 18f, 16f)
                circ(WHITE, 0f, 0f, 5.5f); rect(DARK, -3f, 1f, 6f, 2.2f)   // helmet + visor
                rect(DARK, -18f, -35f, 36f, 5f); rect(RED_DK, -14f, -31f, 3f, 3f); rect(RED_DK, 11f, -31f, 3f, 3f)
                rect(HEADLIGHT, -11f, 31f, 4f, 2.5f); rect(HEADLIGHT, 7f, 31f, 4f, 2.5f)
            }
            VKind.SUPERBIKE -> {
                val l = lean * 1.2f
                if (boost) flame(-30f, clock)
                wheel(0f, 27f, 5f, 16f, ph); wheel(0f, -24f, 7f, 18f, ph)
                rect(NAVY, -5f, -20f, 10f, 40f)
                tri(BLUE, -7f, 16f, 7f, 16f, 0f, 33f)
                rect(BLUE, -4f, -27f, 8f, 8f)
                rect(DARK, -6f + l, -8f, 12f, 16f)
                rect(DARK, -9f + l, 4f, 4f, 12f); rect(DARK, 5f + l, 4f, 4f, 12f)
                circ(YEL, l * 1.2f, 10f, 5.8f)
                rect(DARK, l * 1.2f - 3.5f, 11f, 7f, 2.5f)
            }
        }
    }

    private fun flame(rearY: Float, clock: Float) {
        tri(ORANGE, -7f, rearY, 7f, rearY, 0f, rearY - 24f - 8f * sin(clock * 60f))
        tri(YEL, -3.5f, rearY, 3.5f, rearY, 0f, rearY - 12f - 4f * sin(clock * 70f))
    }
}
