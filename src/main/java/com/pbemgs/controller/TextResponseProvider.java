package com.pbemgs.controller;

import com.pbemgs.generated.tables.records.UsersRecord;
import com.pbemgs.model.MonoSymbol;

/**
 * Stores the blocks of text associated with the base help commands
 */
public class TextResponseProvider {

    public static String getMainHelpTextUnregistered() {
        return "The PBEMGS accepts commands in the subject line, in the general format [Command] [GameType] [GameID]\n\n" +
                "PBEMGS commands for unregistered users:\n\n" +
                "intro : get an introduction to the PBEMGS.\n" +
                "help : returns the contents of this email.\n" +
                "game_preview : return a list of the games in the system with a brief overview of each.\n" +
                "rules [GameType] : returns the detailed rules for the specified game.\n" +
                "check_handle : Checks what player handles (in-game names) are available for use.\n" +
                "               List the requested handles as a space or comma separated list in the email body.\n" +
                "create_account [handle] : Create a player account for the sending email, with the given handle.\n" +
                "test_display : Receive a test email to verify whether your client supports fixed-width fonts,\n" +
                "               Unicode characters, and (for HTML clients) color formatting.";
    }

    public static String getMainHelpTextRegistered() {
        return "The PBEMGS accepts commands in the subject line, in the general format [Command] [GameType] [GameId]\n\n" +
                "PBEMGS commands for registered users:\n\n" +
                "General Commands:\n" +
                "- help : returns this help message.\n" +
                "- intro : return a FAQ.\n" +
                "- game_preview : return a list of the games in the system with a brief overview.\n" +
                "- rules [GameType] : returns the detailed rules for the specified game, including" +
                "                     example boards and detailed move command formats.\n" +
                "- test_display : Receive a test email to verify whether your client supports fixed-width fonts,\n" +
                "                 Unicode characters, and (for HTML clients) color formatting.\n\n" +
                "- feedback : Send feedback to the system creator.\n" +
                "             Email body text will be forwarded as written.\n\n" +
                "Joining and Creating Games:\n" +
                "- create_game [GameType] : Creates a new game.\n" +
                "  - Example Subject Line: 'create_game ninetac'\n" +
                "  - Options must be in the email body.  Use 'rules [GameType] for required options.\n" +
                "- open_games [GameType] : Lists open games waiting for players.\n" +
                "                          Run 'open_games' by itself to see all open games, or specify\n" +
                "                          a game type (i.e. 'open_games ninetac') to filter results.\n" +
                "- join_game [GameType] [GameId] : Join an open game.\n" +
                "  - Example Subject Line: 'join_game ninetac 105\n\n" +
                "Game Status:\n" +
                "- my_games : Lists your active games.\n" +
                "- status [GameType] [GameId] : Gets the current state of a game you are playing.\n" +
                "  - Example Subject Line: 'status ninetac 105'\n\n" +
                "Making a Move:\n" +
                "- move [GameType] [GameId] : Submits a move.\n" +
                "  - Move details must be in the email body in a specific format.\n" +
                "  - Use 'rules [GameType]' to see the correct format for your game\n" +
                "  - Example for Ninetac:\n" +
                "    - Subject Line: 'move ninetac 105'\n" +
                "    - Email Body: '15'\n\n" +
                "Account Management: (not yet implemented):\n" +
                "- deactivate : Mark your account inactive.\n" +
                "- activate : Re-activate your inactive account.";
    }

