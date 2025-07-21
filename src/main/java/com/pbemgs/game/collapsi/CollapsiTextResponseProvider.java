package com.pbemgs.game.collapsi;

import com.pbemgs.generated.tables.records.CollapsiGamesRecord;
import com.pbemgs.generated.tables.records.UsersRecord;
import com.pbemgs.model.Location;

/**
 * Stores blocks of text associated with Triad Cubed.
 */
public class CollapsiTextResponseProvider {

    public static String getCollapsiRulesText() {
        String sampleBoardSer = "1,4,0,0,2,2,0,3,0,3,1,2,1,0,0,1,D2,A2";

        CollapsiBoard sampleBoard = new CollapsiBoard();
        sampleBoard.deserialize(sampleBoardSer);

        return "Rules for Collapsi:\n\n" +
                "GameType Identifier: 'collapsi'\n\n" +
                "Overview:\n" +
                "  Collapsi is a fast-paced, two-player abstract strategy game where you maneuver your token\n" +
                "  across a toroidal 4x4 grid of cards, aiming to trap your opponent so they have no legal moves.\n" +
                "  The board wraps around its edges, and squares collapse after each move, shrinking the playable area.\n\n" +
                "Credits:\n" +
                "  Collapsi was created by Mark [TBD - confirm name]. Check out his YouTube channel 'Riffle Shuffle & Roll' for more!\n\n" +
                "Objective:\n" +
                "  Force your opponent into a position with no valid moves to win the game.\n\n" +
                "Gameplay:\n" +
                "  The Board:\n" +
                "  - The game is played on a 4x4 grid of cards, randomly dealt face-up at the start.\n" +
                "  - The deck consists of 4 Aces (value 1), 4 Twos (value 2), 4 Threes (value 3),\n" +
                "    2 Fours (value 4), and 2 Jokers (value 1, one for each player’s starting position).\n" +
                "  - Future updates may include options for different board sizes or configurations.\n\n" +
                "  Movement:\n" +
                "  - On your turn, move your token exactly the number of spaces equal to the value of the\n" +
                "    card you’re on (e.g., 2 spaces for a Two, 1 space for an Ace or Joker).\n" +
                "  - Moves are made one step at a time, orthogonally (up, down, left, right), and you may\n" +
                "    change direction at each step (e.g., from A1 to B1 to B2 for a Two).\n" +
                "  - The board wraps around: from A1, one step can reach A2, B1, A4, or D1.\n" +
                "  - You may not:\n" +
                "    - Land on or move through a collapsed square (face-down card, blanked out).\n" +
                "    - Land on your opponent’s token.\n" +
                "    - Backtrack to any square visited during this move, including your starting square.\n" +
                "  - After moving, your starting square collapses (turns face-down) and cannot be used again.\n" +
                "  - Submit only the final destination (e.g., 'B4'); the system validates the path.\n\n" +
                "  Sample Board Format:\n" +
                sampleBoard.getBoardTextBody() +
                "  - On the sample board, X (currently on D2) must move 3 squares - B1 is a valid landing square moving through\n" +
                "    A2 then B2 (wrapping across the board, and moving through the opponent's pawn).  Other valid\n" +
                "    landing points are B3 and A4.  A2 is not valid as landing on the opponent is not allowed.  D3 is not\n" +
                "    valid as that involves backtracking (D2 to D3 to D4 then back to D3).\n\n" +
                "  Victory Condition:\n" +
                "  - You win if your move leaves your opponent with no valid moves.\n\n" +
                "  Starting Setup:\n" +
                "  - Each player starts on their designated Joker square (value 1), randomly placed on the board.\n" +
                "  - The first player is chosen randomly.\n\n" +
                "Move Command Format:\n" +
                "  - Subject: 'move collapsi [game_id]'\n" +
                "  - Body: '[to-location]'\n" +
                "    - '[to-location]': The ending position (e.g., 'A1', 'B2', 'C3').\n" +
                "  - Example:\n" +
                "    - Subject: 'move collapsi 1234'\n" +
                "    - Body: 'B4'\n\n" +
                "Game Creation Command:\n" +
                "  - Subject: 'create_game collapsi'\n" +
                "  - No additional options are currently supported (board size options may be added later).\n\n" +
                "Meta-Rules:\n" +
                "  - Maximum simultaneous games per player: 3\n" +
                "  - Maximum open games: 10\n" +
                "  - Inactivity nudge: After 24 hours\n" +
                "  - Auto-move: A random valid move is selected after 72 hours\n\n" +
                "Status:\n" +
                "  - In testing. Send feedback using the 'feedback' command!";
    }

    public static String getNoOpenGamesText() {
        return "There are currently no open Collapsi games to join - use 'create_game collapsi' to create one!\n";
    }

    public static String getOpenGamesHeaderText(int count) {
        return "There are " + count + " currently open Collapsi games.\n" +
                "Use 'join_game collapsi [game_id]' to join one of the following:\n";
    }

    public static String getOpenGameDescription(CollapsiGamesRecord game, UsersRecord usersRecord) {
        return "- Game ID: " + game.getGameId() + " - Created By: " + usersRecord.getHandle() + "\n";
    }

    public static String getMoveFailedText(long gameId, Location badLoc) {
        return "Your move for Collapsi game ID " + gameId + " is not valid.\n" +
                "The requested location " + badLoc.toString() + " is not a valid landing square.";
    }

    public static String getPlayerHeader(String handle, char symbol, boolean activeTurn) {
        return String.valueOf(symbol) + ": " + handle + (activeTurn ? " - TO MOVE!" : "") + "\n";
    }

}
