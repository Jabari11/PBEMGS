package com.pbemgs.game.triad;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.pbemgs.controller.SESEmailSender;
import com.pbemgs.controller.TextResponseProvider;
import com.pbemgs.dko.PlayerOutcomesDKO;
import com.pbemgs.dko.UsersDKO;
import com.pbemgs.game.GameInterface;
import com.pbemgs.game.GameMessageMailer;
import com.pbemgs.game.GameTextUtilities;
import com.pbemgs.game.triad.dko.TriadGameVictorsDKO;
import com.pbemgs.game.triad.dko.TriadGamesDKO;
import com.pbemgs.game.triad.dko.TriadPlayersDKO;
import com.pbemgs.generated.enums.PlayerOutcomesOutcome;
import com.pbemgs.generated.enums.TriadGamesGamePhase;
import com.pbemgs.generated.enums.TriadGamesGameState;
import com.pbemgs.generated.tables.records.TriadGamesRecord;
import com.pbemgs.generated.tables.records.TriadPlayersRecord;
import com.pbemgs.generated.tables.records.UsersRecord;
import com.pbemgs.model.Direction;
import com.pbemgs.model.GameType;
import com.pbemgs.model.Location;
import com.pbemgs.model.MonoSymbol;
import com.pbemgs.model.S3Email;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static com.pbemgs.game.triad.TriadDisplayDefs.BOT_BORDER;
import static com.pbemgs.game.triad.TriadDisplayDefs.TOP_BORDER;

public class TriadCubed implements GameInterface {
    private static final int PLAYER_GAME_LIMIT = 3;
    private static final int MAX_OPEN_GAMES = 10;
    private static final Duration REMINDER_DURATION = Duration.ofHours(24);
    private static final Duration TIMEOUT_DURATION = Duration.ofHours(72);

    private static final String SERIALIZED_INITAL_CARDSET = "1,2,3,4,5,6,7,8,9,10,11,12,13,14,15";

    private final DSLContext dslContext;
    private final TriadGamesDKO triadGamesDKO;
    private final TriadPlayersDKO triadPlayersDKO;
    private final UsersDKO usersDKO;
    private final LambdaLogger logger;

    // players in game
    private List<TriadPlayersRecord> playerList;
    private final List<UsersRecord> usersList;

    public record TriadMove(Integer cardSlot, String cardName, Location loc) {
    }

    private record TextBodyParseResult(TriadMove move, boolean success, String error) {
    }

    private record TextBodyParseHandSelectResult(List<Integer> selection, boolean success, String error) {
    }

    public TriadCubed(DSLContext dslContext, LambdaLogger logger) {
        this.dslContext = dslContext;
        triadGamesDKO = new TriadGamesDKO(dslContext);
        triadPlayersDKO = new TriadPlayersDKO(dslContext);
        usersDKO = new UsersDKO(dslContext);
        this.logger = logger;
        playerList = new ArrayList<>();
        usersList = new ArrayList<>();
    }

    @Override
    public void processCreateGame(UsersRecord user, S3Email email, SESEmailSender emailSender) {
        List<TriadGamesRecord> userGames = triadGamesDKO.getActiveGamesForUser(user.getUserId());
        if (userGames.size() >= PLAYER_GAME_LIMIT) {
            GameMessageMailer.gameLimitReached(emailSender, user.getEmailAddr(), "create_game", GameType.TRIAD);
            return;
        }

        List<TriadGamesRecord> openGames = triadGamesDKO.getOpenGames();
        if (openGames.size() >= MAX_OPEN_GAMES) {
            GameMessageMailer.openGamesLimitReached(emailSender, user.getEmailAddr(), GameType.TRIAD);
            return;
        }

        try {
            Map<String, String> options = GameTextUtilities.parseOptions(email.getEmailBodyText(logger));
            logger.log("-- Options read: " + options.toString());
            String validationErrors = validateOptions(options);
            if (validationErrors != null) {
                logger.log("Game creation failed: Invalid options.");
                GameMessageMailer.createOptionsInvalid(emailSender, user.getEmailAddr(), GameType.TRIAD, validationErrors);
                return;
            }

            String openHandOption = options.getOrDefault("open", "off");
            boolean openHandSet = openHandOption.equals("true") || openHandOption.equals("yes") || openHandOption.equals("on");
            String elementOption = options.getOrDefault("element", "off");
            boolean elementSet = elementOption.equals("true") || elementOption.equals("yes") || elementOption.equals("on");

            // Write the newly-created game data and player data to the DB in one transaction.
            AtomicLong gameIdHolder = new AtomicLong();
            dslContext.transaction(configuration -> {
                DSLContext trx = DSL.using(configuration);

                // Create game and get the new game ID
                // Setting firstTurnPlayerId to the creator temporarily so that getOpenGamesTextBody() works easily.
                long gameId = new TriadGamesDKO(trx).createNewGame(user.getUserId(), openHandSet, elementSet);

                new TriadPlayersDKO(trx).addPlayer(gameId, user.getUserId(), 0, SERIALIZED_INITAL_CARDSET);
                gameIdHolder.set(gameId);
            });

            GameMessageMailer.createSuccess(emailSender, user.getEmailAddr(), GameType.TRIAD, gameIdHolder.get());
            return;
        } catch (Exception e) {
            logger.log("Exception parsing options from create_game text body or creating game: " + e.getMessage());
            emailSender.sendEmail(user.getEmailAddr(), "PBEMGS - create_game triad failed",
                    "Exception parsing options or creating game - please send feedback with the following error: " + e.getMessage());
            return;
        }
    }

