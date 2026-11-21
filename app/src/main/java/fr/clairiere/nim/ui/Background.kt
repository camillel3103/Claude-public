package fr.clairiere.nim.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import fr.clairiere.nim.game.ItemKind
import kotlin.math.PI
import kotlin.math.sin

/** Temps (en secondes) qui s'écoule à chaque image : sert aux animations d'ambiance. */
@Composable
fun rememberAmbientTime(): State<Float> {
    val time = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) {
            withFrameNanos { now -> time.floatValue = (now - start) / 1_000_000_000f }
        }
    }
    return time
}

/** Fond de clairière : ciel, lumière, trois plans d'arbres, sol moussu, feuilles ou lucioles. */
@Composable
fun ForestBackground(modifier: Modifier = Modifier, leafCount: Int = 9) {
    val pal = LocalPalette.current
    val time = rememberAmbientTime()
    Box(modifier.fillMaxSize()) {
        // Couches fixes : dessinées une fois puis mises en cache
        Box(
            Modifier
                .fillMaxSize()
                .drawWithCache {
                    val far = treeLine(size, seed = 3, baseline = 0.62f, height = 0.2f, count = 11, conifer = 0.6f)
                    val mid = treeLine(size, seed = 11, baseline = 0.72f, height = 0.26f, count = 8, conifer = 0.4f)
                    val near = treeLine(size, seed = 23, baseline = 0.86f, height = 0.34f, count = 5, conifer = 0.3f)
                    val hill = Path().apply {
                        moveTo(0f, size.height * 0.84f)
                        cubicTo(
                            size.width * 0.3f, size.height * 0.78f, size.width * 0.7f, size.height * 0.9f,
                            size.width, size.height * 0.82f,
                        )
                        lineTo(size.width, size.height)
                        lineTo(0f, size.height)
                        close()
                    }
                    onDrawBehind {
                        drawRect(Brush.verticalGradient(listOf(pal.skyTop, pal.skyBottom)))
                        val sun = Offset(size.width * 0.78f, size.height * 0.1f)
                        drawCircle(
                            Brush.radialGradient(
                                listOf(pal.glow.copy(alpha = if (pal.night) 0.35f else 0.9f), pal.glow.copy(alpha = 0f)),
                                center = sun, radius = size.width * 0.7f,
                            ),
                            radius = size.width * 0.7f, center = sun,
                        )
                        if (pal.night) {
                            val moon = Offset(size.width * 0.62f, size.height * 0.02f)
                            drawCircle(pal.glow.copy(alpha = 0.8f), radius = size.width * 0.03f, center = moon)
                        }
                        drawPath(far, pal.treesFar)
                        drawPath(mid, pal.treesMid)
                        drawPath(near, pal.treesNear)
                        drawPath(hill, Brush.verticalGradient(listOf(pal.ground, pal.groundDark), startY = size.height * 0.78f))
                    }
                },
        )
        // Couches animées : rayons de lumière, feuilles qui tombent, lucioles
        Canvas(Modifier.fillMaxSize()) {
            val t = time.value
            if (!pal.night) drawLightRays(t, pal.glow)
            drawFallingLeaves(t, leafCount, pal.night)
            if (pal.night) drawFireflies(t, pal.highlight)
        }
    }
}

private fun treeLine(size: Size, seed: Int, baseline: Float, height: Float, count: Int, conifer: Float): Path {
    val r = Seeded(seed)
    val path = Path()
    val baseY = size.height * baseline
    val step = size.width / (count - 1)
    for (i in 0 until count) {
        val x = i * step + r.range(-step * 0.3f, step * 0.3f)
        val h = size.height * height * r.range(0.7f, 1.15f)
        val w = step * r.range(0.8f, 1.2f)
        if (r.next() < conifer) {
            // Sapin : trois étages de triangles adoucis
            for (k in 0..2) {
                val tw = w * (0.9f - k * 0.22f)
                val top = baseY - h * (0.55f + k * 0.22f)
                val bottom = baseY - h * k * 0.22f
                path.moveTo(x, top - h * 0.12f)
                path.quadraticBezierTo(x + tw * 0.15f, top + (bottom - top) * 0.4f, x + tw / 2, bottom)
                path.lineTo(x - tw / 2, bottom)
                path.quadraticBezierTo(x - tw * 0.15f, top + (bottom - top) * 0.4f, x, top - h * 0.12f)
                path.close()
            }
        } else {
            // Feuillu : houppier fait de cercles
            val cy = baseY - h * 0.6f
            for (k in 0..4) {
                val rad = w * r.range(0.25f, 0.38f)
                path.addOval(
                    androidx.compose.ui.geometry.Rect(
                        Offset(x + r.range(-w * 0.28f, w * 0.28f), cy + r.range(-h * 0.25f, h * 0.15f)), rad,
                    ),
                )
            }
        }
        path.addRect(androidx.compose.ui.geometry.Rect(x - w * 0.04f, baseY - h * 0.4f, x + w * 0.04f, baseY + 4f))
    }
    path.addRect(androidx.compose.ui.geometry.Rect(0f, baseY, size.width, size.height))
    return path
}

