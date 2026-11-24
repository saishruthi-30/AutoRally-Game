package com.autorally

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShapeRenderer

enum class DKind { TREE, HUT, HAY, BUILDING, LAMP, SIGN, ROCK, PINE }

/** Roadside scenery. x = signed sideways offset from the road centre. */
class Decor(val s: Float, val x: Float, val kind: DKind, val variant: Int)

private val TREE_DK = Color(0.12f, 0.42f, 0.18f, 1f)
private val TREE_LT = Color(0.22f, 0.58f, 0.25f, 1f)
private val THATCH = Color(0.72f, 0.55f, 0.28f, 1f)
private val THATCH_DK = Color(0.55f, 0.40f, 0.20f, 1f)
private val GOLD = Color(0.93f, 0.78f, 0.25f, 1f)
private val GOLD_DK = Color(0.80f, 0.62f, 0.15f, 1f)
private val BLD = arrayOf(Color(0.35f, 0.40f, 0.55f, 1f), Color(0.55f, 0.35f, 0.40f, 1f), Color(0.30f, 0.50f, 0.50f, 1f))
private val BLD_WIN = Color(1f, 0.92f, 0.55f, 1f)
private val POLE = Color(0.2f, 0.2f, 0.24f, 1f)
private val GLOW = Color(1f, 0.9f, 0.4f, 0.35f)
private val SIGN_BLUE = Color(0.15f, 0.40f, 0.80f, 1f)
private val ROCK_C = Color(0.45f, 0.44f, 0.42f, 1f)
private val ROCK_LT = Color(0.62f, 0.61f, 0.58f, 1f)
private val PINE_DK = Color(0.10f, 0.32f, 0.22f, 1f)
private val SNOW = Color(0.95f, 0.97f, 1f, 1f)

class SceneryRenderer(private val sh: ShapeRenderer) {
    fun draw(d: Decor, x: Float, y: Float, k: Float) {
        when (d.kind) {
            DKind.TREE -> {
                sh.color = TREE_DK; sh.circle(x, y, 15f * k)
                sh.color = TREE_LT; sh.circle(x - 3f * k, y + 3f * k, 9f * k)
            }
            DKind.HUT -> {
                sh.color = THATCH; sh.rect(x - 17f * k, y - 15f * k, 34f * k, 30f * k)
                sh.color = THATCH_DK; sh.rect(x - 1.5f * k, y - 15f * k, 3f * k, 30f * k)
            }
            DKind.HAY -> {
                sh.color = GOLD; sh.circle(x, y, 11f * k)
                sh.color = GOLD_DK; sh.circle(x, y, 5f * k)
            }
            DKind.BUILDING -> {
                val w = 40f + (d.variant % 3) * 8f
                val h = 54f + (d.variant % 2) * 14f
                sh.color = BLD[d.variant % 3]
                sh.rect(x - w / 2f * k, y - h / 2f * k, w * k, h * k)
                sh.color = BLD_WIN
                for (i in 0..2) for (j in 0..3)
                    sh.rect(x + (-w / 2f + 6f + i * (w - 12f) / 3f) * k, y + (-h / 2f + 6f + j * (h - 12f) / 4f) * k, 6f * k, 5f * k)
            }
            DKind.LAMP -> {
                sh.color = GLOW; sh.circle(x, y, 11f * k)
                sh.color = POLE; sh.circle(x, y, 3f * k)
                sh.color = Color.YELLOW; sh.circle(x, y, 1.8f * k)
            }
            DKind.SIGN -> {
                sh.color = POLE; sh.rect(x - 1.5f * k, y - 14f * k, 3f * k, 14f * k)
                sh.color = SIGN_BLUE; sh.rect(x - 17f * k, y - 6f * k, 34f * k, 16f * k)
                sh.color = Color.WHITE; sh.rect(x - 11f * k, y, 22f * k, 3f * k)
            }
            DKind.ROCK -> {
                val r = 10f + (d.variant % 3) * 4f
                sh.color = ROCK_C; sh.circle(x, y, r * k)
                sh.color = ROCK_LT; sh.circle(x - r * 0.3f * k, y + r * 0.3f * k, r * 0.5f * k)
            }
            DKind.PINE -> {
                sh.color = PINE_DK
                sh.triangle(x - 13f * k, y - 13f * k, x + 13f * k, y - 13f * k, x, y + 8f * k)
                sh.triangle(x - 10f * k, y - 4f * k, x + 10f * k, y - 4f * k, x, y + 16f * k)
                sh.color = SNOW
                sh.triangle(x - 4f * k, y + 9f * k, x + 4f * k, y + 9f * k, x, y + 16f * k)
            }
        }
    }
}