    @Override
    public void processJoinGame(UsersRecord user, long gameId, SESEmailSender emailSender) {
        List<TriadGamesRecord> userGames = triadGamesDKO.getActiveGamesForUser(user.getUserId());
        if (userGames.size() >= PLAYER_GAME_LIMIT) {
            GameMessageMailer.gameLimitReached(emailSender, user.getEmailAddr(), "join_game", GameType.TRIAD);
            return;
        }
        TriadGamesRecord game = triadGamesDKO.getGameById(gameId);

        // Validity checks: game must exist, be in OPEN state, and not have a creating player of self.
        if (game == null || game.getGameState() != TriadGamesGameState.OPEN) {
            GameMessageMailer.joinNonopenGame(emailSender, user.getEmailAddr(), GameType.TRIAD, gameId);
            return;
        }
        populatePlayerMap(game);
        if (playerList.get(0).getUserId() == user.getUserId()) {
            GameMessageMailer.joinAlreadyIn(emailSender, user.getEmailAddr(), GameType.TRIAD, gameId);
            return;
        }

        // Set first turn player randomly
        UsersRecord createUser = usersDKO.fetchUserById(playerList.get(0).getUserId());
        Random rng = new Random();
        game.setFirstTurnUserId(rng.nextBoolean() ? createUser.getUserId() : user.getUserId());

        // Generate the game board for G1
        TriadCubedBoard gameBoard = new TriadCubedBoard(logger);
        gameBoard.initializeNewBoard(game.getOptionElemental());

        // Set other initial fields
        game.setGamePhase(TriadGamesGamePhase.HAND_SELECTION);
        game.setGameState(TriadGamesGameState.IN_PROGRESS);
        game.setCurrentActionUserid(null);  // null for hand selection phase
        game.setCurrentSubgame(1);
        game.setBoardState(gameBoard.serialize());
        game.setLastMoveTimestamp(LocalDateTime.now());

        // update the game state and the player1 state in one transaction
        try {
            TriadGamesRecord finalGame = game;
            dslContext.transaction(configuration -> {
                DSLContext trx = DSL.using(configuration);
                new TriadGamesDKO(trx).updateGame(finalGame);
                new TriadPlayersDKO(trx).addPlayer(gameId, user.getUserId(), 1, SERIALIZED_INITAL_CARDSET);
            });
        } catch (Exception e) {
            logger.log("Exception attempting to join game: " + e.getMessage());
            emailSender.sendEmail(user.getEmailAddr(), "PBEMGS - join_game triad failed",
                    "Exception while attempting to join the game - please send feedback with the following error: " + e.getMessage());
            return;
        }

        // reload game so players are both set.
        game = triadGamesDKO.getGameById(gameId);
        populatePlayerMap(game);

        sendHandSelectionEmail(emailSender, "MOVE TRIAD " + gameId + " - GAME START!",
                user.getHandle() + " has joined the action - GAME #1 is underway - select your hand!\n\n",
                game, gameBoard, null);
    }

    @Override
    public void processMove(UsersRecord user, long gameId, S3Email emailBody, SESEmailSender emailSender) {
        TriadGamesRecord game = triadGamesDKO.getGameById(gameId);

        // Validity checks: game must exist, be in IN_PROGRESS state, and user part of game
        if (game == null || game.getGameState() != TriadGamesGameState.IN_PROGRESS) {
            GameMessageMailer.moveGameNotValid(emailSender, user.getEmailAddr(), GameType.TRIAD, gameId);
            return;
        }
        populatePlayerMap(game);
        if (!Objects.equals(user.getUserId(), usersList.get(0).getUserId()) &&
                !Objects.equals(user.getUserId(), usersList.get(1).getUserId())) {
            GameMessageMailer.moveNotActiveText(emailSender, user.getEmailAddr(), GameType.TRIAD, gameId);
            return;
        }
        int playerSeat = user.getUserId() == playerList.get(0).getUserId() ? 0 : 1;

        // hand selection state handled separately
        if (game.getGamePhase() == TriadGamesGamePhase.HAND_SELECTION) {
            processHandSelectionMove(user, playerSeat, game, emailBody, emailSender);
            return;
        }

        if (!Objects.equals(game.getCurrentActionUserid(), user.getUserId())) {
            GameMessageMailer.moveNotActiveText(emailSender, user.getEmailAddr(), GameType.TRIAD, gameId);
            return;
        }

        // Parse email body for the move
        TextBodyParseResult parseResult = parseMoveFromEmail(emailBody);
        if (!parseResult.success()) {
            GameMessageMailer.moveFailedToParse(emailSender, user.getEmailAddr(), GameType.TRIAD, gameId, parseResult.error());
            return;
        }
        TriadMove move = parseResult.move();

        // validate move (card exists)
        Map<Integer, Integer> cardIdBySlot = getPlayerHandMap(playerList.get(playerSeat));
        Map<String, Integer> cardIdByName = getPlayerHandMapByName(playerList.get(playerSeat));
        Integer cardIdPlayed = null;
        if (move.cardSlot() != null) {
            cardIdPlayed = cardIdBySlot.get(move.cardSlot());
            if (cardIdPlayed == null) {
                emailSender.sendEmail(user.getEmailAddr(), "PBEMGS - MOVE TRIAD failed",
                        TriadCubedTextResponseProvider.getInvalidCardSlotText(move.cardSlot()));
                return;
            }
        } else {
            cardIdPlayed = cardIdByName.get(move.cardName());
            if (cardIdPlayed == null) {
                emailSender.sendEmail(user.getEmailAddr(), "PBEMGS - MOVE TRIAD failed",
                        TriadCubedTextResponseProvider.getInvalidCardNameText(move.cardName()));
                return;
            }
        }
        logger.log("-- Triad Move: " + move.toString() + ", card ID: " + cardIdPlayed);

        // Load gameBoard, verify move is valid
        TriadCubedBoard gameBoard = new TriadCubedBoard(logger);
        gameBoard.deserialize(game.getBoardState());
        String boardError = gameBoard.validateMove(move.loc());
        if (boardError != null) {
            emailSender.sendEmail(user.getEmailAddr(), "PBEMGS - MOVE TRIAD failed",
                    TriadCubedTextResponseProvider.getMoveFailedText(gameId, boardError));
            return;
        }

        executeCardPlacementMove(user, playerSeat, game, gameBoard, cardIdPlayed, move.loc(), emailSender);
    }

