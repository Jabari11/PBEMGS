package com.pbemgs.game;

import com.pbemgs.controller.SESEmailSender;
import com.pbemgs.model.GameType;

/**
 * Email message sender utility for things that are common across all games, such as
 * error messages for too many active games, etc.
 */
public class GameMessageMailer {

    public static void gameLimitReached(SESEmailSender sender, String toEmail, String command, GameType gameType) {
        sender.sendEmail(toEmail, "PBEMGS - " + command + " " + gameType.getGameName() + " failed (game limit).",
                "Whoa, you’ve hit the max for active " + gameType.getGameName() + " games!\n" +
                        "Time to finish one before starting more.");
    }

    public static void openGamesLimitReached(SESEmailSender sender, String toEmail, GameType gameType) {
        sender.sendEmail(toEmail, "PBEMGS - create_game " + gameType.getGameName() + " failed (open limit).",
                "Too many open " + gameType.getGameName() + " games already!\n" +
                        "Join the fun instead — try 'open_games " + gameType.getGameName() + "' to find one to hop into!");
    }

    public static void createOptionsInvalid(SESEmailSender sender, String toEmail, GameType gameType, String errorMsg) {
        sender.sendEmail(toEmail, "PBEMGS - create_game " + gameType.getGameName() + " failed (options).",
                "Oops, your 'create_game " + gameType.getGameName() + "' didn’t quite work — something’s off with the options!\n\n" +
                        "Error: " + errorMsg + ".\n\n" +
                        "Check 'rules " + gameType.getGameName() + "' to get it right next time!");
    }

    public static void createSuccess(SESEmailSender sender, String toEmail, GameType gameType, long gameId) {
        sender.sendEmail(toEmail, "PBEMGS - create_game " + gameType.getGameName() + " success!",
                "Your " + gameType.getGameName() + " game is ready — Game ID: " + gameId + ".\n\n" +
                        "Now just waiting for some brave opponents!");
    }

    public static void joinNonopenGame(SESEmailSender sender, String toEmail, GameType gameType, long gameId) {
        sender.sendEmail(toEmail, "PBEMGS - join_game " + gameType.getGameName() + " failed (unavailable).",
                "No dice! " + gameType.getGameName() + " game " + gameId + " isn’t open for new players.\n\n" +
                        "Find one that is with 'open_games " + gameType.getGameName() + "'!");
    }

    public static void joinAlreadyIn(SESEmailSender sender, String toEmail, GameType gameType, long gameId) {
        sender.sendEmail(toEmail, "PBEMGS - join_game " + gameType.getGameName() + " failed (already in).",
                "You’re already in " + gameType.getGameName() + " game " + gameId + " - no need to join again!");
    }

    public static void joinSuccess(SESEmailSender sender, String toEmail, GameType gameType, long gameId) {
        sender.sendEmail(toEmail, "PBEMGS - join_game " + gameType.getGameName() + " success!",
                "Welcome aboard! You’re in " + gameType.getGameName() + " game " + gameId + " — just waiting on\n" +
                        "a few more players to kick things off!");
    }

    public static void moveGameNotValid(SESEmailSender sender, String toEmail, GameType gameType, long gameId) {
        sender.sendEmail(toEmail, "PBEMGS - move " + gameType.getGameName() + " " + gameId + " failed (invalid game).",
                "Hmm, " + gameType.getGameName() + " game " + gameId + " doesn't exist - it either hasn't been\n" +
                        "created yet or has already completed.\n" +
                        "Check 'my_games " + gameType.getGameName() + "' for your real games!");
    }

    public static void moveNotAPlayer(SESEmailSender sender, String toEmail, GameType gameType, long gameId) {
        sender.sendEmail(toEmail, "PBEMGS - move " + gameType.getGameName() + " " + gameId + " failed (not your game).",
                "Nice try, but " + gameType.getGameName() + " game " + gameId + " isn’t yours to mess with!\n\n" +
                        "Find your games with 'my_games " + gameType.getGameName() + "'!");
    }

    public static void moveNotActiveText(SESEmailSender sender, String toEmail, GameType gameType, long gameId) {
        sender.sendEmail(toEmail, "PBEMGS - move " + gameType.getGameName() + " " + gameId + " failed (not active).",
                "Hold up! It’s not your turn in " + gameType.getGameName() + " game " + gameId + " — or maybe\n" +
                        "it’s not your game at all.\n\n" +
                        "Peek at 'my_games " + gameType.getGameName() + "' to see what’s up!");
    }

    public static void moveFailedToParse(SESEmailSender sender, String toEmail, GameType gameType, long gameId, String errorMsg) {
        sender.sendEmail(toEmail, "PBEMGS - move " + gameType.getGameName() + " " + gameId + " failed (bad format).",
                "Hey, your move for " + gameType.getGameName() + " game " + gameId + " didn’t quite work.\n\n" +
                        "What went wrong: " + errorMsg + "\n\n" +
                        "Try 'rules " + gameType.getGameName() + "' to get back on track!");
    }


    public static void statusNotValidGame(SESEmailSender sender, String toEmail, GameType gameType, long gameId) {
        sender.sendEmail(toEmail, "PBEMGS - status " + gameType.getGameName() + " " + gameId + " failed (invalid game).",
                "Oops, " + gameType.getGameName() + " game " + gameId + " isn’t on the radar — or it hasn’t kicked off yet.\n\n" +
                        "See your games with 'my_games " + gameType.getGameName() + "'!");
    }

    public static void statusNotYourGame(SESEmailSender sender, String toEmail, GameType gameType, long gameId) {
        sender.sendEmail(toEmail, "PBEMGS - status " + gameType.getGameName() + " " + gameId + " failed (not your game).",
                "Caught ya! " + gameType.getGameName() + " game " + gameId + " isn’t yours to peek at — no sneaky spying allowed!\n\n" +
                        "Check 'my_games " + gameType.getGameName() + "' for your own battles.");
    }
}
