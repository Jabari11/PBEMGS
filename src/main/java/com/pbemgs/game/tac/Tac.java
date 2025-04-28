package com.pbemgs.game.tac;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.pbemgs.controller.SESEmailSender;
import com.pbemgs.dko.TacGamesDKO;
import com.pbemgs.game.GameInterface;
import com.pbemgs.generated.enums.TacGamesGameState;
import com.pbemgs.generated.tables.records.TacGamesRecord;
import com.pbemgs.generated.tables.records.UsersRecord;
import com.pbemgs.model.S3Email;
import org.jooq.DSLContext;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class Tac implements GameInterface {
    private record TextBodyParseResult(Integer move, boolean success, String error) {
    }

    private final Duration REMINDER_DURATION = Duration.ofHours(24);

    private final TacGamesDKO tacGameDKO;
    private final LambdaLogger logger;
    private final Random rng = new Random();

    public Tac(DSLContext dslContext, LambdaLogger logger) {
        this.tacGameDKO = new TacGamesDKO(dslContext);
        this.logger = logger;
    }

    // Create/Join are disabled for the tutorial.
    @Override
    public void processCreateGame(UsersRecord user, S3Email email, SESEmailSender emailSender) {
        emailSender.sendEmail(user.getEmailAddr(), "PBEMGS - create_game tac unavailable!",
                TacTextResponseProvider.getCreateErrorText());
    }

    @Override
    public void processJoinGame(UsersRecord user, long gameId, SESEmailSender emailSender) {
        emailSender.sendEmail(user.getEmailAddr(), "PBEMGS - join_game tac unavailable!",
                TacTextResponseProvider.getJoinErrorText());
    }

    /**
     * Process a TAC move email.  This will also generate the system response move and send the
     * updated state to the user (with a nice description of what to do next).
     */
    @Override
    public void processMove(UsersRecord user, long gameId, S3Email email, SESEmailSender emailSender) {
        TacGamesRecord game = tacGameDKO.getGameById(gameId);
        if (game == null || game.getGameState() != TacGamesGameState.IN_PROGRESS) {
            emailSender.sendEmail(user.getEmailAddr(), "PBEMGS - Tac move Failed", TacTextResponseProvider.getGameNotValidText());
            return;
        }

        if (game.getUserId() != user.getUserId()) {
            emailSender.sendEmail(user.getEmailAddr(), "PBEMGS - Tac move Failed",
                    TacTextResponseProvider.getMoveNotYourGameText());
            return;
        }

        TacBoard board = new TacBoard();
        board.deserialize(game.getBoardState());
        TextBodyParseResult playerMove = parseMoveFromEmail(email);
        if (!playerMove.success()) {
            emailSender.sendEmail(user.getEmailAddr(), "PBEMGS - Tac move failed - format issue",
                    TacTextResponseProvider.getMoveFailedParseText(playerMove.error()));
            return;
        }

        if (!board.isValidMove(playerMove.move())) {
            emailSender.sendEmail(user.getEmailAddr(), "PBEMGS - Invalid Move", TacTextResponseProvider.getMoveInvalidText());
            return;
        }

        StringBuilder header = new StringBuilder();
        header.append(user.getHandle()).append(" has selected square ").append(playerMove.move()).append("\n");
        StringBuilder footer = new StringBuilder();
        StringBuilder subject = new StringBuilder();

        board.makeMove(playerMove.move(), 'X'); // User plays as 'X'
        if (board.isWin('X')) {
            game.setGameState(TacGamesGameState.COMPLETE);
            header.append("\n").append(user.getHandle()).append(" has won!");
            footer.append(TacTextResponseProvider.getGameWonText());
            subject.append("PBEMGS - Tac Game complete - victory!");
        } else if (!board.hasEmptyCells()) {
            game.setGameState(TacGamesGameState.COMPLETE);
            header.append("\n").append(user.getHandle()).append(" - board is full, drawn!");
            footer.append(TacTextResponseProvider.getGameDrawText());
            subject.append("PBEMGS - Tac Game complete - drawn!");
        } else {
            int systemMove = board.getRandomAvailableMove(rng);
            board.makeMove(systemMove, 'O');
            header.append("PBEMGS selects square ").append(systemMove);
            if (board.isWin('O')) {
                game.setGameState(TacGamesGameState.COMPLETE);
                header.append("\nPBEMGS has won!");
                footer.append(TacTextResponseProvider.getGameLostText());
                subject.append("PBEMGS - Tac Game complete - lost!");
            } else {
                header.append("\nIt is your turn!");
                footer.append(TacTextResponseProvider.getMoveResponseFooterText());
                subject.append("MOVE TAC ").append(gameId);
            }
        }
        game.setBoardState(board.serialize());
        game.setLastMoveTimestamp(LocalDateTime.now());
        game.setLastReminderTimestamp(null);
        tacGameDKO.updateGame(game);

        sendBoardStateEmail(emailSender, subject.toString(), header.toString(), footer.toString(),
                gameId, user, board);
    }

    @Override
    public String getOpenGamesTextBody() {
        return "";  // don't write anything for the tutorial for this command.
    }

    @Override
    public String getRulesTextBody() {
        return TacTextResponseProvider.getTacRulesText();
    }

    @Override
    public String getMyGamesTextBody(long userId) {
        StringBuilder sb = new StringBuilder();
        TacGamesRecord game = tacGameDKO.getActiveGameForUser(userId);
        if (game != null) {
            sb.append("TAC (tutorial):\n");
            sb.append("-- Game ID: ").append(game.getGameId()).
                    append(" - it is your move.  Send a command of 'game_status tac ").
                    append(game.getGameId()).append("' to get the board state.");
        }
        return sb.toString();
    }

    @Override
    public void processStatus(UsersRecord user, long gameId, SESEmailSender emailSender) {
        TacGamesRecord reqGame = tacGameDKO.getGameById(gameId);
        if (reqGame == null || reqGame.getGameState() != TacGamesGameState.IN_PROGRESS) {
            emailSender.sendEmail(user.getEmailAddr(), "PBEMGS - status tac command failed",
                    TacTextResponseProvider.getStatusFailedNoGameText(gameId));
            return;
        }
        if (user.getUserId() != reqGame.getUserId()) {
            emailSender.sendEmail(user.getEmailAddr(), "PBEMGS - status tac command failed",
                    TacTextResponseProvider.getStatusFailedNotYourGameText(gameId));
            return;
        }

        TacBoard board = new TacBoard();
        board.deserialize(reqGame.getBoardState());
        String header = "Game Status for Tac Game ID " + gameId;
        sendBoardStateEmail(emailSender, "PBEMGS - tac status for Game ID " + gameId,
                header, TacTextResponseProvider.getGameStatusFooterText(gameId), gameId, user, board);
    }

    @Override
    public Map<Long, String> processStaleGameCheck(SESEmailSender emailSender) {
        Map<Long, String> staleStringByUserId = new HashMap<>();
        List<TacGamesRecord> activeGames = tacGameDKO.getActiveGames();
        if (activeGames == null || activeGames.isEmpty()) {
            return staleStringByUserId;
        }

        LocalDateTime currTime = LocalDateTime.now();
        for (TacGamesRecord game : activeGames) {
            LocalDateTime lastTime = game.getLastReminderTimestamp() == null ?
                    game.getLastMoveTimestamp() : game.getLastReminderTimestamp();
            if (Duration.between(lastTime, currTime).compareTo(REMINDER_DURATION) > 0) {
                staleStringByUserId.put(game.getUserId(), "TAC (tutorial): Game ID " + game.getGameId() +
                        " - send 'game_status tac " + game.getGameId() + "' to get the board state!");
            }
        }

        // Update reminder timestamps as necessary
        try {
            tacGameDKO.updateReminderTimestamps(staleStringByUserId.keySet(), currTime);
        } catch (Exception e) {
            logger.log("Caught an exception updating reminder timestamps - user ids: " + staleStringByUserId.keySet().toString());
            // don't rethrow or exit - still want to process the email out at the system level
        }

        return staleStringByUserId;
    }

    /**
     * Create the tutorial game objects for the given new user and send the initial creation email.
     */
    public void createTutorialGame(UsersRecord user, SESEmailSender emailSender) {
        TacGamesRecord game = new TacGamesRecord();
        game.setUserId(user.getUserId());
        game.setGameState(TacGamesGameState.IN_PROGRESS);
        TacBoard board = new TacBoard();
        long gameId = tacGameDKO.createNewGame(user.getUserId(), board.serialize());

        String header = "Game of Tac has started - it is your move!";
        sendBoardStateEmail(emailSender, "MOVE TAC " + gameId + " - TUTORIAL STARTED!",
                header, TacTextResponseProvider.getCreateGameFooterText(gameId),
                gameId, user, board);
    }

    // email body retrieval and parsing
    private TextBodyParseResult parseMoveFromEmail(S3Email email) {
        logger.log("Parsing TAC move email body...");
        try {
            // Retrieve email body from S3
            String emailBody = email.getEmailBodyText(logger);

            // Parse the first token as an integer
            String[] tokens = emailBody.split("\\s+"); // Split by whitespace
            if (tokens.length > 0) {
                try {
                    int move = Integer.parseInt(tokens[0]);
                    logger.log("- Parsed move: " + move);
                    return new TextBodyParseResult(move, true, null);
                } catch (NumberFormatException e) {
                    logger.log("- Failed to parse move: " + tokens[0]);
                    return new TextBodyParseResult(null, false, "Invalid input: Please provide a number between 1 and 9\nas the first word in the email body.");
                }
            } else {
                logger.log("- Email body is empty or doesn't contain a move.");
                return new TextBodyParseResult(null, false, "Email body is empty - this should contain the number of\nthe square to claim.");
            }
        } catch (Exception e) {
            logger.log("- Exception parsing email body: " + e.getMessage());
            return new TextBodyParseResult(null, false, "Exception occurred parsing email body - please resubmit!");
        }
    }

    private void sendBoardStateEmail(SESEmailSender emailSender, String subject, String header, String footer,
                                     long gameId, UsersRecord user, TacBoard gameBoard) {
        // Tutorial email is the game ID, board representation, and then a given "footer" with
        // a bunch of description of what to do next.
        StringBuilder sb = new StringBuilder();
        sb.append("Tac (tutorial) Game ID: ").append(gameId).append("\n\n");
        sb.append(header).append("\n\n");

        sb.append(gameBoard.getBoardTextBody()).append("\n\n");
        sb.append(footer);
        emailSender.sendEmail(user.getEmailAddr(), subject, sb.toString());
    }

}
