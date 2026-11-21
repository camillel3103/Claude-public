package fr.clairiere.nim.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import fr.clairiere.nim.game.ItemKind
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Rapport hauteur / largeur de la case occupée par chaque type d'objet. */
fun ItemKind.aspect(): Float = when (this) {
    ItemKind.STICK -> 3.4f
    ItemKind.TWIG -> 3.1f
    ItemKind.ACORN -> 1.2f
    ItemKind.LEAF -> 1.15f
    ItemKind.PEBBLE -> 0.85f
    ItemKind.MUSHROOM -> 1.1f
}

/** Petit générateur pseudo-aléatoire déterministe : chaque objet a son propre aspect, stable. */
class Seeded(seed: Int) {
    private var s = (seed.toLong() * 2654435761L + 0x9E3779B9L) and 0xFFFFFFFFL
    fun next(): Float {
        s = (s * 1103515245L + 12345L) and 0x7FFFFFFFL
        return (s % 10000L) / 10000f
    }
    fun range(a: Float, b: Float) = a + (b - a) * next()
}

/**
 * Dessine un objet naturel dans un rectangle de taille [size] (origine en haut à gauche).
 * [glow] (0..1) ajoute un halo doux quand l'objet est sélectionné.
 */
fun DrawScope.drawNatureItem(kind: ItemKind, seed: Int, size: Size, glow: Float, highlight: Color, night: Boolean) {
    val w = size.width
    val h = size.height
    // Ombre portée douce sur le sol
    drawOval(
        color = Color.Black.copy(alpha = 0.16f * (1f - glow * 0.4f)),
        topLeft = Offset(w * 0.12f, h * 0.9f),
        size = Size(w * 0.76f, h * 0.1f),
    )
    if (glow > 0.01f) {
        drawCircle(
            brush = Brush.radialGradient(
                listOf(highlight.copy(alpha = 0.75f * glow), highlight.copy(alpha = 0f)),
                center = Offset(w / 2, h / 2),
                radius = maxOf(w, h) * 0.62f,
            ),
            radius = maxOf(w, h) * 0.62f,
            center = Offset(w / 2, h / 2),
        )
    }
    val r = Seeded(seed)
    val shade = if (night) 0.78f else 1f
    when (kind) {
        ItemKind.STICK -> drawStick(w, h, r, shade)
        ItemKind.TWIG -> drawTwig(w, h, r, shade)
        ItemKind.ACORN -> drawAcorn(w, h, r, shade)
        ItemKind.LEAF -> drawLeaf(w, h, r, shade)
        ItemKind.PEBBLE -> drawPebble(w, h, r, shade)
        ItemKind.MUSHROOM -> drawMushroom(w, h, r, shade)
    }
}

private fun Color.dim(f: Float) = Color(red * f, green * f, blue * f, alpha)

private fun DrawScope.drawStick(w: Float, h: Float, r: Seeded, shade: Float) {
    val tilt = r.range(-4f, 4f)
    val sw = w * r.range(0.36f, 0.46f)
    val left = (w - sw) / 2
    val top = h * 0.03f
    val len = h * 0.86f
    val base = lerp(Color(0xFF8E5D33), Color(0xFF9C7448), r.next()).dim(shade)
    rotate(tilt, pivot = Offset(w / 2, h * 0.5f)) {
        drawRoundRect(
            brush = Brush.horizontalGradient(
                listOf(base.dim(0.62f), base, lerp(base, Color(0xFFE2C08F), 0.35f), base.dim(0.75f)),
                startX = left, endX = left + sw,
            ),
            topLeft = Offset(left, top),
            size = Size(sw, len),
            cornerRadius = CornerRadius(sw * 0.45f),
        )
        // Veines du bois
        val grain = base.dim(0.55f).copy(alpha = 0.55f)
        repeat(3) { i ->
            val x = left + sw * (0.25f + 0.25f * i) + r.range(-1f, 1f)
            val p = Path().apply {
                moveTo(x, top + len * 0.08f)
                val wob = sw * 0.06f
                cubicTo(x + wob, top + len * 0.35f, x - wob, top + len * 0.6f, x + wob * 0.5f, top + len * 0.92f)
            }
            drawPath(p, grain, style = Stroke(width = maxOf(1f, sw * 0.05f), cap = StrokeCap.Round))
        }
        // Nœud
        val ky = top + len * r.range(0.3f, 0.7f)
        drawOval(base.dim(0.5f), Offset(left + sw * 0.3f, ky), Size(sw * 0.38f, sw * 0.5f))
        drawOval(base.dim(0.75f), Offset(left + sw * 0.38f, ky + sw * 0.1f), Size(sw * 0.2f, sw * 0.28f))
        // Extrémité coupée avec cernes
        val capH = sw * 0.55f
        drawOval(Color(0xFFE2C595).dim(shade), Offset(left + sw * 0.06f, top), Size(sw * 0.88f, capH))
        drawOval(
            Color(0xFFB38A5A).dim(shade), Offset(left + sw * 0.26f, top + capH * 0.22f),
            Size(sw * 0.48f, capH * 0.56f), style = Stroke(width = maxOf(1f, sw * 0.05f)),
        )
    }
}

