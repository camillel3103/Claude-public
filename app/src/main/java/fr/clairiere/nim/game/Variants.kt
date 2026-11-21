package fr.clairiere.nim.game

/** Type d'objet naturel utilisé pour représenter les pions d'une variante. */
enum class ItemKind { STICK, TWIG, ACORN, LEAF, PEBBLE, MUSHROOM }

/** Disposition du plateau à l'écran. */
enum class LayoutStyle {
    /** Chaque tas est une rangée (qui peut passer à la ligne si elle est longue). */
    ROWS,
    /** Une seule ligne où chaque pion garde sa place d'origine : les trous restent visibles. */
    FIXED_SLOTS,
}

/** Un réglage proposé pour démarrer une partie. */
data class Preset(val label: String, val piles: List<Int>, val maxTake: Int = 0)

/**
 * Plateau : une liste de tas, chaque tas étant une liste d'identifiants de pions.
 * Les identifiants sont stables pendant la partie, ce qui permet d'animer chaque pion.
 */
data class Board(val piles: List<List<Int>>) {
    val total: Int get() = piles.sumOf { it.size }

    fun locate(id: Int): Pair<Int, Int>? {
        piles.forEachIndexed { p, pile ->
            val i = pile.indexOf(id)
            if (i >= 0) return p to i
        }
        return null
    }

    companion object {
        fun of(sizes: List<Int>): Board {
            var next = 0
            return Board(sizes.map { n -> List(n) { next++ } })
        }
    }
}

/** Résultat d'un toucher sur un pion : nouvelle sélection, ou refus (avec explication). */
sealed interface TapResult {
    data class Selected(val selection: Set<Int>) : TapResult
    data class Refused(val reason: String) : TapResult
}

