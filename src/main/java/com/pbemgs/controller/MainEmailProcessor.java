package com.pbemgs.controller;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.pbemgs.dko.UsersDKO;
import com.pbemgs.generated.enums.UsersUserType;
import com.pbemgs.generated.tables.records.UsersRecord;
import com.pbemgs.model.Command;
import com.pbemgs.model.GameType;
import com.pbemgs.model.S3Email;
import org.jooq.DSLContext;

import java.util.List;
import java.util.Map;

/**
 * Initial email processor for handling an email sent to the PBEMGS.
 *
 * This handles parsing the subject line, the user lookup, and pushing the command to the appropriate handler.
 */
public class MainEmailProcessor {

    private final DSLContext dslContext;
    private final UsersDKO usersDKO;

    public record SubjectLineCommand(boolean parseSuccess, String message, Command command, GameType game, Long gameId) {}

    public MainEmailProcessor(DSLContext dslContext) {
        this.dslContext = dslContext;
        usersDKO = new UsersDKO(dslContext);
    }

    public void process(S3Email email, LambdaLogger logger) {
        UsersRecord usersRecord = usersDKO.fetchUserForEmail(email.getFrom());
        SubjectLineCommand command = parseSubject(email.getSubject());
        logger.log("Subject Line parse results: " + command.toString());
        if (command.parseSuccess()) {
            boolean validCommand = validateCommand(usersRecord, command);
            if (validCommand) {
                CommandHandler handler = new CommandHandler(dslContext, logger);
                if (usersRecord == null) {
                    handler.handleCommandUnregistered(email.getFrom(), command, email);
                } else {
                    handler.handleCommand(usersRecord, command, email);
                }
            } else {  // end if (valid command)
                logger.log("Sending Invalid Command email to " + email.getFrom());
                sendInvalidCommandEmail(email.getFrom(), command.command(), logger);
            }
        } else {  // end if (subject line parsed successfully)
            logger.log("Sending Failed Subject Line Parse to " + email.getFrom());
            sendFailedSubjectParseEmail(email.getFrom(), email.getSubject(), command, logger);
        }

    }

    private SubjectLineCommand parseSubject(String subject) {
        if (subject == null || subject.isBlank()) {
            return new SubjectLineCommand(false, "Subject line is empty.", null, null, null);
        }

        // Strip "re:" from the subject line to allow direct replies to move emails
        subject = subject.stripLeading().replaceFirst("(?i)^re:\\s*", "");

        // Split the subject into parts
        String[] parts = subject.split("\\s+");
        if (parts.length == 0) {
            return new SubjectLineCommand(false, "Subject line is invalid.", null, null, null);
        }

        String commandPart = parts[0].toLowerCase();
        Command command = switch (commandPart) {
            case "intro", "info" -> Command.INTRO;
            case "help", "help_base" -> Command.HELP_BASE;
            case "game_preview", "game_list" -> Command.GAME_LIST;
            case "check_handle", "check_handles" -> Command.CHECK_HANDLE;
            case "create_account" -> Command.CREATE_ACCOUNT;
            case "rules" -> Command.RULES;
            case "create_game" -> Command.CREATE_GAME;
            case "open_games" -> Command.OPEN_GAMES;
            case "join_game" -> Command.JOIN_GAME;
            case "my_games" -> Command.MY_GAMES;
            case "game_status", "status" -> Command.GAME_STATUS;
            case "move" -> Command.MOVE;
            case "feedback" -> Command.FEEDBACK;
            case "test_display" -> Command.TEST_DISPLAY;
            case "activate" -> Command.ACTIVATE;
            case "deactivate" -> Command.DEACTIVATE;
            case "global_notification", "notification" -> Command.GLOBAL_NOTIFICATION;
            case "test_symbol" -> Command.TEST_SYMBOL;
            case "list_new_users" -> Command.LIST_NEW_USERS;
            case "pbemgs-notification" -> Command.NOTIFICATION_RETURN;
            default -> null;
        };
        if (command == null) {
            return new SubjectLineCommand(false, "Unknown command: " + commandPart, null, null, null);
        }

        // Special case for a CREATE_ACCOUNT command - the second token will be the requested handle rather than
        // the game name.  In this case, it's a successful parse and the requested handle will be put into the "message"
        // field of the return.
        if (command == Command.CREATE_ACCOUNT) {
            if (parts.length < 2) {
                return new SubjectLineCommand(false, "CREATE_ACCOUNT command requires a player handle on the subject line!", command, null, null);
            }
            return new SubjectLineCommand(true, parts[1], command, null, null);
        }

        // Resolve game type
        GameType gameType = GameType.NONE;
        if (parts.length > 1) {
            String gamePart = parts[1].toLowerCase();
            gameType = switch (gamePart) {
                case "tac" -> GameType.TAC;
                case "9tac", "ninetac" -> GameType.NINETAC;
                case "ataxx" -> GameType.ATAXX;
                case "surge" -> GameType.SURGE;
                case "loa" -> GameType.LOA;
                case "gomoku" -> GameType.GOMOKU;
                default -> null;
            };
            if (gameType == null) {
                return new SubjectLineCommand(false, "Unknown game type: " + gamePart, command, null, null);
            }
        }

        // Resolve game ID
        Long gameId = null;
        if (parts.length > 2) {
            try {
                gameId = Long.parseLong(parts[2]);
            } catch (NumberFormatException e) {
                return new SubjectLineCommand(false, "Invalid game ID format: " + parts[2], command, gameType, null);
            }
        }

        return new SubjectLineCommand(true, "Command parsed successfully.", command, gameType, gameId);
    }

