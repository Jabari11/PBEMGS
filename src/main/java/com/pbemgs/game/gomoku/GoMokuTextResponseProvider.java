package com.pbemgs.game.gomoku;

import com.pbemgs.generated.enums.GomokuGamesSwap2State;
import com.pbemgs.generated.tables.records.GomokuGamesRecord;
import com.pbemgs.generated.tables.records.UsersRecord;
import com.pbemgs.model.Location;

/**
 * Stores blocks of text associated with Lines of Action.
 */
public class GoMokuTextResponseProvider {

    public static String getGoMokuRulesText() {
        return "Rules for Gomoku\n\n" +
                "GameType Identifier: 'gomoku'\n\n" +
                "Overview:\n" +
                "  Gomoku is a classic strategy game played on a 15x15 grid. Players (X and O) take turns\n" +
                "  placing stones, aiming to form five in a row — horizontal, vertical, or diagonal.\n" +
                "  This implementation features the Swap2 opening system from modern tournaments, which\n" +
                "  ensures a balanced and strategic start.\n\n" +
                "Objective:\n" +
                "  - Be the first to align five stones in a row (horizontal, vertical, or diagonal).\n" +
                "  - If the board fills completely with no winner, the game is a draw.\n\n" +
                "Gameplay:\n" +
                " - Swap2 Opening (First few moves follow a special structure to ensure fairness):\n" +
                "   1. Tentative First Player (TFP) places THREE stones: two X and one O\n" +
                "      - Example: `H8,I9,G7` (Two black (X) stones, then one white (O) stone)\n" +
                "   2. Tentative Second Player (TSP) chooses one of three options:\n" +
                "      - Play as White (O): Place ONE more O stone (e.g., `J7`).\n" +
                "      - Swap: Take over as X instead, forcing TFP to play as O.\n" +
                "      - Extend the opening: Place TWO more stones (one O, one X) (e.g., `J8,K7`).\n" +
                "   3. If TSP extends the opening, TFP then makes a final choice:\n" +
                "      - STAY: Keep playing as X\n" +
                "      - Swap: Place one more O stone and switch to O (e.g., `H10`).\n" +
                "   4. Normal gameplay begins.  Players alternate placing one stone per turn.\n" +
                " - Stones can only be placed on empty squares (.).\n" +
                " - The first player to form five in a row wins.\n\n" +
                "Move Command Format:\n" +
                " - Subject: `move gomoku [game_id]`\n" +
                " - Body:\n" +
                "   - TFP Initial Move: `[loc1],[loc2],[loc3]` (e.g., `H8,I9,G7`)\n" +
                "   - TSP Choice: `[loc]` (e.g., `J7`), `SWAP`, or `[loc1],[loc2]` (e.g., `J8,K7`)\n" +
                "   - TFP Swap Decision: `STAY` (as X) or `[loc]` (e.g., `H10`) (places an O stone and swaps colors)\n" +
                "   - Normal Move: `[loc]` (e.g., `H10`)\n\n" +
                "Sample Move Command:\n" +
                " - Subject: `move gomoku 1234`\n" +
                " - Body: `H8,I9,G7`  (TFP places 2 X, 1 O to start)\n\n" +
                "Game Creation Options:\n" +
                " - None — standard 15x15 grid.\n\n" +
                "Meta-Rules:\n" +
                " - Max simultaneous games per player: 3\n" +
                " - Max open games: 8\n" +
                " - Reminder: Players are nudged after 24 hours of inactivity.\n" +
                " - Auto-Move: A random stone is placed after 3 days (72 hours) — only in normal gameplay phase.\n\n" +
                "Status:\n" +
                " - Available!  Five in a row wins. Questions? Send 'feedback'!";
    }

    public static String getNoOpenGamesText() {
        return "There are currently no open GoMoku games to join - use 'create_game gomoku' to create one!\n";
    }

    public static String getOpenGamesHeaderText(int count) {
        return "There are " + count + " currently open GoMoku games.\n" +
                "Use 'join_game gomoku [game_id]' to join one of the following:\n";
    }

    public static String getOpenGameDescription(Long gameId, UsersRecord usersRecord) {
        return "- Game ID: " + gameId.toString() + " - Created By: " + usersRecord.getHandle() + "\n";
    }

    public static String getMoveInvalidForStateText(long gameId, String stateError) {
        return "Your requested move in GoMoku game ID " + gameId + " parsed correctly but is not valid.\n" +
                "Error message: " + stateError;
    }

    public static String getNoMoveFoundText(long gameId) {
        return "Your move command for GoMoku game ID " + gameId + " could not find a stone placement location.\n";
    }

    public static String getMoveInvalidText(Location move, long gameId, String errorMessage) {
        return "You attempted to place a stone at " + move.toString() + " in GoMoku Game ID: " + gameId + ".\n" +
                "This move is not valid.  Error message: " + errorMessage;
    }

    public static String getGameHeader(GomokuGamesRecord game, String topSymbol, String topHandle, boolean topActive,
                                       String botSymbol, String botHandle) {
        String tent = game.getSwap2State() == GomokuGamesSwap2State.GAMEPLAY ? "" : " (Tentative) ";
        return "GoMoku Game ID: " + game.getGameId() + "\n\n" +
                topSymbol + tent + ": " + topHandle + (topActive ? " - YOUR TURN\n" : "\n") +
                botSymbol + tent + ": " + botHandle + (!topActive ? " - OPPONENT'S TURN\n\n" : "\n\n");
    }

    public static String getSwap2StateHeader(GomokuGamesSwap2State swap2State) {
        return switch (swap2State) {
            case GAMEPLAY -> "";
            case AWAITING_INITIAL_PLACEMENT -> "Phase: INITIAL PLACEMENT.\n" +
                    "X will place 3 stones, 2 Xs and 1 O, in that order (X, X, O).\n\n";
            case AWAITING_TSP_CHOICE -> "Phase: TENTATIVE SECOND PLAYER CHOICE.\n" +
                    "O (Tentative) may choose:\n" +
                    "1. Place an additional O stone at [O-location] - finalizes colors.\n" +
                    "2. Swap to X (SWAP) - players switch colors, and it will be opponent's (finalized O) turn.\n" +
                    "3. Expand the opening ([O-location], [X-location]) - adds one O and one X to the board. \n" +
                    "   Final color choice moves to Tentative X.\n\n";
            case AWAITING_TFP_SWAP -> "Phase: TENTATIVE FIRST PLAYER CHOICE.\n" +
                    "Colors are finalized after this move.\n" +
                    "X (Tentative) may choose:\n" +
                    "1. Stay as X (STAY) - board unchanged, O will be to move.\n" +
                    "2. Place one O stone at [O-location] - swaps colors and places the stone as an O.\n\n";
        };
    }

}
