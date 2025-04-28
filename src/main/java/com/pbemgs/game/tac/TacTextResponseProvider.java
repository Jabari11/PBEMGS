package com.pbemgs.game.tac;

public class TacTextResponseProvider {

    public static String getTacRulesText() {
        return "Rules for Tic-Tac-Toe:\n\n" +
                "GameType Identifier: 'tac'\n\n" +
                "Overview:\n" +
                "   Welcome to the tutorial for how to use the PBEMGS system!\n" +
                "   Tic-Tac-Toe is used to demonstrate how to submit moves to the PBEMGS.  For this tutorial,\n" +
                "   the system itself will respond to your move immediately, whereas for the other games in the\n" +
                "   system, when you successfully submit a move, it will send the game state to the opponent(s) and\n" +
                "   wait for their response.\n\n" +
                "Objective:\n" +
                "   Learn how to get a list of what games you are playing, retrieve the board state of a game, and submit a move in a game.\n" +
                "   You can also try to win the game - the system is not going to try very hard against you.\n\n" +
                "Board Display Format:\n\n" +
                " 1 | 2 | 3 \n" +
                "---+---+---\n" +
                " 4 | 5 | 6 \n" +
                "---+---+---\n" +
                " 7 | 8 | 9 \n\n" +
                "Gameplay:\n" +
                "- Select a square to mark by number, using the move command format specified below.\n" +
                "Move Command Format:\n" +
                "- Subject line: move tac [game_id]\n" +
                "- Email Body:   [number-to-claim]\n\n" +
                "Example Move Command:\n" +
                " - Subject line: move tac 414\n" +
                " - Email Body:   4\n\n" +
                "Note that there can be extra text in the email body after the move information - you\n" +
                "do not need to manually remove a signature block or other trailing text.";
    }

    public static String getGameNotValidText() {
        return "You have submitted a move for a Tac game that either doesn't exist or is\n" +
                "already complete.  Find the Game ID of your Tac game by sending a\n" +
                "'my_games' command.\n\n" +
                "Once you have the Game ID, make sure that the same Game ID is on the subject line of\n" +
                "your move submit email: 'move tac [GameID]'";
    }

    public static String getMoveNotYourGameText() {
        return "You have submitted a move command for a Tac game that is not yours.\n" +
                "Find the Game ID of your Tac game by sending a 'my_games' command.\n\n" +
                "Once you have the Game ID, make sure that the same Game ID is on the subject line of\n" +
                "your move submit email: 'move tac [GameID]'";
    }

    public static String getMoveFailedParseText(String error) {
        return "Your move email in the tutorial Tac game failed to parse with the following error:\n" +
                error + "\n\n" +
                "The body of the move command email for Tac should just be a single number between 1 and 9.\n" +
                "(Trailing text is fine, as long as the first thing in the email body is the move information!)\n\n" +
                "Example:\n" +
                " - Subject line: move tac 414\n" +
                " - Email Body:   4";
    }

    public static String getMoveInvalidText() {
        return "Your move for the tutorial Tac game was received and parsed correctly, but is invalid!\n" +
                "It is either an occupied square, or outside of the valid range (1-9).";
    }

    // Post-result email text for how to proceed
    public static String getPostGameText() {
        return "You've completed the tutorial!\n\n" +
                "Now that you know how to play, why not try a more advanced game?\n\n" +
                "- See what games are available: 'game_preview'\n" +
                "- Check open games: 'open_games'\n" +
                "- Join a game: 'join_game [GameType] [GameID]\n" +
                "- Create a new game: 'create_game [GameType]\n\n" +
                "Send a command of 'help' to see a full list of available commands!";
    }

    public static String getGameWonText() {
        return "Game over - you won!\n\n" + getPostGameText();
    }

    public static String getGameLostText() {
        return "Game over - you lost (but must have been trying hard to, so well done!)\n\n" +
                getPostGameText();
    }

    public static String getGameDrawText() {
        return "Game over - drawn.\n" + getPostGameText();
    }

    public static String getCreateErrorText() {
        return "Tac is the tutorial game, which is automatically created for each user upon\n" +
                "account creation.  It is not possible to manually create additional Tac games!\n\n" +
                "Use the 'game_preview' command to get a list of the games in the PBEMGS system.";
    }

    public static String getJoinErrorText() {
        return "Tac is the tutorial game, which is automatically created for each user upon\n" +
                "account creation.  It is not possible to manually join additional Tac games!\n\n" +
                "Use the 'game_preview' command to get a list of the games in the PBEMGS system.";
    }

    public static String getStatusFailedNoGameText(long gameId) {
        return "You have submitted a game_status command for a Tac game that either doesn't exist or is\n" +
                "already complete.  Find the Game ID of your Tac game by sending a 'my_games' command.\n\n" +
                "Once you have the Game ID, make sure that the same Game ID is on the subject line of\n" +
                "your move submit email: 'game_status tac [GameID]'";
    }

    public static String getStatusFailedNotYourGameText(long gameId) {
        return "You have submitted a game_status command for a Tac game that is not yours.\n" +
                "Find the Game ID of your Tac game by sending a 'my_games' command.\n\n" +
                "Once you have the Game ID, make sure that the same Game ID is on the subject line of\n" +
                "your move submit email: 'game_stats tac [GameID]'";
    }

    public static String getCreateGameFooterText(long gameId) {
        return "Welcome to the PBEMGS system!\n\n" +
                "We've created a simple Tic-Tac-Toe game for you to learn how to make moves and\n" +
                "interact with the system.\n\n" +
                "The GameType identifier for the tutorial is 'tac', and this is game number (Game ID) " + gameId + ".\n\n" +
                "It's your turn now!  To submit a move:\n" +
                "- Reply to this email.\n" +
                "- The subject line should be in the correct 'move tac " + gameId + "' format." +
                "- Type a number (1-9) in the body of the email\n" +
                "- Hit 'Send'!\n\n" +
                "The following commands are also available to interact with this game -\n" +
                "feel free to try them all!\n\n" +
                "'rules tac' - returns the rules page for Tac, emphasizing how to interact with the system.\n" +
                "'my_games' - shows a list of your games with their Game IDs - this one should be on the list.\n" +
                "'status tac " + gameId + "' - returns the current game status of this game.\n\n" +
                "The 'help' command will return a general help message (different than before you registered), and you\n" +
                "can always run 'info' for a FAQ.";
    }

    public static String getGameStatusFooterText(long gameId) {
        return "This email is generated by a 'game_status' command, returning the current state of\n" +
                "your tutorial game.  To submit a move, send an email to 'pbemgs@angryturtlestudios.com'\n" +
                "with the subject line 'move tac " + gameId + "'\n\n" +
                "You will always need to write the subject line manually in response to a 'game_status' email.\n";
    }

    public static String getMoveResponseFooterText() {
        return "This email is generated when it is your turn in a game.\n\n" +
                "For submitting your own move in response to a move notification,\n" +
                "you can simply reply to the move notification email and the subject line\n" +
                "will already have the correct format!  Try that here - just reply to\n" +
                "this email with your move in the text body.";
    }
}
