package com.pbemgs.game.ironclad;

import com.pbemgs.generated.enums.IroncladGamesCurrentMovePhase;
import com.pbemgs.generated.enums.IroncladGamesForcedMoveOption;
import com.pbemgs.generated.tables.records.UsersRecord;
import com.pbemgs.model.MonoSymbol;

import java.util.List;

import static com.pbemgs.game.triad.TriadDisplayDefs.BOT_BORDER;
import static com.pbemgs.game.triad.TriadDisplayDefs.SHADING_CHAR;
import static com.pbemgs.game.triad.TriadDisplayDefs.TOP_BORDER;

/**
 * Stores blocks of text associated with Ironclad
 */
public class IroncladTextResponseProvider {

    public static String getIroncladRulesText() {
        IroncladBoard rulesDisplay = new IroncladBoard();
        String robots = "B:E2:3,B:D3:1,W:D4:1,W:C4:2,B:A7:2,W:C7:3,W:F4:1,B:A1:1";
        String stones = "...B..W|.BW....|.B.WB..|..BW...|.B.B..B|WWW.BBB|..WW.B.|...W.WW|....B..";
        rulesDisplay.deserialize(robots, stones);

        return "Rules for Ironclad:  (Requires Unicode characters)\n\n" +
                "GameType Identifier: 'ironclad'\n\n" +
                "Overview:\n" +
                "  Ironclad is a two-player abstract strategy game blending two sub-games on different perspectives\n" +
                "  of the 8-row by 6-column chessboard.\n" +
                "  Robots occupy the squares, moving and firing lasers in a tactical race to reach the opposite end\n" +
                "  of the board without being blown to smithereens.\n" +
                "  At the same time, stone populate the corners of the squares. aiming to form a complete chain\n" +
                "  from one side of the board to the other.\n" +
                "  Winning either sub-game - robots or stones - wins the game, with subtle interactions between the two\n" +
                "  shaping the battlefield.\n\n" +
                "Credits:\n" +
                "  Ironclad was created by Frank Lantz as a commissioned study for the book Rules of Play: Game Design Fundamentals\n" +
                "  written by Katie Salen and Eric Zimmerman.\n\n" +
                "Objective:\n" +
                "  Win by either:\n" +
                "  - Moving a robot to the opponent's back row.\n" +
                "  - Connecting your stones from one side of the board to the opposite side (left to right or top to bottom).\n\n" +
                "Sample Board:\n" +
                "  The following mid-game board illustrates the rules. Robots occupy the squares of the grid,\n" +
                "  while stones are placed on the corners between squares. Coordinate guides are provided along\n" +
                "  the edges to indicate positions for both robot and stone movement.\n\n" +
                "  In the upper area of the board, Black has robots on A1 and E2.  There are White stones on\n" +
                "  g1, c2, and d3, and Black stones on d1, b2, b3, and e3.\n\n" +
                rulesDisplay.getBoardStateExpandedText() + "\n" +
                rulesDisplay.getRobotLegendText() + "\n\n" +
                "Gameplay:\n" +
                "  The Stones:\n" +
                "  - Setup: There are no stones on the initial board.\n" +
                "  - Drop: Place your stone on any free intersection, but not on one where a robot occupies any of the adjacent squares.\n" +
                "  - Move: If you cannot legally place any stones on the board, slide any stone (yours or your opponent's)\n" +
                "          horizontally or vertically any distance, without jumping over any others.  This movement is\n" +
                "          not restricted by robots, but the same stone can't be moved twice in a row.\n\n" +
                "  The Robots:\n" +
                "  - Setup: Each player starts with 6 robots on their closest two rows - two with 1 health (HP), two with 2HP,\n" +
                "    and 2 with 3 HP.\n" +
                "  - Move: Robots can move one square in any direction (including diagonally) to an unoccupied square.\n" +
                "  - Fire: Instead of moving, target an enemy robot within two squares (straight or diagonally).  All of your robots within\n" +
                "          range fire at once - each rolls a d6.  Rolls must exceed the target's cover (number of stones on its corners) to hit.\n" +
                "          Each hit reduces the targets HP by 1, and the target is destroyed if its HP reaches 0.\n\n" +
                "  - Firing Examples (from the sample board):\n" +
                "    - White targets D3: The robots at C4 and D4 both fire, needing a 4 or better as the target has 3 cover (the stones at d3, e3, and d4).\n" +
                "    - White targets E2: Only the robot at C4 fires, needing a 2 or better to hit.  Note that the robot on D3 does not block the attack.\n" +
                "    - Black targets C4: D3 and E2 both fire, needing a 4 or better to hit against 3 cover.\n" +
                "    - Black also has potential targets on D4 and C7 with 1 attacker each.\n\n" +
                "  Turn Order:\n" +
                "  - Each turn has two phases:\n" +
                "    - First, make a move in the sub-game opposite your opponent's last move.\n" +
                "      (If your opponent last moved a robot, you start with a stone move.)\n" +
                "    - Second, make a move in either sub-game.\n" +
                "    - On the first turn of the game, only the second phase is used.\n\n" +
                "  VERY IMPORTANT CRITICAL NOTE: The game creator sets the first phase as moving your own pieces or your opponent's pieces!\n\n" +
                "  Victory Conditions:\n" +
                "  - Robot win: Move a robot to the opponent's back row (White to row 1, Black to row 8).\n" +
                "  - Stone win: Connect your stones from one side to the opposite side. Diagonally-opposing corners of\n" +
                "               a square are not connected.\n" +
                "  - In the example board above, Black can win the game by moving the robot on A7 to either A8 or B8.\n" +
                "    White can win by placing a stone on e7, completing a chain from a6 to g8.\n\n" +
                "Move Command Formats:\n" +
                "  Subject: 'move ironclad [game_id]\n\n" +
                "  Send both moves of in a single email (on separate lines), or one at a time.\n\n" +
                "  Robot Move:\n" +
                "  - Body: 'robot [from] [to]'\n" +
                "  Robot Fire:\n" +
                "  - Body: 'fire [target]\n" +
                "  Stone Drop:\n" +
                "  - Body: 'stone [location]\n" +
                "  Stone Move:\n" +
                "  - Body: 'stonemove [from] [to]\n" +
                "  Example Moves:\n" +
                "  - Subject: 'move ironclad 1234'\n" +
                "  - Body: 'robot C4 B4' -> Move a robot from C4 to B4\n" +
                "  - Body: 'fire D3'     -> Fire at the enemy robot on D3\n" +
                "  - Body: 'stone B3'    -> Place a stone at B3\n" +
                "  - Body: 'stonemove G4 G1' -> Move the stone on G4 to G1\n" +
                "  - Body: 'stone D6\n" +
                "           robot C7 B6'  -> Place a stone at D6, then move the robot on C7 to B6\n\n" +
                "Game Creation Options:\n" +
                "  - There is one required setting - whether the first phase of each turn is to move your own pieces or your opponent's.\n" +
                "    - first: 'enemy' or 'self'\n" +
                "  Example Game Creation Command:\n" +
                "  - Subject: 'create_game ironclad'\n" +
                "  - Body: first=self\n\n" +
                "Meta-Rules:\n" +
                "  - Max simultaneous games per player: 3\n" +
                "  - Max open games: 10\n" +
                "  - Reminder: Players are nudged after 24 hours of inactivity.\n" +
                "  - Auto-Move: A random move is chosen after 3 days (72 hours).\n\n" +
                "Status:\n" +
                "  - Released!  Try out both rules variants and let me know which you prefer.";
    }