    public static String getIntroText() {
        return "Welcome to the PBEMGS by Angry Turtle Studios — Async Games for Tactical Turtles!\n" +
                "Turn-based strategy delivered by email. No installs. No timers. No mercy (well, maybe a little...)\n\n" +
                "Why PBEMGS?\n" +
                " - Play on YOUR time: Think for minutes or days between moves - think correspondence chess with a modern twist and variety of games.\n" +
                " - Strategic Variety: From the mind-bending movements of Lines of Action, to nine tic-tac-toe boards at once,\n" +
                "   to capturing cards in Triad Cubed, to the real-time liquid chaos of Surge - our lineup blends originals with underappreciated classics.\n\n" +
                " - Inbox-Only: No apps, no logins - only an initial signup. Moves in, updates out - all via email.\n\n" +
                "What’s in the Library?\n" +
                "  Six games (and counting):\n" +
                "  - Classics like Ataxx, GoMoku, and Lines of Action\n" +
                "  - Originals like Surge (an async RTS!) and competitive twists like Ninetac and Triad Cubed\n" +
                "  - A lot more brewing!\n" +
                "  Check them out with 'game_preview'!\n\n" +
                "How It Works:\n" +
                "  1. Sign up: Email 'create_account yourhandle' to pbemgs@angryturtlestudios.com.\n" +
                "  2. Find a match: 'open_games' or filter by type 'open_games ninetac'\n" +
                "  3. Join or create: 'join_game [GameType] [ID]' or 'create_game [GameType]'.\n" +
                "  4. Make moves: Reply to game emails with commands like 'a2-a4' or 'orc b2'.\n" +
                "About That Whole 'Async' Thing:\n" +
                "  Play when life lets you - most games wait for your turn.  Surge ticks at set times daily, like a slow, strategy heartbeat.\n" +
                "  Reminders go out after 24h.  If you vanish for 3 days (typically), the server moves for you - randomly and without shame.\n\n" +
                "Making Moves:\n" +
                "  Your game emails come from pbemgs@mail.angryturtlestudios.com — reply with your move in the body.\n\n" +
                "  Formats vary by game.  Use 'rules [GameType]' if you're unsure.\n\n" +
                "System Requirements:\n" +
                "  Any email client with fixed-width display.  Some games glow with Unicode or HTML color — test yours with 'test_display'.\n\n" +
                "Need Help?\n" +
                "  Email a command of 'help' for the full command list - more commands unlock after signup.\n\n" +
                "  Have ideas or found a bug?  'feedback' is your friend.\n\n" +
                "Cost?\n" +
                "  100% free.  No ads, no nonsense.  Just good games.  Donations welcome - send 'feedback' for details.\n\n" +
                "PBEMGS - Strategy without schedule pressure. Take your turn, sharpen your mind, and conquer ... eventually!";
    }

    public static String getGamePreview() {
        return "List of PBEMGS games:\n\n" +
                "Available:\n" +
                "- ninetac: Nine Tic-Tac-Toe boards at once — every move shifts the entire game!\n" +
                "- ataxx: A fast-paced battle of expansion and control for 2 or 4 players!\n" +
                "- surge: A real-time strategy battle of liquid force and momentum — flood your enemies away! (HTML email required.)\n" +
                "- loa: Lines of Action – Connect your pieces with clever, dynamic movement before your opponent does!\n" +
                "- gomoku: The timeless 5-in-a-row connection game — simple to learn, hard to master!\n" +
                "- triad: Triad Cubed - A strategic best-of-three twist on the classic FF8 card placement game! (HTML email required.)\n\n" +
                "- ironclad: ironclad: Robots with lasers. Go stones with ambition. Two games on one board - and one win condition will end it all.\n" +
                "Tutorial:\n" +
                "- tac: A guided tutorial game for new players.\n\n" +
                "In Development:\n" +
                "- skirmish: Skirmish for Earth – Conquer the planet, one bidding round at a time!\n" +
                "- <name TBD>: A PvE Fantasy RPG Deckbuilder. Mix and match classes, evolve your deck, and survive the dungeons!\n\n" +
                "Send 'rules [GameType]' for detailed rules on any game!\n\n\n" +
                "In the Backlog (future ideas):\n" +
                "- Quantis: An original Archon-like battle, using atomic particles to shape the map and blow away the opponent.\n" +
                "- <name TBD>: A 4X space adventure of development and conquest, inspired by Master of Orion 2 among others.\n" +
                "- <name TBD>: An async MOBA where your choices shape the battlefield – by email!";
    }

    public static String invalidCommandBody(String commandReceived) {
        return "The command received (" + commandReceived + ") is not valid.\n" +
                "Send a subject of 'help' for a list of valid commands.";
    }

    public static String subjectParseError(String subjectLine, String parseError) {
        return "Could not parse the subject line.\n" +
                "Subject line received: " + subjectLine + "\n" +
                "Error Message: " + parseError + "\n\n" +
                "Subject lines should follow the format:\n" +
                "[Command] [GameType] [GameId]\n\n" +
                "Send a subject of 'help' for a list of valid commands.";
    }

