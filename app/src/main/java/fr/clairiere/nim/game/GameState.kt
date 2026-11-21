package fr.clairiere.nim.game

/** Réglages d'une partie. */
data class GameConfig(
    val variant: Variant,
    val preset: Preset,
    val misere: Boolean,
    val players: List<String>,
    /** Joueur (0 ou 1) qui commence. */
    val firstPlayer: Int = 0,
)

/** État immuable d'une partie en cours. */
data class GameState(
    val config: GameConfig,
    val board: Board,
    val current: Int,
    val selection: Set<Int> = emptySet(),
    val history: List<Pair<Board, Int>> = emptyList(),
    val winner: Int? = null,
    val moves: Int = 0,
) {
    val variant: Variant get() = config.variant
    val isOver: Boolean get() = winner != null
    val selectionError: String? get() = variant.validate(board, selection, config.preset)
    val canPlay: Boolean get() = !isOver && selectionError == null

    fun tap(id: Int): Pair<GameState, String?> {
        if (isOver) return this to null
        return when (val r = variant.onTap(board, selection, id, config.preset)) {
            is TapResult.Selected -> copy(selection = r.selection) to null
            is TapResult.Refused -> this to r.reason
        }
    }

    fun clearSelection(): GameState = copy(selection = emptySet())

    /** Joue la sélection. Retourne l'état inchangé si elle n'est pas valide. */
    fun play(): GameState {
        if (!canPlay) return this
        val next = variant.apply(board, selection)
        val mover = current
        val ended = !variant.hasMoves(next)
        return copy(
            board = next,
            selection = emptySet(),
            history = history + (board to current),
            current = 1 - current,
            moves = moves + 1,
            winner = if (ended) (if (config.misere) 1 - mover else mover) else null,
        )
    }

    fun undo(): GameState {
        val last = history.lastOrNull() ?: return this
        return copy(
            board = last.first,
            current = last.second,
            selection = emptySet(),
            history = history.dropLast(1),
            winner = null,
            moves = moves - 1,
        )
    }

    companion object {
        fun start(config: GameConfig): GameState =
            GameState(config = config, board = Board.of(config.preset.piles), current = config.firstPlayer)
    }
}
