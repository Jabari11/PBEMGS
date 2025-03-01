package com.pbemgs.game.ninetac;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.pbemgs.controller.SESEmailSender;
import com.pbemgs.dko.NinetacGameDKO;
import com.pbemgs.dko.UsersDKO;
import com.pbemgs.game.GameInterface;
import com.pbemgs.game.GameMessageMailer;
import com.pbemgs.generated.enums.NinetacGamesBoardOption;
import com.pbemgs.generated.enums.NinetacGamesGameState;
import com.pbemgs.generated.tables.records.NinetacGamesRecord;
import com.pbemgs.generated.tables.records.UsersRecord;
import com.pbemgs.model.S3Email;
import org.jooq.DSLContext;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

public class Ninetac implements GameInterface {

    private static final String GAME_NAME = "Ninetac";
    private static final int PLAYER_GAME_LIMIT = 3;
    private static final int MAX_OPEN_GAMES = 10;
    private final Duration REMINDER_DURATION = Duration.ofHours(24);
    private final Duration TIMEOUT_DURATION = Duration.ofHours(72);


    private final NinetacGameDKO ninetacDKO;
    private final UsersDKO usersDKO;
    private final LambdaLogger logger;

    private record TextBodyParseResult(Integer move, boolean success, String error) {}

    public Ninetac(DSLContext dslContext, LambdaLogger logger) {
        ninetacDKO = new NinetacGameDKO(dslContext);
        usersDKO = new UsersDKO(dslContext);
        this.logger = logger;
    }

    @Override
    public void processCreateGame(UsersRecord user, S3Email email, SESEmailSender emailSender) {
        List<NinetacGamesRecord> userGames = ninetacDKO.getActiveGamesForUser(user.getUserId());
        if (userGames.size() >= PLAYER_GAME_LIMIT) {
            GameMessageMailer.gameLimitReached(emailSender, user.getEmailAddr(), "create_game", GAME_NAME);
            return;
        }

        List<NinetacGamesRecord> openGames = ninetacDKO.getOpenGames();
        if (openGames.size() >= MAX_OPEN_GAMES) {
            GameMessageMailer.openGamesLimitReached(emailSender, user.getEmailAddr(), GAME_NAME);
            return;
        }

        NinetacBoard newBoard = new NinetacBoard(logger);
        newBoard.createRandomizedBoard27();
        Long newGameNum = ninetacDKO.createNewGame(user.getUserId(), newBoard.serialize(), NinetacGamesBoardOption.DEFAULT_27);
        GameMessageMailer.createSuccess(emailSender, user.getEmailAddr(), GAME_NAME, newGameNum);
    }

    @Override
    public void processJoinGame(UsersRecord user, long gameId, SESEmailSender emailSender) {
        List<NinetacGamesRecord> userGames = ninetacDKO.getActiveGamesForUser(user.getUserId());
        if (userGames.size() >= PLAYER_GAME_LIMIT) {
            GameMessageMailer.gameLimitReached(emailSender, user.getEmailAddr(), "join_game", GAME_NAME);
            return;
        }
        NinetacGamesRecord requestGame = ninetacDKO.getGameById(gameId);

        // Validity checks: game must exist, be in OPEN state, and not have a X-player of self.
        if (requestGame == null || requestGame.getGameState() != NinetacGamesGameState.OPEN) {
            GameMessageMailer.joinNonopenGame(emailSender, user.getEmailAddr(), GAME_NAME, gameId);
            return;
        }
        if (Objects.equals(requestGame.getXPlayerId(), user.getUserId())) {
            GameMessageMailer.joinSelf(emailSender, user.getEmailAddr(), GAME_NAME);
            return;
        }

        UsersRecord xPlayer = usersDKO.fetchUserById(requestGame.getXPlayerId());

        // get player id to move first
        Random rng = new Random();
        Long firstPlayerId = rng.nextBoolean() ? xPlayer.getUserId() : user.getUserId();

        ninetacDKO.completeGameCreation(gameId, user.getUserId(), firstPlayerId);
        NinetacBoard gameBoard = new NinetacBoard(logger);
        gameBoard.deserialize(requestGame.getBoardState());

        sendBoardStateEmail(emailSender, "MOVE NINETAC " + gameId + " - GAME START!", "",
                gameId, xPlayer, user, gameBoard, firstPlayerId, null);
    }