    public static String getExistingHandleError(String handle) {
        return "You attempted to create an account with the handle " + handle + ".\n" +
                "This unfortunately is already taken.  Please try again with a different\n" +
                "handle.  You can also use the check_handle command to see if desired handles\n" +
                "are available.";
    }

    public static String accountCreatedBody(String handle) {
        return "Congratulations! Your account with handle " + handle + " was successfully created!\n\n" +
                "The 'help' command will now list all registered user commands.\n\n" +
                "Next Step: To help you get started, we'll be sending you a short tutorial game in just a moment.\n" +
                "This will guide you through making moves and using the system. Keep an eye on your inbox!\n\n" +
                "Community & Conduct:\n" +
                "- Have fun! This is all about enjoying games together.\n" +
                "- Be a good sport - win or lose, keep the games going and keep it friendly.\n" +
                "- Play fair — one account per person, please. (Future leaderboards will thank you!)\n\n" +
                "Welcome, and enjoy the games!";
    }

    public static String getRulesRequestErrorTextBody() {
        return "The 'rules' command requires a game name.\n" +
                "rules [GameType]\n\n" +
                "Use the 'game_preview' command to get a list of games in the\n" +
                "system with their associated GameType identifiers!";
    }

    public static String getCreateGameMissingGameErrorTextBody() {
        return "The 'create_game' command requires a game name in the subject line.\n" +
                "create_game [GameType]\n\n" +
                "Use the 'game_preview' command to get a list of games in the\n" +
                "system with their associated GameType identifiers!\n\n" +
                "Use the 'rules [GameType]' command to get the required game\n" +
                "creation options to put into the email body.";
    }

    public static String getExceptionTextBody(String command, String e) {
        return "There was an internal error handling your " + command + " command.\n" +
                "Exception caught: " + e + "\n" +
                "Please report this to the admin via the feedback command!";
    }

    public static String unimplementedCommandBody(String command) {
        return "The command passed (" + command + ") is valid but is still under construction!\n" +
                "Thank you for understanding.";
    }

    public static String getJoinGameBadSubjectFormatTextBody() {
        return "The 'join_game' command requires a game name and game ID in the subject line.\n" +
                "join_game [GameType] [GameId]\n\n" +
                "Use the 'open_games' command to get the game IDs of open games!";
    }

    public static String getMoveBadSubjectFormatTextBody() {
        return "The 'move' command requires a game name and game ID in the subject line.\n" +
                "move [GameType] [GameId]\n\n" +
                "Moves also require email body text in a specific format describing the move -\n" +
                "use 'rules [GameType]' for the expectations for each game.\n";
    }

    public static String getStatusBadSubjectFormatTextBody() {
        return "The 'status' command requires a game name and game ID in the subject line.\n" +
                "status [GameType] [GameId]\n\n" +
                "Status requests must also be for a game you are involved in!";
    }

    static String alignmentTest =
            "+-----+-----+-----+\n" +
                    "|     |     |     |\n" +
                    "+-----+-----+-----+\n" +
                    "|     |     |     |\n" +
                    "+-----+-----+-----+\n";

    static String colorTest =
            "<span style='color:red;'>RED X</span> " +
                    "<span style='color:blue;'>BLUE O</span> " +
                    "<span style='color:green;'>GREEN #</span> " +
                    "<span style='color:purple;'>PURPLE @</span> " +
                    "<span style='color:orange;'>ORANGE =</span> " + "\n";

    static String charTest =
            "Notation 1: " + '\u2605' + " " + '\u25CF' + " " + '\u2B1B' + "\n" +
                    "Notation 2: " + String.valueOf(Character.toChars(0x2605)) + " " +
                    String.valueOf(Character.toChars(0x25CF)) + " " + String.valueOf(Character.toChars(0x2B1B)) + "\n";

    static String unicodeAlignmentTest =
            "+-----+-----+-----+\n" +
                    "|  " + '\u25A0' + "  |  " + '\u25A1' + "  |  " + '\u25CF' + "  |\n" +
                    "+-----+-----+-----+\n" +
                    "|  " + '\u25CB' + "  |  " + '\u25BC' + "  |  " + '\u25AB' + "  |\n" +
                    "+-----+-----+-----+\n";