    private void processHandSelectionMove(UsersRecord user, int playerSeat, TriadGamesRecord game,
                                          S3Email emailBody, SESEmailSender emailSender) {
        // Check if hand selection is already done
        if (!playerList.get(playerSeat).getCardsInHand().isEmpty()) {
            emailSender.sendEmail(user.getEmailAddr(), "PBEMGS - MOVE TRIAD failed",
                    TriadCubedTextResponseProvider.getHandSelectionCompleteText(game.getGameId()));
            return;
        }

        // Parse email body for the move
        TextBodyParseHandSelectResult parseResult = parseHandSelectionMoveFromEmail(emailBody);
        if (!parseResult.success()) {
            GameMessageMailer.moveFailedToParse(emailSender, user.getEmailAddr(), GameType.TRIAD, game.getGameId(), parseResult.error());
            return;
        }
        List<Integer> handSelection = parseResult.selection();

        // validate selection (card IDs all available)
        Set<Integer> undraftedIds = new HashSet<>(deserializeCardIds(playerList.get(playerSeat).getUndraftedCards()));
        StringBuilder errorMsg = new StringBuilder("The following card IDs are not available to select: ");
        boolean hasError = false;
        for (int cardId : handSelection) {
            if (!undraftedIds.contains(cardId)) {
                errorMsg.append(cardId).append(" ");
                hasError = true;
            }
            undraftedIds.remove(cardId);
        }

        if (hasError) {
            emailSender.sendEmail(user.getEmailAddr(), "PBEMGS - MOVE TRIAD failed",
                    TriadCubedTextResponseProvider.getInvalidHandSelectonText(game.getGameId(), errorMsg));
            return;
        }

        executeHandSelectionMove(user, playerSeat, game, parseResult.selection(), emailSender);
    }

    // Helper to fully execute a validated card placement move.
    private void executeCardPlacementMove(UsersRecord user, int playerSeat, TriadGamesRecord game,
                                          TriadCubedBoard gameBoard, int cardId, Location loc, SESEmailSender emailSender) {
        StringBuilder boardHeader = new StringBuilder();
        gameBoard.makeMove(playerSeat, cardId, loc);
        game.setBoardState(gameBoard.serialize());

        // remove the card played from hand
        Set<Integer> cardsInHand = new HashSet<>(deserializeCardIds(playerList.get(playerSeat).getCardsInHand()));
        cardsInHand.remove(cardId);
        playerList.get(playerSeat).setCardsInHand(serializeCardIds(cardsInHand.stream().sorted().toList()));

        boardHeader.append(TriadMoveFormatter.formatMoveAnnouncement(user.getHandle(), cardId, loc)).append("\n\n");

        if (gameBoard.isBoardFull()) {
            int p0Score = gameBoard.getCardCount(0);
            int p1Score = gameBoard.getCardCount(1);
            int winner = p0Score > p1Score ? 0 : 1;
            processGameOver(winner, game, gameBoard, emailSender, boardHeader.toString());
            return;
        }

        // Activate opposing player
        int updatedActionSeat = 1 - playerSeat;
        game.setCurrentActionUserid(usersList.get(updatedActionSeat).getUserId());
        game.setLastMoveTimestamp(LocalDateTime.now());
        game.setLastReminderTimestamp(null);

        // update gameBoard state and player hand
        try {
            dslContext.transaction(configuration -> {
                DSLContext trx = DSL.using(configuration);
                TriadPlayersDKO trxPlayerDKO = new TriadPlayersDKO(trx);
                TriadGamesDKO trxGameDKO = new TriadGamesDKO(trx);
                trxPlayerDKO.updatePlayerCardsInHand(user.getUserId(), game.getGameId(), playerList.get(playerSeat).getCardsInHand());
                trxGameDKO.updateGame(game);
            });
        } catch (Exception e) {
            emailSender.sendEmail(user.getEmailAddr(), "PBEMGS - MOVE TRIAD " + game.getGameId() + " Failed (internal error)",
                    TextResponseProvider.getExceptionTextBody("move triad", e.getMessage()));
        }

        triadGamesDKO.updateGame(game);

        // Send emails out
        sendBoardStateEmail(emailSender, "MOVE TRIAD " + game.getGameId(),
                boardHeader.toString(), game, gameBoard, null);
    }

    private void executeHandSelectionMove(UsersRecord user, int playerSeat, TriadGamesRecord game,
                                          List<Integer> cardIds, SESEmailSender emailSender) {
        Set<Integer> undraftedIds = new HashSet<>(deserializeCardIds(playerList.get(playerSeat).getUndraftedCards()));
        cardIds.forEach(undraftedIds::remove);
        List<Integer> newUndrafted = undraftedIds.stream().sorted().toList();
        String handStr = serializeCardIds(cardIds);
        String undraftedStr = serializeCardIds(newUndrafted);

        playerList.get(playerSeat).setCardsInHand(handStr);
        playerList.get(playerSeat).setUndraftedCards(undraftedStr);

        TriadCubedBoard gameBoard = new TriadCubedBoard(logger);
        gameBoard.deserialize(game.getBoardState());

        // check other player - if the other player hasn't selected yet, just update DB for this player and bail.
        TriadPlayersRecord otherPlayerRecord = playerList.get(1 - playerSeat);
        if (otherPlayerRecord.getCardsInHand().isEmpty()) {
            triadPlayersDKO.updatePlayerRecord(playerList.get(playerSeat));
            sendHandSelectionEmail(emailSender, "PBEMGS - TRIAD " + game.getGameId() + " hand selection successful!",
                    "Your hand has been locked in.  Waiting for opponent to select theirs.\n\n", game, gameBoard, user.getUserId());
            return;
        }

        game.setGamePhase(TriadGamesGamePhase.GAMEPLAY);
        game.setCurrentActionUserid(usersList.get(getFirstPlayerSeatForGame(game)).getUserId());
        game.setLastMoveTimestamp(LocalDateTime.now());

        try {
            dslContext.transaction(configuration -> {
                DSLContext trx = DSL.using(configuration);
                TriadPlayersDKO trxPlayersDKO = new TriadPlayersDKO(trx);
                TriadGamesDKO trxGamesDKO = new TriadGamesDKO(trx);
                trxPlayersDKO.updatePlayerCardsInHand(user.getUserId(), game.getGameId(), handStr);
                trxPlayersDKO.updatePlayerUndrafted(user.getUserId(), game.getGameId(), undraftedStr);
                trxGamesDKO.updateGame(game);
            });
        } catch (Exception e) {
            emailSender.sendEmail(user.getEmailAddr(), "PBEMGS - MOVE TRIAD " + game.getGameId() + " Failed (internal error)",
                    TextResponseProvider.getExceptionTextBody("move triad", e.getMessage()));
        }

        sendBoardStateEmail(emailSender, "MOVE TRIAD " + game.getGameId() + " - game # " + game.getCurrentSubgame() + " started!",
                "Both players have selected their hands - game is underway!\n\n", game, gameBoard, null);
    }