private fun DrawScope.drawTwig(w: Float, h: Float, r: Seeded, shade: Float) {
    val color = lerp(Color(0xFF7A5A3C), Color(0xFF6E6A44), r.next()).dim(shade)
    val sw = w * 0.2f
    val bend = w * r.range(-0.12f, 0.12f)
    val main = Path().apply {
        moveTo(w / 2 + bend * 0.3f, h * 0.9f)
        cubicTo(w / 2 + bend, h * 0.6f, w / 2 - bend, h * 0.35f, w / 2 + bend * 0.5f, h * 0.06f)
    }
    drawPath(main, color.dim(0.7f), style = Stroke(width = sw * 1.15f, cap = StrokeCap.Round))
    drawPath(main, color, style = Stroke(width = sw, cap = StrokeCap.Round))
    // Branche latérale + petite feuille
    val side = if (r.next() > 0.5f) 1f else -1f
    val by = h * r.range(0.35f, 0.55f)
    val tip = Offset(w / 2 + side * w * 0.38f, by - h * 0.12f)
    val branch = Path().apply {
        moveTo(w / 2, by)
        quadraticBezierTo(w / 2 + side * w * 0.2f, by - h * 0.02f, tip.x, tip.y)
    }
    drawPath(branch, color, style = Stroke(width = sw * 0.55f, cap = StrokeCap.Round))
    val leafColor = lerp(Color(0xFF7FA650), Color(0xFFB7B84A), r.next()).dim(shade)
    translate(tip.x, tip.y) {
        rotate(side * 40f, pivot = Offset.Zero) {
            val lw = w * 0.22f
            val lh = w * 0.42f
            val leaf = Path().apply {
                moveTo(0f, 0f)
                quadraticBezierTo(lw, -lh * 0.5f, 0f, -lh)
                quadraticBezierTo(-lw, -lh * 0.5f, 0f, 0f)
                close()
            }
            drawPath(leaf, leafColor)
            drawLine(leafColor.dim(0.7f), Offset.Zero, Offset(0f, -lh * 0.85f), strokeWidth = maxOf(1f, w * 0.02f))
        }
    }
    // Bourgeon au sommet
    drawCircle(lerp(color, Color(0xFF9CB86A), 0.5f), radius = sw * 0.55f, center = Offset(w / 2 + bend * 0.5f, h * 0.06f))
}

private fun DrawScope.drawAcorn(w: Float, h: Float, r: Seeded, shade: Float) {
    val tilt = r.range(-12f, 12f)
    val body = lerp(Color(0xFFB9772F), Color(0xFF9C6B2E), r.next()).dim(shade)
    rotate(tilt, pivot = Offset(w / 2, h * 0.55f)) {
        val bw = w * 0.62f
        val bh = h * 0.62f
        val bx = (w - bw) / 2
        val by = h * 0.3f
        drawOval(
            brush = Brush.radialGradient(
                listOf(lerp(body, Color(0xFFF0C987), 0.45f), body, body.dim(0.6f)),
                center = Offset(bx + bw * 0.38f, by + bh * 0.35f), radius = bw * 0.75f,
            ),
            topLeft = Offset(bx, by), size = Size(bw, bh),
        )
        // Pointe
        drawCircle(body.dim(0.55f), radius = w * 0.035f, center = Offset(w / 2, by + bh * 0.98f))
        // Cupule
        val cw = w * 0.74f
        val ch = h * 0.32f
        val cx = (w - cw) / 2
        val cy = h * 0.17f
        val cupColor = Color(0xFF7A5634).dim(shade)
        drawArc(cupColor, 180f, 180f, true, Offset(cx, cy), Size(cw, ch * 1.2f))
        drawRect(cupColor, Offset(cx, cy + ch * 0.58f), Size(cw, ch * 0.18f))
        // Écailles de la cupule
        val scale = cupColor.dim(0.7f)
        for (row in 0..2) {
            for (i in 0..5) {
                val px = cx + cw * (0.12f + i * 0.15f + (row % 2) * 0.07f)
                val py = cy + ch * (0.22f + row * 0.17f)
                if (px < cx + cw * 0.92f) drawCircle(scale, radius = w * 0.025f, center = Offset(px, py))
            }
        }
        // Tige
        drawLine(
            Color(0xFF5E4127).dim(shade), Offset(w / 2, cy + ch * 0.05f), Offset(w / 2 + w * 0.06f, h * 0.04f),
            strokeWidth = w * 0.07f, cap = StrokeCap.Round,
        )
        // Reflet
        drawOval(Color.White.copy(alpha = 0.28f), Offset(bx + bw * 0.22f, by + bh * 0.3f), Size(bw * 0.18f, bh * 0.3f))
    }
}

