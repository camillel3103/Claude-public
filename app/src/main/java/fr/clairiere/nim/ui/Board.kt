package fr.clairiere.nim.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import fr.clairiere.nim.game.Board
import fr.clairiere.nim.game.GameState
import fr.clairiere.nim.game.ItemKind
import fr.clairiere.nim.game.LayoutStyle
import fr.clairiere.nim.game.Variant
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Position calculée de chaque pion sur le plateau, en pixels. */
class BoardLayout(
    val cellW: Float,
    val cellH: Float,
    val positions: Map<Int, Offset>,
    val dimmed: Set<Int>,
    /** Emplacements vides (Kayles) : petites cuvettes dans la mousse. */
    val emptySlots: List<Offset>,
)

private class Line(val ids: List<Int>, val gapAt: Int = -1, val newPile: Boolean)

private const val SPACING = 1.12f
private const val LINE_SPACING = 1.12f

private fun perLine(variant: Variant): Int = when (variant) {
    Variant.Subtraction -> 7
    Variant.Wythoff -> 6
    else -> Int.MAX_VALUE
}

private fun buildLines(variant: Variant, board: Board, selection: Set<Int>): List<Line> {
    val max = perLine(variant)
    val lines = mutableListOf<Line>()
    for (pile in board.piles) {
        var gap = -1
        if (variant == Variant.Grundy && selection.isNotEmpty() && pile.any { it in selection }) {
            gap = pile.indexOfFirst { it in selection }
        }
        if (pile.isEmpty()) {
            lines += Line(emptyList(), newPile = true)
            continue
        }
        pile.chunked(max).forEachIndexed { i, chunk -> lines += Line(chunk, if (i == 0) gap else -1, newPile = i == 0) }
    }
    return lines
}

fun computeLayout(
    variant: Variant,
    board: Board,
    initial: Board,
    selection: Set<Int>,
    width: Float,
    height: Float,
    maxCell: Float,
): BoardLayout {
    val aspect = variant.kind.aspect()
    if (variant.layout == LayoutStyle.FIXED_SLOTS) {
        val slots = initial.total
        val cellW = min(width / (slots * SPACING), min(maxCell, height * 0.6f / aspect))
        val cellH = cellW * aspect
        val rowW = slots * cellW * SPACING
        val x0 = (width - rowW) / 2 + cellW * (SPACING - 1) / 2
        val y0 = (height - cellH) / 2
        val present = board.piles.flatten().toSet()
        val positions = present.associateWith { id -> Offset(x0 + id * cellW * SPACING, y0) }
        val empty = (0 until slots).filter { it !in present }.map { Offset(x0 + it * cellW * SPACING, y0) }
        return BoardLayout(cellW, cellH, positions, emptySet(), empty)
    }

    val lines = buildLines(variant, board, selection)
    // Pour Grundy, la taille suit le plateau actuel (les tas se multiplient) ; sinon elle reste stable.
    val initialLines = if (variant == Variant.Grundy) lines else buildLines(variant, initial, emptySet())
    val slotsOf = { l: Line -> l.ids.size + if (l.gapAt >= 0) 0.7f else 0f }
    val maxSlots = maxOf(lines.maxOfOrNull(slotsOf) ?: 1f, initialLines.maxOfOrNull(slotsOf) ?: 1f, 1f)
    val pileGapCount = { ls: List<Line> -> ls.count { it.newPile } - 1 }
    val lineCount = maxOf(lines.size, initialLines.size, 1)
    val gaps = maxOf(pileGapCount(lines), pileGapCount(initialLines), 0)
    // Les tas qui passent à la ligne (glands, feuilles) ont besoin d'un espace plus marqué entre eux
    val pileGap = if (perLine(variant) != Int.MAX_VALUE) 0.8f else 0.35f
    // Hauteur totale (en unités de cellH) : lignes + espaces entre tas
    val unitsH = lineCount * LINE_SPACING + gaps * pileGap
    val cellW = min(min(width / (maxSlots * SPACING), height / (unitsH * aspect)), maxCell)
    val cellH = cellW * aspect

    val usedH = (lines.size * LINE_SPACING + maxOf(pileGapCount(lines), 0) * pileGap) * cellH
    var y = (height - usedH) / 2 + cellH * (LINE_SPACING - 1) / 2
    val positions = HashMap<Int, Offset>()
    val dimmed = HashSet<Int>()
    lines.forEachIndexed { li, line ->
        if (li > 0 && line.newPile) y += pileGap * cellH
        val slots = slotsOf(line)
        var x = (width - slots * cellW * SPACING) / 2 + cellW * (SPACING - 1) / 2
        line.ids.forEachIndexed { i, id ->
            if (i == line.gapAt) x += 0.7f * cellW * SPACING
            positions[id] = Offset(x, y)
            x += cellW * SPACING
        }
        if (variant == Variant.Grundy && line.ids.size < 3) dimmed += line.ids
        y += cellH * LINE_SPACING
    }
    return BoardLayout(cellW, cellH, positions, dimmed, emptyList())
}

