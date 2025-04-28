package com.pbemgs.game.triad;

import com.pbemgs.generated.tables.records.TriadGamesRecord;
import com.pbemgs.generated.tables.records.UsersRecord;
import com.pbemgs.model.MonoSymbol;

import java.util.List;

import static com.pbemgs.game.triad.TriadDisplayDefs.BOT_BORDER;
import static com.pbemgs.game.triad.TriadDisplayDefs.SHADING_CHAR;
import static com.pbemgs.game.triad.TriadDisplayDefs.TOP_BORDER;

/**
 * Stores blocks of text associated with Triad Cubed.
 */
public class TriadCubedTextResponseProvider {

    public static String getTriadCubedRulesText() {
        return "Rules for Triad Cubed:\n\n" +
                "(Requires HTML mail for color and Unicode characters)\n" +
                "GameType Identifier: 'triad'\n\n" +
                "Overview:\n" +
                "  Triad Cubed is a balanced, competitive version of the Final Fantasy VIII card-placement mini-game.\n" +
                "  In this standalone version, two players compete in a best-of-three format, drafting their hands for each game\n" +
                "  from a starting pool of 15 cards and battling to control a 3x3 grid.\n" +
                "  Simple to learn but rich in strategy, Triad Cubed is perfect for quick, tactical duels!\n\n" +
                "Credits:\n" +
                "  Triad Cubed is inspired by the Triple Triad mini-game from Final Fantasy VIII, developed by Square Enix.\n\n" +
                "Objective:\n" +
                "  Win two out of three sub-games by controlling more cards on the 3x3 grid at the end of each round.\n\n" +
                "Gameplay:\n" +
                "  Card Anatomy:\n" +
                "  - Each card has values on its four sides, as follows:\n" +
                getSampleCard(List.of(3, 2, 6, 7), false) + "\n" +
                "  - The top, right, bottom, and left numbers indicate the card's strength in that direction.\n\n" +
                "  Hand Selection:\n" +
                "  - Each player selects 5 cards from the 15-card set.\n" +
                "  - Chosen cards cannot be used again for future rounds in this match.\n" +
                "  - Selections are simultaneous and hidden.\n\n" +
                "  Card Placement:\n" +
                "  - Players take turns placing one card at a time onto the 3x3 board.\n" +
                "  - When a card is placed adjacent to an opponent's card, the following happens:\n" +
                "    - If the placed card has a higher value on the connecting side, the opponent's card is captured and flipped.\n" +
                "    - Equal values do not result in a capture." +
                "    - It is possible to capture multiple cards in a single move.\n" +
                "  - Example:\n" +
                "    A card with an Right-side value of 5 placed next to an opponent's card with a Left-side value of 3 will capture that card.\n\n" +
                "  Sub-Game End and Match Victory:\n" +
                "  - A sub-game ends when all nine grid spaces are filled.\n" +
                "  - The player with more cards under their control wins the sub-game.\n" +
                "  - The first player to win two sub-games wins the match.\n\n" +
                "  Elemental Option (if enabled):\n" +
                "  - If enabled, some cards and board squares have elemental properties (Fire, Ice, Lit).\n" +
                "  - Card elements are displayed below the card name in hand selection.\n" +
                "  - Elemental squares are marked by their first letter in the center of the square.\n" +
                "  - Playing a card on a matching elemental square increases all of its values by +1.\n" +
                "  - Boosted Cards Display:\n" +
                "    - A boosted card (placed on a matching elemental square) will display with its original values but shaded, as follows:\n" +
                getSampleCard(List.of(5, 9, 3, 4), true) + "\n" +
                "    - The actual values of this card used are 6 (up), 10 (right), 4 (down), 5 (left).\n\n" +
                "  Turn Order and First Player:\n" +
                "  - The first player is randomly determined for the first sub-game.\n" +
                "  - The starting player alternates for subsequent sub-games.\n\n" +
                "Move Command Formats:\n" +
                "  Card Placement:\n" +
                "  - Subject: 'move triad [game_id]'\n" +
                "  - Body: '[slot] [location]' or '[name] [location]'\n" +
                "    - '[slot]': Position in hand (1 - 5).\n" +
                "    - '[name]': Card name (case-insensitive).\n" +
                "    - '[location]': Board position (e.g., 'A1', 'B2', 'C3').\n\n" +
                "  Example Moves:\n" +
                "  - Subject: 'move triad 1234'\n" +
                "  - Body: '1 A1' -> Places card from hand slot 1 to A1\n" +
                "  - Body: 'Goblin B3' -> Places 'Goblin' card at B3\n\n" +
                "  Hand Selection:\n" +
                "  - Subject: 'move triad [game_id]'\n" +
                "  - Body: A space- or comma-separated list of five card names or card IDs.\n\n" +
                "  Example Hand Selections:\n" +
                "  - Subject: 'move triad 1234'\n" +
                "  - Body: Imp,Goblin,Dragon,Zephyr,Orc\n" +
                "  - Body: 5, 13, 10, 1, 7\n\n" +
                "Game Creation Options:\n" +
                " - There are two optional settings - omitting them from game creation command defaults them to 'off'.\n" +
                "   - open: If enabled, cards in hand are visible to both players.\n" +
                "   - element: If enabled, some cards and squares will have elemental properties as described above.\n\n" +
                " - Enable using 'on', 'yes', or 'true' in the game creation email body.\n" +
                " Example Game Creation Command:\n" +
                " - Subject: 'create_game triad'\n" +
                " - Body: open:on\n" +
                "         element:off\n\n" +
                "Meta-Rules:\n" +
                " - Max simultaneous games per player: 3\n" +
                " - Max open games: 10\n" +
                " - Reminder: Players are nudged after 24 hours of inactivity.\n" +
                " - Auto-Move: A random hand selection or move is chosen after 3 days (72 hours).\n\n" +
                "Status:\n" +
                " - AVAILABLE!  I may add options for the SAME and PLUS rules, but everything here\n" +
                "   is ready to go!  Feedback?  Ping me with a 'feedback' command!";
    }