    @Override
    public String getOpenGamesTextBody() {
        List<TriadGamesRecord> openGames = triadGamesDKO.getOpenGames();
        if (openGames.isEmpty()) {
            return TriadCubedTextResponseProvider.getNoOpenGamesText();
        }
        Set<Long> creatorUserIds = openGames.stream().map(TriadGamesRecord::getFirstTurnUserId).collect(Collectors.toSet());
        Map<Long, UsersRecord> usersById = usersDKO.fetchUsersByIds(creatorUserIds);
        StringBuilder sb = new StringBuilder();
        sb.append(TriadCubedTextResponseProvider.getOpenGamesHeaderText(openGames.size()));
        for (TriadGamesRecord game : openGames) {
            sb.append(TriadCubedTextResponseProvider.getOpenGameDescription(game, usersById.get(game.getFirstTurnUserId())));
        }
        return sb.toString();
    }

    @Override
    public String getRulesTextBody() {
        return TriadCubedTextResponseProvider.getTriadCubedRulesText();
    }

    @Override
    public String getMyGamesTextBody(long userId) {
        StringBuilder sb = new StringBuilder();
        sb.append("TRIAD:\n");
        List<TriadGamesRecord> games = triadGamesDKO.getActiveGamesForUser(userId);
        for (TriadGamesRecord game : games) {
            sb.append("-- Game ID: ").append(game.getGameId());
            if (game.getGameState() == TriadGamesGameState.OPEN) {
                sb.append(" is waiting for an opponent.\n");
            } else {
                sb.append(" is in progress - ");
                if (game.getGamePhase() == TriadGamesGamePhase.GAMEPLAY) {
                    sb.append(game.getCurrentActionUserid() == userId ? "YOUR TURN!\n" : "opponent's turn.\n");
                } else {
                    sb.append(" HAND SELECTION PHASE ");
                    TriadPlayersRecord gamePlayerRecord = triadPlayersDKO.getPlayerByUserId(game.getGameId(), userId);
                    if (gamePlayerRecord.getCardsInHand().isEmpty()) {
                        sb.append(" - YOU NEED TO SELECT!");
                    }
                }
            }
        }
        return sb.toString();
    }

    @Override
    public void processStatus(UsersRecord user, long gameId, SESEmailSender emailSender) {
        TriadGamesRecord game = triadGamesDKO.getGameById(gameId);
        if (game == null || game.getGameState() == TriadGamesGameState.OPEN) {
            GameMessageMailer.statusNotValidGame(emailSender, user.getEmailAddr(), GameType.TRIAD, gameId);
            return;
        }
        populatePlayerMap(game);

        if (user.getUserId() != usersList.get(0).getUserId() &&
                user.getUserId() != usersList.get(1).getUserId()) {
            GameMessageMailer.statusNotYourGame(emailSender, user.getEmailAddr(), GameType.TRIAD, gameId);
            return;
        }

        TriadCubedBoard gameBoard = new TriadCubedBoard(logger);
        gameBoard.deserialize(game.getBoardState());

        if (game.getGamePhase() == TriadGamesGamePhase.GAMEPLAY) {
            sendBoardStateEmail(emailSender, "PBEMGS - TRIAD STATUS for game id " + gameId, "",
                    game, gameBoard, user.getUserId());
        } else {
            sendHandSelectionEmail(emailSender, "PBEMGS - TRIAD STATUS for game id " + gameId,
                    "", game, gameBoard, user.getUserId());
        }
    }

