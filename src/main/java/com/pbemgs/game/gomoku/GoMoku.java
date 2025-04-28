package com.pbemgs.game.gomoku;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.pbemgs.controller.SESEmailSender;
import com.pbemgs.dko.GoMokuGameDKO;
import com.pbemgs.dko.PlayerOutcomesDKO;
import com.pbemgs.dko.UsersDKO;
import com.pbemgs.game.GameInterface;
import com.pbemgs.game.GameMessageMailer;
import com.pbemgs.generated.enums.GomokuGamesGameState;
import com.pbemgs.generated.enums.GomokuGamesSwap2State;
import com.pbemgs.generated.enums.PlayerOutcomesOutcome;
import com.pbemgs.generated.tables.records.GomokuGamesRecord;
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
import java.util.stream.Collectors;

public class GoMoku implements GameInterface {
    private static final int PLAYER_GAME_LIMIT = 3;
    private static final int MAX_OPEN_GAMES = 8;
    private static final int BOARD_SIZE = 15;  // static for now, maybe add configuration later
    private final Duration REMINDER_DURATION = Duration.ofHours(24);
    private final Duration TIMEOUT_DURATION = Duration.ofHours(72);

    private final DSLContext dslContext;
    private final GoMokuGameDKO goMokuGameDKO;
    private final UsersDKO usersDKO;
    private final LambdaLogger logger;

    private final BiMap<UsersRecord, Character> symbolByUser;

    public record GoMokuMove(List<Location> placements, boolean swap, boolean stay) {
    }

    private record TextBodyParseResult(GoMokuMove move, boolean success, String error) {
    }

    public GoMoku(DSLContext dslContext, LambdaLogger logger) {
        this.dslContext = dslContext;
        goMokuGameDKO = new GoMokuGameDKO(dslContext);
        usersDKO = new UsersDKO(dslContext);
        this.logger = logger;
        this.symbolByUser = HashBiMap.create();
    }

    @Override
    public void processCreateGame(UsersRecord user, S3Email email, SESEmailSender emailSender) {
        List<GomokuGamesRecord> userGames = goMokuGameDKO.getActiveGamesForUser(user.getUserId());
        if (userGames.size() >= PLAYER_GAME_LIMIT) {
            GameMessageMailer.gameLimitReached(emailSender, user.getEmailAddr(), "create_game", GameType.GOMOKU);
            return;
        }

        List<GomokuGamesRecord> openGames = goMokuGameDKO.getOpenGames();
        if (openGames.size() >= MAX_OPEN_GAMES) {
            GameMessageMailer.openGamesLimitReached(emailSender, user.getEmailAddr(), GameType.GOMOKU);
            return;
        }

        GoMokuBoard newBoard = new GoMokuBoard(BOARD_SIZE, logger);
        Long newGameNum = goMokuGameDKO.createNewGame(user.getUserId(), newBoard.serialize(), BOARD_SIZE);
        GameMessageMailer.createSuccess(emailSender, user.getEmailAddr(), GameType.GOMOKU, newGameNum);
    }

