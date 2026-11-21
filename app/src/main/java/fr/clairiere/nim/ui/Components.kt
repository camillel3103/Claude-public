package fr.clairiere.nim.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.clairiere.nim.audio.LocalSound
import fr.clairiere.nim.audio.Sfx
import androidx.compose.material3.Text

/** Clic sans effet d'onde Material, avec un léger enfoncement animé. */
@Composable
fun Modifier.softClick(enabled: Boolean = true, sound: Boolean = true, onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && enabled) 0.95f else 1f, spring(dampingRatio = 0.5f), label = "press")
    val sfx = LocalSound.current
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interactionSource = source, indication = null, enabled = enabled) {
            if (sound) sfx.play(Sfx.TAP)
            onClick()
        }
}

/** Panneau façon papier ancien posé sur la mousse. */
@Composable
fun Panel(modifier: Modifier = Modifier, padding: Dp = 18.dp, content: @Composable ColumnScope.() -> Unit) {
    val pal = LocalPalette.current
    Column(
        modifier
            .shadow(10.dp, RoundedCornerShape(22.dp), ambientColor = Color.Black.copy(alpha = 0.3f))
            .clip(RoundedCornerShape(22.dp))
            .background(pal.panel)
            .border(1.5.dp, pal.panelBorder.copy(alpha = 0.6f), RoundedCornerShape(22.dp))
            .padding(padding),
        content = content,
    )
}

/** Bouton en planche de bois, avec veines dessinées. */
@Composable
fun WoodButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accent: Color? = null,
    small: Boolean = false,
    onClick: () -> Unit,
) {
    val pal = LocalPalette.current
    val alpha by animateFloatAsState(if (enabled) 1f else 0.45f, label = "enabled")
    val base = accent ?: pal.wood
    val shape = RoundedCornerShape(if (small) 14.dp else 18.dp)
    Box(
        modifier
            .graphicsLayer { this.alpha = alpha }
            .shadow(if (enabled) 6.dp else 0.dp, shape)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(lerp(base, Color.White, 0.18f), base, lerp(base, Color.Black, 0.2f))))
            .drawBehind {
                val grain = Color.Black.copy(alpha = 0.08f)
                for (i in 1..3) {
                    val y = size.height * i / 4f
                    drawLine(grain, Offset(size.width * 0.06f, y), Offset(size.width * 0.94f, y + (i - 2) * 2f), strokeWidth = 2f, cap = StrokeCap.Round)
                }
            }
            .border(1.dp, Color.Black.copy(alpha = 0.18f), shape)
            .softClick(enabled = enabled, onClick = onClick)
            .padding(horizontal = if (small) 14.dp else 24.dp, vertical = if (small) 9.dp else 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = Color(0xFFFFF7E6),
            fontFamily = BodyFont,
            fontWeight = FontWeight.SemiBold,
            fontSize = if (small) 15.sp else 18.sp,
            textAlign = TextAlign.Center,
        )
    }
}

/** Puce de choix (réglages, préréglages) en forme de galet. */
@Composable
fun ChoiceChip(text: String, selected: Boolean, modifier: Modifier = Modifier, accent: Color? = null, onClick: () -> Unit) {
    val pal = LocalPalette.current
    val col = accent ?: pal.wood
    val t by animateFloatAsState(if (selected) 1f else 0f, spring(stiffness = Spring.StiffnessLow), label = "chip")
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(lerp(pal.panel, col, t * 0.9f))
            .border(1.5.dp, lerp(pal.panelBorder, col, t), RoundedCornerShape(50))
            .softClick(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = lerp(pal.ink, Color(0xFFFFF7E6), t), fontFamily = BodyFont, fontSize = 15.sp)
    }
}

/** Petit bouton rond (retour, aide). */
@Composable
fun RoundIconButton(symbol: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val pal = LocalPalette.current
    Box(
        modifier
            .size(44.dp)
            .shadow(4.dp, CircleShape)
            .clip(CircleShape)
            .background(pal.panel)
            .border(1.dp, pal.panelBorder, CircleShape)
            .softClick(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, color = pal.ink, fontSize = 20.sp, fontFamily = BodyFont, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun Title(text: String, modifier: Modifier = Modifier, size: Int = 26) {
    val pal = LocalPalette.current
    Text(
        text,
        modifier = modifier,
        style = TextStyle(
            color = pal.ink,
            fontFamily = TitleFont,
            fontWeight = FontWeight.Bold,
            fontSize = size.sp,
            shadow = androidx.compose.ui.graphics.Shadow(
                color = Color.White.copy(alpha = if (pal.night) 0.1f else 0.5f), offset = Offset(0f, 2f), blurRadius = 2f,
            ),
        ),
    )
}

@Composable
fun Body(text: String, modifier: Modifier = Modifier, soft: Boolean = false, size: Int = 16, align: TextAlign? = null) {
    val pal = LocalPalette.current
    Text(
        text, modifier = modifier, color = if (soft) pal.inkSoft else pal.ink,
        fontFamily = BodyFont, fontSize = size.sp, lineHeight = (size * 1.35f).sp, textAlign = align,
    )
}

/** Puce en forme de petite feuille pour les listes de règles. */
@Composable
fun LeafBullet(text: String) {
    val pal = LocalPalette.current
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(vertical = 3.dp)) {
        Canvas(Modifier.padding(top = 4.dp).size(14.dp)) {
            drawNatureItem(fr.clairiere.nim.game.ItemKind.LEAF, 4, size, 0f, Color.Transparent, pal.night)
        }
        Spacer(Modifier.width(8.dp))
        Body(text, size = 15)
    }
}

@Composable
fun SectionLabel(text: String) {
    val pal = LocalPalette.current
    Text(
        text.uppercase(), color = pal.inkSoft, fontFamily = BodyFont, fontSize = 12.sp,
        letterSpacing = 2.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 14.dp, bottom = 6.dp),
    )
}

@Composable
fun SpacedRow(content: @Composable () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { content() }
}
