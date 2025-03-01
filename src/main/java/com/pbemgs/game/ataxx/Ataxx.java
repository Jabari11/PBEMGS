package com.pbemgs.game.ataxx;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.pbemgs.controller.SESEmailSender;
import com.pbemgs.dko.AtaxxGameDKO;
import com.pbemgs.dko.AtaxxVictorsDKO;
import com.pbemgs.dko.UsersDKO;
import com.pbemgs.game.GameInterface;
import com.pbemgs.game.GameMessageMailer;
import com.pbemgs.game.OptionParser;
import com.pbemgs.generated.enums.AtaxxGamesBoardOption;
import com.pbemgs.generated.enums.AtaxxGamesGameState;
import com.pbemgs.generated.tables.records.AtaxxGamesRecord;
import com.pbemgs.generated.tables.records.UsersRecord;
import com.pbemgs.model.Location;
import com.pbemgs.model.S3Email;
import org.jooq.DSLContext;
import software.amazon.awssdk.utils.Pair;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Ataxx game controller - handles the commands from the system for this game.  The board functionality
 * is in AtaxxBoard, and the text strings are mostly in the text response provider.
 * Terminology notes:
 * - A "player slot" is the slot in the join order (0, 1) or (0-3) - this also gives their piece
 *   representation.  Turn order is independent though.
 */
public class Ataxx implements GameInterface {

    private static final String GAME_NAME = "ataxx";
    private static final int PLAYER_GAME_LIMIT = 5;
    private static final int MAX_OPEN_GAMES = 15;
    private final Duration REMINDER_DURATION = Duration.ofHours(24);
    private final Duration TIMEOUT_DURATION = Duration.ofHours(72);


    private final AtaxxGameDKO ataxxDKO;
    private final AtaxxVictorsDKO ataxxVictorDKO;
    private final UsersDKO usersDKO;
    private final LambdaLogger logger;

    private record TextBodyParseResult(Location from, Location to, boolean success, String error) {}

    public Ataxx(DSLContext dslContext, LambdaLogger logger) {
        ataxxDKO = new AtaxxGameDKO(dslContext);
        ataxxVictorDKO = new AtaxxVictorsDKO(dslContext);
        usersDKO = new UsersDKO(dslContext);
        this.logger = logger;
    }

    @Override
    public void processCreateGame(UsersRecord user, S3Email email, SESEmailSender emailSender) {
        List<AtaxxGamesRecord> userGames = ataxxDKO.getActiveGamesForUser(user.getUserId());
        if (userGames.size() >= PLAYER_GAME_LIMIT) {
            GameMessageMailer.gameLimitReached(emailSender, user.getEmailAddr(), "create_game", GAME_NAME);
            return;
        }

        List<AtaxxGamesRecord> openGames = ataxxDKO.getOpenGames();
        if (openGames.size() >= MAX_OPEN_GAMES) {
            GameMessageMailer.openGamesLimitReached(emailSender, user.getEmailAddr(), GAME_NAME);
            return;
        }

        try {
            Map<String, String> options = OptionParser.parseOptions(email.getEmailBodyText(logger));
            logger.log("-- Options read: " + options.toString());
            boolean validOptions = validateOptions(options);
            if (!validOptions) {
                logger.log("Game creation failed: Invalid options.");
                emailSender.sendEmail(user.getEmailAddr(), "PBEMGS - CREATE_GAME ATAXX failed (options)",
                        AtaxxTextResponseProvider.getBadOptionsText());
                return;
            }

            // Validation catches exceptions on these conversions.
            int players = Integer.parseInt(options.get("players"));
            int boardSize = Integer.parseInt(options.get("size"));
            AtaxxGamesBoardOption boardType = AtaxxGamesBoardOption.valueOf(options.get("board").toUpperCase());

            // Create: Initial board, turn order
            AtaxxBoard newBoard = new AtaxxBoard(boardSize, logger);
            newBoard.createInitialBoard(players, boardType);
            String turnOrder = generateTurnOrderString(players);
            long gameId = ataxxDKO.createNewGame(user.getUserId(), players, turnOrder, boardSize, newBoard.serialize(), boardType);
            GameMessageMailer.createSuccess(emailSender, user.getEmailAddr(), GAME_NAME, gameId);
            return;
        } catch (Exception e) {
            logger.log("Exception parsing options from create_game text body: " + e.getMessage());
            emailSender.sendEmail(user.getEmailAddr(), "PBEMGS - CREATE_GAME ATAXX failed",
                    "Exception parsing options - please send feedback with the following error: " + e.getMessage());
            return;
        }
    }