    @Override
    public void processJoinGame(UsersRecord user, long gameId, SESEmailSender emailSender) {
        List<GomokuGamesRecord> userGames = goMokuGameDKO.getActiveGamesForUser(user.getUserId());
        if (userGames.size() >= PLAYER_GAME_LIMIT) {
            GameMessageMailer.gameLimitReached(emailSender, user.getEmailAddr(), "join_game", GameType.GOMOKU);
            return;
        }
        GomokuGamesRecord game = goMokuGameDKO.getGameById(gameId);

        // Validity checks: game must exist, be in OPEN state, and not have a X-player of self.
        if (game == null || game.getGameState() != GomokuGamesGameState.OPEN) {
            GameMessageMailer.joinNonopenGame(emailSender, user.getEmailAddr(), GameType.GOMOKU, gameId);
            return;
        }
        if (Objects.equals(game.getXUserId(), user.getUserId())) {
            GameMessageMailer.joinAlreadyIn(emailSender, user.getEmailAddr(), GameType.GOMOKU, gameId);
            return;
        }

        // Set player symbols randomly
        UsersRecord createUser = usersDKO.fetchUserById(game.getXUserId());
        Random rng = new Random();
        if (rng.nextBoolean()) {
            goMokuGameDKO.completeGameCreation(gameId, createUser.getUserId(), user.getUserId());
        } else {
            goMokuGameDKO.completeGameCreation(gameId, user.getUserId(), createUser.getUserId());
        }

        // reload game so players are both set.
        game = goMokuGameDKO.getGameById(gameId);
        populatePlayerMap(game);
        GoMokuBoard gameBoard = new GoMokuBoard(BOARD_SIZE, logger);
        gameBoard.deserialize(game.getBoardState());

        sendBoardStateEmail(emailSender, "MOVE GOMOKU " + gameId + " - GAME START!",
                user.getHandle() + " has joined the action - GAME ON!\n\n",
                game, gameBoard, null);
    }

    @Override
    public void processMove(UsersRecord user, long gameId, S3Email emailBody, SESEmailSender emailSender) {
        GomokuGamesRecord game = goMokuGameDKO.getGameById(gameId);

        // Validity checks: gameBoard must exist, be in IN_PROGRESS state, and the user must be active player.
        if (game == null || game.getGameState() != GomokuGamesGameState.IN_PROGRESS) {
            GameMessageMailer.moveGameNotValid(emailSender, user.getEmailAddr(), GameType.GOMOKU, gameId);
            return;
        }
        if (!Objects.equals(game.getUserIdToMove(), user.getUserId())) {
            GameMessageMailer.moveNotActiveText(emailSender, user.getEmailAddr(), GameType.GOMOKU, gameId);
            return;
        }

        populatePlayerMap(game);

        // Parse email body for the move
        TextBodyParseResult parseResult = parseMoveFromEmail(emailBody);
        if (!parseResult.success()) {
            GameMessageMailer.moveFailedToParse(emailSender, user.getEmailAddr(), GameType.GOMOKU, gameId, parseResult.error());
            return;
        }

        GoMokuMove move = parseResult.move();

        // Load gameBoard, verify move is valid
        GoMokuBoard gameBoard = new GoMokuBoard(BOARD_SIZE, logger);
        gameBoard.deserialize(game.getBoardState());

        // Check move vs game swap2 state
        String stateError = validateMoveType(move, game.getSwap2State());
        if (stateError != null) {
            emailSender.sendEmail(user.getEmailAddr(), "PBEMGS - MOVE GOMOKU failed",
                    GoMokuTextResponseProvider.getMoveInvalidForStateText(gameId, stateError));
            return;
        }

        if (move.swap()) {
            processPlayerSwap(game, gameBoard, emailSender);
            return;
        }
        if (move.stay()) {
            processPlayerKeepTFP(game, gameBoard, emailSender);
            return;
        }

        if (move.placements().isEmpty()) {
            emailSender.sendEmail(user.getEmailAddr(), "PBEMGS - MOVE GOMOKU failed",
                    GoMokuTextResponseProvider.getNoMoveFoundText(gameId));
            return;
        }

        for (Location loc : move.placements()) {
            String errorMessage = gameBoard.validateMove(loc);
            if (errorMessage != null) {
                emailSender.sendEmail(user.getEmailAddr(), "PBEMGS - MOVE GOMOKU failed",
                        GoMokuTextResponseProvider.getMoveInvalidText(loc, gameId, errorMessage));
                return;
            }
        }

        executeMove(user, game, gameBoard, move, emailSender);
    }