sealed class Variant(
    val id: String,
    val title: String,
    val subtitle: String,
    val kind: ItemKind,
    val layout: LayoutStyle,
    val rules: List<String>,
    val presets: List<Preset>,
    val misereByDefault: Boolean,
    /** Consigne courte affichée pendant la partie. */
    val hint: String,
) {
    /** Le joueur qui ne peut plus jouer a perdu (normal) ou gagné (misère). */
    open fun hasMoves(board: Board): Boolean = board.total > 0

    open fun onTap(board: Board, selection: Set<Int>, id: Int, preset: Preset): TapResult {
        val newSel = if (id in selection) selection - id else selection + id
        return TapResult.Selected(newSel)
    }

    /** Retourne null si le coup est valide, sinon un message expliquant pourquoi. */
    abstract fun validate(board: Board, selection: Set<Int>, preset: Preset): String?

    /** Applique la sélection (supposée valide) : par défaut, retire les pions choisis. */
    open fun apply(board: Board, selection: Set<Int>): Board =
        Board(board.piles.map { pile -> pile.filter { it !in selection } }.filter { it.isNotEmpty() })

    // ---------------------------------------------------------------------------------------

    /** Variante « tous les pions choisis doivent venir du même tas ». */
    abstract class SinglePile(
        id: String, title: String, subtitle: String, kind: ItemKind, rules: List<String>,
        presets: List<Preset>, misere: Boolean, hint: String,
    ) : Variant(id, title, subtitle, kind, LayoutStyle.ROWS, rules, presets, misere, hint) {
        override fun onTap(board: Board, selection: Set<Int>, id: Int, preset: Preset): TapResult {
            val pile = board.locate(id)?.first ?: return TapResult.Selected(selection)
            val samePile = selection.all { board.locate(it)?.first == pile }
            if (!samePile) return TapResult.Selected(setOf(id))
            return super.onTap(board, selection, id, preset)
        }

        override fun validate(board: Board, selection: Set<Int>, preset: Preset): String? {
            if (selection.isEmpty()) return "Choisis au moins un objet"
            val piles = selection.mapNotNull { board.locate(it)?.first }.toSet()
            if (piles.size != 1) return "Un seul tas à la fois"
            return null
        }

        // Pas de suppression des tas vides : les rangées gardent leur place.
        override fun apply(board: Board, selection: Set<Int>): Board =
            Board(board.piles.map { pile -> pile.filter { it !in selection } })
    }

    data object Classic : SinglePile(
        id = "classic",
        title = "Nim classique",
        subtitle = "Des bâtons de bois en plusieurs tas",
        kind = ItemKind.STICK,
        rules = listOf(
            "Les bâtons sont rangés en plusieurs tas.",
            "À ton tour, retire autant de bâtons que tu veux, mais dans un seul tas.",
            "Il faut retirer au moins un bâton.",
            "En mode normal, celui qui prend le dernier bâton gagne. En mode misère, il perd.",
        ),
        presets = listOf(
            Preset("3 · 4 · 5", listOf(3, 4, 5)),
            Preset("2 · 4 · 6 · 8", listOf(2, 4, 6, 8)),
            Preset("1 · 3 · 5 · 7 · 9", listOf(1, 3, 5, 7, 9)),
        ),
        misere = false,
        hint = "Retire autant de bâtons que tu veux dans un seul tas",
    )

    data object Marienbad : SinglePile(
        id = "marienbad",
        title = "Marienbad",
        subtitle = "Quatre rangées de brindilles",
        kind = ItemKind.TWIG,
        rules = listOf(
            "Quatre rangées de 1, 3, 5 et 7 brindilles.",
            "À ton tour, retire autant de brindilles que tu veux, dans une seule rangée.",
            "Traditionnellement, celui qui prend la dernière brindille perd (mode misère).",
        ),
        presets = listOf(
            Preset("1 · 3 · 5 · 7", listOf(1, 3, 5, 7)),
        ),
        misere = true,
        hint = "Retire des brindilles dans une seule rangée",
    )

    data object Subtraction : Variant(
        id = "subtraction",
        title = "Soustraction",
        subtitle = "Un panier de glands",
        kind = ItemKind.ACORN,
        layout = LayoutStyle.ROWS,
        rules = listOf(
            "Un seul tas de glands.",
            "À ton tour, retire entre 1 et 3 glands (ou 1 à 4 selon le réglage).",
            "En mode normal, celui qui prend le dernier gland gagne. En mode misère, il perd.",
        ),
        presets = listOf(
            Preset("15 glands · 1 à 3", listOf(15), maxTake = 3),
            Preset("21 glands · 1 à 3", listOf(21), maxTake = 3),
            Preset("25 glands · 1 à 4", listOf(25), maxTake = 4),
        ),
        misereByDefault = false,
        hint = "Retire de 1 à %d glands",
    ) {
        override fun onTap(board: Board, selection: Set<Int>, id: Int, preset: Preset): TapResult {
            if (id !in selection && selection.size >= preset.maxTake) {
                return TapResult.Refused("Pas plus de ${preset.maxTake} glands")
            }
            return super.onTap(board, selection, id, preset)
        }

        override fun validate(board: Board, selection: Set<Int>, preset: Preset): String? = when {
            selection.isEmpty() -> "Choisis au moins un gland"
            selection.size > preset.maxTake -> "Pas plus de ${preset.maxTake} glands"
            else -> null
        }

        override fun apply(board: Board, selection: Set<Int>): Board =
            Board(board.piles.map { pile -> pile.filter { it !in selection } })
    }

    data object Wythoff : Variant(
        id = "wythoff",
        title = "Wythoff",
        subtitle = "Deux tas de feuilles",
        kind = ItemKind.LEAF,
        layout = LayoutStyle.ROWS,
        rules = listOf(
            "Deux tas de feuilles.",
            "À ton tour, retire autant de feuilles que tu veux dans un seul tas…",
            "… ou bien retire le même nombre de feuilles dans chacun des deux tas.",
            "En mode normal, celui qui prend la dernière feuille gagne. En mode misère, il perd.",
        ),
        presets = listOf(
            Preset("5 · 7", listOf(5, 7)),
            Preset("7 · 10", listOf(7, 10)),
            Preset("9 · 12", listOf(9, 12)),
        ),
        misereByDefault = false,
        hint = "Un seul tas, ou autant dans les deux tas",
    ) {
        override fun validate(board: Board, selection: Set<Int>, preset: Preset): String? {
            if (selection.isEmpty()) return "Choisis au moins une feuille"
            val counts = selection.mapNotNull { board.locate(it)?.first }.groupingBy { it }.eachCount()
            if (counts.size == 2 && counts.values.distinct().size != 1) {
                return "Le même nombre dans les deux tas"
            }
            return null
        }

        override fun apply(board: Board, selection: Set<Int>): Board =
            Board(board.piles.map { pile -> pile.filter { it !in selection } })
    }

    data object Kayles : Variant(
        id = "kayles",
        title = "Kayles",
        subtitle = "Une ligne de galets",
        kind = ItemKind.PEBBLE,
        layout = LayoutStyle.FIXED_SLOTS,
        rules = listOf(
            "Les galets sont alignés.",
            "À ton tour, retire un galet, ou deux galets voisins (sans trou entre eux).",
            "Retirer un galet au milieu coupe la ligne en deux.",
            "En mode normal, celui qui prend le dernier galet gagne. En mode misère, il perd.",
        ),
        presets = listOf(
            Preset("7 galets", listOf(7)),
            Preset("10 galets", listOf(10)),
            Preset("12 galets", listOf(12)),
        ),
        misereByDefault = false,
        hint = "Retire un galet ou deux galets voisins",
    ) {
        private fun adjacent(board: Board, a: Int, b: Int): Boolean {
            val (pa, ia) = board.locate(a) ?: return false
            val (pb, ib) = board.locate(b) ?: return false
            return pa == pb && kotlin.math.abs(ia - ib) == 1
        }

        override fun onTap(board: Board, selection: Set<Int>, id: Int, preset: Preset): TapResult = when {
            id in selection -> TapResult.Selected(selection - id)
            selection.size == 1 && adjacent(board, selection.first(), id) -> TapResult.Selected(selection + id)
            else -> TapResult.Selected(setOf(id))
        }

        override fun validate(board: Board, selection: Set<Int>, preset: Preset): String? = when {
            selection.isEmpty() -> "Choisis un ou deux galets"
            selection.size > 2 -> "Deux galets au maximum"
            selection.size == 2 && !adjacent(board, selection.first(), selection.last()) ->
                "Les deux galets doivent se toucher"
            else -> null
        }

        override fun apply(board: Board, selection: Set<Int>): Board {
            val out = mutableListOf<List<Int>>()
            for (pile in board.piles) {
                var current = mutableListOf<Int>()
                for (id in pile) {
                    if (id in selection) {
                        if (current.isNotEmpty()) out += current
                        current = mutableListOf()
                    } else current += id
                }
                if (current.isNotEmpty()) out += current
            }
            return Board(out)
        }
    }

    data object Grundy : Variant(
        id = "grundy",
        title = "Grundy",
        subtitle = "Des champignons à partager",
        kind = ItemKind.MUSHROOM,
        layout = LayoutStyle.ROWS,
        rules = listOf(
            "Un tas de champignons.",
            "À ton tour, partage un tas en deux tas de tailles différentes.",
            "Touche le champignon où commence la seconde partie.",
            "Un tas de 1 ou 2 ne peut plus être partagé.",
            "En mode normal, celui qui ne peut plus jouer perd. En mode misère, il gagne.",
        ),
        presets = listOf(
            Preset("7 champignons", listOf(7)),
            Preset("10 champignons", listOf(10)),
            Preset("13 champignons", listOf(13)),
        ),
        misereByDefault = false,
        hint = "Partage un tas en deux parts inégales",
    ) {
        override fun hasMoves(board: Board): Boolean = board.piles.any { it.size >= 3 }

        override fun onTap(board: Board, selection: Set<Int>, id: Int, preset: Preset): TapResult {
            val (p, i) = board.locate(id) ?: return TapResult.Selected(selection)
            val pile = board.piles[p]
            if (pile.size < 3) return TapResult.Refused("Ce tas ne peut plus être partagé")
            if (i == 0) return TapResult.Refused("Touche le premier champignon de la seconde part")
            val part = pile.subList(i, pile.size).toSet()
            if (part == selection) return TapResult.Selected(emptySet())
            return TapResult.Selected(part)
        }

        override fun validate(board: Board, selection: Set<Int>, preset: Preset): String? {
            if (selection.isEmpty()) return "Choisis où partager"
            val (p, _) = board.locate(selection.first()) ?: return "Choisis où partager"
            val n = board.piles[p].size
            val k = selection.size
            if (k >= n) return "Choisis où partager"
            if (k * 2 == n) return "Les deux parts doivent être différentes"
            return null
        }

        override fun apply(board: Board, selection: Set<Int>): Board {
            val out = mutableListOf<List<Int>>()
            for (pile in board.piles) {
                if (pile.any { it in selection }) {
                    out += pile.filter { it !in selection }
                    out += pile.filter { it in selection }
                } else out += pile
            }
            return Board(out)
        }
    }

    companion object {
        val all: List<Variant> by lazy { listOf(Classic, Marienbad, Subtraction, Wythoff, Kayles, Grundy) }
        fun byId(id: String): Variant = all.first { it.id == id }
    }
}
