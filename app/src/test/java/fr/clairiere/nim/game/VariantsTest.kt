package fr.clairiere.nim.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VariantsTest {

    private fun start(v: Variant, preset: Preset = v.presets.first(), misere: Boolean = false) =
        GameState.start(GameConfig(v, preset, misere, listOf("A", "B")))

    private fun GameState.tapAll(vararg ids: Int): GameState =
        ids.fold(this) { s, id -> s.tap(id).first }

    @Test fun classicOnlyOnePile() {
        var s = start(Variant.Classic) // tas : [0,1,2] [3..6] [7..11]
        s = s.tapAll(0, 1)
        assertEquals(setOf(0, 1), s.selection)
        s = s.tapAll(3) // changer de tas remplace la sélection
        assertEquals(setOf(3), s.selection)
        assertTrue(s.canPlay)
        s = s.play()
        assertEquals(listOf(3, 3, 5), s.board.piles.map { it.size })
        assertEquals(1, s.current)
    }

    @Test fun classicNormalLastTakerWins() {
        var s = start(Variant.Classic, Preset("1", listOf(1)))
        s = s.tapAll(0).play()
        assertEquals(0, s.winner)
    }

    @Test fun classicMisereLastTakerLoses() {
        var s = start(Variant.Classic, Preset("1", listOf(1)), misere = true)
        s = s.tapAll(0).play()
        assertEquals(1, s.winner)
    }

    @Test fun emptySelectionCannotBePlayed() {
        val s = start(Variant.Classic)
        assertFalse(s.canPlay)
        assertEquals(s, s.play())
    }

    @Test fun subtractionLimit() {
        val preset = Variant.Subtraction.presets.first()
        var s = start(Variant.Subtraction, preset)
        s = s.tapAll(0, 1, 2)
        val (s2, refusal) = s.tap(3)
        assertNotNull(refusal)
        assertEquals(3, s2.selection.size)
        s = s.play()
        assertEquals(12, s.board.total)
    }

    @Test fun wythoffEqualCounts() {
        var s = start(Variant.Wythoff) // [0..4] [5..11]
        s = s.tapAll(0, 5, 6)
        assertNotNull(s.selectionError)
        s = s.tapAll(1)
        assertNull(s.selectionError)
        s = s.play()
        assertEquals(listOf(3, 5), s.board.piles.map { it.size })
    }

    @Test fun kaylesSplitsRow() {
        var s = start(Variant.Kayles) // 7 galets
        s = s.tapAll(3)
        s = s.play()
        assertEquals(listOf(listOf(0, 1, 2), listOf(4, 5, 6)), s.board.piles)
        // Deux galets non voisins : le second remplace le premier
        s = s.tapAll(2, 4)
        assertEquals(setOf(4), s.selection)
        s = s.tapAll(5)
        assertEquals(setOf(4, 5), s.selection)
        assertTrue(s.canPlay)
        s = s.play()
        assertEquals(listOf(listOf(0, 1, 2), listOf(6)), s.board.piles)
    }

    @Test fun grundySplit() {
        var s = start(Variant.Grundy) // 7
        val (s1, refused) = s.tap(0)
        assertNotNull(refused)
        s = s1.tapAll(4) // parts 4 et 3
        assertEquals(setOf(4, 5, 6), s.selection)
        assertTrue(s.canPlay)
        s = s.play()
        assertEquals(listOf(4, 3), s.board.piles.map { it.size })
    }

    @Test fun grundyEqualSplitRefused() {
        var s = start(Variant.Grundy, Preset("6", listOf(6)))
        s = s.tapAll(3)
        assertNotNull(s.selectionError)
    }

    @Test fun grundyEndsWhenOnlySmallPiles() {
        var s = start(Variant.Grundy, Preset("3", listOf(3)))
        s = s.tapAll(2).play() // 2 + 1 : plus de coup possible
        assertEquals(0, s.winner) // normal : le dernier à jouer gagne
    }

    @Test fun undoRestoresState() {
        var s = start(Variant.Classic)
        val initial = s.board
        s = s.tapAll(0).play().undo()
        assertEquals(initial, s.board)
        assertEquals(0, s.current)
        assertEquals(0, s.moves)
    }

    @Test fun marienbadDefaultsToMisere() {
        assertTrue(Variant.Marienbad.misereByDefault)
        assertEquals(16, Board.of(Variant.Marienbad.presets.first().piles).total)
    }
}