    private String validateMoveType(GoMokuMove move, GomokuGamesSwap2State swap2State) {
        switch (swap2State) {
            case GAMEPLAY:
                if (move.stay() || move.swap()) {
                    return "Player colors have been set, SWAP or STAY are not allowed.";
                }
                if (move.placements().size() != 1) {
                    return "Expecting a single placement location.";
                }
                break;
            case AWAITING_INITIAL_PLACEMENT:
                if (move.stay() || move.swap() || move.placements().size() != 3) {
                    return "Initial Placement - please select 3 locations (X, X, O).";
                }
                break;
            case AWAITING_TSP_CHOICE:
                // Tentative Second Player options: 1 location (keep O), 2 locations (pass back), or swap.
                if (move.stay()) {
                    return "STAY is not allowed standalone - select 1 placement (O) to stay as O.";
                }
                if (!move.swap() && move.placements().size() > 2) {
                    return "Too many placements.  Select either a single placement to stay as O,\n" +
                            "two placements (O, X) to pass the option back, or SWAP to X.";
                }
                break;
            case AWAITING_TFP_SWAP:
                // 1 location (O), player stay
                if (move.swap()) {
                    return "SWAP is not allowed standalone - select 1 placement (O) to swap to O.";
                }
                if (!move.stay() && move.placements().size() > 1) {
                    return "Too many placements.  Select either a single placement (O) to swap to O,\n" +
                            "or command 'STAY' to stay as X and pass the turn back.";
                }
                break;
        }

        return null;
    }

    // Helper to fully execute a validated move.
    // Player swap is handled separately, multiple-stone placement is handled here.
    private void executeMove(UsersRecord user, GomokuGamesRecord game, GoMokuBoard gameBoard, GoMokuMove move, SESEmailSender emailSender) {
        char playerSymbol = symbolByUser.get(user);
        char oppSymbol = playerSymbol == GoMokuBoard.PLAYER_X ? GoMokuBoard.PLAYER_O : GoMokuBoard.PLAYER_X;

        StringBuilder boardHeader = new StringBuilder();
        switch (game.getSwap2State()) {
            case GAMEPLAY:
                gameBoard.makeMove(symbolByUser.get(user), move.placements().get(0));
                boardHeader.append(user.getHandle()).append(" has placed a stone at ");
                boardHeader.append(move.placements().get(0).toString()).append(".\n\n");
                if (gameBoard.isVictoryCondition(playerSymbol)) {
                    processGameOver(playerSymbol, game, gameBoard, emailSender,
                            boardHeader.toString(), symbolByUser.inverse().get(oppSymbol).getUserId());
                    return;
                }
                if (gameBoard.isBoardFull()) {
                    processGameOver(null, game, gameBoard, emailSender,
                            boardHeader.toString(), symbolByUser.inverse().get(oppSymbol).getUserId());
                    return;
                }
                break;
            case AWAITING_INITIAL_PLACEMENT:
                gameBoard.makeMove(GoMokuBoard.PLAYER_X, move.placements().get(0));
                gameBoard.makeMove(GoMokuBoard.PLAYER_X, move.placements().get(1));
                gameBoard.makeMove(GoMokuBoard.PLAYER_O, move.placements().get(2));
                boardHeader.append(user.getHandle()).append(" has placed the opening stones as follows:\n");
                boardHeader.append("- X at: ").append(move.placements().get(0).toString()).append("\n");
                boardHeader.append("- X at: ").append(move.placements().get(1).toString()).append("\n");
                boardHeader.append("- O at: ").append(move.placements().get(2).toString()).append("\n\n");
                game.setSwap2State(GomokuGamesSwap2State.AWAITING_TSP_CHOICE);
                break;
            case AWAITING_TSP_CHOICE:
                if (move.placements().size() == 1) {
                    gameBoard.makeMove(GoMokuBoard.PLAYER_O, move.placements().get(0));
                    boardHeader.append(user.getHandle()).append(" has confirmed playing O, and placed one stone at ");
                    boardHeader.append(move.placements().get(0).toString()).append(".\n\n");
                    game.setSwap2State(GomokuGamesSwap2State.GAMEPLAY);
                } else {
                    gameBoard.makeMove(GoMokuBoard.PLAYER_O, move.placements().get(0));
                    gameBoard.makeMove(GoMokuBoard.PLAYER_X, move.placements().get(1));
                    boardHeader.append(user.getHandle()).append(" has placed two stones as follows:\n");
                    boardHeader.append("- O at: ").append(move.placements().get(0).toString()).append("\n");
                    boardHeader.append("- X at: ").append(move.placements().get(1).toString()).append("\n");
                    boardHeader.append("Color choice rests with X.\n\n");
                    game.setSwap2State(GomokuGamesSwap2State.AWAITING_TFP_SWAP);
                }
                break;
            case AWAITING_TFP_SWAP:
                // X plays an O stone and swaps colors
                gameBoard.makeMove(GoMokuBoard.PLAYER_O, move.placements().get(0));
                boardHeader.append(user.getHandle()).append(" has swapped colors to O, and placed one stone at ");
                boardHeader.append(move.placements().get(0).toString()).append(".\n\n");
                game.setXUserId(symbolByUser.inverse().get(GoMokuBoard.PLAYER_O).getUserId());
                game.setOUserId(symbolByUser.inverse().get(GoMokuBoard.PLAYER_X).getUserId());
                oppSymbol = GoMokuBoard.PLAYER_X;
                game.setUserIdToMove(game.getOUserId());  // O just moved, post-process with flip back.
                game.setSwap2State(GomokuGamesSwap2State.GAMEPLAY);
                populatePlayerMap(game);  // repopulate after player swap
                break;
        }

        game.setBoardState(gameBoard.serialize());

        // Activate opposing player
        UsersRecord oppUser = symbolByUser.inverse().get(oppSymbol);
        game.setUserIdToMove(oppUser.getUserId());
        game.setLastMoveTimestamp(LocalDateTime.now());
        game.setLastReminderTimestamp(null);

        // update gameBoard state in DB
        goMokuGameDKO.updateGame(game);

        // Send emails out
        sendBoardStateEmail(emailSender, "MOVE GOMOKU " + game.getGameId(),
                boardHeader.toString(), game, gameBoard, null);
    }