    // Command validity map
    private static final List<Command> unregisterCommmands = List.of(
            Command.INTRO, Command.HELP_BASE, Command.TEST_DISPLAY,
            Command.CHECK_HANDLE, Command.CREATE_ACCOUNT, Command.GAME_LIST, Command.RULES,
            Command.NOTIFICATION_RETURN);
    private static final Map<UsersUserType, List<Command>> commandValidity =
    Map.of(
            UsersUserType.BASIC, List.of(Command.INTRO, Command.HELP_BASE, Command.GAME_LIST, Command.RULES, Command.FEEDBACK,
                    Command.CREATE_GAME, Command.JOIN_GAME, Command.OPEN_GAMES, Command.MY_GAMES,
                    Command.GAME_STATUS, Command.MOVE, Command.TEST_DISPLAY, Command.TEST_SYMBOL, Command.ACTIVATE, Command.DEACTIVATE),
            UsersUserType.SUPERUSER, List.of(),  // TBD, not using this tier at the moment
            UsersUserType.OWNER, List.of(Command.INTRO, Command.HELP_BASE, Command.TEST_DISPLAY, Command.GAME_LIST, Command.RULES,
                    Command.CREATE_GAME, Command.JOIN_GAME, Command.OPEN_GAMES, Command.MY_GAMES,
                    Command.GAME_STATUS, Command.MOVE, Command.GLOBAL_NOTIFICATION, Command.LIST_NEW_USERS, Command.TEST_SYMBOL)
            );

    private boolean validateCommand(UsersRecord user, SubjectLineCommand command) {
        // Check that the command is valid for the user.  Unregistered user will have a null user record.
        if (user == null) {
            return unregisterCommmands.contains(command.command());
        }

        return commandValidity.get(user.getUserType()).contains(command.command());
    }

    private void sendInvalidCommandEmail(String sender, Command command, LambdaLogger logger) {
        SESEmailSender emailSender = new SESEmailSender(logger);
        emailSender.sendEmail(sender, "PBEMGS - ERROR - Invalid Command received", TextResponseProvider.invalidCommandBody(command.toString()));
    }

    private void sendFailedSubjectParseEmail(String sender, String subjectLine, SubjectLineCommand command, LambdaLogger logger) {
        SESEmailSender emailSender = new SESEmailSender(logger);
        emailSender.sendEmail(sender, "PBEMGS - ERROR - Unable to parse subject line", TextResponseProvider.subjectParseError(subjectLine, command.message()));
    }

}