    @Override
    public void processMove(UsersRecord user, long gameId, S3Email emailBody, SESEmailSender emailSender) {
        NinetacGamesRecord requestGame = ninetacDKO.getGameById(gameId);

        // Validity checks: game must exist, be in IN_PROGRESS state, and the user must be active player.
        if (requestGame == null || requestGame.getGameState() != NinetacGamesGameState.IN_PROGRESS) {
            GameMessageMailer.moveGameNotValid(emailSender, user.getEmailAddr(), GAME_NAME, gameId);
            return;
        }
        if (!Objects.equals(requestGame.getPlayerIdToMove(), user.getUserId())) {

            emailSender.sendEmail(user.getEmailAddr(), "PBEMGS - MOVE NINETAC failed",
                    NinetacTextResponseProvider.getMoveNotActiveText(gameId));
            return;
        }

        // Parse email body for the move (a single number), validate between 1 and 27.
        TextBodyParseResult parseResult = parseMoveFromEmail(emailBody);
        if (!parseResult.success()) {
            emailSender.sendEmail(user.getEmailAddr(), "PBEMGS - MOVE NINETAC failed",
                    parseResult.error());
        }

        int move = parseResult.move();
        if (move < 1 || move > 27) {
            emailSender.sendEmail(user.getEmailAddr(), "PBEMGS - MOVE NINETAC failed",
                    NinetacTextResponseProvider.getMoveInvalidNumberText(move, gameId));
            return;
        }
        // Load game, verify move is valid (number is available)
        NinetacBoard game = new NinetacBoard(logger);
        game.deserialize(requestGame.getBoardState());

        if (!game.isMoveValid(move)) {
            emailSender.sendEmail(user.getEmailAddr(), "PBEMGS - MOVE NINETAC failed",
                    NinetacTextResponseProvider.getMoveUnavailableText(gameId, move));
            return;
        }


        executeMove(user, gameId, emailSender, requestGame, game, move);
    }

    // Helper to fully execute a validated move
    private void executeMove(UsersRecord user, long gameId, SESEmailSender emailSender, NinetacGamesRecord requestGame, NinetacBoard game, int move) {
        // Make move.
        int playerSymbol = user.getUserId().equals(requestGame.getXPlayerId()) ? NinetacBoard.PLAYER_X : NinetacBoard.PLAYER_O;
        game.makeMove(playerSymbol, move);
        requestGame.setBoardState(game.serialize());

        // Swap active player
        Long oppUserId = (playerSymbol == NinetacBoard.PLAYER_X) ? requestGame.getOPlayerId() : requestGame.getXPlayerId();
        UsersRecord oppUser = usersDKO.fetchUserById(oppUserId);
        requestGame.setPlayerIdToMove(oppUserId);
        requestGame.setLastMoveTimestamp(LocalDateTime.now());

        // Check end state - immediate victory (boards >= 5), no more numbers (condition (boards >= 5) - if so, end the game.
        if (game.getClaimedCount(playerSymbol) >= 5) {
            processGameOver(playerSymbol, requestGame, game, emailSender);
            return;
        }

        // Check no-more-numbers condition
        if (game.isBoardFull()) {
            int xCount = game.getClaimedCount(NinetacBoard.PLAYER_X);
            int oCount = game.getClaimedCount(NinetacBoard.PLAYER_O);
            if (xCount > oCount) {
                processGameOver(NinetacBoard.PLAYER_X, requestGame, game, emailSender);
            } else if (oCount > xCount) {
                processGameOver(NinetacBoard.PLAYER_O, requestGame, game, emailSender);
            } else {
                // drawn
                processGameOver(0, requestGame, game, emailSender);
            }
            return;
        }

        // update game state in DB
        requestGame.setLastReminderTimestamp(null);
        ninetacDKO.updateGame(requestGame);

        // Send emails out (2)
        // Generate the "header" portion for both players
        UsersRecord xPlayer = (playerSymbol == NinetacBoard.PLAYER_X) ? user : oppUser;
        UsersRecord oPlayer = (playerSymbol == NinetacBoard.PLAYER_O) ? user : oppUser;

        sendBoardStateEmail(emailSender, "MOVE NINETAC " + gameId,
                user.getHandle() + " has selected square " + move + "!\n\n", gameId, xPlayer, oPlayer,
                game, oppUserId, null);
    }

    @Override
    public String getOpenGamesTextBody() {
        List<NinetacGamesRecord> openGames = ninetacDKO.getOpenGames();
        if (openGames.isEmpty()) {
            return NinetacTextResponseProvider.getNoOpenGamesText();
        }
        Set<Long> creatorUserIds = openGames.stream().map(NinetacGamesRecord::getXPlayerId).collect(Collectors.toSet());
        Map<Long, UsersRecord> usersById = usersDKO.fetchUsersByIds(creatorUserIds);
        StringBuilder sb = new StringBuilder();
        sb.append(NinetacTextResponseProvider.getOpenGamesHeaderText(openGames.size()));
        for (NinetacGamesRecord game : openGames) {
            sb.append(NinetacTextResponseProvider.getOpenGameDescription(game.getGameId(), usersById.get(game.getXPlayerId())));
        }
        return sb.toString();
    }