    // Process SWAP2 player swap - this is on TSP (tentative O)'s first turn
    private void processPlayerSwap(GomokuGamesRecord game, GoMokuBoard gameBoard, SESEmailSender emailSender) {
        UsersRecord TFP = symbolByUser.inverse().get(GoMokuBoard.PLAYER_X);
        UsersRecord TSP = symbolByUser.inverse().get(GoMokuBoard.PLAYER_O);
        game.setXUserId(TSP.getUserId());
        game.setOUserId(TFP.getUserId());
        game.setUserIdToMove(TFP.getUserId());
        game.setSwap2State(GomokuGamesSwap2State.GAMEPLAY);
        game.setLastMoveTimestamp(LocalDateTime.now());
        game.setLastReminderTimestamp(null);
        goMokuGameDKO.updateGame(game);
        populatePlayerMap(game);  // player swap, update internal map
        String boardHeader = TSP.getHandle() + " has elected to swap colors!\n\n";
        sendBoardStateEmail(emailSender, "MOVE GOMOKU " + game.getGameId(), boardHeader,
                game, gameBoard, null);
    }

    // Process SWAP2 TFP (tentative X) keeping X.  No board state or player change, just active move.
    private void processPlayerKeepTFP(GomokuGamesRecord game, GoMokuBoard gameBoard, SESEmailSender emailSender) {
        UsersRecord TFP = symbolByUser.inverse().get(GoMokuBoard.PLAYER_X);
        UsersRecord TSP = symbolByUser.inverse().get(GoMokuBoard.PLAYER_O);
        game.setUserIdToMove(TSP.getUserId());
        game.setSwap2State(GomokuGamesSwap2State.GAMEPLAY);
        game.setLastMoveTimestamp(LocalDateTime.now());
        game.setLastReminderTimestamp(null);
        goMokuGameDKO.updateGame(game);
        String boardHeader = TFP.getHandle() + " has elected to stay as X - it is now O's move!\n\n";
        sendBoardStateEmail(emailSender, "MOVE GOMOKU " + game.getGameId(), boardHeader,
                game, gameBoard, null);
    }