    @Override
    public void processJoinGame(UsersRecord user, long gameId, SESEmailSender emailSender) {
        List<AtaxxGamesRecord> userGames = ataxxDKO.getActiveGamesForUser(user.getUserId());
        if (userGames.size() >= PLAYER_GAME_LIMIT) {
            GameMessageMailer.gameLimitReached(emailSender, user.getEmailAddr(), "join_game", GAME_NAME);
            return;
        }
        AtaxxGamesRecord requestGame = ataxxDKO.getGameById(gameId);

        if (requestGame == null || requestGame.getGameState() != AtaxxGamesGameState.OPEN) {
            GameMessageMailer.joinNonopenGame(emailSender, user.getEmailAddr(), GAME_NAME, gameId);
            return;
        }

        if (Objects.equals(requestGame.getPlayer0Id(), user.getUserId()) ||
            Objects.equals(requestGame.getPlayer1Id(), user.getUserId()) ||
            Objects.equals(requestGame.getPlayer2Id(), user.getUserId())) {
            GameMessageMailer.joinSelf(emailSender, user.getEmailAddr(), GAME_NAME);
            return;
        }

        List<UsersRecord> playerList = getPlayerList(requestGame);
        playerList.add(user);
        boolean starting = playerList.size() == requestGame.getNumPlayers();
        if (starting) {
            List<Integer> turnOrder = parseTurnOrderString(requestGame.getTurnOrder());
            int firstTurn = turnOrder.get(0);
            requestGame.setPlayerIdToMove(playerList.get(firstTurn).getUserId());
            if (requestGame.getNumPlayers() == 2) {
                ataxxDKO.completeGameCreation2P(gameId, user.getUserId(), playerList.get(firstTurn).getUserId());
            } else {
                ataxxDKO.completeGameCreation4P(gameId, user.getUserId(), playerList.get(firstTurn).getUserId());
            }

            // Send "game created" email with board to all players.
            AtaxxBoard board = new AtaxxBoard(requestGame.getBoardSize(), logger);
            board.deserialize(requestGame.getBoardState());
            sendGameStateEmail(requestGame, board, emailSender, "MOVE ATAXX " + gameId + " - GAME START!",
                    playerList.get(firstTurn).getHandle() + " has the first move.\n\n", playerList, turnOrder);
        } else {
            ataxxDKO.addPlayerToGame(gameId, user.getUserId());
            emailSender.sendEmail(user.getEmailAddr(), "PBEMGS - JOIN_GAME ATAXX command successful!",
                    AtaxxTextResponseProvider.getJoinSuccessText(gameId));
        }
    }

    @Override
    public void processMove(UsersRecord user, long gameId, S3Email email, SESEmailSender emailSender) {
        AtaxxGamesRecord game = ataxxDKO.getGameById(gameId);

        if (game == null || game.getGameState() != AtaxxGamesGameState.IN_PROGRESS) {
            GameMessageMailer.moveGameNotValid(emailSender, user.getEmailAddr(), GAME_NAME, gameId);
            return;
        }
        if (!Objects.equals(game.getPlayerIdToMove(), user.getUserId())) {
            GameMessageMailer.getMoveNotActiveText(emailSender, user.getEmailAddr(), GAME_NAME, gameId);
            return;
        }

        TextBodyParseResult move = parseMoveFromEmail(email);
        if (!move.success()) {
            emailSender.sendEmail(user.getEmailAddr(), "PBEMGS - MOVE ATAXX failed",
                    AtaxxTextResponseProvider.getMoveFailedParseText(gameId, move.error()));
            return;
        }

        AtaxxBoard gameBoard = new AtaxxBoard(game.getBoardSize(), logger);
        gameBoard.deserialize(game.getBoardState());
        int movingPlayerSlot = getUserPlayerSlot(user, game);

        AtaxxBoard.MoveResult moveResult = move.from() == null ?
                gameBoard.processMove(movingPlayerSlot, move.to()) : gameBoard.processMove(movingPlayerSlot, move.from(), move.to());

        if (!moveResult.success()) {
            emailSender.sendEmail(user.getEmailAddr(), "PBEMGS - MOVE ATAXX failed",
                    AtaxxTextResponseProvider.getIllegalMoveText(gameId, moveResult.errorMsg()));
            return;
        }

        String moveString = user.getHandle() + " moved: " +
                (move.from() == null ? move.to().toString() + " (clone)" :
                        move.from().toString() + " to " + move.to().toString());

        processPostMove(emailSender, game, gameBoard, moveString, movingPlayerSlot);
    }

