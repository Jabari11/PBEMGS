package com.pbemgs.game.ninetac;

import com.pbemgs.generated.tables.records.UsersRecord;

/**
 * Stores blocks of text associated with Ninetac.
 */
public class NinetacTextResponseProvider {

    public static String getNinetacRulesText() {
        return "Rules for Ninetac:\n\n" +
                "GameType Identifier: 'ninetac' or '9tac'\n\n" +
                "Overview:\n" +
                "   Ninetac is an enhanced version of Tic-Tac-Toe played on nine boards simultaneously.\n" +
                "   Each square on the board has a numeric identifier.  Players make a move by selecting a\n" +
                "   number, which claims all matching squares across all boards.\n\n" +
                "Objective:\n" +
                "- Win the game by claiming the majority of the nine individual boards.\n" +
                "- A player claims a board by getting three-in-a-row on it.\n" +
                "- If all nine squares on a board are claimed without a winner, that board is drawn.\n\n" +
                "Sample initial board:\n\n" +
                "23 | 21 | 14    24 | 18 | 25    25 | 21 | 09\n" +
                "---+----+---    ---+----+---    ---+----+---\n" +
                "02 | 27 | 15    16 | 13 | 12    10 | 11 | 26\n" +
                "---+----+---    ---+----+---    ---+----+---\n" +
                "26 | 17 | 12    22 | 15 | 11    15 | 19 | 05\n\n" +
                "06 | 24 | 01    13 | 03 | 18    17 | 23 | 18\n" +
                "---+----+---    ---+----+---    ---+----+---\n" +
                "08 | 20 | 13    17 | 05 | 09    05 | 25 | 24\n" +
                "---+----+---    ---+----+---    ---+----+---\n" +
                "02 | 27 | 16    22 | 06 | 07    19 | 09 | 04\n\n" +
                "01 | 04 | 23    08 | 07 | 20    22 | 16 | 12\n" +
                "---+----+---    ---+----+---    ---+----+---\n" +
                "02 | 19 | 21    10 | 03 | 27    08 | 01 | 04\n" +
                "---+----+---    ---+----+---    ---+----+---\n" +
                "14 | 26 | 03    06 | 11 | 14    20 | 07 | 10\n\n" +
                "Gameplay:\n" +
                "- Players select a number on their turn to claim all matching squares across all boards.\n" +
                "- The first player is chosen randomly when the game starts.\n" +
                "- Initial boards have 27 unique numbers, each appearing exactly 3 times.\n\n" +
                "- A board is resolved when a player gets three-in-a-row on it, or all squares are filled.\n" +
                "- The player who claims the majority of the nine boards wins.\n\n" +
                "Move Command Format:\n" +
                "- Subject line: move ninetac [game_id]\n" +
                "- Email Body:   [number-to-claim]\n\n" +
                "Example Move Command:\n" +
                " - Subject line: move ninetac 1234\n" +
                " - Email Body:   15\n\n" +
                "Game Creation Options:\n" +
                " - None at this time\n\n" +
                "Game Meta-Rules:\n" +
                "- Maximum simultaneous games per player: 3\n" +
                "- Maximum open (unfilled) games: 10\n" +
                "- Turn Reminder: A reminder email will be sent if you haven’t moved for 24 hours.\n" +
                "- Turn Timeout: If you haven’t moved within 3 days (72 hours), a random move will be made for you.\n\n" +
                "Game Status:\n" +
                " - COMPLETE.  I may add board creation options or a first-move limitation\n" +
                "              if it seems like the first player has an advantage.";
    }

    public static String getNoOpenGamesText() {
        return "There are currently no open Ninetac games to join - use 'create_game ninetac' to create one!\n";
    }

    public static String getOpenGamesHeaderText(int count) {
        return "There are " + count + " currently open Ninetac games.\n" +
                "Use 'join_game ninetac [game_id]' to join one of the following:\n\n";
    }

    public static String getOpenGameDescription(Long gameId, UsersRecord usersRecord) {
        return "- Game ID: " + gameId.toString() + " - Created By: " + usersRecord.getHandle() + "\n";
    }

    public static String getMoveNotActiveText(long gameId) {
        return "You requested a move in Ninetac game ID " + gameId + ".\n" +
                "It is either your opponent's turn, or this isn't a game you are part of.\n\n" +
                "Use 'my_games ninetac' to get the list of games you are a part of and the current player!";
    }

    public static String getMoveInvalidNumberText(int move, long gameId) {
        return "You requested the move " + move + " in Ninetac Game ID: " + gameId + ".\n" +
                "This number is not valid, the range is 1 to 27.";
    }

    public static String getMoveUnavailableText(long gameId, int move) {
        return "You requested the move " + move + " in Ninetac Game ID: " + gameId + ".\n" +
                "This number is not available on the board.  Please pick a valid number.";
    }

    public static String getStatusFailedNoGameText(long gameId) {
        return "You requested status for Ninetac game id " + gameId + ".\n" +
                "This game id either doesn't exist or hasn't started.\n\n" +
                "Use 'my_games ninetac' to get a list of the games you are a part of!";
    }

    public static String getStatusFailedNotYourGameText(long gameId) {
        return "You requested status for Ninetac game id " + gameId + ".\n" +
                "You are not a player in this game, so the command is not allowed.\n\n" +
                "Use 'my_games ninetac' to get a list of the games you are a part of!";
    }

    public static String getGameHeader(long gameId, String topSymbol, String topHandle, int topClaimed, boolean topActive,
                                       String botSymbol, String botHandle, int botClaimed) {
        return "Ninetac Game ID: " + gameId + "\n\n" +
                topSymbol + ": " + topHandle + " [" + topClaimed + "] " + (topActive ? " - YOUR TURN\n" : "\n") +
                botSymbol + ": " + botHandle + " [" + botClaimed + "] " + (!topActive ? " - OPPONENT'S TURN\n\n" : "\n\n");
    }

}