    @Override
    public String getOpenGamesTextBody() {
        List<GomokuGamesRecord> openGames = goMokuGameDKO.getOpenGames();
        if (openGames.isEmpty()) {
            return GoMokuTextResponseProvider.getNoOpenGamesText();
        }
        Set<Long> creatorUserIds = openGames.stream().map(GomokuGamesRecord::getXUserId).collect(Collectors.toSet());
        Map<Long, UsersRecord> usersById = usersDKO.fetchUsersByIds(creatorUserIds);
        StringBuilder sb = new StringBuilder();
        sb.append(GoMokuTextResponseProvider.getOpenGamesHeaderText(openGames.size()));
        for (GomokuGamesRecord game : openGames) {
            sb.append(GoMokuTextResponseProvider.getOpenGameDescription(game.getGameId(), usersById.get(game.getXUserId())));
        }
        return sb.toString();
    }

    @Override
    public String getRulesTextBody() {
        return GoMokuTextResponseProvider.getGoMokuRulesText();
    }

    @Override
    public String getMyGamesTextBody(long userId) {
        StringBuilder sb = new StringBuilder();
        sb.append("GOMOKU:\n");
        List<GomokuGamesRecord> games = goMokuGameDKO.getActiveGamesForUser(userId);
        for (GomokuGamesRecord game : games) {
            sb.append("-- Game ID: ").append(game.getGameId());
            if (game.getGameState() == GomokuGamesGameState.OPEN) {
                sb.append(" is waiting for an opponent.\n");
            } else {
                sb.append(" is in progress - ");
                sb.append(game.getUserIdToMove() == userId ? "YOUR TURN!\n" : "opponent's turn.\n");
            }
        }
        return sb.toString();
    }

    @Override
    public void processStatus(UsersRecord user, long gameId, SESEmailSender emailSender) {
        GomokuGamesRecord game = goMokuGameDKO.getGameById(gameId);
        if (game == null || game.getGameState() == GomokuGamesGameState.OPEN) {
            GameMessageMailer.statusNotValidGame(emailSender, user.getEmailAddr(), GameType.GOMOKU, gameId);
            return;
        }
        if (user.getUserId() != game.getXUserId() && user.getUserId() != game.getOUserId()) {
            GameMessageMailer.statusNotYourGame(emailSender, user.getEmailAddr(), GameType.GOMOKU, gameId);
            return;
        }

        populatePlayerMap(game);

        String textHeader = "GoMoku Game ID: " + gameId +
                (game.getGameState() == GomokuGamesGameState.IN_PROGRESS ? " - In Progress\n\n" : " - Complete\n\n");
        GoMokuBoard gameBoard = new GoMokuBoard(BOARD_SIZE, logger);
        gameBoard.deserialize(game.getBoardState());
        sendBoardStateEmail(emailSender, "PBEMGS - GOMOKU STATUS for game id " + gameId, textHeader,
                game, gameBoard, user.getUserId());
    }