    @Override
    public Map<Long, String> processStaleGameCheck(SESEmailSender emailSender) {
        Map<Long, String> staleStringByUserId = new HashMap<>();
        Set<Long> gameIdsToUpdateReminderTime = new HashSet<>();

        List<TriadGamesRecord> activeGames = triadGamesDKO.getActiveGames();
        if (activeGames == null || activeGames.isEmpty()) {
            return staleStringByUserId;
        }

        LocalDateTime currTime = LocalDateTime.now();
        for (TriadGamesRecord game : activeGames) {
            LocalDateTime lastMoveTime = game.getLastMoveTimestamp();

            // Timeout processing:
            // - Hand selection, select hands randomly for each player who hasn't yet.
            // - Gameplay: select random card to random open slot.
            if (Duration.between(lastMoveTime, currTime).compareTo(TIMEOUT_DURATION) > 0) {
                logger.log("TriadCubed Game ID: " + game.getGameId() + " exceeded timeout...");
                populatePlayerMap(game);
                TriadGamesGamePhase phase = game.getGamePhase();
                if (phase == TriadGamesGamePhase.HAND_SELECTION) {
                    logger.log("-- hand selection phase");
                    for (int seat = 0; seat <= 1; ++seat) {
                        if (playerList.get(seat).getCardsInHand().isEmpty()) {
                            logger.log("--- selecting hand for seat: " + seat + ", user: " + usersList.get(seat).getHandle());
                            List<Integer> undraftedIds = deserializeCardIds(playerList.get(seat).getUndraftedCards());
                            Collections.shuffle(undraftedIds);
                            executeHandSelectionMove(usersList.get(seat), seat, game, undraftedIds.subList(0, 5), emailSender);
                        }
                    }
                } else {
                    logger.log("-- gameplay phase");
                    TriadCubedBoard gameBoard = new TriadCubedBoard(logger);
                    gameBoard.deserialize(game.getBoardState());
                    int seat = game.getCurrentActionUserid() == playerList.get(0).getUserId() ? 0 : 1;

                    List<Integer> cardIds = deserializeCardIds(playerList.get(seat).getCardsInHand());
                    Collections.shuffle(cardIds);
                    logger.log("--- card placement for " + usersList.get(seat).getHandle() + " - cardID: " + cardIds.get(0));
                    executeCardPlacementMove(usersList.get(seat), seat, game, gameBoard,
                            cardIds.get(0), gameBoard.getRandomEmptyLocation(), emailSender);
                }
            } else {
                LocalDateTime lastTime = game.getLastReminderTimestamp() == null ?
                        game.getLastMoveTimestamp() : game.getLastReminderTimestamp();

                if (Duration.between(lastTime, currTime).compareTo(REMINDER_DURATION) > 0) {
                    logger.log("TriadCubed Game ID: " + game.getGameId() + " exceeded reminder time...");
                    gameIdsToUpdateReminderTime.add(game.getGameId());
                    populatePlayerMap(game);
                    TriadGamesGamePhase phase = game.getGamePhase();
                    if (phase == TriadGamesGamePhase.HAND_SELECTION) {
                        // Reminder processing - if in hand selection, send to all players needing to select.
                        logger.log("-- hand selection phase");
                        for (int seat = 0; seat <= 1; ++seat) {
                            if (playerList.get(seat).getCardsInHand().isEmpty()) {
                                logger.log("--- sending reminder for seat: " + seat + ", user: " + usersList.get(seat).getHandle());
                                staleStringByUserId.put(usersList.get(seat).getUserId(), "Triad Cubed: Game ID " + game.getGameId() + " - hand selection.");
                            }
                        }
                    } else {
                        logger.log("-- gameplay phase");
                        int seat = game.getCurrentActionUserid() == playerList.get(0).getUserId() ? 0 : 1;
                        logger.log("--- sending reminder for seat: " + seat + ", user: " + usersList.get(seat).getHandle());
                        staleStringByUserId.put(usersList.get(seat).getUserId(), "Triad Cubed: Game ID " + game.getGameId());
                    }
                }
            }
        }  // end for (processing active games)

        // Update reminder timestamps as necessary
        try {
            triadGamesDKO.updateReminderTimestamps(gameIdsToUpdateReminderTime, currTime);
        } catch (Exception e) {
            logger.log("Caught an exception updating reminder timestamps - game ids: " + gameIdsToUpdateReminderTime.toString());
            // don't rethrow or exit - still want to process the email out at the system level
        }

        return staleStringByUserId;
    }

    /**
     * Process the end of a single subgame.  This sets up the next if needed, and forwards on
     * to a separate method if it's match end.
     * For game end, it's easiest to send two separate emails because of the board state -
     * one that shows the ending state of the board, and one for the hand selection.
     */
    private void processGameOver(int winnerSeat, TriadGamesRecord game, TriadCubedBoard gameBoard,
                                 SESEmailSender emailSender, String boardHeader) {

        game.setLastMoveTimestamp(LocalDateTime.now());
        game.setLastReminderTimestamp(null);
        game.setCurrentActionUserid(null);

        int firstPlayerSeat = getFirstPlayerSeatForGame(game);

        int gamesWon = playerList.get(winnerSeat).getSubgameCount() + 1;
        playerList.get(winnerSeat).setSubgameCount(gamesWon);

        // Check if match is won
        if (gamesWon == 2) {
            processMatchOver(winnerSeat, game, gameBoard, emailSender, boardHeader);
            return;
        }

        StringBuilder subject = new StringBuilder("PBEMGS - TRIAD Game ID ").append(game.getGameId());
        subject.append(" subgame # ").append(game.getCurrentSubgame()).append(" has ended.  ");

        // Send first email with end of game and ending board state.
        sendBoardStateEmail(emailSender, subject.toString(), boardHeader + "\nFinal Board:\n\n", game, gameBoard, null);

        // Now, update to the next in the set.
        game.setCurrentSubgame(game.getCurrentSubgame() + 1);
        gameBoard.initializeNewBoard(game.getOptionElemental());
        game.setBoardState(gameBoard.serialize());

        // Game 2: clear cards in hand, set starting player to opposite of first game
        if (game.getCurrentSubgame() == 2) {
            playerList.get(0).setCardsInHand("");
            playerList.get(1).setCardsInHand("");
            game.setGamePhase(TriadGamesGamePhase.HAND_SELECTION);
        } else {
            // Game 3: only 5 cards left, so no hand selection phase needed
            playerList.get(0).setCardsInHand(playerList.get(0).getUndraftedCards());
            playerList.get(0).setUndraftedCards("");
            playerList.get(1).setCardsInHand(playerList.get(1).getUndraftedCards());
            playerList.get(1).setUndraftedCards("");
            game.setCurrentActionUserid(game.getFirstTurnUserId());
            game.setGamePhase(TriadGamesGamePhase.GAMEPLAY);
        }

        // DB write - game victor, game state, players
        try {
            dslContext.transaction(configuration -> {
                DSLContext trx = DSL.using(configuration);
                TriadPlayersDKO trxPlayersDKO = new TriadPlayersDKO(trx);
                TriadGamesDKO trxGamesDKO = new TriadGamesDKO(trx);
                TriadGameVictorsDKO trxGameVictorDKO = new TriadGameVictorsDKO(trx);
                trxGamesDKO.updateGame(game);
                trxPlayersDKO.updatePlayerRecord(playerList.get(0));
                trxPlayersDKO.updatePlayerRecord(playerList.get(1));
                trxGameVictorDKO.addVictor(game.getGameId(), usersList.get(winnerSeat).getUserId(),
                        game.getCurrentSubgame() - 1, usersList.get(firstPlayerSeat).getUserId());
            });
        } catch (Exception e) {
            logger.log("-- EXCEPTION writing updated game state for " + game.getCurrentSubgame() + ": " + e.getMessage());
            emailSender.sendEmail(usersList.get(winnerSeat).getEmailAddr(), "PBEMGS - MOVE TRIAD " + game.getGameId() + " Failed (internal error)",
                    TextResponseProvider.getExceptionTextBody("triad internal subgame advancement", e.getMessage()));
        }

        // Now send the "new game" email, depending on if this is G2 or G3
        if (game.getCurrentSubgame() == 2) {
            sendHandSelectionEmail(emailSender, "MOVE TRIAD " + game.getGameId() + " - hand selection for game 2!",
                    "", game, gameBoard, null);
        } else {
            sendBoardStateEmail(emailSender, "MOVE TRIAD " + game.getGameId() + " - start of game 3!",
                    "-- Start of game 3 --\n" +
                            "Players have received their remaining cards for the final game.\n\n",
                    game, gameBoard, null);
        }
    }