private fun DrawScope.drawLightRays(t: Float, glow: Color) {
    val origin = Offset(size.width * 0.85f, -size.height * 0.05f)
    for (i in 0..3) {
        val breathe = 0.5f + 0.5f * sin(t * 0.25f + i * 1.7f)
        val angle = 112f + i * 11f
        rotate(angle, pivot = origin) {
            val p = Path().apply {
                moveTo(origin.x, origin.y)
                lineTo(origin.x + size.height * 1.3f, origin.y - size.width * 0.05f * (i + 1))
                lineTo(origin.x + size.height * 1.3f, origin.y + size.width * 0.05f * (i + 1))
                close()
            }
            drawPath(
                p,
                Brush.linearGradient(
                    listOf(glow.copy(alpha = 0.18f * breathe), glow.copy(alpha = 0f)),
                    start = origin, end = Offset(origin.x + size.height, origin.y),
                ),
            )
        }
    }
}

private fun DrawScope.drawFallingLeaves(t: Float, count: Int, night: Boolean) {
    val leafSize = size.minDimension * 0.045f
    for (i in 0 until count) {
        val r = Seeded(i * 31 + 5)
        val speed = r.range(0.018f, 0.04f) // fraction d'écran par seconde
        val x0 = r.next()
        val phase = r.next() * 10f
        val sway = r.range(0.03f, 0.08f)
        val span = 1.2f
        val p = ((t * speed + r.next() * span) % span) - 0.1f
        val y = p * size.height
        val x = (x0 + sway * sin(t * r.range(0.4f, 0.8f) + phase)) * size.width
        val rot = 40f * sin(t * r.range(0.5f, 1.1f) + phase) + r.range(0f, 360f)
        val s = leafSize * r.range(0.7f, 1.2f)
        translate(x, y) {
            rotate(rot, pivot = Offset.Zero) {
                translate(-s / 2, -s / 2) {
                    drawNatureItem(ItemKind.LEAF, i * 7 + 1, Size(s, s * 1.15f), 0f, Color.Transparent, night)
                }
            }
        }
    }
}

private fun DrawScope.drawFireflies(t: Float, color: Color) {
    for (i in 0 until 16) {
        val r = Seeded(i * 17 + 3)
        val x = (r.next() + 0.06f * sin(t * r.range(0.1f, 0.25f) + i)) * size.width
        val y = (0.3f + r.next() * 0.65f + 0.04f * sin(t * r.range(0.15f, 0.3f) + i * 2f)) * size.height
        val pulse = (0.5f + 0.5f * sin(t * r.range(0.8f, 1.6f) + i * 1.3f)).let { it * it }
        val rad = size.minDimension * 0.035f
        drawCircle(
            Brush.radialGradient(
                listOf(color.copy(alpha = 0.55f * pulse), color.copy(alpha = 0f)),
                center = Offset(x, y), radius = rad,
            ),
            radius = rad, center = Offset(x, y),
        )
        drawCircle(lerp(color, Color.White, 0.5f).copy(alpha = 0.9f * pulse), radius = rad * 0.12f, center = Offset(x, y))
    }
}

/** Pluie de pétales et de feuilles pour célébrer une victoire. */
fun DrawScope.drawCelebration(t: Float, colors: List<Color>) {
    for (i in 0 until 36) {
        val r = Seeded(i * 13 + 2)
        val delay = r.next() * 1.2f
        val tt = (t - delay).coerceAtLeast(0f)
        val y = -0.05f * size.height + tt * size.height * r.range(0.12f, 0.22f)
        if (y > size.height * 1.05f || tt <= 0f) continue
        val x = (r.next() + 0.05f * sin(tt * r.range(1f, 2f) + i)) * size.width
        val s = size.minDimension * r.range(0.015f, 0.03f)
        val c = colors[i % colors.size]
        rotate(tt * r.range(40f, 140f), pivot = Offset(x, y)) {
            drawOval(c.copy(alpha = 0.85f), Offset(x - s, y - s * 0.6f), Size(s * 2f, s * 1.2f))
        }
    }
}

internal fun twoPi() = (2 * PI).toFloat()