    public static String getNoOpenGamesText() {
        return "There are currently no open Ironclad games to join - use 'create_game ironclad' to create one!\n";
    }

    public static String getOpenGamesHeaderText(int count) {
        return "There are " + count + " currently open Ironclad games.\n" +
                "Use 'join_game triad [game_id]' to join one of the following:\n";
    }

    public static String getOpenGameDescription(Long gameId, IroncladGamesForcedMoveOption option, UsersRecord usersRecord) {
        return "- Game ID: " + gameId + " - First phase moves are on: " +
                (option == IroncladGamesForcedMoveOption.SELF ? "OWN" : "ENEMY") +
                " pieces.  Created By: " + usersRecord.getHandle() + "\n";
    }

    public static String getMoveFailedText(long gameId, int moveNum, String errorMsg) {
        return "Your move for Ironclad game ID " + gameId + " is not valid.\n" +
                "It was the " + (moveNum == 0 ? "first" : "second") + " move that had an error.\n\n" +
                "Error message: " + errorMsg;
    }

    public static String getPlayerHeader(IroncladSide side, String handle, boolean activeTurn) {
        return side.getStoneDisplayChar() + " - " + handle + (activeTurn ? " - TO MOVE!" : "") + "\n";
    }

    public static String getPhaseHeader(IroncladGamesCurrentMovePhase currentMovePhase, IroncladGamesForcedMoveOption forcedMoveOption) {
        if (currentMovePhase == IroncladGamesCurrentMovePhase.OPEN_MOVE) {
            return "\nThe active player has one move, which may be either a Robot move or a Stone move.\n\n";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("\nThe active player has two moves. The first must be a ")
                .append(currentMovePhase == IroncladGamesCurrentMovePhase.FORCED_ROBOT ? "Robot" : "Stone")
                .append(" move.\n");
        if (forcedMoveOption == IroncladGamesForcedMoveOption.ENEMY) {
            sb.append("The first move must be with the opponent's pieces!\n");
        }
        sb.append("\n");
        return sb.toString();
    }
}