    @Override
    public String getOpenGamesTextBody() {
        List<AtaxxGamesRecord> openGames = ataxxDKO.getOpenGames();
        if (openGames.isEmpty()) {
            return AtaxxTextResponseProvider.getNoOpenGamesText();
        }
        Set<Long> playerIds = openGames.stream()
                .flatMap(game -> Stream.of(game.getPlayer0Id(), game.getPlayer1Id(), game.getPlayer2Id())) // Gather all player IDs
                .filter(Objects::nonNull) // Remove nulls
                .collect(Collectors.toSet()); // Collect into a set (removes duplicates)
        Map<Long, UsersRecord> usersById = usersDKO.fetchUsersByIds(playerIds);
        StringBuilder sb = new StringBuilder();
        sb.append(AtaxxTextResponseProvider.getOpenGamesHeaderText(openGames.size()));
        for (AtaxxGamesRecord game : openGames) {
            List<UsersRecord> thisGameUsers = getPlayerList(game);
            sb.append(AtaxxTextResponseProvider.getOpenGameDescription(game, thisGameUsers));
        }
        return sb.toString();
    }

    @Override
    public String getRulesTextBody() {
        return AtaxxTextResponseProvider.getAtaxxRulesText();
    }

    @Override
    public String getMyGamesTextBody(long userId) {
        StringBuilder sb = new StringBuilder();
        sb.append("ATAXX:\n");
        List<AtaxxGamesRecord> myGames = ataxxDKO.getActiveGamesForUser(userId);
        if (myGames.isEmpty()) {
            sb.append(" - No active Ataxx games.");
        }
        for (AtaxxGamesRecord game : myGames) {
            sb.append("-- Game ID: ").append(game.getGameId());
            if (game.getGameState() == AtaxxGamesGameState.OPEN) {
                sb.append(" is waiting for opponent(s).\n");
            } else {
                sb.append(" is in progress - ");
                sb.append(game.getPlayerIdToMove() == userId ? "YOUR TURN!\n" : "opponent's turn.\n");
            }
        }
        return sb.toString();
    }

    @Override
    public void processStatus(UsersRecord user, long gameId, SESEmailSender emailSender) {
        // Validate part of game
        AtaxxGamesRecord reqGame = ataxxDKO.getGameById(gameId);
        if (reqGame == null || reqGame.getGameState() == AtaxxGamesGameState.OPEN ||
                reqGame.getGameState() == AtaxxGamesGameState.ABANDONED) {
            emailSender.sendEmail(user.getEmailAddr(), "PBEMGS - STATUS command failed",
                    AtaxxTextResponseProvider.getStatusFailedNoGameText(gameId));
            return;
        }
        if (!Objects.equals(user.getUserId(), reqGame.getPlayer0Id()) &&
                !Objects.equals(user.getUserId(), reqGame.getPlayer1Id()) &&
                !Objects.equals(user.getUserId(), reqGame.getPlayer2Id()) &&
                !Objects.equals(user.getUserId(), reqGame.getPlayer3Id())) {
            emailSender.sendEmail(user.getEmailAddr(), "PBEMGS - STATUS command failed",
                    AtaxxTextResponseProvider.getStatusFailedNotYourGameText(gameId));
            return;
        }

        AtaxxBoard board = new AtaxxBoard(reqGame.getBoardSize(), logger);
        board.deserialize(reqGame.getBoardState());
        List<UsersRecord> playerList = getPlayerList(reqGame);
        int playerSlot = getUserPlayerSlot(user, reqGame);

        sendGameStateEmail(reqGame, board, emailSender, "PBEMGS - ATAXX Game Status for Game ID " + gameId,
                "", playerList, List.of(playerSlot));
    }

