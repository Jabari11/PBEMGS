package com.pbemgs.game.loa;

import com.pbemgs.generated.tables.records.UsersRecord;

/**
 * Stores blocks of text associated with Lines of Action.
 */
public class LoaTextResponseProvider {

    public static String getLoaRulesText() {
        return "Rules for Lines of Action (LOA)\n\n" +
                "GameType Identifier: 'loa'\n\n" +
                "Overview:\n" +
                "  LOA is a deep strategy duel on an 8x8 grid. Players (X and O) shift their pieces\n" +
                "  across rows, columns, or diagonals, aiming to merge them into a single connected group.\n" +
                "  Each move’s distance is determined by the total number of pieces (both yours and your\n" +
                "  opponent’s) in that row, column, or diagonal — plan deep, strike clever!\n\n" +
                "Credits: Lines of Action was invented by Claude Soucie and popularized by Sid Sackson.\n" +
                "         It is also featured as an event in the Mind Sports Olympiad!\n\n" +
                "Objective:\n" +
                "  Connect all your pieces (X or O) into a single group — touching in any direction\n" +
                "  (horizontally, vertically, or diagonally). First player to do so wins!\n\n" +
                "Starting Board:\n" +
                "    A B C D E F G H\n" +
                " 1  . X X X X X X :\n" +
                " 2  O . : . : . : O\n" +
                " 3  O : . : . : . O\n" +
                " 4  O . : . : . : O\n" +
                " 5  O : . : . : . O\n" +
                " 6  O . : . : . : O\n" +
                " 7  O : . : . : . O\n" +
                " 8  : X X X X X X .\n" +
                " - X: 12 pieces in rows 1 and 8.\n" +
                " - O: 12 pieces in columns A and H.\n\n" +
                "Gameplay:\n" +
                " - Move one piece per turn along a straight line (row, column, diagonal).\n" +
                " - Distance moved MUST BE EQUAL to the total number of pieces (yours + opponent’s) along the move path.\n" +
                "   This includes both ends of the line, not just the segment between the start and destination.\n" +
                " - Land on an empty square (.) or capture an opponent’s piece — but you cannot land on your own.\n" +
                " - You may leap over your own pieces but **not** opponent’s — enemy pieces block movement.\n" +
                " - Example:\n" +
                "   For the pieces in row 4 shown below - each piece must move exactly 4 spaces.\n" +
                "   - Neither X piece can move: A4-E4 and H4-D4 are blocked by O (jumping enemy pieces is not allowed).\n" +
                "   - O can move: F4-B4 (jumping own pieces is fine), or D4-H4 (capturing X at H4).\n\n" +
                "    A B C D E F G H\n" +
                " 4  X . . O . O . X\n\n" +
                " - The game ends immediately when one player’s pieces form a single connected group.\n\n" +
                "Winning Position (X Wins):\n" +
                "    A B C D E F G H\n" +
                " 1  . : . : . : . :\n" +
                " 2  : . : . : . : .\n" +
                " 3  . : X X O : . :\n" +
                " 4  : . X X X . O .\n" +
                " 5  O : X : X O . :\n" +
                " 6  : O X X O . : .\n" +
                " 7  O : . O . : . :\n" +
                " 8  : . : . : . : .\n" +
                " - X’s 9 pieces form a single connected group — touching across rows/diagonals.\n" +
                " - If a move leaves both players fully connected, the player who moved wins.\n\n" +
                "Move Command Format:\n" +
                " - Subject: move loa [game_id]\n" +
                " - Body: [from] [to] (e.g., B2 B5)\n" +
                " - Formats: 'B2 B5', 'B2-B5', 'B2:B5' all work.\n\n" +
                "Sample Move Command:\n" +
                " - Subject: move loa 1234\n" +
                " - Body: E1 G3\n" +
                "   (X moves from E1 to G3 — because there are exactly 2 pieces in the E1-H4 diagonal.)\n\n" +
                "Game Creation Options:\n" +
                " - None — standard 8x8 grid, fixed start.\n\n" +
                "Meta-Rules:\n" +
                " - Max simultaneous games/player: 3\n" +
                " - Max open games: 10\n" +
                " - Reminder: Email nudge after 24 hours idle.\n" +
                " - Auto-Move: Random move after 4 days (96 hours) no response.\n" +
                " - First player (X or O) randomized at start.\n\n" +
                "Status:\n" +
                " - Ready! Test your wits — connect the chaos. Feedback? Hit 'feedback'!";
    }

    public static String getNoOpenGamesText() {
        return "There are currently no open Lines of Action games to join - use 'create_game loa' to create one!\n";
    }

    public static String getOpenGamesHeaderText(int count) {
        return "There are " + count + " currently open Lines of Action games.\n" +
                "Use 'join_game loa [game_id]' to join one of the following:\n";
    }

    public static String getOpenGameDescription(Long gameId, UsersRecord usersRecord) {
        return "- Game ID: " + gameId.toString() + " - Created By: " + usersRecord.getHandle() + "\n";
    }

    public static String getMoveInvalidText(LinesOfAction.LoaMove move, long gameId, String errorMessage) {
        return "You requested the move (" + move.from() + ") - (" + move.to() + ") in LOA Game ID: " + gameId + ".\n" +
                "This move is not valid.  Error message: " + errorMessage;
    }

    public static String getGameHeader(long gameId, String topSymbol, String topHandle, int topCount, boolean topActive,
                                       String botSymbol, String botHandle, int botCount) {
        return "LOA Game ID: " + gameId + "\n\n" +
                topSymbol + ": " + topHandle + " [" + topCount + "] " + (topActive ? " - YOUR TURN\n" : "\n") +
                botSymbol + ": " + botHandle + " [" + botCount + "] " + (!topActive ? " - OPPONENT'S TURN\n\n" : "\n\n");
    }

}
