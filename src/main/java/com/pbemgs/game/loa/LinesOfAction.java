package com.pbemgs.game.loa;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.pbemgs.controller.SESEmailSender;
import com.pbemgs.dko.LoaGameDKO;
import com.pbemgs.dko.UsersDKO;
import com.pbemgs.game.GameInterface;
import com.pbemgs.game.GameMessageMailer;
import com.pbemgs.generated.enums.LoaGamesGameState;
import com.pbemgs.generated.tables.records.LoaGamesRecord;
import com.pbemgs.generated.tables.records.UsersRecord;
import com.pbemgs.model.Location;
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

public class LinesOfAction implements GameInterface {

    private static final String GAME_NAME = "LOA";
    private static final int PLAYER_GAME_LIMIT = 3;
    private static final int MAX_OPEN_GAMES = 10;
    private final Duration REMINDER_DURATION = Duration.ofHours(24);
    private final Duration TIMEOUT_DURATION = Duration.ofHours(96);

    private final LoaGameDKO loaGameDKO;
    private final UsersDKO usersDKO;
    private final LambdaLogger logger;

    private final BiMap<UsersRecord, Character> symbolByUser;

    public record LoaMove(Location from, Location to) {}
    private record TextBodyParseResult(LoaMove move, boolean success, String error) {}

    public LinesOfAction(DSLContext dslContext, LambdaLogger logger) {
        loaGameDKO = new LoaGameDKO(dslContext);
        usersDKO = new UsersDKO(dslContext);
        this.logger = logger;
        this.symbolByUser = HashBiMap.create();
    }

    @Override
    public void processCreateGame(UsersRecord user, S3Email email, SESEmailSender emailSender) {
        List<LoaGamesRecord> userGames = loaGameDKO.getActiveGamesForUser(user.getUserId());
        if (userGames.size() >= PLAYER_GAME_LIMIT) {
            GameMessageMailer.gameLimitReached(emailSender, user.getEmailAddr(), "create_game", GAME_NAME);
            return;
        }

        List<LoaGamesRecord> openGames = loaGameDKO.getOpenGames();
        if (openGames.size() >= MAX_OPEN_GAMES) {
            GameMessageMailer.openGamesLimitReached(emailSender, user.getEmailAddr(), GAME_NAME);
            return;
        }

        LoaBoard newBoard = new LoaBoard(logger);
        newBoard.createNewGame();
        Long newGameNum = loaGameDKO.createNewGame(user.getUserId(), newBoard.serialize());
        GameMessageMailer.createSuccess(emailSender, user.getEmailAddr(), GAME_NAME, newGameNum);
    }

    @Override
    public void processJoinGame(UsersRecord user, long gameId, SESEmailSender emailSender) {
        List<LoaGamesRecord> userGames = loaGameDKO.getActiveGamesForUser(user.getUserId());
        if (userGames.size() >= PLAYER_GAME_LIMIT) {
            GameMessageMailer.gameLimitReached(emailSender, user.getEmailAddr(), "join_game", GAME_NAME);
            return;
        }
        LoaGamesRecord game = loaGameDKO.getGameById(gameId);

        // Validity checks: game must exist, be in OPEN state, and not have a X-player of self.
        if (game == null || game.getGameState() != LoaGamesGameState.OPEN) {
            GameMessageMailer.joinNonopenGame(emailSender, user.getEmailAddr(), GAME_NAME, gameId);
            return;
        }
        if (Objects.equals(game.getXPlayerId(), user.getUserId())) {
            GameMessageMailer.joinSelf(emailSender, user.getEmailAddr(), GAME_NAME);
            return;
        }

        UsersRecord xPlayer = usersDKO.fetchUserById(game.getXPlayerId());

        // get player id to move first
        Random rng = new Random();
        Long firstPlayerId = rng.nextBoolean() ? xPlayer.getUserId() : user.getUserId();

        loaGameDKO.completeGameCreation(gameId, user.getUserId(), firstPlayerId);

        // reload game so players are both set.
        game = loaGameDKO.getGameById(gameId);
        populatePlayerMap(game);
        LoaBoard gameBoard = new LoaBoard(logger);
        gameBoard.deserialize(game.getBoardState());

        sendBoardStateEmail(emailSender, "MOVE LOA " + gameId + " - GAME STARTED!",
                user.getHandle() + " has joined the game!",
                gameId, gameBoard, firstPlayerId, null);
    }

