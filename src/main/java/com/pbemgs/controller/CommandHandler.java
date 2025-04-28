package com.pbemgs.controller;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.pbemgs.dko.UsersDKO;
import com.pbemgs.game.GameFactory;
import com.pbemgs.game.GameInterface;
import com.pbemgs.game.tac.Tac;
import com.pbemgs.generated.enums.UsersStatus;
import com.pbemgs.generated.enums.UsersUserType;
import com.pbemgs.generated.tables.records.UsersRecord;
import com.pbemgs.model.GameType;
import com.pbemgs.model.S3Email;
import org.jooq.DSLContext;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class CommandHandler {

    private final LambdaLogger logger;
    private final DSLContext dslContext;
    private final UsersDKO usersDKO;


    public CommandHandler(DSLContext dslContext, LambdaLogger logger) {
        this.logger = logger;
        this.dslContext = dslContext;
        usersDKO = new UsersDKO(dslContext);
    }

    public void handleCommand(UsersRecord user, MainEmailProcessor.SubjectLineCommand command, S3Email email) {
        // TODO: ACTIVATE, DEACTIVATE, LIST_NEW_USERS
        logger.log("handle command for user: " + user.getEmailAddr() + ", command: " + command.command().name());
        SESEmailSender emailSender = new SESEmailSender(logger);
        String from = user.getEmailAddr();
        switch (command.command()) {
            case INTRO:
                emailSender.sendEmail(from, "PBEMGS - Intro/FAQ", TextResponseProvider.getIntroText());
                break;
            case HELP_BASE:
                emailSender.sendEmail(from, "PBEMGS - Help", TextResponseProvider.getMainHelpTextRegistered());
                break;
            case TEST_DISPLAY:
                processTestDisplay(from, emailSender);
                break;
            case GAME_LIST:
                emailSender.sendEmail(from, "PBEMGS - Game List and Preview", TextResponseProvider.getGamePreview());
                break;
            case FEEDBACK:
                processFeedbackRequest(user, email, emailSender);
                break;
            case RULES:
                processGameRulesRequest(from, command, emailSender);
                break;
            case CREATE_GAME:
                processGameCreateRequest(user, command, email, emailSender);
                break;
            case OPEN_GAMES:
                processOpenGamesRequest(user, command, emailSender);
                break;
            case JOIN_GAME:
                processJoinGameRequest(user, command, emailSender);
                break;
            case MOVE:
                processMoveRequest(user, command, email, emailSender);
                break;
            case MY_GAMES:
                processMyGamesRequest(user, command, emailSender);
                break;
            case GAME_STATUS:
                processGameStatusRequest(user, command, emailSender);
                break;
            case GLOBAL_NOTIFICATION:
                processNotification(user, email, emailSender);
                break;
            case TEST_SYMBOL:
                emailSender.sendEmail(from, "PBEMGS - Unicode symbol alignment test", TextResponseProvider.getMonoSymbolTestText());
                break;
            default:
                emailSender.sendEmail(user.getEmailAddr(), "PBEMGS - registered user command not available yet", TextResponseProvider.unimplementedCommandBody(command.command().toString()));
        }
    }  // end handleCommand()

    // handle commands for unregistered senders
    // TODO: CHECK_HANDLE
    public void handleCommandUnregistered(String from, MainEmailProcessor.SubjectLineCommand command, S3Email email) {
        logger.log("handle command for unregistered user: " + from + ", command: " + command.command().name());

        SESEmailSender emailSender = new SESEmailSender(logger);
        switch (command.command()) {
            case INTRO:
                emailSender.sendEmail(from, "PBEMGS - Intro/FAQ", TextResponseProvider.getIntroText());
                break;
            case HELP_BASE:
                emailSender.sendEmail(from, "PBEMGS - Help", TextResponseProvider.getMainHelpTextUnregistered());
                break;
            case TEST_DISPLAY:
                processTestDisplay(from, emailSender);
                break;
            case GAME_LIST:
                emailSender.sendEmail(from, "PBEMGS - Game List and Preview", TextResponseProvider.getGamePreview());
                break;
            case CREATE_ACCOUNT:
                processCreateAccount(from, command, emailSender);
                break;
            case CHECK_HANDLE:
                // TODO: Code
                // - get list of handles to check (S3 body)
                // - return an email with the ones that are available.
                emailSender.sendEmail(from, "PBEMGS - CHECK_HANDLE not implemented", TextResponseProvider.unimplementedCommandBody("check_handle"));
                break;
            case RULES:
                processGameRulesRequest(from, command, emailSender);
                break;
            case NOTIFICATION_RETURN:
                // do nothing - this is the message received as the TO target for a global notification
                break;
        }
    }  // end handleCommandUnregistered()

    private void processTestDisplay(String from, SESEmailSender emailSender) {
        logger.log("Processing TEST_DISPLAY command for email: " + from);
        try {
            emailSender.sendEmail(from, "PBEMGS - TEST_DISPLAY",
                    TextResponseProvider.getTestDisplayHtmlTextBody());
        } catch (Exception e) {
            logger.log("-- Exception in processTestDisplay: " + getStackTrace(e));
            emailSender.sendEmail(from, "PBEMGS - TEST_DISPLAY Exception",
                    TextResponseProvider.getExceptionTextBody("test_display", e.getMessage()));
        }
    }

    private void processCreateAccount(String from, MainEmailProcessor.SubjectLineCommand command, SESEmailSender emailSender) {
        logger.log("Processing CREATE_ACCOUNT command for email: " + from + " and handle: " + command.message());
        try {
            // - create new account with handle
            // The requested handle for the new user is in the "message" part of the SubjectLineCommand.
            UsersRecord matchingHandle = usersDKO.fetchUserForHandle(command.message());
            if (matchingHandle != null) {
                logger.log("-- failed due to non-unique handle...");
                emailSender.sendEmail(from, "PBEMGS - CREATE_ACCOUNT failed", TextResponseProvider.getExistingHandleError(command.message()));
                return;
            }

            UsersRecord newUser = new UsersRecord();
            newUser.setEmailAddr(from);
            newUser.setHandle(command.message());
            newUser.setStatus(UsersStatus.ACTIVE);
            newUser.setUserType(UsersUserType.BASIC);
            newUser.setMvpTier(0);
            newUser.setCreatedAt(LocalDateTime.now());

            Long newUserId = usersDKO.createUser(newUser);
            emailSender.sendEmail(from, "PBEMGS - Account Created!",
                    TextResponseProvider.accountCreatedBody(command.message()));
            logger.log("-- Created Successfully!");

            // Create the tutorial game
            Tac tutorial = new Tac(dslContext, logger);
            UsersRecord user = usersDKO.fetchUserById(newUserId);
            tutorial.createTutorialGame(user, emailSender);
            logger.log("-- Created the tutorial game successfully!");
        } catch (Exception e) {
            logger.log("-- Exception in processCreateAccount: " + getStackTrace(e));
            emailSender.sendEmail(from, "PBEMGS - CREATE_ACCOUNT Exception",
                    TextResponseProvider.getExceptionTextBody(command.command().name(), e.getMessage()));
        }
    }

    private void processGameRulesRequest(String from, MainEmailProcessor.SubjectLineCommand command, SESEmailSender emailSender) {
        if (command.game() == null || command.game() == GameType.NONE) {
            logger.log("Processing RULES command for " + from + " failed due to no game type specified.");
            emailSender.sendEmail(from, "PBEMGS - RULES request failed",
                    TextResponseProvider.getRulesRequestErrorTextBody());
            return;
        }
        try {
            GameInterface game = GameFactory.createGame(command.game(), dslContext, logger);
            emailSender.sendEmail(from, "PBEMGS - Rules for " + command.game().name(),
                    game.getRulesTextBody());
        } catch (Exception e) {
            logger.log("-- Exception in processGameRulesRequest: " + getStackTrace(e));
            emailSender.sendEmail(from, "PBEMGS - RULES Exception",
                    TextResponseProvider.getExceptionTextBody(command.command().name(), e.getMessage()));
        }
    }

    private void processFeedbackRequest(UsersRecord user, S3Email email, SESEmailSender emailSender) {
        logger.log("processFeedbackRequest() for user id " + user.getUserId() + " - email: " + user.getEmailAddr());
        try {
            String feedbackText = email.getEmailBodyText(logger);
            UsersRecord ownerUser = usersDKO.fetchOwnerUser();
            emailSender.sendEmail(ownerUser.getEmailAddr(), "PBEMGS FEEDBACK MESSAGE",
                    TextResponseProvider.getFeedbackTextBody(user, feedbackText));
            emailSender.sendEmail(user.getEmailAddr(), "PBEMGS - FEEDBACK received!",
                    TextResponseProvider.getFeedbackThanksText());
        } catch (Exception e) {
            logger.log("-- Exception in processGameRulesRequest: " + getStackTrace(e));
            emailSender.sendEmail(user.getEmailAddr(), "PBEMGS - FEEDBACK Exception",
                    TextResponseProvider.getExceptionTextBody("FEEDBACK", e.getMessage()));
        }
    }

    private void processGameCreateRequest(UsersRecord user, MainEmailProcessor.SubjectLineCommand command, S3Email email, SESEmailSender emailSender) {
        if (command.game() == null || command.game() == GameType.NONE) {
            logger.log("Processing CREATE_GAME command for " + user.getEmailAddr() + " failed due to no game type specified.");
            emailSender.sendEmail(user.getEmailAddr(), "PBEMGS - CREATE_GAME request failed",
                    TextResponseProvider.getCreateGameMissingGameErrorTextBody());
            return;
        }
        try {
            GameInterface game = GameFactory.createGame(command.game(), dslContext, logger);
            game.processCreateGame(user, email, emailSender);
        } catch (Exception e) {
            logger.log("-- Exception in processGameCreateRequest: " + getStackTrace(e));
            emailSender.sendEmail(user.getEmailAddr(), "PBEMGS - CREATE_GAME Exception",
                    TextResponseProvider.getExceptionTextBody(command.command().name(), e.getMessage()));
        }
    }

    private void processOpenGamesRequest(UsersRecord user, MainEmailProcessor.SubjectLineCommand command, SESEmailSender emailSender) {
        List<GameInterface> games = new ArrayList<>();
        String subjectTail = "";
        try {
            if (command.game() == null || command.game() == GameType.NONE) {
                games = GameFactory.createAllGames(dslContext, logger);
                subjectTail = "all games";
            } else {
                games.add(GameFactory.createGame(command.game(), dslContext, logger));
                subjectTail = command.game().name();
            }

            StringBuilder sb = new StringBuilder();
            for (GameInterface game : games) {
                sb.append(game.getOpenGamesTextBody()).append("\n");
            }
            emailSender.sendEmail(user.getEmailAddr(), "PBEMGS - OPEN_GAMES for " + subjectTail,
                    sb.toString());
        } catch (Exception e) {
            logger.log("-- Exception in processOpenGamesRequest: " + getStackTrace(e));
            emailSender.sendEmail(user.getEmailAddr(), "PBEMGS - OPEN_GAMES Exception",
                    TextResponseProvider.getExceptionTextBody(command.command().name(), e.getMessage()));
        }
    }

    private void processJoinGameRequest(UsersRecord user, MainEmailProcessor.SubjectLineCommand command, SESEmailSender emailSender) {
        if (command.game() == null || command.game() == GameType.NONE || command.gameId() == null) {
            logger.log("Processing JOIN_GAME command for " + user.getEmailAddr() + " failed due to no game type or game id.  Command: " + command.toString());
            emailSender.sendEmail(user.getEmailAddr(), "PBEMGS - JOIN_GAME request failed",
                    TextResponseProvider.getJoinGameBadSubjectFormatTextBody());
            return;
        }
        try {
            GameInterface game = GameFactory.createGame(command.game(), dslContext, logger);
            game.processJoinGame(user, command.gameId(), emailSender);
        } catch (Exception e) {
            logger.log("-- Exception in processJoinGameRequest: " + getStackTrace(e));
            emailSender.sendEmail(user.getEmailAddr(), "PBEMGS - JOIN_GAME Exception",
                    TextResponseProvider.getExceptionTextBody(command.command().name(), e.getMessage()));
        }
    }

    private void processMoveRequest(UsersRecord user, MainEmailProcessor.SubjectLineCommand command, S3Email email, SESEmailSender emailSender) {
        if (command.game() == null || command.game() == GameType.NONE || command.gameId() == null) {
            logger.log("Processing MOVE command for " + user.getEmailAddr() + " failed due to no game type or game id.  Command: " + command.toString());
            emailSender.sendEmail(user.getEmailAddr(), "PBEMGS - MOVE failed",
                    TextResponseProvider.getMoveBadSubjectFormatTextBody());
            return;
        }
        try {
            GameInterface game = GameFactory.createGame(command.game(), dslContext, logger);
            game.processMove(user, command.gameId(), email, emailSender);
        } catch (Exception e) {
            logger.log("-- Exception in processMoveRequest: " + getStackTrace(e));
            emailSender.sendEmail(user.getEmailAddr(), "PBEMGS - MOVE Exception",
                    TextResponseProvider.getExceptionTextBody(command.command().name(), e.getMessage()));
        }
    }

    private void processMyGamesRequest(UsersRecord user, MainEmailProcessor.SubjectLineCommand command, SESEmailSender emailSender) {
        logger.log("processMyGamesRequest() for user id " + user.getUserId() + " - email: " + user.getEmailAddr());
        try {
            List<GameInterface> games = GameFactory.createAllGames(dslContext, logger);
            StringBuilder sb = new StringBuilder();
            sb.append("Game list for ").append(user.getHandle()).append("\n\n");
            for (GameInterface game : games) {
                sb.append(game.getMyGamesTextBody(user.getUserId())).append("\n\n");
            }
            emailSender.sendEmail(user.getEmailAddr(), "PBEMGS - MY_GAMES", sb.toString());
        } catch (Exception e) {
            logger.log("-- Exception in processMyGamesRequest: " + getStackTrace(e));
            emailSender.sendEmail(user.getEmailAddr(), "PBEMGS - MY_GAMES Exception",
                    TextResponseProvider.getExceptionTextBody(command.command().name(), e.getMessage()));
        }
    }

    private void processGameStatusRequest(UsersRecord user, MainEmailProcessor.SubjectLineCommand command, SESEmailSender emailSender) {
        if (command.game() == null || command.game() == GameType.NONE || command.gameId() == null) {
            logger.log("Processing GAME_STATUS command for " + user.getEmailAddr() + " failed due to no game type or game id.  Command: " + command.toString());
            emailSender.sendEmail(user.getEmailAddr(), "PBEMGS - GAME_STATUS failed",
                    TextResponseProvider.getStatusBadSubjectFormatTextBody());
            return;
        }
        try {
            GameInterface game = GameFactory.createGame(command.game(), dslContext, logger);
            game.processStatus(user, command.gameId(), emailSender);
        } catch (Exception e) {
            logger.log("-- Exception in processGameStatusRequest: " + getStackTrace(e));
            emailSender.sendEmail(user.getEmailAddr(), "PBEMGS - GAME_STATUS Exception",
                    TextResponseProvider.getExceptionTextBody(command.command().name(), e.getMessage()));
        }
    }

    // Owner commands
    private void processNotification(UsersRecord user, S3Email email, SESEmailSender emailSender) {
        logger.log("processNotification() for user id " + user.getUserId() + " - email: " + user.getEmailAddr());

        if (user.getUserType() != UsersUserType.OWNER) {
            // safety check, throw an uncaught exception intentionally here
            throw new IllegalArgumentException("Global Notification method called for non-owner userId " + user.getUserId());
        }
        try {
            List<UsersRecord> allUsers = usersDKO.fetchAllActiveUsers();
            List<String> toAddrs = allUsers.stream().map(UsersRecord::getEmailAddr).toList();

            emailSender.sendNotificationEmail(toAddrs, email.getEmailBodyText(logger));
        } catch (Exception e) {
            logger.log("-- Exception in processNotification: " + getStackTrace(e));
            emailSender.sendEmail(user.getEmailAddr(), "PBEMGS - notification failed!",
                    "The last notification command failed, check the logs...");
        }
    }

    private String getStackTrace(Exception e) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        return e.getMessage() + "\n\n" + sw.toString();
    }

}