    @Override
    public String getRulesTextBody() {
        return NinetacTextResponseProvider.getNinetacRulesText();
    }

    @Override
    public String getMyGamesTextBody(long userId) {
        StringBuilder sb = new StringBuilder();
        sb.append("NINETAC:\n");
        List<NinetacGamesRecord> games = ninetacDKO.getActiveGamesForUser(userId);
        for (NinetacGamesRecord game : games) {
            sb.append("-- Game ID: ").append(game.getGameId());
            if (game.getGameState() == NinetacGamesGameState.OPEN) {
                sb.append(" is waiting for an opponent.\n");
            } else {
                sb.append(" is in progress - ");
                sb.append(game.getPlayerIdToMove() == userId ? "YOUR TURN!\n" : "opponent's turn.\n");
            }
        }
        return sb.toString();
    }

    @Override
    public void processStatus(UsersRecord user, long gameId, SESEmailSender emailSender) {
        NinetacGamesRecord reqGame = ninetacDKO.getGameById(gameId);
        if (reqGame == null || reqGame.getGameState() == NinetacGamesGameState.OPEN ||
                reqGame.getGameState() == NinetacGamesGameState.ABANDONED) {
            emailSender.sendEmail(user.getEmailAddr(), "PBEMGS - STATUS command failed",
                    NinetacTextResponseProvider.getStatusFailedNoGameText(gameId));
            return;
        }
        if (user.getUserId() != reqGame.getXPlayerId() && user.getUserId() != reqGame.getOPlayerId()) {
            emailSender.sendEmail(user.getEmailAddr(), "PBEMGS - STATUS command failed",
                    NinetacTextResponseProvider.getStatusFailedNotYourGameText(gameId));
            return;
        }

        UsersRecord xPlayer = usersDKO.fetchUserById(reqGame.getXPlayerId());
        UsersRecord oPlayer = usersDKO.fetchUserById(reqGame.getOPlayerId());

        String textHeader = "Ninetac Game ID: " + gameId +
                (reqGame.getGameState() == NinetacGamesGameState.IN_PROGRESS ? " - In Progress\n\n" : " - Complete\n\n");
        NinetacBoard board = new NinetacBoard(logger);
        board.deserialize(reqGame.getBoardState());
        sendBoardStateEmail(emailSender, "PBEMGS - NINETAC STATUS for game id " + gameId, textHeader,
                gameId, xPlayer, oPlayer, board, reqGame.getPlayerIdToMove(), user.getUserId());
    }

    @Override
    public Map<Long, String> processStaleGameCheck(SESEmailSender emailSender) {
        Map<Long, String> staleStringByUserId = new HashMap<>();
        List<NinetacGamesRecord> activeGames = ninetacDKO.getActiveGames();
        if (activeGames == null || activeGames.isEmpty()) {
            return staleStringByUserId;
        }

        LocalDateTime currTime = LocalDateTime.now();
        for (NinetacGamesRecord game : activeGames) {
            LocalDateTime lastMoveTime = game.getLastMoveTimestamp();

            if (Duration.between(lastMoveTime, currTime).compareTo(TIMEOUT_DURATION) > 0) {
                try {
                    UsersRecord user = usersDKO.fetchUserById(game.getPlayerIdToMove());
                    NinetacBoard gameBoard = new NinetacBoard(logger);
                    gameBoard.deserialize(game.getBoardState());
                    int randomMove = gameBoard.getRandomMove();
                    logger.log("Auto-move for NINETAC Game ID " + game.getGameId() + ": selected " + randomMove);
                    executeMove(user, game.getGameId(), emailSender, game, gameBoard, randomMove);
                } catch (Exception e) {
                    logger.log("Exception while attempting to auto-move: " + e.getMessage());
                    // continue processing the rest...
                }
            } else {
                LocalDateTime lastTime = game.getLastReminderTimestamp() == null ?
                        game.getLastMoveTimestamp() : game.getLastReminderTimestamp();
                if (Duration.between(lastTime, currTime).compareTo(REMINDER_DURATION) > 0) {
                    staleStringByUserId.put(game.getPlayerIdToMove(), "NINETAC: Game ID " + game.getGameId());
                }
            }
        }

        // Update reminder timestamps as necessary
        try {
            ninetacDKO.updateReminderTimestamps(staleStringByUserId.keySet(), currTime);
        } catch (Exception e) {
            logger.log("Caught an exception updating reminder timestamps - user ids: " + staleStringByUserId.keySet().toString());
            // don't rethrow or exit - still want to process the email out at the system level
        }

        return staleStringByUserId;
    }


