package fr.clairiere.nim.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.clairiere.nim.audio.LocalSound
import fr.clairiere.nim.audio.Sfx
import fr.clairiere.nim.game.GameConfig
import fr.clairiere.nim.game.GameState
import fr.clairiere.nim.game.ItemKind
import fr.clairiere.nim.game.Variant
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private fun removeSound(variant: Variant): Sfx = when {
    variant == Variant.Grundy -> Sfx.SPLIT
    variant.kind == ItemKind.STICK || variant.kind == ItemKind.TWIG -> Sfx.REMOVE_WOOD
    variant.kind == ItemKind.LEAF -> Sfx.REMOVE_LEAF
    variant.kind == ItemKind.PEBBLE -> Sfx.REMOVE_STONE
    else -> Sfx.REMOVE_SOFT
}

@Composable
fun GameScreen(
    config: GameConfig,
    haptics: Boolean,
    scores: Map<String, Int>,
    backHandler: BackHandlerHook,
    onWin: (String) -> Unit,
    onRematch: (loser: Int) -> Unit,
    onOtherGame: () -> Unit,
    onQuit: () -> Unit,
    initialState: GameState? = null,
) {
    val pal = LocalPalette.current
    val sound = LocalSound.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(initialState ?: GameState.start(config)) }
    var refusal by remember { mutableStateOf<String?>(null) }
    var refusalJob by remember { mutableStateOf<Job?>(null) }
    var showRules by remember { mutableStateOf(false) }
    var confirmQuit by remember { mutableStateOf(false) }
    var showEnd by remember { mutableStateOf(initialState?.isOver == true) }

    fun refuse(msg: String) {
        sound.play(Sfx.INVALID)
        refusal = msg
        refusalJob?.cancel()
        refusalJob = scope.launch { delay(2200); refusal = null }
    }

    fun tap(id: Int) {
        val before = state.selection
        val (next, reason) = state.tap(id)
        if (reason != null) {
            refuse(reason)
            return
        }
        state = next
        refusal = null
        if (next.selection.size >= before.size && next.selection != before) sound.play(Sfx.SELECT) else sound.play(Sfx.DESELECT)
        if (haptics) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    fun play() {
        if (!state.canPlay) {
            state.selectionError?.let { refuse(it) }
            return
        }
        val next = state.play()
        state = next
        sound.play(removeSound(config.variant))
        if (haptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        scope.launch {
            if (next.isOver) {
                onWin(config.players[next.winner!!])
                delay(700)
                sound.play(Sfx.WIN)
                delay(250)
                showEnd = true
            } else {
                delay(450)
                sound.play(Sfx.TURN)
            }
        }
    }

    backHandler(true) {
        when {
            showRules -> showRules = false
            confirmQuit -> confirmQuit = false
            state.isOver -> onQuit()
            state.moves == 0 -> onQuit()
            else -> confirmQuit = true
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            ScreenHeader(config.variant.title, onBack = { if (state.moves == 0 || state.isOver) onQuit() else confirmQuit = true }) {
                RoundIconButton("?") { showRules = true }
            }
            PlayersBar(state, scores)
            TurnBanner(state)
            BoardView(
                state = state,
                onTap = ::tap,
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            )
            MessageLine(state, refusal)
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WoodButton("Défaire", small = true, enabled = state.history.isNotEmpty() && !state.isOver, accent = pal.woodDark) {
                    state = state.undo()
                    showEnd = false
                }
                WoodButton("Effacer", small = true, enabled = state.selection.isNotEmpty(), accent = pal.woodDark) {
                    state = state.clearSelection()
                }
                Spacer(Modifier.weight(1f))
                WoodButton(
                    if (config.variant == Variant.Grundy) "Partager" else "Valider",
                    enabled = state.canPlay,
                    accent = pal.players[state.current],
                    onClick = ::play,
                )
            }
        }

        Overlay(showRules, onDismiss = { showRules = false }) {
            Title(config.variant.title, size = 22)
            Spacer(Modifier.height(8.dp))
            config.variant.rules.forEach { LeafBullet(it) }
            Spacer(Modifier.height(6.dp))
            Body("Partie en mode ${if (config.misere) "misère" else "normal"} : " + modeExplanation(config.variant, config.misere).lowercase(), soft = true, size = 14)
            Spacer(Modifier.height(14.dp))
            WoodButton("Compris", Modifier.align(Alignment.End), small = true) { showRules = false }
        }

        Overlay(confirmQuit, onDismiss = { confirmQuit = false }) {
            Title("Quitter la partie ?", size = 22)
            Spacer(Modifier.height(8.dp))
            Body("La partie en cours sera abandonnée.", soft = true)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.align(Alignment.End)) {
                WoodButton("Rester", small = true, accent = pal.players[0]) { confirmQuit = false }
                WoodButton("Quitter", small = true, accent = pal.woodDark) { confirmQuit = false; onQuit() }
            }
        }

        EndOverlay(
            visible = showEnd && state.isOver,
            state = state,
            scores = scores,
            onRematch = { onRematch(1 - state.winner!!) },
            onOtherGame = onOtherGame,
        )
    }
}