    public static String getTestDisplayHtmlTextBody() {
        return "Email Client Display Test:\n\n" +
                "This is an html email, so you may have capability to display color.\n\n" +
                "Alignment (fixed-with/monospace font) test:\n\n" +
                alignmentTest + "\n" +
                "There should be a 2x3 grid of boxes displayed above.  If they are not aligned, you\n" +
                "do not have any of the standard monospace fonts available.\n\n" +
                "Unicode (special character) tests:\n\n" +
                charTest + "\n\n" +
                "There should be two rows of 3 characters (star, circle, box) - if you do not see them you\n" +
                "won't be able to play games that require [Unicode].\n\n" +
                "Combined Alignment/Unicode test:\n\n" +
                unicodeAlignmentTest + "\n" +
                "HTML (color) tests:\n\n" +
                colorTest + "\n" +
                "If you do not see colors, your email client does not support inline HTML styling.";
    }

    public static String getTestDisplayPlainTextBody() {
        return "Email Client Display Test:\n\n" +
                "This is a plain-text email, so you do not have capability to display color.\n\n" +
                "Alignment (fixed-with/monospace font) test:\n\n" +
                alignmentTest + "\n" +
                "There should be a 2x3 grid of boxes displayed above.  If they are not boxes, you do not have\n" +
                "any of the standard monospace fonts available.\n\n" +
                "Unicode (special character) tests:\n\n" +
                charTest + "\n" +
                "There should be two rows of 3 characters (star, circle, box) - if you do not see them you\n" +
                "won't be able to play games that require [Unicode].\n" +
                "Combined Alignment/Unicode test:\n\n" +
                unicodeAlignmentTest;
    }

    public static String getMonoSymbolTestText() {
        MonoSymbol[] symbols = MonoSymbol.values();

        String pieceDivider = MonoSymbol.GRID_CROSS.getSymbol() +
                String.valueOf(MonoSymbol.GRID_HORIZONTAL.getSymbol()).repeat(5);
        StringBuilder fullDivider = new StringBuilder();
        fullDivider.append(pieceDivider.repeat(3)).append(MonoSymbol.GRID_CROSS.getSymbol());

        StringBuilder sb = new StringBuilder();
        sb.append("Monospace Symbol Alignment Test:\n\n");

        sb.append(fullDivider).append("\n");
        for (int i = 0; i < symbols.length; i += 3) {
            // Fill gaps with the first symbol of the row
            char symbol1 = symbols[i].getSymbol();
            char symbol2 = (i + 1 < symbols.length) ? symbols[i + 1].getSymbol() : symbol1;
            char symbol3 = (i + 2 < symbols.length) ? symbols[i + 2].getSymbol() : symbol1;

            String name1 = symbols[i].getName();
            String name2 = (i + 1 < symbols.length) ? symbols[i + 1].getName() : name1;
            String name3 = (i + 2 < symbols.length) ? symbols[i + 2].getName() : name1;

            sb.append(String.format("%c  %c  %c  %c  %c  %c  %c", MonoSymbol.GRID_VERTICAL.getSymbol(), symbol1,
                    MonoSymbol.GRID_VERTICAL.getSymbol(), symbol2,
                    MonoSymbol.GRID_VERTICAL.getSymbol(), symbol3, MonoSymbol.GRID_VERTICAL.getSymbol()));
            sb.append(String.format("\t( %-11s , %-11s , %-11s )\n", name1, name2, name3));
            sb.append(fullDivider).append("\n");
        }

        sb.append("\nIf the grid appears misaligned, certain symbols may not be monospace-safe.");

        return sb.toString();
    }

    public static String getFeedbackTextBody(UsersRecord user, String feedbackText) {
        return "Feedback from User ID: " + user.getUserId() + ", Handle: " + user.getHandle() +
                ", email: " + user.getEmailAddr() + "\n\n" +
                feedbackText + "\n\n" + "<eom>";
    }

    public static String getFeedbackThanksText() {
        return "Thank you for your feedback!  Your message has been forwarded.";
    }

    public static String getStaleGameEmailBody(String gameText) {
        return "Automated System Message from the PBEMGS:\n\nThe following game(s) are awaiting your move:\n\n" +
                gameText +
                "\n\nUse 'game_status [GameType] [GameID]' for the board state of a specific game.";
    }
}
