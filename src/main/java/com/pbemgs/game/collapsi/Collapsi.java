package com.pbemgs.game.collapsi;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.pbemgs.controller.SESEmailSender;
import com.pbemgs.controller.TextResponseProvider;
import com.pbemgs.dko.PlayerOutcomesDKO;
import com.pbemgs.dko.UsersDKO;
import com.pbemgs.game.GameInterface;
import com.pbemgs.game.GameMessageMailer;
import com.pbemgs.game.collapsi.dko.CollapsiGamesDKO;
import com.pbemgs.game.collapsi.dko.CollapsiPlayersDKO;
import com.pbemgs.generated.enums.CollapsiGamesGameState;
import com.pbemgs.generated.enums.PlayerOutcomesOutcome;
import com.pbemgs.generated.tables.records.CollapsiGamesRecord;
import com.pbemgs.generated.tables.records.CollapsiPlayersRecord;
import com.pbemgs.generated.tables.records.UsersRecord;
import com.pbemgs.model.GameType;
import com.pbemgs.model.Location;
import com.pbemgs.model.S3Email;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public class Collapsi implements GameInterface {
    private static final int PLAYER_GAME_LIMIT = 3;
    private static final int MAX_OPEN_GAMES = 10;
    private static final Duration REMINDER_DURATION = Duration.ofHours(24);
    private static final Duration TIMEOUT_DURATION = Duration.ofHours(72);

    private final DSLContext dslContext;
    private final CollapsiGamesDKO collapsiGamesDKO;
    private final CollapsiPlayersDKO collapsiPlayersDKO;
    private final UsersDKO usersDKO;
    private final LambdaLogger logger;

    // players in game
    private List<CollapsiPlayersRecord> playerList;
    private final List<UsersRecord> usersList;

    private record TextBodyParseResult(Location move, boolean success, String error) {
    }

    public Collapsi(DSLContext dslContext, LambdaLogger logger) {
        this.dslContext = dslContext;
        collapsiGamesDKO = new CollapsiGamesDKO(dslContext);
        collapsiPlayersDKO = new CollapsiPlayersDKO(dslContext);
        usersDKO = new UsersDKO(dslContext);
        this.logger = logger;
        playerList = new ArrayList<>();
        usersList = new ArrayList<>();
    }

    @Override
    public void processCreateGame(UsersRecord user, S3Email email, SESEmailSender emailSender) {
        List<CollapsiGamesRecord> userGames = collapsiGamesDKO.getActiveGamesForUser(user.getUserId());
        if (userGames.size() >= PLAYER_GAME_LIMIT) {
            GameMessageMailer.gameLimitReached(emailSender, user.getEmailAddr(), "create_game", GameType.COLLAPSI);
            return;
        }

        List<CollapsiGamesRecord> openGames = collapsiGamesDKO.getOpenGames();
        if (openGames.size() >= MAX_OPEN_GAMES) {
            GameMessageMailer.openGamesLimitReached(emailSender, user.getEmailAddr(), GameType.COLLAPSI);
            return;
        }

        try {
            /*
            Map<String, String> options = GameTextUtilities.parseOptions(email.getEmailBodyText(logger));
            logger.log("-- Options read: " + options.toString());
            String validationErrors = validateOptions(options);
            if (validationErrors != null) {
                logger.log("Game creation failed: Invalid options.");
                GameMessageMailer.createOptionsInvalid(emailSender, user.getEmailAddr(), GameType.COLLAPSI, validationErrors);
                return;
            }
             */

            // Write the newly-created game data and player data to the DB in one transaction.
            AtomicLong gameIdHolder = new AtomicLong();
            dslContext.transaction(configuration -> {
                DSLContext trx = DSL.using(configuration);

                // Create game and get the new game ID
                // Setting firstTurnPlayerId to the creator temporarily so that getOpenGamesTextBody() works easily.
                long gameId = new CollapsiGamesDKO(trx).createNewGame(user.getUserId());

                new CollapsiPlayersDKO(trx).addPlayer(gameId, user.getUserId(), 0);
                gameIdHolder.set(gameId);
            });

            GameMessageMailer.createSuccess(emailSender, user.getEmailAddr(), GameType.COLLAPSI, gameIdHolder.get());
            return;
        } catch (Exception e) {
            logger.log("Exception parsing options from create_game text body or creating game: " + e.getMessage());
            emailSender.sendEmail(user.getEmailAddr(), "PBEMGS - create_game collapsi failed",
                    "Exception parsing options or creating game - please send feedback with the following error: " + e.getMessage());
            return;
        }
    }

    @Override
    public void processJoinGame(UsersRecord user, long gameId, SESEmailSender emailSender) {
        List<CollapsiGamesRecord> userGames = collapsiGamesDKO.getActiveGamesForUser(user.getUserId());
        if (userGames.size() >= PLAYER_GAME_LIMIT) {
            GameMessageMailer.gameLimitReached(emailSender, user.getEmailAddr(), "join_game", GameType.COLLAPSI);
            return;
        }
        CollapsiGamesRecord game = collapsiGamesDKO.getGameById(gameId);

        // Validity checks: game must exist, be in OPEN state, and not have a creating player of self.
        if (game == null || game.getGameState() != CollapsiGamesGameState.OPEN) {
            GameMessageMailer.joinNonopenGame(emailSender, user.getEmailAddr(), GameType.COLLAPSI, gameId);
            return;
        }
        populatePlayerMap(game);
        if (playerList.get(0).getUserId() == user.getUserId()) {
            GameMessageMailer.joinAlreadyIn(emailSender, user.getEmailAddr(), GameType.COLLAPSI, gameId);
            return;
        }

        // Set first turn player randomly
        UsersRecord createUser = usersDKO.fetchUserById(playerList.get(0).getUserId());
        Random rng = new Random();
        game.setFirstTurnUserId(rng.nextBoolean() ? createUser.getUserId() : user.getUserId());

        CollapsiBoard gameBoard = new CollapsiBoard();
        gameBoard.initializeNewBoard();

        // Set other initial fields
        game.setGameState(CollapsiGamesGameState.IN_PROGRESS);
        game.setCurrentActionUserid(game.getFirstTurnUserId());
        game.setBoardState(gameBoard.serialize());
        game.setLastMoveTimestamp(LocalDateTime.now());
        game.setLastReminderTimestamp(LocalDateTime.now());

        // update the game state and the player1 state in one transaction
        try {
            CollapsiGamesRecord finalGame = game;
            dslContext.transaction(configuration -> {
                DSLContext trx = DSL.using(configuration);
                new CollapsiGamesDKO(trx).completeGame(finalGame, user.getUserId(), finalGame.getCurrentActionUserid());
                new CollapsiPlayersDKO(trx).addPlayer(gameId, user.getUserId(), 1);
            });
        } catch (Exception e) {
            logger.log("Exception attempting to join game: " + e.getMessage());
            emailSender.sendEmail(user.getEmailAddr(), "PBEMGS - join_game collapsi failed",
                    "Exception while attempting to join the game - please send feedback with the following error: " + e.getMessage());
            return;
        }

        // reload game so players are both set.
        game = collapsiGamesDKO.getGameById(gameId);
        populatePlayerMap(game);

        sendBoardStateEmail(emailSender, "MOVE COLLAPSI " + gameId + " - GAME START!",
                user.getHandle() + " has joined the action!\n\n",
                game, gameBoard, null);
    }

    @Override
    public void processMove(UsersRecord user, long gameId, S3Email emailBody, SESEmailSender emailSender) {
        CollapsiGamesRecord game = collapsiGamesDKO.getGameById(gameId);

        // Validity checks: game must exist, be in IN_PROGRESS state, and user part of game
        if (game == null || game.getGameState() != CollapsiGamesGameState.IN_PROGRESS) {
            GameMessageMailer.moveGameNotValid(emailSender, user.getEmailAddr(), GameType.COLLAPSI, gameId);
            return;
        }
        populatePlayerMap(game);
        if (!Objects.equals(user.getUserId(), usersList.get(0).getUserId()) &&
                !Objects.equals(user.getUserId(), usersList.get(1).getUserId())) {
            GameMessageMailer.moveNotActiveText(emailSender, user.getEmailAddr(), GameType.COLLAPSI, gameId);
            return;
        }
        int playerSeat = user.getUserId() == playerList.get(0).getUserId() ? 0 : 1;

        if (!Objects.equals(game.getCurrentActionUserid(), user.getUserId())) {
            GameMessageMailer.moveNotActiveText(emailSender, user.getEmailAddr(), GameType.COLLAPSI, gameId);
            return;
        }

        // Parse email body for the move
        TextBodyParseResult parseResult = parseMoveFromEmail(emailBody);
        if (!parseResult.success()) {
            GameMessageMailer.moveFailedToParse(emailSender, user.getEmailAddr(), GameType.COLLAPSI, gameId, parseResult.error());
            return;
        }
        Location move = parseResult.move();

        // validate move
        logger.log("-- Collapsi Move: " + move.toString());

        // Load gameBoard, verify move is valid
        CollapsiBoard gameBoard = new CollapsiBoard();
        gameBoard.deserialize(game.getBoardState());
        Map<Location, String> validMoveMap = gameBoard.getValidMoves(playerSeat);

        if (!validMoveMap.containsKey(move)) {
            emailSender.sendEmail(user.getEmailAddr(), "PBEMGS - MOVE COLLAPSI failed",
                    CollapsiTextResponseProvider.getMoveFailedText(gameId, move));
            return;
        }

        executeMove(user, playerSeat, game, gameBoard, move, validMoveMap.get(move), emailSender);
    }

    // Helper to fully execute a validated card placement move.
    private void executeMove(UsersRecord user, int playerSeat, CollapsiGamesRecord game,
                             CollapsiBoard gameBoard, Location loc, String pathString, SESEmailSender emailSender) {
        StringBuilder boardHeader = new StringBuilder();

        boardHeader.append(user.getHandle()).append(" moves to ").append(loc.toString());
        boardHeader.append(" along the path ").append(pathString).append("!\n\n");

        gameBoard.makeMove(playerSeat, loc);
        game.setBoardState(gameBoard.serialize());

        // Activate opposing player
        int updatedActionSeat = 1 - playerSeat;
        game.setCurrentActionUserid(usersList.get(updatedActionSeat).getUserId());
        game.setLastMoveTimestamp(LocalDateTime.now());
        game.setLastReminderTimestamp(null);


        // Check end of game condition
        Map<Location, String> oppValidMoveMap = gameBoard.getValidMoves(updatedActionSeat);
        if (oppValidMoveMap.isEmpty()) {
            processGameOver(playerSeat, game, gameBoard, emailSender, boardHeader.toString());
            return;
        }

        // update game state in DB
        collapsiGamesDKO.updateGame(game);

        // Send emails out
        sendBoardStateEmail(emailSender, "MOVE COLLAPSI " + game.getGameId(),
                boardHeader.toString(), game, gameBoard, null);
    }

    @Override
    public String getOpenGamesTextBody() {
        List<CollapsiGamesRecord> openGames = collapsiGamesDKO.getOpenGames();
        if (openGames.isEmpty()) {
            return CollapsiTextResponseProvider.getNoOpenGamesText();
        }
        Set<Long> creatorUserIds = openGames.stream().map(CollapsiGamesRecord::getFirstTurnUserId).collect(Collectors.toSet());
        Map<Long, UsersRecord> usersById = usersDKO.fetchUsersByIds(creatorUserIds);
        StringBuilder sb = new StringBuilder();
        sb.append(CollapsiTextResponseProvider.getOpenGamesHeaderText(openGames.size()));
        for (CollapsiGamesRecord game : openGames) {
            sb.append(CollapsiTextResponseProvider.getOpenGameDescription(game, usersById.get(game.getFirstTurnUserId())));
        }
        return sb.toString();
    }

    @Override
    public String getRulesTextBody() {
        return CollapsiTextResponseProvider.getCollapsiRulesText();
    }

    @Override
    public String getMyGamesTextBody(long userId) {
        StringBuilder sb = new StringBuilder();
        sb.append("COLLAPSI:\n");
        List<CollapsiGamesRecord> games = collapsiGamesDKO.getActiveGamesForUser(userId);
        for (CollapsiGamesRecord game : games) {
            sb.append("-- Game ID: ").append(game.getGameId());
            if (game.getGameState() == CollapsiGamesGameState.OPEN) {
                sb.append(" is waiting for an opponent.\n");
            } else {
                sb.append(" is in progress - ");
                sb.append(game.getCurrentActionUserid() == userId ? "YOUR TURN!\n" : "opponent's turn.\n");
            }
        }
        return sb.toString();
    }

    @Override
    public void processStatus(UsersRecord user, long gameId, SESEmailSender emailSender) {
        CollapsiGamesRecord game = collapsiGamesDKO.getGameById(gameId);
        if (game == null || game.getGameState() == CollapsiGamesGameState.OPEN) {
            GameMessageMailer.statusNotValidGame(emailSender, user.getEmailAddr(), GameType.COLLAPSI, gameId);
            return;
        }
        populatePlayerMap(game);

        if (user.getUserId() != usersList.get(0).getUserId() &&
                user.getUserId() != usersList.get(1).getUserId()) {
            GameMessageMailer.statusNotYourGame(emailSender, user.getEmailAddr(), GameType.COLLAPSI, gameId);
            return;
        }

        CollapsiBoard gameBoard = new CollapsiBoard();
        gameBoard.deserialize(game.getBoardState());

        sendBoardStateEmail(emailSender, "PBEMGS - COLLAPSI STATUS for game id " + gameId, "",
                game, gameBoard, user.getUserId());
    }

    @Override
    public Map<Long, String> processStaleGameCheck(SESEmailSender emailSender) {
        Map<Long, String> staleStringByUserId = new HashMap<>();
        Set<Long> gameIdsToUpdateReminderTime = new HashSet<>();

        List<CollapsiGamesRecord> activeGames = collapsiGamesDKO.getActiveGames();
        if (activeGames == null || activeGames.isEmpty()) {
            return staleStringByUserId;
        }

        LocalDateTime currTime = LocalDateTime.now();
        for (CollapsiGamesRecord game : activeGames) {
            LocalDateTime lastMoveTime = game.getLastMoveTimestamp();

            // Timeout processing:
            // - Hand selection, select hands randomly for each player who hasn't yet.
            // - Gameplay: select random card to random open slot.
            if (Duration.between(lastMoveTime, currTime).compareTo(TIMEOUT_DURATION) > 0) {
                logger.log("Collapsi Game ID: " + game.getGameId() + " exceeded timeout...");
                populatePlayerMap(game);

                CollapsiBoard gameBoard = new CollapsiBoard();
                gameBoard.deserialize(game.getBoardState());
                int seat = game.getCurrentActionUserid() == playerList.get(0).getUserId() ? 0 : 1;

                // Select a random valid move to make
                Map<Location, String> validMoveMap = gameBoard.getValidMoves(seat);

                List<Location> validMoves = new ArrayList<>(validMoveMap.keySet());

                Random random = new Random();
                int randomIndex = random.nextInt(validMoves.size());
                Location newLoc = validMoves.get(randomIndex);

                logger.log("--- user " + usersList.get(seat).getHandle() + " - moving to: " + newLoc);
                executeMove(usersList.get(seat), seat, game, gameBoard, newLoc, validMoveMap.get(newLoc), emailSender);
            } else {
                LocalDateTime lastTime = game.getLastReminderTimestamp() == null ?
                        game.getLastMoveTimestamp() : game.getLastReminderTimestamp();

                if (Duration.between(lastTime, currTime).compareTo(REMINDER_DURATION) > 0) {
                    logger.log("Collapsi Game ID: " + game.getGameId() + " exceeded reminder time...");
                    gameIdsToUpdateReminderTime.add(game.getGameId());
                    populatePlayerMap(game);
                    int seat = game.getCurrentActionUserid() == playerList.get(0).getUserId() ? 0 : 1;
                    logger.log("--- sending reminder for seat: " + seat + ", user: " + usersList.get(seat).getHandle());
                    staleStringByUserId.put(usersList.get(seat).getUserId(), "Collapsi: Game ID " + game.getGameId());
                }
            }
        }  // end for (processing active games)

        // Update reminder timestamps as necessary
        try {
            collapsiGamesDKO.updateReminderTimestamps(gameIdsToUpdateReminderTime, currTime);
        } catch (Exception e) {
            logger.log("Caught an exception updating reminder timestamps - game ids: " + gameIdsToUpdateReminderTime.toString());
            // don't rethrow or exit - still want to process the email out at the system level
        }

        return staleStringByUserId;
    }

    /**
     * Process the end of the game.
     */
    private void processGameOver(int winnerSeat, CollapsiGamesRecord game, CollapsiBoard gameBoard,
                                 SESEmailSender emailSender, String boardHeader) {

        StringBuilder subject = new StringBuilder("PBEMGS - COLLAPSI Game ID ").append(game.getGameId());
        subject.append(" has ended!  Winner is: ").append(usersList.get(winnerSeat).getHandle());

        String endingBoardHeader = boardHeader + "\nThere are no valid moves - the Collapsi game has ended. Congratulations to " +
                usersList.get(winnerSeat).getHandle() + "!\n\n";

        game.setGameState(CollapsiGamesGameState.COMPLETE);

        // DB write - game victor, game state, players
        try {
            long winUserId = usersList.get(winnerSeat).getUserId();
            long loseUserId = usersList.get(1 - winnerSeat).getUserId();
            dslContext.transaction(configuration -> {
                DSLContext trx = DSL.using(configuration);
                CollapsiGamesDKO trxGamesDKO = new CollapsiGamesDKO(trx);
                PlayerOutcomesDKO trxPlayerOutcomesDKO = new PlayerOutcomesDKO(trx);
                trxGamesDKO.updateGame(game);
                trxPlayerOutcomesDKO.insertOutcome(GameType.COLLAPSI, game.getGameId(), winUserId,
                        PlayerOutcomesOutcome.WIN, null, winUserId == game.getFirstTurnUserId());
                trxPlayerOutcomesDKO.insertOutcome(GameType.COLLAPSI, game.getGameId(), loseUserId,
                        PlayerOutcomesOutcome.LOSS, null, loseUserId == game.getFirstTurnUserId());
            });
        } catch (Exception e) {
            logger.log("-- EXCEPTION writing updated end of game state for game ID " + game.getGameId() + ": " + e.getMessage());
            emailSender.sendEmail(usersList.get(winnerSeat).getEmailAddr(), "PBEMGS - MOVE COLLAPSI " + game.getGameId() + " Failed (internal error)",
                    TextResponseProvider.getExceptionTextBody("Collapsi match end processing", e.getMessage()));
            return;
        }

        // Send email with end of match and ending board state.
        sendBoardStateEmail(emailSender, subject.toString(), endingBoardHeader + "\nFinal Board:\n\n", game, gameBoard, null);
    }

    private String validateOptions(Map<String, String> options) {
        // TODO: put options here if any
        return null;
    }

    private TextBodyParseResult parseMoveFromEmail(S3Email email) {
        try {
            String text = email.getEmailBodyText(logger);
            if (text.isEmpty()) {
                return new TextBodyParseResult(null, false, "No move detected in email body.");
            }

            // Split on spaces, commas, colons, dashes
            String[] tokens = text.split("[\\s,:-]+");

            // First token is the location
            if (tokens.length < 1) {
                return new TextBodyParseResult(null, false, "Invalid move format.");
            }
            Location loc = Location.fromString(tokens[0]);
            if (loc == null) {
                return new TextBodyParseResult(null, false, "Cannot parse a square name from " + tokens[1]);
            }

            return new TextBodyParseResult(loc, true, null);
        } catch (Exception e) {
            logger.log("Error parsing move from email: " + e.getMessage());
            return new TextBodyParseResult(null, false, "Internal error while parsing move.");
        }
    }

    private void populatePlayerMap(CollapsiGamesRecord game) {
        usersList.clear();
        playerList = collapsiPlayersDKO.getPlayersForGame(game.getGameId());
        for (CollapsiPlayersRecord p : playerList) {
            usersList.add(usersDKO.fetchUserById(p.getUserId()));
        }
    }

    /**
     * Send the board state email - if toUserId is null then to both players.
     */
    private void sendBoardStateEmail(SESEmailSender emailSender, String subject, String header,
                                     CollapsiGamesRecord game, CollapsiBoard gameBoard, Long toUserId) {
        String gameHeader = getGameHeaderString(game.getGameId());  // Match header

        // Player headers
        String p0Header = getPlayerHeaderText(0, game);
        String p1Header = getPlayerHeaderText(1, game);

        String gameBoardTextBody = gameBoard.getBoardTextBody();  // Board text

        // get valid move list, for testing, may leave it in depending.
        int activeSeat = usersList.get(0).getUserId() == game.getCurrentActionUserid() ? 0 : 1;

        Map<Location, String> validMoveMap = gameBoard.getValidMoves(activeSeat);
        StringBuilder moveText = new StringBuilder();
        moveText.append("\nValid Moves for ").append(usersList.get(activeSeat).getHandle()).append(":\n");
        for (Location loc : validMoveMap.keySet()) {
            moveText.append("- ").append(loc.toString()).append(" via ").append(validMoveMap.get(loc)).append("\n");
        }

        if (toUserId == null || toUserId == usersList.get(0).getUserId()) {
            emailSender.sendEmail(usersList.get(0).getEmailAddr(), subject,
                    gameHeader + p0Header + p1Header + "\n" + header + gameBoardTextBody + moveText.toString());
        }
        if (toUserId == null || toUserId == usersList.get(1).getUserId()) {
            emailSender.sendEmail(usersList.get(1).getEmailAddr(), subject,
                    gameHeader + p1Header + p0Header + "\n" + header + gameBoardTextBody + moveText.toString());
        }
    }

    // Text formatting helpers
    private String getGameHeaderString(long gameId) {
        return "Collapsi Game ID: " + gameId + "\n\n";
    }

    private String getPlayerHeaderText(int playerSeat, CollapsiGamesRecord game) {
        boolean toMove = usersList.get(playerSeat).getUserId().equals(game.getCurrentActionUserid());
        return CollapsiTextResponseProvider.getPlayerHeader(usersList.get(playerSeat).getHandle(),
                CollapsiBoard.getPlayerSymbol(playerSeat), toMove);
    }

}