private val leafColors = listOf(
    Color(0xFF6E9D45), Color(0xFF8DB04E), Color(0xFFC9A23A), Color(0xFFD27C33), Color(0xFFB65A2E), Color(0xFF9EAE45),
)

private fun DrawScope.drawLeaf(w: Float, h: Float, r: Seeded, shade: Float) {
    val color = leafColors[(r.next() * leafColors.size).toInt().coerceIn(0, leafColors.size - 1)].dim(shade)
    val tilt = r.range(-25f, 25f)
    rotate(tilt, pivot = Offset(w / 2, h / 2)) {
        val top = Offset(w / 2, h * 0.04f)
        val bottom = Offset(w / 2, h * 0.8f)
        val bulge = w * r.range(0.4f, 0.48f)
        val leaf = Path().apply {
            moveTo(bottom.x, bottom.y)
            cubicTo(w / 2 + bulge, h * 0.7f, w / 2 + bulge * 0.9f, h * 0.2f, top.x, top.y)
            cubicTo(w / 2 - bulge * 0.9f, h * 0.2f, w / 2 - bulge, h * 0.7f, bottom.x, bottom.y)
            close()
        }
        drawPath(
            leaf,
            Brush.linearGradient(
                listOf(lerp(color, Color(0xFFF2E3A0), 0.25f), color, color.dim(0.72f)),
                start = Offset(0f, 0f), end = Offset(w, h),
            ),
        )
        drawPath(leaf, color.dim(0.6f), style = Stroke(width = maxOf(1f, w * 0.015f)))
        val vein = color.dim(0.62f).copy(alpha = 0.8f)
        drawLine(vein, bottom, top, strokeWidth = maxOf(1f, w * 0.03f), cap = StrokeCap.Round)
        for (i in 1..4) {
            val y = bottom.y - (bottom.y - top.y) * i * 0.19f
            val dx = bulge * (0.75f - i * 0.1f)
            drawLine(vein, Offset(w / 2, y), Offset(w / 2 + dx, y - h * 0.1f), strokeWidth = maxOf(1f, w * 0.018f))
            drawLine(vein, Offset(w / 2, y), Offset(w / 2 - dx, y - h * 0.1f), strokeWidth = maxOf(1f, w * 0.018f))
        }
        // Pétiole
        drawLine(color.dim(0.55f), bottom, Offset(w / 2 + w * 0.05f, h * 0.95f), strokeWidth = w * 0.04f, cap = StrokeCap.Round)
    }
}