    private static String getSampleCard(List<Integer> sides, boolean shaded) {
        char shade = shaded ? SHADING_CHAR : ' ';
        StringBuilder sb = new StringBuilder();
        sb.append(TOP_BORDER).append("\n");
        sb.append(MonoSymbol.GRID_VERTICAL.getSymbol()).append(shade).append(sides.get(0)).append(shade);
        sb.append(MonoSymbol.GRID_VERTICAL.getSymbol()).append("\n");
        sb.append(MonoSymbol.GRID_VERTICAL.getSymbol()).append(sides.get(3)).append(shade);
        sb.append(sides.get(1)).append(MonoSymbol.GRID_VERTICAL.getSymbol()).append("\n");
        sb.append(MonoSymbol.GRID_VERTICAL.getSymbol()).append(shade).append(sides.get(2)).append(shade);
        sb.append(MonoSymbol.GRID_VERTICAL.getSymbol()).append("\n");
        sb.append(BOT_BORDER).append("\n");
        return sb.toString();
    }

    public static String getNoOpenGamesText() {
        return "There are currently no open Triad Cubed games to join - use 'create_game triad' to create one!\n";
    }

    public static String getOpenGamesHeaderText(int count) {
        return "There are " + count + " currently open Triad Cubed games.\n" +
                "Use 'join_game triad [game_id]' to join one of the following:\n";
    }

    public static String getOpenGameDescription(TriadGamesRecord game, UsersRecord usersRecord) {
        return "- Game ID: " + game.getGameId() + ";  OpenHand: " +
                (game.getOptionFaceup() ? "on " : "off") + ", Elemental: " +
                (game.getOptionElemental() ? "on " : "off") + " - Created By: " + usersRecord.getHandle() + "\n";
    }

    public static String getMoveFailedText(long gameId, String errorMsg) {
        return "Your move for Triad Cubed game ID " + gameId + " is not valid.\n" +
                "Error message: " + errorMsg;
    }

    public static String getInvalidCardSlotText(Integer slot) {
        return "The requested card slot number " + slot + " is not valid.  Please select a valid card number\n" +
                "or name to place.  Move format: [Slot/Name] [Location] - i.e. '2 A2' or 'Imp B1'.";
    }

    public static String getInvalidCardNameText(String name) {
        return "The requested card name " + name + " is not a card in your hand.  Please select a valid card number\n" +
                "or name to place.  Move format: [Slot/Name] [Location] - i.e. '2 A2' or 'Imp B1'.";
    }

    public static String getHandSelectionCompleteText(Long gameId) {
        return "You attempted to select your hand for Triad Cubed game " + gameId + " - you have already selected!\n" +
                "You will get an email once your opponent has selected their hand.";
    }

    public static String getInvalidHandSelectonText(Long gameId, StringBuilder errorMsg) {
        return "Your attempted hand selection for Triad Cubed game " + gameId + " failed.\n" +
                "Error message: " + errorMsg;
    }

    public static String getPlayerHeader(String colorStr, String handle,
                                         int cardCount, boolean activeTurn) {
        return "<span style='color:" + colorStr + ";'>" +
                "(" + cardCount + ") " + handle + (activeTurn ? " - TO MOVE!" : "") + "</span>\n";
    }

    public static String getHandDisplay(String myHand, String theirHand, boolean optionFaceup) {
        String myStr = "Your cards in hand:\n\n" + myHand;
        String theirStr = optionFaceup ? "Opponent's cards in hand:\n\n" + theirHand : "";
        return myStr + theirStr;
    }

    public static String getHandSelectionDisplay(String myHand, String myUndrafted) {
        if (!myHand.isBlank()) {
            return "Your selected hand:\n\n" + myHand;
        }
        return "Select 5 cards from the following:\n" +
                "(Undrafted Cards - once picked they are unavailable for future rounds.)\n\n" + myUndrafted;
    }

}
