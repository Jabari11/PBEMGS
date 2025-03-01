package com.pbemgs.game.ataxx;

import com.pbemgs.generated.tables.records.AtaxxGamesRecord;
import com.pbemgs.generated.tables.records.UsersRecord;

import java.util.List;

public class AtaxxTextResponseProvider {

    public static String getAtaxxRulesText() {
        return "Rules for Ataxx\n\n" +
                "GameType Identifier: 'ataxx'\n\n" +
                "Overview:\n" +
                "Ataxx is blob warfare unleashed — spread your pieces, flip your foes, and dominate the board!\n\n" +
                "Ataxx supports both 2-player and 4-player matches on various board sizes.\n" +
                "Players choose from different board layouts to add variety to the game.\n\n" +
                "Credit: Inspired by Leland Corporation’s 1990 arcade classic, cranked up for PBEMGS chaos.\n\n" +
                "Objective:\n" +
                "  The player who controls the most squares when no legal moves remain wins the game.\n\n" +

                "Sample Initial Board (7x7):\n\n" +
                "    A B C D E F G\n" +
                " 1  x . . . . . o\n" +
                " 2  . . . . . . .\n" +
                " 3  . . . # . . .\n" +
                " 4  . . # . # . .\n" +
                " 5  . . . # . . .\n" +
                " 6  . . . . . . .\n" +
                " 7  o . . . . . x\n\n" +
                "- Each square is identified by its grid coordinates (Column Letter + Row Number).\n" +
                "- Example: The x pieces are at A1 and G7.\n\n" +
                "- 'x' and 'o' represent the two players' starting pieces.\n" +
                "- '.' represents empty spaces where moves can be made.\n" +
                "- '#' represents obstacles, which cannot be moved onto or captured.\n\n" +
                "Gameplay:\n" +
                "- Players move a piece to an empty space using one of two movement types:\n" +
                " - Clone Move (1 space away in any direction): Creates a new piece on the destination while keeping the original piece.\n" +
                " - Jump Move (exactly 2 spaces away): Moves the piece to the new location, leaving the original space empty.\n" +
                "- The landing square must be empty — pieces cannot move onto occupied squares or obstacles (`#`)\n" +
                "- All opponent's pieces next to the landing square are captured and converted.\n" +
                "- If a player has no legal moves, they automatically pass.\n\n" +
                "Move Command Format:\n" +
                "- Subject: move ataxx [game_id]\n" +
                "- Body: [from-square] [to-square] (Jump or Clone)\n" +
                "- Body: [to-square] (Clone move only; starting square inferred)\n\n" +
                "Example Move Commands:\n" +
                "Subject: move ataxx 5678\n" +
                "Body:    B3 D4\n" +
                "(move is a jump from B3 to D4)\n\n" +
                "Subject: move ataxx 5678\n" +
                "Body:    B3 C3\n" +
                "(move is a clone from B3 to adjacent square C3)\n\n" +
                "Subject: move ataxx 5678\n" +
                "Body:    C4\n" +
                "(move is a clone to C4 - as long as an adjacent piece exists)\n\n" +
                "Note: 'B3 C4', 'B3-C4', and 'B3:C4' are all valid and equivalent move formats.\n\n" +
                "Game Creation Options:\n" +
                "Ataxx requires specifying all three of the following options when creating a game.\n\n" +
                "- 'players' (Choose 2 or 4)\n" +
                "  - `players:2` (Two-player game: X vs O)\n" +
                "  - `players:4` (Four-player game: X, O, +, and *)\n\n" +
                "- 'size' (Choose between 7-9)\n" +
                "  - `size:7` (7x7 board, standard gameplay for 1v1)\n" +
                "  - `size:8` (8x8 board, more space, no 'center square')\n" +
                "  - `size:9` (9x9 board, larger board, longer game)\n" +
                "- 'board' (Choose starting board layout)\n" +
                "  - `board:blank` (No obstacles, open board)\n" +
                "  - `board:standard` (Balanced symmetrical obstacles from predefined layouts)\n" +
                "  - `board:random` (Randomly generated symmetrical obstacles for variety)\n\n" +
                "Example Game Creation Command:\n" +
                "Subject: create_game ataxx\n" +
                "Body:\n" +
                "players:4\n" +
                "size:9\n" +
                "board:standard\n\n" +
                "Game Meta-Rules:\n" +
                "- Turn order is randomized at the start of the game.\n" +
                "- For 4-player games, each player starts in a random corner.\n" +
                "- Maximum simultaneous games per player: 5\n" +
                "- Maximum open games: 15\n\n" +
                "- Turn Reminder: A reminder email will be sent if you haven’t moved for 24 hours.\n" +
                "- Turn Timeout: If you haven’t moved within 3 days (72 hours), a random move will be made.\n\n" +
                "Game Status:\n" +
                "- FINAL TEST - Ataxx is released and in the final testing phase for 4P games.\n\n" +
                "Thank you for playing! Feedback is always welcome - use the 'feedback' command anytime.";
    }