@Composable
private fun PlayersBar(state: GameState, scores: Map<String, Int>) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        state.config.players.forEachIndexed { i, name ->
            PlayerBadge(name, i, active = state.current == i && !state.isOver, score = scores[name] ?: 0, Modifier.weight(1f))
        }
    }
}

@Composable
private fun PlayerBadge(name: String, index: Int, active: Boolean, score: Int, modifier: Modifier) {
    val pal = LocalPalette.current
    val color = pal.players[index]
    val t by animateFloatAsState(if (active) 1f else 0f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow), label = "active")
    Row(
        modifier
            .graphicsLayer { scaleX = 0.94f + 0.06f * t; scaleY = 0.94f + 0.06f * t; alpha = 0.6f + 0.4f * t }
            .shadow((2 + 6 * t).dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(lerp(pal.panel, lerp(pal.panel, color, 0.22f), t))
            .border((1 + t).dp, color.copy(alpha = 0.4f + 0.6f * t), RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(8.dp))
        Body(name, Modifier.weight(1f), size = 15)
        Body("$score", soft = true, size = 15)
    }
}

@Composable
private fun TurnBanner(state: GameState) {
    val pal = LocalPalette.current
    AnimatedContent(
        targetState = if (state.isOver) -1 else state.current,
        transitionSpec = {
            (slideInVertically(tween(450)) { it / 2 } + fadeIn(tween(450))) togetherWith
                (slideOutVertically(tween(300)) { -it / 2 } + fadeOut(tween(300)))
        },
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        label = "turn",
    ) { cur ->
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (cur >= 0) {
                androidx.compose.material3.Text(
                    "À toi de jouer, ${state.config.players[cur]}",
                    color = pal.players[cur].let { lerp(it, pal.ink, 0.25f) },
                    fontFamily = TitleFont,
                    fontSize = 21.sp,
                    textAlign = TextAlign.Center,
                )
            } else {
                Body("Partie terminée", soft = true, size = 18)
            }
        }
    }
}

@Composable
private fun MessageLine(state: GameState, refusal: String?) {
    val hint = state.variant.hint.let { if (it.contains("%d")) it.replace("%d", state.config.preset.maxTake.toString()) else it }
    val message = refusal ?: if (state.selection.isNotEmpty()) state.selectionError ?: selectionSummary(state) else hint
    val warn = refusal != null || (state.selection.isNotEmpty() && state.selectionError != null)
    val pal = LocalPalette.current
    AnimatedContent(
        targetState = message to warn,
        transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(200)) },
        modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp).padding(horizontal = 20.dp),
        label = "message",
    ) { (msg, isWarn) ->
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (isWarn) pal.players[1].copy(alpha = 0.18f) else pal.panel.copy(alpha = 0.7f))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) { Body(msg, size = 15, align = TextAlign.Center) }
        }
    }
}