    @Override
    public Map<Long, String> processStaleGameCheck(SESEmailSender emailSender) {
        Map<Long, String> staleStringByUserId = new HashMap<>();
        List<GomokuGamesRecord> activeGames = goMokuGameDKO.getActiveGames();
        if (activeGames == null || activeGames.isEmpty()) {
            return staleStringByUserId;
        }

        LocalDateTime currTime = LocalDateTime.now();
        for (GomokuGamesRecord game : activeGames) {
            LocalDateTime lastMoveTime = game.getLastMoveTimestamp();

            // TODO: random action if not in GAMEPLAY?  Or just spam reminders?
            if (Duration.between(lastMoveTime, currTime).compareTo(TIMEOUT_DURATION) > 0 &&
                    game.getSwap2State() == GomokuGamesSwap2State.GAMEPLAY) {
                try {
                    populatePlayerMap(game);
                    UsersRecord user = usersDKO.fetchUserById(game.getUserIdToMove());
                    GoMokuBoard gameBoard = new GoMokuBoard(BOARD_SIZE, logger);
                    gameBoard.deserialize(game.getBoardState());
                    GoMokuMove randomMove = new GoMokuMove(List.of(gameBoard.getRandomMove()), false, false);
                    logger.log("Auto-move for GoMoku Game ID " + game.getGameId() + ": selected " + randomMove);
                    executeMove(user, game, gameBoard, randomMove, emailSender);
                } catch (Exception e) {
                    logger.log("Exception while attempting to auto-move: " + e.getMessage());
                    // continue processing the rest...
                }
            } else {
                LocalDateTime lastTime = game.getLastReminderTimestamp() == null ?
                        game.getLastMoveTimestamp() : game.getLastReminderTimestamp();
                if (Duration.between(lastTime, currTime).compareTo(REMINDER_DURATION) > 0) {
                    staleStringByUserId.put(game.getUserIdToMove(), "GoMoku: Game ID " + game.getGameId());
                }
            }
        }

        // Update reminder timestamps as necessary
        try {
            goMokuGameDKO.updateReminderTimestamps(staleStringByUserId.keySet(), currTime);
        } catch (Exception e) {
            logger.log("Caught an exception updating reminder timestamps - user ids: " + staleStringByUserId.keySet().toString());
            // don't rethrow or exit - still want to process the email out at the system level
        }

        return staleStringByUserId;
    }

    private void processGameOver(Character winnerMarker, GomokuGamesRecord game, GoMokuBoard gameBoard,
                                 SESEmailSender emailSender, String boardHeader, Long newActiveUserId) {
        game.setGameState(GomokuGamesGameState.COMPLETE);

        // Activate opposing player (header display only)
        game.setUserIdToMove(newActiveUserId);
        game.setLastMoveTimestamp(LocalDateTime.now());
        game.setLastReminderTimestamp(null);

        StringBuilder subject = new StringBuilder("PBEMGS - GOMOKU Game ID ").append(game.getGameId());
        subject.append(" has ended.  ");
        if (winnerMarker != null) {
            UsersRecord winPlayer = symbolByUser.inverse().get(winnerMarker);
            subject.append("Winner is ").append(winPlayer.getHandle()).append("!");
        } else {
            subject.append("Game is Drawn!");
        }

        // Store to DB and send final email.
        try {
            dslContext.transaction(configuration -> {
                DSLContext trx = DSL.using(configuration);
                GoMokuGameDKO txGomokuDKO = new GoMokuGameDKO(trx);
                PlayerOutcomesDKO txOutcomesDKO = new PlayerOutcomesDKO(trx);
                for (UsersRecord user : symbolByUser.keySet()) {
                    PlayerOutcomesOutcome outcome = (winnerMarker == null ? PlayerOutcomesOutcome.DRAW :
                            (winnerMarker == symbolByUser.get(user) ? PlayerOutcomesOutcome.WIN : PlayerOutcomesOutcome.LOSS));
                    txOutcomesDKO.insertOutcome(GameType.GOMOKU, game.getGameId(), user.getUserId(),
                            outcome, null, null);
                }
                txGomokuDKO.updateGame(game);
            });

            sendBoardStateEmail(emailSender, subject.toString(), boardHeader + "\nFinal Board:\n\n", game, gameBoard, null);
        } catch (Exception e) {
            logger.log("Exception while processing end of GoMoku game: " + e.getMessage());
            throw e;
        }
    }