    public static String getGameLimitReachedText() {
        return "You have reached the maximum number of active Ataxx games.";
    }

    public static String getOpenGamesLimitReachedText() {
        return "The maximum number of open Ataxx games has been reached - use 'open_games ataxx'\n" +
                "and join an open game instead of creating a new one!";
    }

    public static String getBadOptionsText() {
        return "Your 'create_game ataxx' command failed due to missing or incorrect options.\n\n" +
                "Ensure the following:\n" +
                "- 'players' is 2 or 4 (e.g., players:2)\n" +
                "- 'size' is between 7 and 9 inclusive (e.g., size:7)\n" +
                "- 'board' is 'blank', 'standard', or 'random' (e.g., board:standard)";
    }

    public static String getCreateSuccessText(long gameId) {
        return "Your Ataxx game has been created and is waiting for opponent(s)! Game ID: " + gameId;
    }

    public static String getJoinNonopenGameText(long gameId) {
        return "You cannot join game " + gameId + " as it is not waiting for players.";
    }

    public static String getJoinSelfText() {
        return "You cannot join a game you are already part of!";
    }

    public static String getJoinSuccessText(long gameId) {
        return "You have been added to Ataxx game ID " + gameId + "!\n" +
                "This game is still awaiting additional player(s).";
    }

    public static String getMoveNoGameText(long gameId) {
        return "Ataxx game ID " + gameId + " either doesn't exist or is not an in-progress game.\n" +
                "Use 'my_games ataxx' to get the list of games you are a part of!";

    }

    public static String getMoveNotActiveText(long gameId) {
        return "You requested a move in Ataxx game ID " + gameId + ".\n" +
                "It is either your opponent's turn, or this isn't a game you are part of.\n\n" +
                "Use 'my_games ataxx' to get the list of games you are a part of and the current player!";
    }

    public static String getNoOpenGamesText() {
        return "There are currently no Ataxx games waiting for players -\n" +
                "use 'create_game ataxx' to create a new game!\n";
    }

    public static String getOpenGamesHeaderText(int count) {
        return "There are " + count + " currently open Ataxx games.\n" +
                "Use 'join_game ataxx [game_id]' to join one of the following:\n\n";
    }


    public static String getOpenGameDescription(AtaxxGamesRecord game, List<UsersRecord> joined) {
        // Format of an open game: gameID, numPlayers, boardType, size, Player1 [Player2] [Player3]
        StringBuilder sb = new StringBuilder();
        sb.append("- Game ID: ").append(game.getGameId());
        sb.append(", #Players: ").append(game.getNumPlayers());
        sb.append(", Board Type: ").append(game.getBoardOption().name());
        sb.append(", Board Size: ").append(game.getBoardSize());
        sb.append(".  Players: ");
        for (UsersRecord user : joined) {
            sb.append(user.getHandle()).append("  ");
        }
        sb.append("\n");
        return sb.toString();
    }

    public static String getStatusFailedNoGameText(long gameId) {
        return "You requested status for Ataxx game id " + gameId + ".\n" +
                "This game id either doesn't exist or hasn't started.\n\n" +
                "Use 'my_games ataxx' to get a list of the games you are a part of!";
    }

    public static String getStatusFailedNotYourGameText(long gameId) {
        return "You requested status for Ataxx game id " + gameId + ".\n" +
                "You are not a player in this game, so the command is not allowed.\n\n" +
                "Use 'my_games ataxx' to get a list of the games you are a part of!";
    }

    public static String getMoveFailedParseText(long gameId, String error) {
        return "Your move command for Ataxx game id " + gameId + " failed to parse correctly.\n" +
                "Error message: " + error + "\n\n" +
                "Coordinates should be [letter][number] (i.e. A4-B5).";
    }

    public static String getIllegalMoveText(long gameId, String error) {
        return "Your move command for Ataxx game id " + gameId + " is not legal.\n" +
                "Error message: " + error + ".";
    }
}