    private void processGameOver(int winnerMarker, NinetacGamesRecord gameRecord, NinetacBoard gameBoard, SESEmailSender emailSender) {
        // Game over processing:
        // Find winning player user number (or null if drawn)
        // Set the game state to complete, winner_id to winner.
        // Send final email (identical to both users)
        UsersRecord xPlayer = usersDKO.fetchUserById(gameRecord.getXPlayerId());
        UsersRecord oPlayer = usersDKO.fetchUserById(gameRecord.getOPlayerId());
        UsersRecord winPlayer = null;
        if (winnerMarker == NinetacBoard.PLAYER_X) {
            winPlayer = xPlayer;
        }
        if (winnerMarker == NinetacBoard.PLAYER_O) {
            winPlayer = oPlayer;
        }

        gameRecord.setVictorPlayerId(winPlayer == null ? -1L : winPlayer.getUserId());
        gameRecord.setGameState(NinetacGamesGameState.COMPLETE);
        gameRecord.setLastMoveTimestamp(LocalDateTime.now());

        // Store to DB
        ninetacDKO.updateGame(gameRecord);

        // Emails out
        String subject = "PBEMGS - NINETAC game number " + gameRecord.getGameId() + " has ended - " +
                (winPlayer == null ? "DRAW!" : " winner is " + winPlayer.getHandle() + "!");

        sendBoardStateEmail(emailSender, subject, "Final Board:\n\n", gameRecord.getGameId(),
                xPlayer, oPlayer, gameBoard, gameRecord.getPlayerIdToMove(), null);
    }

    // email body retrieval and parsing
    private TextBodyParseResult parseMoveFromEmail(S3Email email) {
        try {
            // Retrieve email body from S3
            String emailBody = email.getEmailBodyText(logger);

            // Parse the first token as an integer
            String[] tokens = emailBody.split("\\s+"); // Split by whitespace
            if (tokens.length > 0) {
                try {
                    int move = Integer.parseInt(tokens[0]);
                    logger.log("Parsed move: " + move);
                    return new TextBodyParseResult(move, true, null);
                } catch (NumberFormatException e) {
                    logger.log("Failed to parse move: " + tokens[0]);
                    return new TextBodyParseResult(null, false, "Invalid input: Please provide a number between 1 and 27\nas the first word in the email body.");
                }
            } else {
                logger.log("Email body is empty or doesn't contain a move.");
                return new TextBodyParseResult(null, false, "Email body is empty - this should contain the number of\nthe squares to claim.");
            }
        } catch (Exception e) {
            logger.log("Exception parsing email body: " + e.getMessage());
            return new TextBodyParseResult(null, false, "Exception occurred parsing email body - please resubmit!");
        }
    }

    // Helper methods for formatting and sending outgoing board-state emails.
    private String getXHeaderText(long gameId, UsersRecord x, UsersRecord o, long playerToMove, NinetacBoard board) {
        return NinetacTextResponseProvider.getGameHeader(gameId, "X", x.getHandle(), board.getClaimedCount(NinetacBoard.PLAYER_X),
                playerToMove == x.getUserId(), "O", o.getHandle(), board.getClaimedCount(NinetacBoard.PLAYER_O));
    }

    private String getOHeaderText(long gameId, UsersRecord x, UsersRecord o, long playerToMove, NinetacBoard board) {
        return NinetacTextResponseProvider.getGameHeader(gameId, "O", o.getHandle(), board.getClaimedCount(NinetacBoard.PLAYER_O),
                playerToMove == o.getUserId(), "X", x.getHandle(), board.getClaimedCount(NinetacBoard.PLAYER_X));
    }

    private void sendBoardStateEmail(SESEmailSender emailSender, String subject, String header,
                                    long gameId, UsersRecord xPlayer, UsersRecord oPlayer,
                                    NinetacBoard gameBoard, long playerToMove, Long toUserId) {
        String xGameHeader = getXHeaderText(gameId, xPlayer, oPlayer, playerToMove, gameBoard);
        String oGameHeader = getOHeaderText(gameId, xPlayer, oPlayer, playerToMove, gameBoard);

        // Generate the board text (used for both)
        String gameBoardTextBody = gameBoard.getBoardTextBody();

        if (toUserId == null || toUserId == xPlayer.getUserId()) {
            emailSender.sendEmail(xPlayer.getEmailAddr(), subject,
                    header + xGameHeader + gameBoardTextBody);
        }
        if (toUserId == null || toUserId == oPlayer.getUserId()) {
            emailSender.sendEmail(oPlayer.getEmailAddr(), subject,
                    header + oGameHeader + gameBoardTextBody);
        }

    }
}
