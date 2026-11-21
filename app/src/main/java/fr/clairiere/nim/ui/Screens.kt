package fr.clairiere.nim.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.clairiere.nim.game.GameConfig
import fr.clairiere.nim.game.ItemKind
import fr.clairiere.nim.game.Variant
import kotlinx.coroutines.delay
import kotlin.random.Random

// ------------------------------------------------------------------------------------------------ Accueil

@Composable
fun HomeScreen(onPlay: () -> Unit, onSettings: () -> Unit) {
    val float = rememberInfiniteTransition(label = "float")
    val bob by float.animateFloat(
        -1f, 1f, infiniteRepeatable(tween(3200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "bob",
    )
    Column(
        Modifier.fillMaxSize().systemBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Logo(Modifier.size(150.dp).graphicsLayer { translationY = bob * 6.dp.toPx(); rotationZ = bob * 1.5f })
        Spacer(Modifier.height(12.dp))
        Title("Clairière", size = 52)
        Body("Jeux de Nim à deux", soft = true, size = 18)
        Spacer(Modifier.height(40.dp))
        WoodButton("Jouer", Modifier.widthIn(min = 220.dp), onClick = onPlay)
        Spacer(Modifier.height(14.dp))
        WoodButton("Réglages", Modifier.widthIn(min = 220.dp), accent = LocalPalette.current.woodDark, onClick = onSettings)
        Spacer(Modifier.height(36.dp))
        Body(
            "Un seul téléphone pour deux :\npassez-le à l'autre joueur après chaque coup.",
            soft = true, size = 14, align = TextAlign.Center,
        )
    }
}

/** Logo : trois bâtons croisés posés sur deux feuilles. */
@Composable
fun Logo(modifier: Modifier = Modifier) {
    val pal = LocalPalette.current
    Canvas(modifier) {
        val s = size.minDimension
        val leaf = Size(s * 0.42f, s * 0.48f)
        translate(s * 0.14f, s * 0.42f) { rotate(-35f, Offset(leaf.width / 2, leaf.height)) { drawNatureItem(ItemKind.LEAF, 3, leaf, 0f, Color.Transparent, pal.night) } }
        translate(s * 0.44f, s * 0.42f) { rotate(35f, Offset(leaf.width / 2, leaf.height)) { drawNatureItem(ItemKind.LEAF, 9, leaf, 0f, Color.Transparent, pal.night) } }
        val stick = Size(s * 0.16f, s * 0.7f)
        listOf(-24f to 1, 0f to 2, 24f to 5).forEach { (angle, seed) ->
            translate(s / 2 - stick.width / 2, s * 0.12f) {
                rotate(angle, Offset(stick.width / 2, stick.height * 0.85f)) {
                    drawNatureItem(ItemKind.STICK, seed, stick, 0f, Color.Transparent, pal.night)
                }
            }
        }
        val acorn = Size(s * 0.18f, s * 0.22f)
        translate(s * 0.62f, s * 0.7f) { drawNatureItem(ItemKind.ACORN, 4, acorn, 0f, Color.Transparent, pal.night) }
    }
}

// ------------------------------------------------------------------------------------------------ Choix du jeu

@Composable
fun ScreenHeader(title: String, onBack: () -> Unit, trailing: (@Composable () -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoundIconButton("←", onClick = onBack)
        Spacer(Modifier.width(14.dp))
        Title(title, Modifier.weight(1f), size = 24)
        trailing?.invoke()
    }
}

@Composable
fun VariantsScreen(onBack: () -> Unit, onPick: (Variant) -> Unit) {
    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        ScreenHeader("Choisis un jeu", onBack)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Variant.all.forEachIndexed { i, v -> VariantCard(v, i) { onPick(v) } }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun VariantCard(variant: Variant, index: Int, onClick: () -> Unit) {
    val pal = LocalPalette.current
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(80L * index)
        appear.animateTo(1f, tween(550, easing = FastOutSlowInEasing))
    }
    Panel(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = appear.value
                translationY = (1f - appear.value) * 40.dp.toPx()
            }
            .softClick(onClick = onClick),
        padding = 14.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ItemTrio(variant.kind, Modifier.size(74.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Title(variant.title, size = 20)
                Body(variant.subtitle, soft = true, size = 14)
                if (variant.misereByDefault) {
                    Spacer(Modifier.height(4.dp))
                    Box(
                        Modifier.clip(RoundedCornerShape(50)).background(pal.players[1].copy(alpha = 0.18f))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    ) { Body("misère", size = 12) }
                }
            }
            Body("›", size = 28, soft = true)
        }
    }
}

/** Petite vignette : trois objets de la variante. */
@Composable
fun ItemTrio(kind: ItemKind, modifier: Modifier = Modifier) {
    val pal = LocalPalette.current
    Canvas(modifier.clip(CircleShape).background(pal.moss.copy(alpha = 0.35f))) {
        val s = size.minDimension
        val a = kind.aspect()
        val h = s * 0.62f
        val w = (h / a).coerceAtMost(s * 0.32f)
        val hh = w * a
        val y = (s - hh) / 2 + s * 0.03f
        listOf(-1, 0, 1).forEachIndexed { i, k ->
            translate(s / 2 - w / 2 + k * w * 0.95f, y + (if (k == 0) -s * 0.04f else 0f)) {
                drawNatureItem(kind, i * 5 + 2, Size(w, hh), 0f, Color.Transparent, pal.night)
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------ Préparation

fun ItemKind.lastLabel(): String = when (this) {
    ItemKind.STICK -> "le dernier bâton"
    ItemKind.TWIG -> "la dernière brindille"
    ItemKind.ACORN -> "le dernier gland"
    ItemKind.LEAF -> "la dernière feuille"
    ItemKind.PEBBLE -> "le dernier galet"
    ItemKind.MUSHROOM -> "le dernier champignon"
}

fun modeExplanation(variant: Variant, misere: Boolean): String =
    if (variant == Variant.Grundy) {
        if (misere) "Celui qui joue le dernier coup perd." else "Celui qui joue le dernier coup gagne."
    } else {
        if (misere) "Celui qui prend ${variant.kind.lastLabel()} perd." else "Celui qui prend ${variant.kind.lastLabel()} gagne."
    }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SetupScreen(variant: Variant, settings: AppSettings, onBack: () -> Unit, onStart: (GameConfig) -> Unit) {
    val pal = LocalPalette.current
    var preset by remember { mutableStateOf(variant.presets.first()) }
    var misere by remember { mutableStateOf(variant.misereByDefault) }
    var name1 by remember { mutableStateOf(settings.player1) }
    var name2 by remember { mutableStateOf(settings.player2) }
    var starter by remember { mutableStateOf(0) } // 0, 1, ou 2 = au hasard

    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        ScreenHeader(variant.title, onBack)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Panel(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ItemTrio(variant.kind, Modifier.size(56.dp))
                    Spacer(Modifier.width(12.dp))
                    Title("Règles", size = 20)
                }
                Spacer(Modifier.height(8.dp))
                variant.rules.forEach { LeafBullet(it) }
            }
            Panel(Modifier.fillMaxWidth()) {
                if (variant.presets.size > 1) {
                    SectionLabel("Plateau")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        variant.presets.forEach { p -> ChoiceChip(p.label, p == preset) { preset = p } }
                    }
                }
                SectionLabel("Mode")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceChip("Normal", !misere) { misere = false }
                    ChoiceChip("Misère", misere) { misere = true }
                }
                Spacer(Modifier.height(6.dp))
                Body(modeExplanation(variant, misere), soft = true, size = 14)

                SectionLabel("Joueurs")
                NameField(name1, pal.players[0]) { name1 = it.take(16) }
                Spacer(Modifier.height(8.dp))
                NameField(name2, pal.players[1]) { name2 = it.take(16) }

                SectionLabel("Qui commence ?")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceChip(name1.ifBlank { "Joueur 1" }, starter == 0, accent = pal.players[0]) { starter = 0 }
                    ChoiceChip(name2.ifBlank { "Joueur 2" }, starter == 1, accent = pal.players[1]) { starter = 1 }
                    ChoiceChip("Au hasard", starter == 2) { starter = 2 }
                }
            }
            WoodButton("Commencer la partie", Modifier.fillMaxWidth()) {
                val n1 = name1.trim().ifBlank { "Joueur 1" }
                var n2 = name2.trim().ifBlank { "Joueur 2" }
                if (n2 == n1) n2 = "$n2 (2)"
                val first = if (starter == 2) Random.nextInt(2) else starter
                onStart(GameConfig(variant, preset, misere, listOf(n1, n2), first))
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun NameField(value: String, color: Color, onChange: (String) -> Unit) {
    val pal = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(pal.panel)
            .border(1.5.dp, color.copy(alpha = 0.7f), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(14.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(12.dp))
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(color = pal.ink, fontFamily = BodyFont, fontSize = 17.sp),
            cursorBrush = SolidColor(color),
            modifier = Modifier.weight(1f),
        )
    }
}

// ------------------------------------------------------------------------------------------------ Réglages

@Composable
fun SettingsScreen(settings: AppSettings, onChange: (AppSettings) -> Unit, onBack: () -> Unit) {
    val pal = LocalPalette.current
    val sliderColors = SliderDefaults.colors(
        thumbColor = pal.wood, activeTrackColor = pal.players[0], inactiveTrackColor = pal.panelBorder.copy(alpha = 0.4f),
    )
    val switchColors = SwitchDefaults.colors(
        checkedThumbColor = Color.White, checkedTrackColor = pal.players[0],
        uncheckedThumbColor = pal.panelBorder, uncheckedTrackColor = pal.panel, uncheckedBorderColor = pal.panelBorder,
    )
    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        ScreenHeader("Réglages", onBack)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Panel(Modifier.fillMaxWidth()) {
                SectionLabel("Sons")
                Body("Ambiance de la forêt")
                Slider(settings.ambience, { onChange(settings.copy(ambience = it)) }, colors = sliderColors)
                Body("Effets sonores")
                Slider(settings.effects, { onChange(settings.copy(effects = it)) }, colors = sliderColors)
                SectionLabel("Confort")
                SettingSwitch("Vibrations légères", settings.haptics, switchColors) { onChange(settings.copy(haptics = it)) }
                SettingSwitch("Ambiance de nuit", settings.night, switchColors) { onChange(settings.copy(night = it)) }
            }
            Panel(Modifier.fillMaxWidth()) {
                SectionLabel("À propos")
                Body(
                    "Clairière réunit six jeux de Nim, des jeux de stratégie très anciens. Il n'y a pas de hasard : " +
                        "avec de l'entraînement, on découvre les positions gagnantes.",
                    size = 15,
                )
                Spacer(Modifier.height(8.dp))
                Body("Les dessins et les sons ont été créés pour l'application.", soft = true, size = 13)
            }
        }
    }
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, colors: androidx.compose.material3.SwitchColors, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Body(label, Modifier.weight(1f))
        Switch(checked, onChange, colors = colors)
    }
}