    @Override
    public Map<Long, String> processStaleGameCheck(SESEmailSender emailSender) {
        Map<Long, String> staleStringByUserId = new HashMap<>();
        List<AtaxxGamesRecord> activeGames = ataxxDKO.getActiveGames();
        if (activeGames == null || activeGames.isEmpty()) {
            return staleStringByUserId;
        }

        LocalDateTime currTime = LocalDateTime.now();
        for (AtaxxGamesRecord game : activeGames) {
            LocalDateTime lastMoveTime = game.getLastMoveTimestamp();

            if (Duration.between(lastMoveTime, currTime).compareTo(TIMEOUT_DURATION) > 0) {
                try {
                    UsersRecord user = usersDKO.fetchUserById(game.getPlayerIdToMove());
                    AtaxxBoard gameBoard = new AtaxxBoard(game.getBoardSize(), logger);

                    // Get a random move from the board and process it.
                    int movingPlayerSlot = getUserPlayerSlot(user, game);

                    Pair<Location, Location> randomMove = gameBoard.generateRandomMove(movingPlayerSlot);
                    AtaxxBoard.MoveResult moveResult = gameBoard.processMove(movingPlayerSlot, randomMove.left(), randomMove.right());

                    if (!moveResult.success()) {
                        logger.log("Bad random move generated for game ID " + game.getGameId() + " - " +
                                randomMove.left() + " -> " + randomMove.right());
                        continue;
                    }

                    String moveString = user.getHandle() + " moved: " +
                            randomMove.left().toString() + " to " + randomMove.right().toString();

                    processPostMove(emailSender, game, gameBoard, moveString, movingPlayerSlot);

                    logger.log("Time-out random move made for ATAXX Game ID " + game.getGameId());
                } catch (Exception e) {
                    logger.log("Exception while attempting to pass turn: " + e.getMessage());
                    // continue processing the rest...
                }
            } else {
                LocalDateTime lastTime = game.getLastReminderTimestamp() == null ?
                        game.getLastMoveTimestamp() : game.getLastReminderTimestamp();
                if (Duration.between(lastTime, currTime).compareTo(REMINDER_DURATION) > 0) {
                    staleStringByUserId.put(game.getPlayerIdToMove(), "ATAXX: Game ID " + game.getGameId());
                }
            }
        }

        // Update reminder timestamps as necessary
        try {
            ataxxDKO.updateReminderTimestamps(staleStringByUserId.keySet(), currTime);
        } catch (Exception e) {
            logger.log("Caught an exception updating reminder timestamps - user ids: " + staleStringByUserId.keySet().toString());
            // don't rethrow or exit - still want to process the email out at the system level
        }

        return staleStringByUserId;
    }