private fun DrawScope.drawPebble(w: Float, h: Float, r: Seeded, shade: Float) {
    val base = lerp(Color(0xFF9DA3A0), Color(0xFFB9AE9C), r.next()).dim(shade)
    val cx = w / 2
    val cy = h * 0.52f
    val rx = w * 0.44f
    val ry = h * 0.38f
    val n = 10
    val pts = (0 until n).map { i ->
        val a = (i.toFloat() / n) * 2 * PI.toFloat()
        val k = r.range(0.9f, 1.06f)
        Offset(cx + cos(a) * rx * k, cy + sin(a) * ry * k)
    }
    val path = Path().apply {
        val mid = { a: Offset, b: Offset -> Offset((a.x + b.x) / 2, (a.y + b.y) / 2) }
        val start = mid(pts[n - 1], pts[0])
        moveTo(start.x, start.y)
        for (i in 0 until n) {
            val p = pts[i]
            val m = mid(p, pts[(i + 1) % n])
            quadraticBezierTo(p.x, p.y, m.x, m.y)
        }
        close()
    }
    drawPath(
        path,
        Brush.radialGradient(
            listOf(lerp(base, Color.White, 0.35f), base, base.dim(0.62f)),
            center = Offset(cx - rx * 0.3f, cy - ry * 0.35f), radius = rx * 1.3f,
        ),
    )
    // Mouchetures
    repeat(6) {
        drawCircle(
            base.dim(r.range(0.55f, 0.8f)).copy(alpha = 0.6f), radius = w * r.range(0.012f, 0.025f),
            center = Offset(cx + r.range(-0.6f, 0.6f) * rx, cy + r.range(-0.5f, 0.5f) * ry),
        )
    }
    // Veine claire éventuelle
    if (r.next() > 0.5f) {
        val p = Path().apply {
            moveTo(cx - rx * 0.8f, cy + ry * r.range(-0.3f, 0.3f))
            quadraticBezierTo(cx, cy + ry * r.range(-0.5f, 0.5f), cx + rx * 0.8f, cy + ry * r.range(-0.3f, 0.3f))
        }
        drawPath(p, Color.White.copy(alpha = 0.35f * shade), style = Stroke(width = maxOf(1f, w * 0.025f)))
    }
    drawOval(Color.White.copy(alpha = 0.3f), Offset(cx - rx * 0.55f, cy - ry * 0.7f), Size(rx * 0.6f, ry * 0.35f))
}

private fun DrawScope.drawMushroom(w: Float, h: Float, r: Seeded, shade: Float) {
    val capColor = lerp(Color(0xFFB4432C), Color(0xFF94592C), r.next()).dim(shade)
    val tilt = r.range(-8f, 8f)
    rotate(tilt, pivot = Offset(w / 2, h * 0.9f)) {
        // Pied
        val sw = w * 0.3f
        val stem = Path().apply {
            moveTo(w / 2 - sw * 0.5f, h * 0.45f)
            cubicTo(w / 2 - sw * 0.6f, h * 0.65f, w / 2 - sw * 0.75f, h * 0.85f, w / 2 - sw * 0.6f, h * 0.9f)
            lineTo(w / 2 + sw * 0.6f, h * 0.9f)
            cubicTo(w / 2 + sw * 0.75f, h * 0.85f, w / 2 + sw * 0.6f, h * 0.65f, w / 2 + sw * 0.5f, h * 0.45f)
            close()
        }
        drawPath(
            stem,
            Brush.horizontalGradient(
                listOf(Color(0xFFD9CDB1).dim(shade), Color(0xFFF5EEDC).dim(shade), Color(0xFFCFC0A0).dim(shade)),
                startX = w / 2 - sw, endX = w / 2 + sw,
            ),
        )
        // Lamelles sous le chapeau
        drawOval(Color(0xFFE6D6B3).dim(shade * 0.9f), Offset(w * 0.12f, h * 0.38f), Size(w * 0.76f, h * 0.12f))
        // Chapeau
        val cap = Path().apply {
            moveTo(w * 0.06f, h * 0.46f)
            cubicTo(w * 0.06f, h * 0.05f, w * 0.94f, h * 0.05f, w * 0.94f, h * 0.46f)
            quadraticBezierTo(w / 2, h * 0.38f, w * 0.06f, h * 0.46f)
            close()
        }
        drawPath(
            cap,
            Brush.radialGradient(
                listOf(lerp(capColor, Color(0xFFF2B07A), 0.35f), capColor, capColor.dim(0.65f)),
                center = Offset(w * 0.4f, h * 0.18f), radius = w * 0.6f,
            ),
        )
        // Points blancs
        val dots = listOf(0.3f to 0.25f, 0.55f to 0.17f, 0.72f to 0.3f, 0.42f to 0.36f, 0.2f to 0.38f)
        dots.forEach { (dx, dy) ->
            if (r.next() > 0.2f) {
                drawCircle(
                    Color(0xFFFFF8E8).copy(alpha = 0.92f * shade), radius = w * r.range(0.035f, 0.055f),
                    center = Offset(w * dx, h * dy),
                )
            }
        }
    }
}
