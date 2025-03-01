package com.pbemgs.game.surge;

import com.pbemgs.generated.tables.records.SurgeGamesRecord;
import com.pbemgs.generated.tables.records.UsersRecord;

import java.util.List;

public class SurgeTextResponseProvider {

    public static String getSurgeRulesText() {
        return "Rules for Surge\n\n" +
                "GameType Identifier: 'surge'\n\n" +
                "Overview:\n" +
                "An Angry Turtle Studios Original!\n\n" +
                "Surge is a tactical, real-time-inspired strategy game played asynchronously.\n" +
                " - Players control liquid `force` and issue gate commands to shape movement across the board.\n" +
                " - The game updates periodically, applying all commands and resolving movement simultaneously.\n\n" +
                "Objective:\n" +
                "  Eliminate all of the other players.\n\n" +
                "Gameplay:\n" +
                " - The map is a rectangular grid, with each square representing a pressurized chamber that holds liquid of\n" +
                "   various types.  Those chambers are connected by pipes ('gates'), which can be open or closed.\n" +
                " - Each square can be from 0% (empty/uncontrolled) to 100% full.\n" +
                " - The map also contains geysers of various strengths, which generate force on every turn for the\n" +
                "   controlling player.  Each player begins with a strong geyser as their home base, and neutral geysers\n" +
                "   can be captured and controlled.\n\n" +
                "Game State Updates:\n" +
                "- The game automatically updates at fixed intervals, simulating a real-time battlefield where force\n" +
                "  flows continuously, and players react in-between steps.\n" +
                "- During an update, the following effects are applied:\n" +
                "  - First, gates are opened and closed per player commands.\n" +
                "  - Next, geysers produce force equal to their power.  That force belongs to the owning player.\n" +
                "  - Then, force is redistributed throughout the map through open gates.\n" +
                "    - Each gate has a limit to the amount of force that can pass through it.\n\n" +
                "Momentum and Force Movement:\n" +
                "- Force moves dynamically across the board, adjusting to pressure and past movement.\n" +
                "- Momentum builds through force movement over time, meaning:\n" +
                "  - Momentum matters! Force moving continuously pushes harder (higher quantity) through gates.\n" +
                "  - Narrow paths create fast-moving currents, while wider areas disperse momentum.\n" +
                "  - High momentum is indicated on the map by arrows over open gates.\n\n" +
                "Geysers - Ownership and Force Generation:\n" +
                " - Geysers generate force every turn based on their power level.\n" +
                " - Controlled Geysers: If owned, they generate force for the owning player.\n" +
                " - Neutral Geysers (Unowned): These also generate force to defend the geyser.  The generated force\n" +
                "   will actively resist capture.\n" +
                " - To take control of a neutral geyser, a player must defeat its accumulated force in combat.\n" +
                " - If a geyser changes hands, it begins producing force for the new owner immediately on the next turn.\n\n" +
                "Combat:\n" +
                "    - When multiple players have force in the same square, combat happens immediately.\n" +
                "    - Combat reduces the number of players occupying a square to one (or zero).\n" +
                "    - Larger forces win, but damage is nonlinear—smaller forces deal proportionally less damage.\n" +
                "      For example, an army of 20% will do more damage to an army of 40% than it will an army of 80%.\n" +
                "    - Force attacking into a square has a slight advantage over force defending a square, though\n" +
                "      quantity (and therefore momentum) matters much more.\n\n" +
                "Gate Commands:\n" +
                "- Gates are your primary tool for controlling movement — opening a path creates flow, while closing one\n" +
                "  can trap an opponent or block an attack.\n" +
                "- To open or close a gate, you must control one of the connected squares.\n" +
                "- You may not open a gate against the edge of the map or into an obstructed (###) square.\n" +
                "- Your move submission is made up of a number of gate commands, up to the limit configured for that match.\n" +
                "- Gate commands are executed simultaneously at the start of the update step, before force movement.\n" +
                "  - Conflicting commands, where one player commands a gate open, and another player commands the same\n" +
                "    gate closed from the other side, cancel each other leaving the gate unchanged.\n" +
                "- You may command a gate to its current state, which will hold it in that state.\n" +
                "- Move commands are cleared after the update step.\n" +
                "- If you submit multiple move emails before a single update, the second email (if valid) replaces the first.\n\n" +
                "Move Command Format:\n" +
                "- Subject: move surge [game_id]\n" +
                "- Body: [(open/close)] [Square/Direction],[Square/Direction],[...]\n\n" +
                "Example Move Command Body:\n" +
                " open A1E, B3S, B4E\n" +
                " close D3S, F5E\n\n" +
                " (Directions can be N/S/E/W, or U/D/R/L - 'North' is toward the top of the email text)\n\n" +
                "Surge requires specifying the following options when creating a game.\n\n" +
                "+-----------+--------------------+-----------------+-----------+\n" +
                "|  Option   |      Controls      |   Values        |  Example  |\n" +
                "+-----------+--------------------+-----------------+-----------+\n" +
                "| players   | Number of players  | 2-4 (for now)   | players:4 |\n" +
                "| limit     | Command Limit      | 3-6             | limit:5   |\n" +
                "| ticks     | Updates per Day    | 1-4             | ticks:2   |\n" +
                "| zone      | Time Zone          | ET, GMT, TK, SH | zone:ET   |\n" +
                "+-----------+--------------------+-----------------+-----------+\n" +
                "  Time zone is used to set the time-of-day for board updates - defaults to ET if not specified.\n" +
                "  Eastern (US), GMT (Europe), Tokyo (Japan), Shanghai (China)\n\n" +
                "Example Game Creation Command:\n" +
                "Subject: create_game surge\n" +
                "Body:\n" +
                "players:4\n" +
                "limit:5\n" +
                "ticks:3\n" +
                "zone:ET\n\n" +
                "Game Meta-Rules:\n" +
                "- Players are assigned to starting positions randomly.\n" +
                "- Maps are chosen from a set of predefined maps for the given number of players and command limit.\n" +
                "- Maximum simultaneous games per player: 3\n" +
                "- Maximum open games: 15\n\n" +
                "Update Time Details:\n" +
                "- The game runs the periodic updates according to the following schedule:\n" +
                "  (All times in the configured time zone for the game.)\n" +
                "  - 1 tick/day: 12:00pm (1200 hours)\n" +
                "  - 2 ticks/day: 1:00am (0100 hours), 1:00pm (1300 hours)\n" +
                "  - 3 ticks/day: 11:00am (1100 hours), 5:00pm (1700 hours), 10:00pm (2200 hours)\n" +
                "  - 4 ticks/day: 12:00am (0000 hours), 10:00am (1000 hours), 3:00pm (1500 hours), 8:00pm (2000 hours)\n\n" +
                "Game Status:\n" +
                "- AVAILABLE - IN INITIAL TESTING AND MAP DESIGN:\n" +
                "  Available to play!  I currently only have some 2-player maps and a 3-player and 4-player map created,\n" +
                "  working on adding more maps for larger numbers of players (aiming for 8P games max!)\n" +
                "  If game creation doesn't work try some other options for players and/or limit.\n" +
                "  I'll almost certainly be making balance adjustments on the fly, and may need to wipe the games\n" +
                "  if there are any major errors or additions needed.";
    }