    /**
     * Process the end of the match
     */
    private void processMatchOver(int winnerSeat, TriadGamesRecord game, TriadCubedBoard gameBoard,
                                  SESEmailSender emailSender, String boardHeader) {
        StringBuilder subject = new StringBuilder("PBEMGS - TRIAD Match ID ").append(game.getGameId());
        subject.append(" has ended!  Winner is: ").append(usersList.get(winnerSeat).getHandle());

        String endingBoardHeader = boardHeader + "\nThis concludes the Triad Cubed match. Congratulations to " +
                usersList.get(winnerSeat).getHandle() + "!\n\n";

        game.setGameState(TriadGamesGameState.COMPLETE);

        // DB write - game victor, match victor, game state, players
        try {
            long winUserId = usersList.get(winnerSeat).getUserId();
            long loseUserId = usersList.get(1 - winnerSeat).getUserId();
            dslContext.transaction(configuration -> {
                DSLContext trx = DSL.using(configuration);
                TriadPlayersDKO trxPlayersDKO = new TriadPlayersDKO(trx);
                TriadGamesDKO trxGamesDKO = new TriadGamesDKO(trx);
                TriadGameVictorsDKO trxGameVictorDKO = new TriadGameVictorsDKO(trx);
                PlayerOutcomesDKO trxPlayerOutcomesDKO = new PlayerOutcomesDKO(trx);
                trxGamesDKO.updateGame(game);
                trxPlayersDKO.updatePlayerRecord(playerList.get(0));
                trxPlayersDKO.updatePlayerRecord(playerList.get(1));
                trxGameVictorDKO.addVictor(game.getGameId(), usersList.get(winnerSeat).getUserId(),
                        game.getCurrentSubgame(), getFirstPlayerSeatForGame(game));
                trxPlayerOutcomesDKO.insertOutcome(GameType.TRIAD, game.getGameId(), winUserId,
                        PlayerOutcomesOutcome.WIN, null, winUserId == game.getFirstTurnUserId());
                trxPlayerOutcomesDKO.insertOutcome(GameType.TRIAD, game.getGameId(), loseUserId,
                        PlayerOutcomesOutcome.WIN, null, loseUserId == game.getFirstTurnUserId());
            });
        } catch (Exception e) {
            logger.log("-- EXCEPTION writing updated game state for " + game.getCurrentSubgame() + ": " + e.getMessage());
            emailSender.sendEmail(usersList.get(winnerSeat).getEmailAddr(), "PBEMGS - MOVE TRIAD " + game.getGameId() + " Failed (internal error)",
                    TextResponseProvider.getExceptionTextBody("Triad match end processing", e.getMessage()));
            return;
        }

        // Send email with end of match and ending board state.
        sendBoardStateEmail(emailSender, subject.toString(), endingBoardHeader + "\nFinal Board:\n\n", game, gameBoard, null);
    }

    private String validateOptions(Map<String, String> options) {
        Set<String> validOptVals = Set.of("yes", "no", "true", "false", "on", "off");
        for (String optName : options.keySet()) {
            if (optName.equalsIgnoreCase("open") || optName.equalsIgnoreCase("element")) {
                if (!validOptVals.contains(options.get(optName))) {
                    logger.log("-- create failed - invalid option value for " + optName + " of " + options.get(optName));
                    return "Invalid option value for " + optName + " of " + options.get(optName);
                }
            } else {
                logger.log("-- create failed - invalid option name " + optName);
                return "Invalid option name of " + optName;
            }
        }
        return null;
    }

    private List<Integer> deserializeCardIds(String cardList) {
        if (cardList == null || cardList.isBlank()) {
            return new ArrayList<>();
        }

        return Arrays.stream(cardList.split(","))
                .map(String::trim)
                .map(Integer::parseInt)
                .toList();
    }