    // email body retrieval and parsing
    private TextBodyParseResult parseMoveFromEmail(S3Email email) {
        try {
            String text = email.getEmailBodyText(logger);
            if (text.isEmpty()) {
                return new TextBodyParseResult(null, false, "No move detected in email body.");
            }

            // Split on spaces, commas, colons
            String[] tokens = text.split("[\\s,:]+");

            // Check if first token is "SWAP" or "STAY"
            if (tokens[0].equalsIgnoreCase("SWAP")) {
                return new TextBodyParseResult(new GoMokuMove(List.of(), true, false), true, null);
            }
            if (tokens[0].equalsIgnoreCase("STAY")) {
                return new TextBodyParseResult(new GoMokuMove(List.of(), false, true), true, null);
            }

            // check the first (up to) 3 tokens as locations.
            // Stop on parse failure (could be auto-generated reply-to text, etc).  An error on first token
            // will be handled here as a valid parse with zero locations, and handled by processMove().
            List<Location> locs = new ArrayList<>();
            Set<Location> locSet = new HashSet<>();
            for (int x = 0; x < 3; ++x) {
                if (tokens.length > x) {
                    Location loc = Location.fromString(tokens[x]);
                    if (loc == null) {
                        break;
                    }
                    if (locSet.contains(loc)) {
                        return new TextBodyParseResult(null, false, "Duplicate location in placement list.");
                    }
                    locs.add(loc);
                    locSet.add(loc);
                }
            }

            return new TextBodyParseResult(new GoMokuMove(locs, false, false), true, null);
        } catch (Exception e) {
            logger.log("Error parsing move from email: " + e.getMessage());
            return new TextBodyParseResult(null, false, "Internal error while parsing move.");
        }
    }

    private void populatePlayerMap(GomokuGamesRecord game) {
        symbolByUser.clear();
        UsersRecord xPlayer = usersDKO.fetchUserById(game.getXUserId());
        UsersRecord oPlayer = usersDKO.fetchUserById(game.getOUserId());
        symbolByUser.put(xPlayer, GoMokuBoard.PLAYER_X);
        symbolByUser.put(oPlayer, GoMokuBoard.PLAYER_O);
    }

    // Helper methods for formatting and sending outgoing board-state emails.
    private String getXHeaderText(GomokuGamesRecord game, UsersRecord x, UsersRecord o, long playerToMove) {
        return GoMokuTextResponseProvider.getGameHeader(game, "X", x.getHandle(),
                playerToMove == x.getUserId(), "O", o.getHandle());
    }

    private String getOHeaderText(GomokuGamesRecord game, UsersRecord x, UsersRecord o, long playerToMove) {
        return GoMokuTextResponseProvider.getGameHeader(game, "O", o.getHandle(),
                playerToMove == o.getUserId(), "X", x.getHandle());
    }

    private void sendBoardStateEmail(SESEmailSender emailSender, String subject, String header,
                                     GomokuGamesRecord game, GoMokuBoard gameBoard, Long toUserId) {
        UsersRecord xPlayer = symbolByUser.inverse().get(GoMokuBoard.PLAYER_X);
        UsersRecord oPlayer = symbolByUser.inverse().get(GoMokuBoard.PLAYER_O);
        String xGameHeader = getXHeaderText(game, xPlayer, oPlayer, game.getUserIdToMove());
        String oGameHeader = getOHeaderText(game, xPlayer, oPlayer, game.getUserIdToMove());

        String stateHeader = GoMokuTextResponseProvider.getSwap2StateHeader(game.getSwap2State());

        // Generate the board text (used for both)
        String gameBoardTextBody = gameBoard.getBoardTextBody();

        if (toUserId == null || toUserId == xPlayer.getUserId()) {
            emailSender.sendEmail(xPlayer.getEmailAddr(), subject,
                    xGameHeader + header + stateHeader + gameBoardTextBody);
        }
        if (toUserId == null || toUserId == oPlayer.getUserId()) {
            emailSender.sendEmail(oPlayer.getEmailAddr(), subject,
                    oGameHeader + header + stateHeader + gameBoardTextBody);
        }

    }
}
