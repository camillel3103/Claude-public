package fr.clairiere.nim.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

/** Palette de l'application : une version jour et une version nuit. */
@Immutable
data class Palette(
    val night: Boolean,
    val skyTop: Color,
    val skyBottom: Color,
    val glow: Color,
    val treesFar: Color,
    val treesMid: Color,
    val treesNear: Color,
    val ground: Color,
    val groundDark: Color,
    val moss: Color,
    val panel: Color,
    val panelBorder: Color,
    val ink: Color,
    val inkSoft: Color,
    val wood: Color,
    val woodLight: Color,
    val woodDark: Color,
    val highlight: Color,
    val players: List<Color>,
)

val DayPalette = Palette(
    night = false,
    skyTop = Color(0xFFF3F1DC),
    skyBottom = Color(0xFFC9DBAE),
    glow = Color(0xFFFFF4C2),
    treesFar = Color(0xFFADC497),
    treesMid = Color(0xFF8DAA79),
    treesNear = Color(0xFF6A8C5A),
    ground = Color(0xFF7E9A5A),
    groundDark = Color(0xFF5E7B45),
    moss = Color(0xFF9BB873),
    panel = Color(0xFFF6EEDB),
    panelBorder = Color(0xFFB99B72),
    ink = Color(0xFF3D2B1C),
    inkSoft = Color(0xFF6F5A45),
    wood = Color(0xFF946339),
    woodLight = Color(0xFFC1925F),
    woodDark = Color(0xFF5C3B21),
    highlight = Color(0xFFFFF1A8),
    players = listOf(Color(0xFF4F8A3A), Color(0xFFC0692A)),
)

val NightPalette = Palette(
    night = true,
    skyTop = Color(0xFF14212A),
    skyBottom = Color(0xFF26392F),
    glow = Color(0xFFDCE7F2),
    treesFar = Color(0xFF223529),
    treesMid = Color(0xFF1B2C22),
    treesNear = Color(0xFF14231A),
    ground = Color(0xFF2C4130),
    groundDark = Color(0xFF1D2E21),
    moss = Color(0xFF3E5A3C),
    panel = Color(0xFF25302A),
    panelBorder = Color(0xFF6B5A40),
    ink = Color(0xFFF0E6CC),
    inkSoft = Color(0xFFBFB396),
    wood = Color(0xFF8A5E37),
    woodLight = Color(0xFFB48A5C),
    woodDark = Color(0xFF4E321C),
    highlight = Color(0xFFF6E58D),
    players = listOf(Color(0xFF8CC46E), Color(0xFFE79A56)),
)

val LocalPalette = staticCompositionLocalOf { DayPalette }

/** Police à empattements : rappelle les lettres gravées dans le bois. */
val TitleFont: FontFamily = FontFamily.Serif
val BodyFont: FontFamily = FontFamily.Serif

@Composable
fun ClairiereTheme(night: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalPalette provides if (night) NightPalette else DayPalette) {
        content()
    }
}