    private String serializeCardIds(List<Integer> cardIds) {
        return cardIds.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    private Map<Integer, Integer> getPlayerHandMap(TriadPlayersRecord triadPlayersRecord) {
        Map<Integer, Integer> idBySlot = new HashMap<>();
        String cardStr = triadPlayersRecord.getCardsInHand();
        List<Integer> cardIds = deserializeCardIds(cardStr);
        for (int i = 0; i < cardIds.size(); ++i) {
            idBySlot.put(i + 1, cardIds.get(i));
        }
        return idBySlot;
    }

    private Map<String, Integer> getPlayerHandMapByName(TriadPlayersRecord triadPlayersRecord) {
        Map<String, Integer> idByName = new HashMap<>();
        String cardStr = triadPlayersRecord.getCardsInHand();
        List<Integer> cardIds = deserializeCardIds(cardStr);
        for (int i = 0; i < cardIds.size(); ++i) {
            int cardId = cardIds.get(i);
            idByName.put(TriadCardSet.getById(cardId).name().toLowerCase(), cardId);
        }
        return idByName;
    }

    // email body retrieval and parsing.  Separate functions per phase as the format is distinct.

    // Main GAMEPLAY parse - this is simply slot (or name) and location.
    private TextBodyParseResult parseMoveFromEmail(S3Email email) {
        try {
            String text = email.getEmailBodyText(logger);
            if (text.isEmpty()) {
                return new TextBodyParseResult(null, false, "No move detected in email body.");
            }

            // Split on spaces, commas, colons, dashes
            String[] tokens = text.split("[\\s,:-]+");

            // First token can be numeric (slot) or string (name).
            // Second token is a location.
            if (tokens.length < 2) {
                return new TextBodyParseResult(null, false, "Invalid move format.");
            }
            Location loc = Location.fromString(tokens[1]);
            if (loc == null) {
                return new TextBodyParseResult(null, false, "Cannot parse a square name from " + tokens[1]);
            }

            try {
                Integer slot = Integer.parseInt(tokens[0]);
                return new TextBodyParseResult(new TriadMove(slot, null, loc), true, null);
            } catch (NumberFormatException e) {
                String name = tokens[0].toLowerCase();
                TriadCard card = TriadCardSet.getByName(name);
                if (card == null) {
                    return new TextBodyParseResult(null, false, "Card name given does not exist!");
                } else {
                    return new TextBodyParseResult(new TriadMove(null, name, loc), true, null);
                }
            }
        } catch (Exception e) {
            logger.log("Error parsing move from email: " + e.getMessage());
            return new TextBodyParseResult(null, false, "Internal error while parsing move.");
        }
    }

    // HAND_SELECTION parser - 5 tokens, card IDs with strings converted to ids.  Check for duplicates.
    private TextBodyParseHandSelectResult parseHandSelectionMoveFromEmail(S3Email email) {
        try {
            String text = email.getEmailBodyText(logger);
            if (text.isEmpty()) {
                return new TextBodyParseHandSelectResult(null, false, "No move detected in email body.");
            }

            // Split on spaces, commas
            String[] tokens = text.split("[\\s,]+");
            if (tokens.length < 5) {
                return new TextBodyParseHandSelectResult(null, false, "Must select 5 cards.");
            }

            Set<Integer> cardIds = new HashSet<>();
            StringBuilder errors = new StringBuilder("Parse Errors: ");
            boolean hasError = false;
            for (int i = 0; i < 5; ++i) {
                try {
                    Integer cardId = Integer.parseInt(tokens[i]);
                    TriadCard card = TriadCardSet.getById(cardId);
                    if (card == null) {
                        hasError = true;
                        errors.append("Card ID ").append(cardId).append(" does not exist (range is 1-15).\n");
                    } else if (cardIds.contains(cardId)) {
                        hasError = true;
                        errors.append("Card ID ").append(cardId).append(" is duplicated.\n");
                    } else {
                        cardIds.add(cardId);
                    }
                } catch (NumberFormatException e) {
                    String name = tokens[i];
                    TriadCard card = TriadCardSet.getByName(name);
                    if (card == null) {
                        hasError = true;
                        errors.append("Card Name ").append(name).append(" is not the name of a card.");
                    } else if (cardIds.contains(card.cardId())) {
                        hasError = true;
                        errors.append("Card Name ").append(name).append(" is duplicated.");
                    } else {
                        cardIds.add(card.cardId());
                    }
                }
            } // end for (checking first 5 tokens)

            if (hasError) {
                return new TextBodyParseHandSelectResult(null, false, errors.toString());
            }
            return new TextBodyParseHandSelectResult(cardIds.stream().sorted().toList(), true, null);
        } catch (Exception e) {
            logger.log("Error parsing move from email: " + e.getMessage());
            return new TextBodyParseHandSelectResult(null, false, "Internal error while parsing hand selection.");
        }
    }

    private void populatePlayerMap(TriadGamesRecord game) {
        usersList.clear();
        playerList = triadPlayersDKO.getPlayersForGame(game.getGameId());
        for (TriadPlayersRecord p : playerList) {
            usersList.add(usersDKO.fetchUserById(p.getUserId()));
        }
    }

    private int getFirstPlayerSeatForGame(TriadGamesRecord game) {
        int firstGameFirstSeat = usersList.get(0).getUserId() == game.getFirstTurnUserId() ? 0 : 1;
        if (game.getCurrentSubgame() == 2) {
            return 1 - firstGameFirstSeat;
        } else {
            return firstGameFirstSeat;
        }
    }

    //
    private void sendBoardStateEmail(SESEmailSender emailSender, String subject, String header,
                                     TriadGamesRecord game, TriadCubedBoard gameBoard, Long toUserId) {
        String gameHeader = getGameHeaderString(game.getGameId());  // Match header

        // Player headers
        String p0Header = getPlayerHeaderText(0, game, gameBoard);
        String p1Header = getPlayerHeaderText(1, game, gameBoard);

        String gameBoardTextBody = gameBoard.getBoardTextBody();  // Board text

        // Hand Display
        String p0Hand = getPlayerHandText(0, game.getOptionElemental());
        String p1Hand = getPlayerHandText(1, game.getOptionElemental());

        if (toUserId == null || toUserId == usersList.get(0).getUserId()) {
            emailSender.sendEmail(usersList.get(0).getEmailAddr(), subject,
                    gameHeader + p0Header + p1Header + "\n" + header + gameBoardTextBody +
                            TriadCubedTextResponseProvider.getHandDisplay(p0Hand, p1Hand, game.getOptionFaceup()));
        }
        if (toUserId == null || toUserId == usersList.get(1).getUserId()) {
            emailSender.sendEmail(usersList.get(1).getEmailAddr(), subject,
                    gameHeader + p1Header + p0Header + "\n" + header + gameBoardTextBody +
                            TriadCubedTextResponseProvider.getHandDisplay(p1Hand, p0Hand, game.getOptionFaceup()));
        }
    }

    private void sendHandSelectionEmail(SESEmailSender emailSender, String subject, String header,
                                        TriadGamesRecord game, TriadCubedBoard gameBoard, Long toUserId) {
        String gameHeader = getGameHeaderString(game.getGameId());  // match header
        String boardElements = gameBoard.getBoardElementString();  // Board element composition, if any
        String firstPlayerStr = usersList.get(getFirstPlayerSeatForGame(game)).getHandle() + " will play the first card.\n\n";

        if (toUserId == null || toUserId == usersList.get(0).getUserId()) {
            String p0Hand = getPlayerHandText(0, game.getOptionElemental());
            String p0Undrafted = getPlayerUndraftedText(0, game.getOptionElemental());

            emailSender.sendEmail(usersList.get(0).getEmailAddr(), subject,
                    gameHeader + header + boardElements + firstPlayerStr +
                            TriadCubedTextResponseProvider.getHandSelectionDisplay(p0Hand, p0Undrafted));
        }
        if (toUserId == null || toUserId == usersList.get(1).getUserId()) {
            String p1Hand = getPlayerHandText(1, game.getOptionElemental());
            String p1Undrafted = getPlayerUndraftedText(1, game.getOptionElemental());

            emailSender.sendEmail(usersList.get(1).getEmailAddr(), subject,
                    gameHeader + header + boardElements + firstPlayerStr +
                            TriadCubedTextResponseProvider.getHandSelectionDisplay(p1Hand, p1Undrafted));
        }
    }

    // Text formatting helpers
    private String getGameHeaderString(long gameId) {
        return "Triad Cubed Match ID: " + gameId + "\n\n" +
                "Game Score: " + usersList.get(0).getHandle() + " " + playerList.get(0).getSubgameCount() +
                " - " + playerList.get(1).getSubgameCount() + " " + usersList.get(1).getHandle() + "\n\n";
    }

    private String getPlayerHeaderText(int playerSeat, TriadGamesRecord game, TriadCubedBoard gameBoard) {
        int cardCount = gameBoard.getCardCount(playerSeat);
        boolean toMove = usersList.get(playerSeat).getUserId().equals(game.getCurrentActionUserid());
        return TriadCubedTextResponseProvider.getPlayerHeader(TriadCubedBoard.COLOR.get(playerSeat),
                usersList.get(playerSeat).getHandle(), cardCount, toMove);
    }

    private String getPlayerHandText(int playerSlot, boolean elementOn) {
        String cardStr = playerList.get(playerSlot).getCardsInHand();
        if (cardStr.isEmpty()) {
            return "";
        }
        List<Integer> cardIds = deserializeCardIds(cardStr);
        return generateCardRowDisplay(cardIds, false, elementOn) + "\n";
    }

    private String getPlayerUndraftedText(int playerSlot, boolean elementOn) {
        String cardStr = playerList.get(playerSlot).getUndraftedCards();
        if (cardStr.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();

        List<Integer> cardIds = deserializeCardIds(cardStr);
        for (int i = 0; i < cardIds.size(); i += 5) {
            sb.append(generateCardRowDisplay(cardIds.subList(i, i + 5), true, elementOn));
            sb.append("\n\n");
        }

        return sb.toString();
    }

    // Cards display - 1 row of cards, with the slot # and name underneath
    // rows 0 to 4 are the card display rows, 5 is the slot or ID #s, and 6 is the names.
    // if elemental is set, add a row for the elements.
    private static final int NAME_WIDTH = 7;

    private String generateCardRowDisplay(List<Integer> cardIds, boolean writeId, boolean elementOn) {
        StringBuilder sb = new StringBuilder();
        List<TriadCard> cards = cardIds.stream().map(TriadCardSet::getById).toList();

        for (int r = 0; r < 7; ++r) {
            for (int c = 0; c < cards.size(); ++c) {
                if (r == 0) {
                    sb.append(" ").append(TOP_BORDER).append(" ");
                }
                if (r == 1) {
                    sb.append(" ").append(MonoSymbol.GRID_VERTICAL.getSymbol()).append(" ");
                    sb.append(cards.get(c).valueOfSide(Direction.NORTH)).append(" ");
                    sb.append(MonoSymbol.GRID_VERTICAL.getSymbol()).append(" ");
                }
                if (r == 2) {
                    sb.append(" ").append(MonoSymbol.GRID_VERTICAL.getSymbol());
                    sb.append(cards.get(c).valueOfSide(Direction.WEST)).append(" ");
                    sb.append(cards.get(c).valueOfSide(Direction.EAST)).append(MonoSymbol.GRID_VERTICAL.getSymbol()).append(" ");
                }
                if (r == 3) {
                    sb.append(" ").append(MonoSymbol.GRID_VERTICAL.getSymbol()).append(" ");
                    sb.append(cards.get(c).valueOfSide(Direction.SOUTH)).append(" ");
                    sb.append(MonoSymbol.GRID_VERTICAL.getSymbol()).append(" ");
                }
                if (r == 4) {
                    sb.append(" ").append(BOT_BORDER).append(" ");
                }
                if (r == 5) {
                    if (writeId) {
                        sb.append("  ").append(String.format("%2d", cards.get(c).cardId())).append("   ");
                    } else {
                        sb.append("   ").append(c + 1).append("   ");
                    }
                }
                if (r == 6) {
                    String name = cards.get(c).name();
                    int padding = (NAME_WIDTH - name.length()) / 2;
                    String centered = " ".repeat(padding) + name + " ".repeat(NAME_WIDTH - name.length() - padding);
                    sb.append(centered);
                }
                sb.append(" ");
            }
            sb.append("\n");
        }  // end for (text rows)
        if (elementOn) {
            for (TriadCard card : cards) {
                String name = card.element().getDisplayStr();
                int padding = (NAME_WIDTH - name.length()) / 2;
                String centered = " ".repeat(padding) + name + " ".repeat(NAME_WIDTH - name.length() - padding);
                sb.append(centered).append(" ");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

}