private fun selectionSummary(state: GameState): String {
    val n = state.selection.size
    val name = when (state.variant.kind) {
        ItemKind.STICK -> "bâton"
        ItemKind.TWIG -> "brindille"
        ItemKind.ACORN -> "gland"
        ItemKind.LEAF -> "feuille"
        ItemKind.PEBBLE -> "galet"
        ItemKind.MUSHROOM -> "champignon"
    }
    if (state.variant == Variant.Grundy) {
        val pile = state.board.locate(state.selection.first())?.first ?: 0
        val total = state.board.piles[pile].size
        return "Partager en ${total - n} et $n"
    }
    return "$n $name${if (n > 1) "s" else ""} choisi${if (state.variant.kind == ItemKind.TWIG || state.variant.kind == ItemKind.LEAF) "e" else ""}${if (n > 1) "s" else ""}"
}

/** Voile sombre + panneau centré, avec apparition douce. */
@Composable
fun Overlay(visible: Boolean, onDismiss: () -> Unit, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    AnimatedVisibility(visible, enter = fadeIn(tween(350)), exit = fadeOut(tween(250))) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.35f))
                .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Panel(
                Modifier
                    .padding(24.dp)
                    .widthIn(max = 420.dp)
                    .clickable(remember { MutableInteractionSource() }, indication = null) {},
                padding = 22.dp,
                content = content,
            )
        }
    }
}

@Composable
private fun EndOverlay(
    visible: Boolean,
    state: GameState,
    scores: Map<String, Int>,
    onRematch: () -> Unit,
    onOtherGame: () -> Unit,
) {
    val pal = LocalPalette.current
    AnimatedVisibility(visible, enter = fadeIn(tween(600)), exit = fadeOut(tween(300))) {
        val winner = state.winner ?: 0
        val loser = 1 - winner
        val names = state.config.players
        val time = rememberAmbientTime()
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                drawCelebration(time.value, listOf(pal.players[winner], Color(0xFFF2C14E), Color(0xFFE88D67), Color(0xFFF6E7C1), pal.moss))
            }
            AnimatedVisibility(true, enter = scaleIn(spring(dampingRatio = 0.6f), initialScale = 0.85f) + fadeIn()) {
                Panel(Modifier.padding(24.dp).widthIn(max = 420.dp), padding = 24.dp) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        ItemTrio(state.variant.kind, Modifier.size(80.dp))
                        Spacer(Modifier.height(10.dp))
                        Title("${names[winner]} gagne !", size = 28)
                        Spacer(Modifier.height(6.dp))
                        Body(endReason(state, names[winner], names[loser]), soft = true, size = 15, align = TextAlign.Center)
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                            names.forEachIndexed { i, n ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(10.dp).clip(CircleShape).background(pal.players[i]))
                                    Spacer(Modifier.width(6.dp))
                                    Body("$n : ${scores[n] ?: 0}", size = 15)
                                }
                            }
                        }
                        Spacer(Modifier.height(20.dp))
                        WoodButton("Revanche", Modifier.fillMaxWidth(), accent = pal.players[loser], onClick = onRematch)
                        Spacer(Modifier.height(10.dp))
                        WoodButton("Changer de jeu", Modifier.fillMaxWidth(), small = true, accent = pal.woodDark, onClick = onOtherGame)
                    }
                }
            }
        }
    }
}

private fun endReason(state: GameState, winner: String, loser: String): String {
    val v = state.variant
    val misere = state.config.misere
    return if (v == Variant.Grundy) {
        if (misere) "$loser a joué le dernier coup : en misère, c'est perdu." else "$loser ne peut plus partager aucun tas."
    } else {
        if (misere) "$loser a dû prendre ${v.kind.lastLabel()}." else "$winner a pris ${v.kind.lastLabel()}."
    }
}