    @Override
    public void processMove(UsersRecord user, long gameId, S3Email emailBody, SESEmailSender emailSender) {
        LoaGamesRecord game = loaGameDKO.getGameById(gameId);

        // Validity checks: gameBoard must exist, be in IN_PROGRESS state, and the user must be active player.
        if (game == null || game.getGameState() != LoaGamesGameState.IN_PROGRESS) {
            GameMessageMailer.moveGameNotValid(emailSender, user.getEmailAddr(), GAME_NAME, gameId);
            return;
        }
        if (!Objects.equals(game.getPlayerIdToMove(), user.getUserId())) {
            emailSender.sendEmail(user.getEmailAddr(), "PBEMGS - MOVE LOA failed",
                    LoaTextResponseProvider.getMoveNotActiveText(gameId));
            return;
        }

        populatePlayerMap(game);

        // Parse email body for the move
        TextBodyParseResult parseResult = parseMoveFromEmail(emailBody);
        if (!parseResult.success()) {
            emailSender.sendEmail(user.getEmailAddr(), "PBEMGS - MOVE LOA failed",
                    parseResult.error());
        }

        LoaMove move = parseResult.move();

        // Load gameBoard, verify move is valid
        LoaBoard gameBoard = new LoaBoard(logger);
        gameBoard.deserialize(game.getBoardState());
        char playerSymbol = symbolByUser.get(user);

        String errorMessage = gameBoard.validateMove(move.from(), move.to(), playerSymbol);

        if (errorMessage != null) {
            emailSender.sendEmail(user.getEmailAddr(), "PBEMGS - MOVE LOA failed",
                    LoaTextResponseProvider.getMoveInvalidText(move, gameId, errorMessage));
            return;
        }

        executeMove(user, game, gameBoard, move, emailSender);
    }

    // Helper to fully execute a validated move
    private void executeMove(UsersRecord user, LoaGamesRecord game, LoaBoard gameBoard, LoaMove move, SESEmailSender emailSender) {

        char playerSymbol = symbolByUser.get(user);
        char oppSymbol = playerSymbol == LoaBoard.PLAYER_X ? LoaBoard.PLAYER_O : LoaBoard.PLAYER_X;
        boolean capture = gameBoard.makeMove(playerSymbol, move);
        game.setBoardState(gameBoard.serialize());

        // Swap active player
        UsersRecord oppUser = symbolByUser.inverse().get(oppSymbol);
        game.setPlayerIdToMove(oppUser.getUserId());
        game.setLastMoveTimestamp(LocalDateTime.now());
        game.setLastReminderTimestamp(null);

        // Check victory conditions, current player first
        if (gameBoard.isVictoryCondition(playerSymbol)) {
            processGameOver(playerSymbol, game, gameBoard, emailSender);
            return;
        }
        if (gameBoard.isVictoryCondition(oppSymbol)) {
            processGameOver(oppSymbol, game, gameBoard, emailSender);
            return;
        }

        // update gameBoard state in DB
        loaGameDKO.updateGame(game);

        // Send emails out (2)
        // Generate the "header" portion for both players

        String boardHeader = user.getHandle() + " moved from " + move.from() + " to " + move.to() +
                (capture ? " - capture!\n\n" : ".\n\n");
        sendBoardStateEmail(emailSender, "MOVE LOA " + game.getGameId(),
                 boardHeader, game.getGameId(),
                 gameBoard, oppUser.getUserId(), null);
    }

    @Override
    public String getOpenGamesTextBody() {
        List<LoaGamesRecord> openGames = loaGameDKO.getOpenGames();
        if (openGames.isEmpty()) {
            return LoaTextResponseProvider.getNoOpenGamesText();
        }
        Set<Long> creatorUserIds = openGames.stream().map(LoaGamesRecord::getXPlayerId).collect(Collectors.toSet());
        Map<Long, UsersRecord> usersById = usersDKO.fetchUsersByIds(creatorUserIds);
        StringBuilder sb = new StringBuilder();
        sb.append(LoaTextResponseProvider.getOpenGamesHeaderText(openGames.size()));
        for (LoaGamesRecord game : openGames) {
            sb.append(LoaTextResponseProvider.getOpenGameDescription(game.getGameId(), usersById.get(game.getXPlayerId())));
        }
        return sb.toString();
    }