    public static String getBadOptionsText() {
        return "Your 'create_game surge' command failed due to missing or incorrect options.\n\n" +
                "Ensure the following:\n" +
                "- 'players' is 2-4 inclusive (e.g. players:2)\n" +
                "- 'limit' is between 3 and 6 inclusive (e.g., limit:3)\n" +
                "- 'ticks' is between 1 and 4 inclusive (e.g., ticks:2)\n" +
                "- if 'zone' is set, it is one of 'ET', 'GMT', 'TK', 'SH'";
    }

    public static String getNoValidMapsText() {
        return "Your 'create_game surge' command failed because there are currently no maps\n" +
                "available that match your requested number of players and command limit.\n\n" +
                "If you'd like to request additional maps for those settings, send a 'feedback' request\n" +
                "with the details, and I'll try to put one together!\n" +
                "(This is a manual process, so it may take some time.)\n\n" +
                "To start a game immediately, try lowering the command limit.";
    }

    public static String getNoOpenGamesText() {
        return "There are currently no Surge games waiting for players -\n" +
                "use 'create_game surge' to create a new game!\n";
    }

    public static String getOpenGamesHeaderText(int count) {
        return "There are " + count + " currently open Surge games.\n" +
                "Use 'join_game surge [game_id]' to join one of the following:\n\n" +
                "Game ID\t#Plr\tTicks\tTZone\tC-Limit\n\n";
    }

    public static String getOpenGameDescription(SurgeGamesRecord game, List<UsersRecord> joined) {
        // Format of an open game: gameID, numPlayers, ticks, zone, flow, players
        StringBuilder sb = new StringBuilder();
        sb.append(game.getGameId()).append("\t");
        sb.append(game.getNumPlayers()).append("\t");
        sb.append(game.getTicksPerDay()).append("\t");
        sb.append(game.getGameTimezone().toString()).append("\t");
        sb.append(game.getCommandLimit()).append("\t");
        sb.append("Players: ");
        for (UsersRecord user : joined) {
            sb.append(user.getHandle()).append("  ");
        }
        return sb.toString();
    }

    public static String getMoveNotPlayerText(long gameId) {
        return "You requested status for Surge game id " + gameId + ".\n" +
                "You are not a player in this game, so the command is not allowed.\n\n" +
                "Use 'my_games surge' to get a list of the games you are a part of!";
    }

    public static String getMoveFailedParseText(long gameId, String error) {
        return "Your move command for Surge game id " + gameId + " failed to parse correctly.\n" +
                "Error message: " + error + "\n\n" +
                "<format>.";
    }

    public static String getMoveFailedLimitText(long gameId, int size, Integer commandLimit) {
        return "Your move command for Surge game id " + gameId + " is not legal.\n" +
                "You submitted a set of " + size + " commands, but the limit for this game\n" +
                "is " + commandLimit + ".\n";
    }

    public static String getIllegalMoveText(long gameId, String error) {
        return "Your move command for Surge game id " + gameId + " is not legal.\n" +
                "Error message(s):\n" + error + ".";
    }


    public static String getStatusFailedNoGameText(long gameId) {
        return "You requested status for Surge game id " + gameId + ".\n" +
                "This game id either doesn't exist or hasn't started.\n\n" +
                "Use 'my_games surge' to get a list of the games you are a part of!";
    }

    public static String getStatusFailedNotPlayerText(long gameId) {
        return "You requested status for Surge game id " + gameId + ".\n" +
                "You are not a player in this game, so the command is not allowed.\n\n" +
                "Use 'my_games surge' to get a list of the games you are a part of!";
    }

}