/** Pion en train de quitter le plateau (animation d'envol). */
private class Departure(val id: Int, val pos: Offset, val w: Float, val h: Float, val dir: Float)

/** Repère les pions retirés d'un coup à l'autre pour les faire s'envoler. */
private class DepartTracker {
    var board: Board? = null
    var layout: BoardLayout? = null
    var firstShown = true
    val departing = ArrayList<Departure>()

    fun update(board: Board, layout: BoardLayout) {
        val prevBoard = this.board
        val prevLayout = this.layout
        if (prevBoard != null && prevBoard != board && prevLayout != null) {
            firstShown = false
            val now = board.piles.flatten().toSet()
            departing.removeAll { it.id in now }
            prevBoard.piles.flatten().filter { it !in now }.forEach { id ->
                val p = prevLayout.positions[id] ?: return@forEach
                departing += Departure(id, p, prevLayout.cellW, prevLayout.cellH, if (id % 2 == 0) 1f else -1f)
            }
        }
        this.board = board
        this.layout = layout
    }
}

@Composable
fun BoardView(state: GameState, onTap: (Int) -> Unit, modifier: Modifier = Modifier) {
    val pal = LocalPalette.current
    val density = LocalDensity.current
    val variant = state.variant
    val initial = remember(state.config) { Board.of(state.config.preset.piles) }
    BoxWithConstraints(modifier) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val maxCell = (if (variant.kind.aspect() > 2f) 40f else 72f) * density.density
        val layout = remember(state.board, state.selection, w, h) {
            computeLayout(variant, state.board, initial, state.selection, w, h, maxCell)
        }
        val tracker = remember { DepartTracker() }
        var version by remember { mutableIntStateOf(0) }
        tracker.update(state.board, layout)
        check(version >= 0) // lecture : redessine quand un envol se termine

        // Clairière moussue sous les pions
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2, size.height / 2)
            drawOval(
                Brush.radialGradient(
                    listOf(pal.moss.copy(alpha = 0.55f), pal.moss.copy(alpha = 0.25f), pal.moss.copy(alpha = 0f)),
                    center = c, radius = size.maxDimension * 0.55f,
                ),
                topLeft = Offset(-size.width * 0.05f, size.height * 0.02f),
                size = Size(size.width * 1.1f, size.height * 0.96f),
            )
            layout.emptySlots.forEach { p ->
                drawOval(
                    Color.Black.copy(alpha = 0.12f),
                    topLeft = Offset(p.x + layout.cellW * 0.1f, p.y + layout.cellH * 0.55f),
                    size = Size(layout.cellW * 0.8f, layout.cellH * 0.3f),
                )
            }
        }

        val order = state.board.piles.flatten()
        order.forEachIndexed { index, id ->
            val target = layout.positions[id] ?: return@forEachIndexed
            key(id) {
                ItemView(
                    id = id,
                    kind = variant.kind,
                    target = target,
                    cellW = layout.cellW,
                    cellH = layout.cellH,
                    selected = id in state.selection,
                    dim = id in layout.dimmed,
                    appearDelay = if (tracker.firstShown) 120L + index * 45L else 0L,
                    onTap = onTap,
                )
            }
        }
        tracker.departing.toList().forEach { d ->
            key("departing-${d.id}") {
                DepartingView(d, variant.kind) {
                    tracker.departing.remove(d)
                    version++
                }
            }
        }
    }
}