    @Override
    public String getRulesTextBody() {
        return LoaTextResponseProvider.getLoaRulesText();
    }

    @Override
    public String getMyGamesTextBody(long userId) {
        StringBuilder sb = new StringBuilder();
        sb.append("LOA:\n");
        List<LoaGamesRecord> games = loaGameDKO.getActiveGamesForUser(userId);
        for (LoaGamesRecord game : games) {
            sb.append("-- Game ID: ").append(game.getGameId());
            if (game.getGameState() == LoaGamesGameState.OPEN) {
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
        LoaGamesRecord game = loaGameDKO.getGameById(gameId);
        if (game == null || game.getGameState() == LoaGamesGameState.OPEN) {
            emailSender.sendEmail(user.getEmailAddr(), "PBEMGS - STATUS command failed",
                    LoaTextResponseProvider.getStatusFailedNoGameText(gameId));
            return;
        }
        if (user.getUserId() != game.getXPlayerId() && user.getUserId() != game.getOPlayerId()) {
            emailSender.sendEmail(user.getEmailAddr(), "PBEMGS - STATUS command failed",
                    LoaTextResponseProvider.getStatusFailedNotYourGameText(gameId));
            return;
        }

        populatePlayerMap(game);

        String textHeader = "Lines of Action Game ID: " + gameId +
                (game.getGameState() == LoaGamesGameState.IN_PROGRESS ? " - In Progress\n\n" : " - Complete\n\n");
        LoaBoard gameBoard = new LoaBoard(logger);
        gameBoard.deserialize(game.getBoardState());
        sendBoardStateEmail(emailSender, "PBEMGS - LOA STATUS for game id " + gameId, textHeader,
                gameId, gameBoard, game.getPlayerIdToMove(), user.getUserId());
    }

    @Override
    public Map<Long, String> processStaleGameCheck(SESEmailSender emailSender) {
        Map<Long, String> staleStringByUserId = new HashMap<>();
        List<LoaGamesRecord> activeGames = loaGameDKO.getActiveGames();
        if (activeGames == null || activeGames.isEmpty()) {
            return staleStringByUserId;
        }

        LocalDateTime currTime = LocalDateTime.now();
        for (LoaGamesRecord game : activeGames) {
            LocalDateTime lastMoveTime = game.getLastMoveTimestamp();

            if (Duration.between(lastMoveTime, currTime).compareTo(TIMEOUT_DURATION) > 0) {
                try {
                    populatePlayerMap(game);
                    UsersRecord user = usersDKO.fetchUserById(game.getPlayerIdToMove());
                    LoaBoard gameBoard = new LoaBoard(logger);
                    gameBoard.deserialize(game.getBoardState());
                    char playerSymbol = symbolByUser.get(user);
                    LoaMove randomMove = gameBoard.getRandomMove(playerSymbol);
                    logger.log("Auto-move for LOA Game ID " + game.getGameId() + ": selected " + randomMove);
                    executeMove(user, game, gameBoard, randomMove, emailSender);
                } catch (Exception e) {
                    logger.log("Exception while attempting to auto-move: " + e.getMessage());
                    // continue processing the rest...
                }
            } else {
                LocalDateTime lastTime = game.getLastReminderTimestamp() == null ?
                        game.getLastMoveTimestamp() : game.getLastReminderTimestamp();
                if (Duration.between(lastTime, currTime).compareTo(REMINDER_DURATION) > 0) {
                    staleStringByUserId.put(game.getPlayerIdToMove(), "LOA: Game ID " + game.getGameId());
                }
            }
        }

        // Update reminder timestamps as necessary
        try {
            loaGameDKO.updateReminderTimestamps(staleStringByUserId.keySet(), currTime);
        } catch (Exception e) {
            logger.log("Caught an exception updating reminder timestamps - user ids: " + staleStringByUserId.keySet().toString());
            // don't rethrow or exit - still want to process the email out at the system level
        }

        return staleStringByUserId;
    }


    private void processGameOver(char winnerMarker, LoaGamesRecord game, LoaBoard gameBoard, SESEmailSender emailSender) {
        // Game over processing:
        // Find winning player user number
        // Set the game state to complete, winner_id to winner.
        // Send final email (identical to both users)
        UsersRecord winPlayer = symbolByUser.inverse().get(winnerMarker);

        game.setVictorPlayerId(winPlayer.getUserId());
        game.setGameState(LoaGamesGameState.COMPLETE);
        game.setLastMoveTimestamp(LocalDateTime.now());

        // Store to DB
        loaGameDKO.updateGame(game);

        // Emails out
        String subject = "PBEMGS - LOA game number " + game.getGameId() + " has ended - winner is " +
                winPlayer.getHandle() + "!";

        sendBoardStateEmail(emailSender, subject, "Final Board:\n\n", game.getGameId(),
               gameBoard, game.getPlayerIdToMove(), null);
    }

    // email body retrieval and parsing
    private TextBodyParseResult parseMoveFromEmail(S3Email email) {
        try {
            String text = email.getEmailBodyText(logger);
            if (text.isEmpty()) {
                return new TextBodyParseResult(null, false, "No move detected in email body.");
            }

            // Split on spaces, commas, colons, or dashes
            String[] tokens = text.split("[\\s,:-]+");

            if (tokens.length < 2) {
                return new TextBodyParseResult(null, false, "No valid move found - both a from and to square are required.");
            }

            Location from = Location.fromString(tokens[0]);
            if (from == null) {
                return new TextBodyParseResult(null, false, "Expected a board location as first word of email text body, received " + tokens[0]);
            }

            Location to = Location.fromString(tokens[1]);
            if (to == null) {
                return new TextBodyParseResult(null, false, "Expected a board location as second word of email text body, received " + tokens[1]);
            }
            return new TextBodyParseResult(new LoaMove(from, to), true, null);
        } catch (Exception e) {
            logger.log("Error parsing move from email: " + e.getMessage());
            return new TextBodyParseResult(null, false, "Internal error while parsing move.");
        }
    }

    private void populatePlayerMap(LoaGamesRecord game) {
        symbolByUser.clear();
        UsersRecord xPlayer = usersDKO.fetchUserById(game.getXPlayerId());
        UsersRecord oPlayer = usersDKO.fetchUserById(game.getOPlayerId());
        symbolByUser.put(xPlayer, LoaBoard.PLAYER_X);
        symbolByUser.put(oPlayer, LoaBoard.PLAYER_O);
    }

    // Helper methods for formatting and sending outgoing board-state emails.
    private String getXHeaderText(long gameId, UsersRecord x, UsersRecord o, long playerToMove, LoaBoard board) {
        return LoaTextResponseProvider.getGameHeader(gameId, "X", x.getHandle(), board.getPieceCount(LoaBoard.PLAYER_X),
                playerToMove == x.getUserId(), "O", o.getHandle(), board.getPieceCount(LoaBoard.PLAYER_O));
    }

    private String getOHeaderText(long gameId, UsersRecord x, UsersRecord o, long playerToMove, LoaBoard board) {
        return LoaTextResponseProvider.getGameHeader(gameId, "O", o.getHandle(), board.getPieceCount(LoaBoard.PLAYER_O),
                playerToMove == o.getUserId(), "X", x.getHandle(), board.getPieceCount(LoaBoard.PLAYER_X));
    }

    private void sendBoardStateEmail(SESEmailSender emailSender, String subject, String header,
                                     long gameId, LoaBoard gameBoard, long playerToMove, Long toUserId) {
        UsersRecord xPlayer = symbolByUser.inverse().get(LoaBoard.PLAYER_X);
        UsersRecord oPlayer = symbolByUser.inverse().get(LoaBoard.PLAYER_O);
        String xGameHeader = getXHeaderText(gameId, xPlayer, oPlayer, playerToMove, gameBoard);
        String oGameHeader = getOHeaderText(gameId, xPlayer, oPlayer, playerToMove, gameBoard);

        // Generate the board text (used for both)
        String gameBoardTextBody = gameBoard.getBoardTextBody();

        if (toUserId == null || toUserId == xPlayer.getUserId()) {
            emailSender.sendEmail(xPlayer.getEmailAddr(), subject,
                    xGameHeader + header + gameBoardTextBody);
        }
        if (toUserId == null || toUserId == oPlayer.getUserId()) {
            emailSender.sendEmail(oPlayer.getEmailAddr(), subject,
                    oGameHeader + header + gameBoardTextBody);
        }

    }
}
