package com.pbemgs.game;

import com.pbemgs.controller.SESEmailSender;

/**
 * Email message sender utility for things that are common across all games, such as
 * error messages for too many active games, etc.
 */
public class GameMessageMailer {

    public static void gameLimitReached(SESEmailSender sender, String toEmail, String command, String gameName) {
        sender.sendEmail(toEmail, "PBEMGS - " + command + " " + gameName + " failed.",
                "You have reached the maximum number of active " + gameName + " games.");
    }

    public static void openGamesLimitReached(SESEmailSender sender, String toEmail, String gameName) {
        sender.sendEmail(toEmail, "PBEMGS - create_game " + gameName + " failed.",
                 "The maximum number of open " + gameName + " games has been reached.\n\n" +
                         "Use 'open_games " + gameName + "' and join an open game instead of creating a new one!");
    }

    public static void createSuccess(SESEmailSender sender, String toEmail, String gameName, long gameId) {
        sender.sendEmail(toEmail, "PBEMGS - create_game " + gameName + " success!",
                "Your " + gameName + " game has been created and is waiting for opponent(s)! Game ID: " + gameId);
    }

    public static void joinNonopenGame(SESEmailSender sender, String toEmail, String gameName, long gameId) {
        sender.sendEmail(toEmail, "PBEMGS - join_game " + gameName + " failed.",
                "You cannot join " + gameName + " game " + gameId + " as it is not waiting for players.\n\n" +
                "Use 'open_games " + gameName + "' to get the game IDs of open games.");
    }

    public static void joinSelf(SESEmailSender sender, String toEmail, String gameName) {
        sender.sendEmail(toEmail, "PBEMGS - join_game " + gameName + " failed.",
                "You cannot join a game you are already part of!");
    }

    public static void joinSuccess(SESEmailSender sender, String toEmail, String gameName, long gameId) {
        sender.sendEmail(toEmail, "PBEMGS - join_game " + gameName + " success!",
                "You have been added to " + gameName + " game ID " + gameId + "!\n" +
                "This game is still awaiting additional player(s).");
    }

    public static void getMoveNotActiveText(SESEmailSender sender, String toEmail, String gameName, long gameId) {
        sender.sendEmail(toEmail, "PBEMGS - move " + gameName + " " + gameId + " failed.",
        "You requested a move in " + gameName + " game ID " + gameId + ".\n" +
                "It is either your opponent's turn, or this isn't a game you are part of.\n\n" +
                "Use 'my_games " + gameName + "' to get the list of games you are a part of and the current player!");
    }

    public static void moveGameNotValid(SESEmailSender sender, String toEmail, String gameName, long gameId) {
        sender.sendEmail(toEmail, "PBEMGS - move " + gameName + " " + gameId + " failed.",
        gameName + " game ID " + gameId + " either doesn't exist or is not an in-progress game.\n" +
                "Use 'my_games " + gameName + "' to get the list of games you are a part of!");

    }


}