    private void processPostMove(SESEmailSender emailSender, AtaxxGamesRecord game, AtaxxBoard gameBoard, String moveString, int movingPlayerSlot) {
        game.setBoardState(gameBoard.serialize());
        List<UsersRecord> playerList = getPlayerList(game);
        List<Integer> turnOrder = parseTurnOrderString(game.getTurnOrder());

        // Check end of game (no more blank spots)
        if (gameBoard.isBoardFull()) {
            game.setGameState(AtaxxGamesGameState.COMPLETE);

            // Use a TreeMap to store scores -> list of player slots (since multiple can have same score)
            TreeMap<Integer, List<Integer>> scoreToSlots = new TreeMap<>(Collections.reverseOrder());
            for (int p = 0; p < game.getNumPlayers(); ++p) {
                int score = gameBoard.getPieceCount(p);
                scoreToSlots.computeIfAbsent(score, k -> new ArrayList<>()).add(p);
            }

            // Get the highest score entry
            List<Integer> highestScoreSlots = scoreToSlots.firstEntry().getValue();

            // Convert player slots to user IDs and handles
            List<Long> highestScoreUserIds = highestScoreSlots.stream()
                    .map(slot -> playerList.get(slot).getUserId())
                    .toList();
            List<String> highestScoreHandles = highestScoreSlots.stream()
                    .map(slot -> playerList.get(slot).getHandle())
                    .toList();

            boolean drawn = highestScoreUserIds.size() == game.getNumPlayers();
            if (!drawn) {
                for (Long uid : highestScoreUserIds) {
                    ataxxVictorDKO.addVictor(game.getGameId(), uid);
                }
            }
            ataxxDKO.updateGame(game);
            String subjectEnd = drawn ? "Game is a draw!" : "Winner: " + String.join(", ", highestScoreHandles);
            sendGameStateEmail(game, gameBoard, emailSender,
                    "PBEMGS - ATAXX Game # " + game.getGameId() + " is Complete. " + subjectEnd, moveString + "\n\nBoard is full, game is over.\n\n",
                    playerList, turnOrder);
            return;
        }  // end if (end of game)

        // Determine next player in turn order
        int currPlayerIndex = turnOrder.indexOf(movingPlayerSlot);
        List<Integer> skippedSlots = new ArrayList<>();  // slots skipped because no valid move
        while (true) {
            currPlayerIndex = (currPlayerIndex + 1) % turnOrder.size();
            int currPlayerSlot = turnOrder.get(currPlayerIndex);
            if (gameBoard.hasLegalMove(currPlayerSlot)) {
                game.setPlayerIdToMove(playerList.get(currPlayerSlot).getUserId());
                game.setLastMoveTimestamp(LocalDateTime.now());
                ataxxDKO.updateGame(game);

                sendGameStateEmail(game, gameBoard, emailSender, "MOVE ATAXX " + game.getGameId(),
                        moveString + "\n\nIt is your move!\n\n", playerList, List.of(currPlayerSlot));

                // don't send "move accepted" if it comes back to the moving player
                if (movingPlayerSlot != currPlayerSlot) {
                    sendGameStateEmail(game, gameBoard, emailSender, "MOVE ATAXX " + game.getGameId() + " Accepted",
                            moveString + "\n\n", playerList, List.of(movingPlayerSlot));
                }

                // Send email to skipped player(s)
                if (!skippedSlots.isEmpty()) {
                    sendGameStateEmail(game, gameBoard, emailSender, "ATAXX " + game.getGameId() + " - no legal moves",
                            moveString + "\n\nYou have no legal moves and were skipped.\n\n",
                            playerList, skippedSlots);
                }
                return;
            } else {
                skippedSlots.add(currPlayerSlot);
            }
        }
    }

    /** Return a list of the player records, in slot order */
    private List<UsersRecord> getPlayerList(AtaxxGamesRecord game) {
        List<UsersRecord> players = new ArrayList<>();
        try {
            players.add(usersDKO.fetchUserById(game.getPlayer0Id()));
            if (game.getPlayer1Id() != null) {
                players.add(usersDKO.fetchUserById(game.getPlayer1Id()));
            }
            if (game.getPlayer2Id() != null) {
                players.add(usersDKO.fetchUserById(game.getPlayer2Id()));
            }
            if (game.getPlayer3Id() != null) {
                players.add(usersDKO.fetchUserById(game.getPlayer3Id()));
            }
        } catch (Exception e) {
            logger.log("Ataxx::getPlayerList() failed, likely due to a non-existent user record. " + e.getMessage());
            throw e;
        }
        return players;
    }

    private int getUserPlayerSlot(UsersRecord user, AtaxxGamesRecord game) {
        if (Objects.equals(user.getUserId(), game.getPlayer0Id())) {
            return 0;
        }
        if (Objects.equals(user.getUserId(), game.getPlayer1Id())) {
            return 1;
        }
        if (Objects.equals(user.getUserId(), game.getPlayer2Id())) {
            return 2;
        }
        if (Objects.equals(user.getUserId(), game.getPlayer3Id())) {
            return 3;
        }
        logger.log("Ataxx::getUserPlayerSlot failed - user ID " + user.getUserId() + "is not a part of game id " + game.getGameId());
        throw new IllegalArgumentException("User ID not part of game!");
    }

    private boolean validateOptions(Map<String, String> options) {
        if (!options.containsKey("players") || !options.containsKey("size") || !options.containsKey("board")) {
            logger.log("-- create failed, missing required option(s).");
            return false;
        }

        try {
            int players = Integer.parseInt(options.get("players"));
            if (players != 2 && players != 4) {
                logger.log("-- create failed, invalid number of players.");
                return false;
            }
            int boardSize = Integer.parseInt(options.get("size"));
            if (boardSize < 7 || boardSize > 9) {
                logger.log("-- create failed, invalid board size.");
                return false;
            }
        } catch (NumberFormatException e) {
            logger.log("-- create failed, invalid players and/or board size value (not a number).");
            return false;
        }

        String boardType = options.get("board").toUpperCase();
        try {
            AtaxxGamesBoardOption.valueOf(boardType);
        } catch (IllegalArgumentException e) {
            logger.log("-- create failed, invalid board type.");
            return false;
        }

        return true;
    }