@Composable
private fun ItemView(
    id: Int,
    kind: ItemKind,
    target: Offset,
    cellW: Float,
    cellH: Float,
    selected: Boolean,
    dim: Boolean,
    appearDelay: Long,
    onTap: (Int) -> Unit,
) {
    val pal = LocalPalette.current
    val density = LocalDensity.current
    val pos by animateOffsetAsState(target, spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessLow), label = "pos")
    val w by animateFloatAsState(cellW, spring(stiffness = Spring.StiffnessLow), label = "w")
    val h by animateFloatAsState(cellH, spring(stiffness = Spring.StiffnessLow), label = "h")
    val sel by animateFloatAsState(
        if (selected) 1f else 0f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow), label = "sel",
    )
    val dimA by animateFloatAsState(if (dim) 0.45f else 1f, tween(600), label = "dim")
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(appearDelay)
        appear.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow))
    }
    val source = remember { MutableInteractionSource() }
    Box(
        Modifier
            .offset { IntOffset(pos.x.roundToInt(), pos.y.roundToInt()) }
            .size(with(density) { w.toDp() }, with(density) { h.toDp() })
            .graphicsLayer {
                val a = appear.value
                translationY = -sel * h * 0.1f - (1f - a) * h * 0.25f
                val s = (0.5f + 0.5f * a) * (1f + 0.07f * sel)
                scaleX = s
                scaleY = s
                alpha = a.coerceIn(0f, 1f) * dimA
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
            }
            .clickable(interactionSource = source, indication = null) { onTap(id) }
            .drawBehind { drawNatureItem(kind, id * 37 + 11, size, sel, pal.highlight, pal.night) },
    )
}

@Composable
private fun DepartingView(d: Departure, kind: ItemKind, onDone: () -> Unit) {
    val pal = LocalPalette.current
    val density = LocalDensity.current
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        p.animateTo(1f, tween(if (kind == ItemKind.LEAF) 1500 else 1100, easing = FastOutSlowInEasing))
        onDone()
    }
    Box(
        Modifier
            .offset { IntOffset(d.pos.x.roundToInt(), d.pos.y.roundToInt()) }
            .size(with(density) { d.w.toDp() }, with(density) { d.h.toDp() })
            .graphicsLayer {
                val t = p.value
                val dir = d.dir
                when (kind) {
                    ItemKind.LEAF -> {
                        translationX = dir * d.w * (3f * t + 0.5f * sin(t * PI.toFloat() * 3f))
                        translationY = -d.h * 1.6f * t + d.h * 0.4f * sin(t * PI.toFloat() * 2f)
                        rotationZ = dir * (t * 220f + 25f * sin(t * 14f))
                        alpha = 1f - t * t
                    }
                    ItemKind.STICK, ItemKind.TWIG -> {
                        translationX = dir * d.w * 2.2f * t
                        translationY = -d.h * 0.5f * t
                        rotationZ = dir * 80f * t
                        alpha = 1f - t * t
                    }
                    else -> {
                        translationX = dir * d.w * 1.2f * t
                        translationY = -d.h * (1.6f * t - 0.9f * t * t)
                        val s = 1f - 0.45f * t
                        scaleX = s
                        scaleY = s
                        alpha = 1f - t * t
                    }
                }
            }
            .drawBehind { drawNatureItem(kind, d.id * 37 + 11, size, 1f - p.value, pal.highlight, pal.night) },
    )
}