    private String generateTurnOrderString(int numPlayers) {
        List<Integer> turnOrder = new ArrayList<>();
        for (int i = 0; i < numPlayers; i++) {
            turnOrder.add(i);
        }

        Collections.shuffle(turnOrder); // Randomize turn order

        return turnOrder.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(",")); // Join into a comma-separated string
    }

    private List<Integer> parseTurnOrderString(String turnOrderString) {
        return Arrays.stream(turnOrderString.split(","))
                .map(Integer::parseInt)
                .collect(Collectors.toList());
    }

    // Move parsing methods.  This converts from 1-based to 0-based ("A2" becomes (0, 1) in the result).
    private TextBodyParseResult parseMoveFromEmail(S3Email email) {
        try {
            String text = email.getEmailBodyText(logger);
            if (text.isEmpty()) {
                return new TextBodyParseResult(null, null, false, "No move detected in email body.");
            }

            // Split on spaces, commas, colons, or dashes
            String[] tokens = text.split("[\\s,:-]+");

            if (tokens.length == 0) {
                return new TextBodyParseResult(null, null, false, "No valid move found.");
            }

            Location from = Location.fromString(tokens[0]);
            if (from == null) {
                return new TextBodyParseResult(null, null, false, "Expected a board location as first word of email text body, received " + tokens[0]);
            }

            if (tokens.length > 1) {
                Location to = Location.fromString(tokens[1]);
                if (to == null) {
                    // Second token parsed invalid.  Can't tell whether this is because it's actually bad
                    // or it was actually a single-token clone move followed by reply-to text (or similar)
                    // Treat it as a clone move, as if it was meant as a 2-square move the square will (likely)
                    // be self-occupied and fail there.
                    return new TextBodyParseResult(null, from, true, null);
                }
                return new TextBodyParseResult(from, to, true, null);
            }

            // only 1 token, return it as the to (clone move)
            return new TextBodyParseResult(null, from, true, null);
        } catch (Exception e) {
            logger.log("Error parsing move from email: " + e.getMessage());
            return new TextBodyParseResult(null, null, false, "Internal error while parsing move.");
        }
    }

    // Email format and sending functionality
    // Entry point: takes the game and list of user ids to send to.
    private void sendGameStateEmail(AtaxxGamesRecord gameRecord, AtaxxBoard board, SESEmailSender emailSender,
                                    String subject, String bodyHeader,
                                    List<UsersRecord> playerList, List<Integer> playerSlotsEmailTo) {

        // get the text of the board itself, same for everyone
        String boardText = board.getBoardTextBody();

        // "Double turn-order list" to run through full set of turns from any start without rotation.
        List<Integer> turnOrder = parseTurnOrderString(gameRecord.getTurnOrder());
        List<Integer> fullTurnOrder = Stream.concat(turnOrder.stream(), turnOrder.stream()).toList();

        for (int playerSlot : playerSlotsEmailTo) {
            int recipientPosition = turnOrder.indexOf(playerSlot);

            if (recipientPosition == -1) {
                logger.log("ERROR: Player slot " + playerSlot + " not found in turn order.");
                continue; // Skip this email
            }

            StringBuilder infoBlock = new StringBuilder();
            for (int plr = 0; plr < gameRecord.getNumPlayers(); ++plr) {
                int slot = fullTurnOrder.get(recipientPosition + plr);  // player position 0-3, matching PlayerXId
                UsersRecord player = playerList.get(slot);
                char symbol = board.getPlayerSymbol(slot);
                int pieceCount = board.getPieceCount(slot);
                boolean isCurrentTurn = Objects.equals(gameRecord.getPlayerIdToMove(), player.getUserId());

                infoBlock.append("'").append(symbol).append("' (").append(String.format("%02d", pieceCount)).append(") - ")
                        .append(player.getHandle());

                if (isCurrentTurn) {
                    infoBlock.append(" -- TO MOVE");
                }
                infoBlock.append("\n");
            }
            infoBlock.append("\n");

            emailSender.sendEmail(playerList.get(playerSlot).getEmailAddr(), subject,
                    bodyHeader + infoBlock.toString() + boardText);
        }
    }

}
