package com.pbemgs.game.ironclad;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.pbemgs.controller.SESEmailSender;
import com.pbemgs.dko.PlayerOutcomesDKO;
import com.pbemgs.dko.UsersDKO;
import com.pbemgs.game.GameInterface;
import com.pbemgs.game.GameMessageMailer;
import com.pbemgs.game.GameTextUtilities;
import com.pbemgs.game.ironclad.dko.IroncladGameDKO;
import com.pbemgs.generated.enums.IroncladGamesCurrentMovePhase;
import com.pbemgs.generated.enums.IroncladGamesForcedMoveOption;
import com.pbemgs.generated.enums.IroncladGamesGameState;
import com.pbemgs.generated.enums.PlayerOutcomesOutcome;
import com.pbemgs.generated.tables.records.IroncladGamesRecord;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

public class Ironclad implements GameInterface {

    private static final int PLAYER_GAME_LIMIT = 3;
    private static final int MAX_OPEN_GAMES = 10;
    private final Duration REMINDER_DURATION = Duration.ofHours(24);
    private final Duration TIMEOUT_DURATION = Duration.ofHours(72);

    private final DSLContext dslContext;
    private final IroncladGameDKO ironcladGameDKO;
    private final UsersDKO usersDKO;
    private final LambdaLogger logger;
    private final Random rng;

    private final BiMap<UsersRecord, IroncladSide> symbolByUser;

    private enum IroncladMoveType {ROBOT_MOVE, ROBOT_FIRE, STONE_DROP, STONE_MOVE}

    ;

    private record IroncladMove(IroncladMoveType moveType, Location start, Location to) {
    }

    private record TextBodyParseResult(List<IroncladMove> moves, boolean success, String error) {
    }


    public Ironclad(DSLContext dslContext, LambdaLogger logger) {
        this.dslContext = dslContext;
        ironcladGameDKO = new IroncladGameDKO(dslContext);
        usersDKO = new UsersDKO(dslContext);
        this.logger = logger;
        rng = new Random();
        this.symbolByUser = HashBiMap.create();
    }

    @Override
    public void processCreateGame(UsersRecord user, S3Email email, SESEmailSender emailSender) {
        logger.log("IRONCLAD: create_game command from " + user.getEmailAddr());
        List<IroncladGamesRecord> userGames = ironcladGameDKO.getActiveGamesForUser(user.getUserId());
        if (userGames.size() >= PLAYER_GAME_LIMIT) {
            GameMessageMailer.gameLimitReached(emailSender, user.getEmailAddr(), "create_game", GameType.IRONCLAD);
            return;
        }

        List<IroncladGamesRecord> openGames = ironcladGameDKO.getOpenGames();
        if (openGames.size() >= MAX_OPEN_GAMES) {
            GameMessageMailer.openGamesLimitReached(emailSender, user.getEmailAddr(), GameType.IRONCLAD);
            return;
        }

        try {
            Map<String, String> options = GameTextUtilities.parseOptions(email.getEmailBodyText(logger));
            logger.log("-- Options read: " + options.toString());
            String optionValidationError = validateOptions(options);
            if (optionValidationError != null) {
                logger.log("Game creation failed: Invalid options.");
                GameMessageMailer.createOptionsInvalid(emailSender, user.getEmailAddr(), GameType.IRONCLAD, optionValidationError);
                return;
            }
            IroncladGamesForcedMoveOption option = IroncladGamesForcedMoveOption.valueOf(options.get("first").toUpperCase());

            IroncladBoard gameBoard = new IroncladBoard();
            gameBoard.initializeNewBoard();
            Long newGameNum = ironcladGameDKO.createNewGame(user.getUserId(), gameBoard.serializeRobots(),
                    gameBoard.serializeStones(), option);

            GameMessageMailer.createSuccess(emailSender, user.getEmailAddr(), GameType.IRONCLAD, newGameNum);
        } catch (Exception e) {
            logger.log("Exception parsing options from create_game text body: " + e.getMessage());
            emailSender.sendEmail(user.getEmailAddr(), "PBEMGS - CREATE_GAME IRONCLAD failed",
                    "Exception parsing options - please send feedback with the following error: " + e.getMessage());
            return;
        }
    }

    @Override
    public void processJoinGame(UsersRecord user, long gameId, SESEmailSender emailSender) {
        List<IroncladGamesRecord> userGames = ironcladGameDKO.getActiveGamesForUser(user.getUserId());
        if (userGames.size() >= PLAYER_GAME_LIMIT) {
            GameMessageMailer.gameLimitReached(emailSender, user.getEmailAddr(), "join_game", GameType.IRONCLAD);
            return;
        }
        IroncladGamesRecord game = ironcladGameDKO.getGameById(gameId);

        // Validity checks: game must exist, be in OPEN state, and not have a player of self.
        if (game == null || game.getGameState() != IroncladGamesGameState.OPEN) {
            GameMessageMailer.joinNonopenGame(emailSender, user.getEmailAddr(), GameType.IRONCLAD, gameId);
            return;
        }
        if (Objects.equals(game.getWhiteUserId(), user.getUserId())) {
            GameMessageMailer.joinAlreadyIn(emailSender, user.getEmailAddr(), GameType.IRONCLAD, gameId);
            return;
        }

        Long startingUserId = rng.nextBoolean() ? user.getUserId() : game.getWhiteUserId();
        ironcladGameDKO.completeGameCreation(gameId, user.getUserId(), startingUserId);

        // reload game so players are both set.
        game = ironcladGameDKO.getGameById(gameId);
        populatePlayerMap(game);
        IroncladBoard gameBoard = new IroncladBoard();
        gameBoard.deserialize(game.getRobotBoardState(), game.getStoneBoardState());

        sendBoardStateEmail(emailSender, "MOVE IRONCLAD " + gameId + " - GAME START!",
                user.getHandle() + " has joined the action - GAME ON!\n\n",
                game, gameBoard, null);
    }

    @Override
    public void processMove(UsersRecord user, long gameId, S3Email emailBody, SESEmailSender emailSender) {
        IroncladGamesRecord game = ironcladGameDKO.getGameById(gameId);

        // Validity checks: gameBoard must exist, be in IN_PROGRESS state, and the user must be active player.
        if (game == null || game.getGameState() != IroncladGamesGameState.IN_PROGRESS) {
            GameMessageMailer.moveGameNotValid(emailSender, user.getEmailAddr(), GameType.IRONCLAD, gameId);
            return;
        }
        if (!Objects.equals(game.getUserIdToMove(), user.getUserId())) {
            GameMessageMailer.moveNotActiveText(emailSender, user.getEmailAddr(), GameType.IRONCLAD, gameId);
            return;
        }

        // Parse email body for the move
        TextBodyParseResult parseResult = parseMoveFromEmail(emailBody);
        if (!parseResult.success()) {
            GameMessageMailer.moveFailedToParse(emailSender, user.getEmailAddr(), GameType.IRONCLAD, gameId, parseResult.error());
            return;
        }

        List<IroncladMove> moves = parseResult.moves();
        populatePlayerMap(game);
        IroncladSide actorSide = symbolByUser.get(user);

        IroncladBoard gameBoard = new IroncladBoard();
        gameBoard.deserialize(game.getRobotBoardState(), game.getStoneBoardState());

        // Process the moves in order, sending emails and saving state at the end if it/both are successful.
        StringBuilder outheader = new StringBuilder();
        if (game.getHalfMoveText() != null) {
            outheader.append(game.getHalfMoveText());
        }

        boolean done = false;
        boolean halfOnly = true;  // half-move only
        int moveNum = 0;
        while (!done) {
            IroncladMove thisMove = moves.get(moveNum);
            IroncladSide pieceMovedSide = getSideToMove(game, actorSide);

            String moveValidationError = validateMove(pieceMovedSide, thisMove, game, gameBoard, moveNum);
            if (moveValidationError != null) {
                emailSender.sendEmail(user.getEmailAddr(), "PBEMGS - MOVE IRONCLAD " + game.getGameId() + " failed (illegal move)",
                        IroncladTextResponseProvider.getMoveFailedText(game.getGameId(), moveNum, moveValidationError));
                return;
            }

            outheader.append(user.getHandle()).append(": ");
            if (thisMove.moveType() == IroncladMoveType.ROBOT_MOVE) {
                gameBoard.executeRobotMove(thisMove.start(), thisMove.to());
                outheader.append(pieceMovedSide.name()).append(" Robot at ").append(thisMove.start().toString())
                        .append(" moves to ").append(thisMove.to().toString()).append(".\n");
            } else if (thisMove.moveType() == IroncladMoveType.ROBOT_FIRE) {
                String fireResultText = gameBoard.executeRobotFire(thisMove.start(), rng);
                outheader.append(fireResultText).append("\n");
            } else if (thisMove.moveType() == IroncladMoveType.STONE_DROP) {
                gameBoard.executeStoneDrop(pieceMovedSide, thisMove.start());
                outheader.append("A ").append(pieceMovedSide.name()).append(" stone is placed at ")
                        .append(thisMove.start().toString()).append(".\n");
            } else if (thisMove.moveType() == IroncladMoveType.STONE_MOVE) {
                gameBoard.executeStoneMove(thisMove.start(), thisMove.to());
                game.setLastStoneMoved(thisMove.to().toString());
                outheader.append("The stone at ").append(thisMove.start().toString()).append(" is moved to ")
                        .append(thisMove.to().toString()).append(".\n");
            }

            IroncladBoard.VictoryResult winningSide = gameBoard.checkVictoryConditions();
            if (winningSide.side() != null) {
                outheader.append("\n").append(winningSide.message()).append("\n");
                processGameOver(winningSide.side(), game, gameBoard, emailSender, outheader.toString());
                return;
            }

            advanceGamePhase(game, thisMove, actorSide);
            moveNum++;

            if (!game.getUserIdToMove().equals(user.getUserId())) {
                done = true;
                halfOnly = false;
                IroncladSide oppSide = actorSide == IroncladSide.WHITE ? IroncladSide.BLACK : IroncladSide.WHITE;
                IroncladSide nextMoveSide = getSideToMove(game, oppSide);
                checkNoForcedRobotPlay(game, gameBoard, nextMoveSide, outheader);
            } else if (moves.size() - 1 < moveNum) {
                done = true;
            }
        }  // end while (not done)

        game.setRobotBoardState(gameBoard.serializeRobots());
        game.setStoneBoardState(gameBoard.serializeStones());

        if (halfOnly) {
            game.setHalfMoveText(outheader.toString());
            ironcladGameDKO.updateGame(game);
            sendBoardStateEmail(emailSender, "MOVE IRONCLAD " + gameId + " - partial move!",
                    outheader.toString(), game, gameBoard, user.getUserId());
        } else {
            game.setHalfMoveText(null);
            game.setLastMoveTimestamp(LocalDateTime.now());
            game.setLastReminderTimestamp(null);
            ironcladGameDKO.updateGame(game);
            sendBoardStateEmail(emailSender, "MOVE IRONCLAD " + gameId,
                    outheader.toString(), game, gameBoard, null);
        }
    }

    private TextBodyParseResult parseMoveFromEmail(S3Email emailBody) {
        try {
            String text = emailBody.getEmailBodyText(logger);
            if (text.isEmpty()) {
                return new TextBodyParseResult(null, false, "No move detected in email body.");
            }

            // Split the email body into lines
            String[] lines = text.split("\n");
            List<IroncladMove> moves = new ArrayList<>();

            // Process each line to extract up to two valid moves
            for (String line : lines) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue; // Skip empty lines
                }

                // Split the line into tokens using space or dash as separators
                List<String> tokens = GameTextUtilities.tokenizeLine(line, " +|-");
                StringBuilder tokenLine = new StringBuilder();
                for (String token : tokens) {
                    tokenLine.append("'").append(token).append("', ");
                }
                logger.log("- Line tokens: " + tokenLine.toString());

                String command = tokens.get(0).toLowerCase();
                IroncladMove move = null;

                // Parse based on command type
                if (command.equals("robot") && tokens.size() >= 3) {
                    Location from = Location.fromString(tokens.get(1));
                    Location to = Location.fromString(tokens.get(2));
                    if (from != null && to != null) {
                        move = new IroncladMove(IroncladMoveType.ROBOT_MOVE, from, to);
                    }
                } else if (command.equals("fire") && tokens.size() >= 2) {
                    Location target = Location.fromString(tokens.get(1));
                    if (target != null) {
                        move = new IroncladMove(IroncladMoveType.ROBOT_FIRE, target, null);
                    }
                } else if (command.equals("stone") && tokens.size() >= 2) {
                    Location location = Location.fromString(tokens.get(1));
                    if (location != null) {
                        move = new IroncladMove(IroncladMoveType.STONE_DROP, location, null);
                    }
                } else if (command.equals("stonemove") && tokens.size() >= 3) {
                    Location from = Location.fromString(tokens.get(1));
                    Location to = Location.fromString(tokens.get(2));
                    if (from != null && to != null) {
                        move = new IroncladMove(IroncladMoveType.STONE_MOVE, from, to);
                    }
                }

                // If a valid move was parsed, add it and check if we have two moves
                if (move != null) {
                    moves.add(move);
                    if (moves.size() == 2) {
                        break; // Stop after parsing two moves
                    }
                }
            }

            // Return result based on whether any moves were parsed
            if (moves.isEmpty()) {
                return new TextBodyParseResult(null, false, "No valid moves found in email body.");
            }
            return new TextBodyParseResult(moves, true, null);
        } catch (Exception e) {
            logger.log("Error parsing move from email: " + e.getMessage());
            return new TextBodyParseResult(null, false, "Internal error while parsing move.");
        }
    }

    /**
     * Validates both the move type (against game state) and the actual move (against the game board).
     */
    private String validateMove(IroncladSide pieceMovedSide, IroncladMove thisMove, IroncladGamesRecord game, IroncladBoard gameBoard, int moveNum) {
        if (thisMove.moveType() == IroncladMoveType.ROBOT_MOVE) {
            if (game.getCurrentMovePhase() == IroncladGamesCurrentMovePhase.FORCED_STONE) {
                return "Your first action on this turn must be a Stone play!";
            } else {
                return gameBoard.validateRobotMove(pieceMovedSide, thisMove.start(), thisMove.to());
            }
        }
        if (thisMove.moveType() == IroncladMoveType.ROBOT_FIRE) {
            if (game.getCurrentMovePhase() == IroncladGamesCurrentMovePhase.FORCED_STONE) {
                return "Your first action on this turn must be a Stone play!";
            } else {
                return gameBoard.validateRobotAttack(pieceMovedSide, thisMove.start());
            }
        }
        if (thisMove.moveType() == IroncladMoveType.STONE_DROP) {
            if (game.getCurrentMovePhase() == IroncladGamesCurrentMovePhase.FORCED_ROBOT) {
                return "Your first action on this turn must be a Robot play!";
            } else {
                return gameBoard.validateStoneDrop(pieceMovedSide, thisMove.start());
            }
        }
        if (thisMove.moveType() == IroncladMoveType.STONE_MOVE) {
            if (game.getCurrentMovePhase() == IroncladGamesCurrentMovePhase.FORCED_ROBOT) {
                return "Your first action on this turn must be a Robot play!";
            } else {
                Location lastStoneMoved =
                        game.getLastStoneMoved() == null || game.getLastStoneMoved().isEmpty() ? null : Location.fromString(game.getLastStoneMoved());
                return gameBoard.validateStoneMove(thisMove.start(), thisMove.to(), lastStoneMoved);
            }
        }
        return "Invalid game state - please send feedback with this error and the game ID!";
    }

    @Override
    public String getOpenGamesTextBody() {
        List<IroncladGamesRecord> openGames = ironcladGameDKO.getOpenGames();
        if (openGames.isEmpty()) {
            return IroncladTextResponseProvider.getNoOpenGamesText();
        }
        Set<Long> creatorUserIds = openGames.stream().map(IroncladGamesRecord::getWhiteUserId).collect(Collectors.toSet());
        Map<Long, UsersRecord> usersById = usersDKO.fetchUsersByIds(creatorUserIds);
        StringBuilder sb = new StringBuilder();
        sb.append(IroncladTextResponseProvider.getOpenGamesHeaderText(openGames.size()));
        for (IroncladGamesRecord game : openGames) {
            sb.append(IroncladTextResponseProvider.getOpenGameDescription(game.getGameId(),
                    game.getForcedMoveOption(), usersById.get(game.getWhiteUserId())));
        }
        return sb.toString();
    }

    @Override
    public String getRulesTextBody() {
        return IroncladTextResponseProvider.getIroncladRulesText();
    }

    @Override
    public String getMyGamesTextBody(long userId) {
        StringBuilder sb = new StringBuilder();
        sb.append("IRONCLAD:\n");
        List<IroncladGamesRecord> games = ironcladGameDKO.getActiveGamesForUser(userId);
        for (IroncladGamesRecord game : games) {
            sb.append("-- Game ID: ").append(game.getGameId());
            if (game.getGameState() == IroncladGamesGameState.OPEN) {
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
        IroncladGamesRecord game = ironcladGameDKO.getGameById(gameId);
        if (game == null || game.getGameState() == IroncladGamesGameState.OPEN) {
            GameMessageMailer.statusNotValidGame(emailSender, user.getEmailAddr(), GameType.IRONCLAD, gameId);
            return;
        }
        if (!Objects.equals(user.getUserId(), game.getWhiteUserId()) && !Objects.equals(user.getUserId(), game.getBlackUserId())) {
            GameMessageMailer.statusNotYourGame(emailSender, user.getEmailAddr(), GameType.IRONCLAD, gameId);
            return;
        }

        populatePlayerMap(game);

        String textHeader = "Ironclad Game ID: " + gameId +
                (game.getGameState() == IroncladGamesGameState.IN_PROGRESS ? " - In Progress\n\n" : " - Complete\n\n");
        IroncladBoard gameBoard = new IroncladBoard();
        gameBoard.deserialize(game.getRobotBoardState(), game.getStoneBoardState());
        sendBoardStateEmail(emailSender, "PBEMGS - IRONCLAD STATUS for game id " + gameId, textHeader,
                game, gameBoard, user.getUserId());
    }

    @Override
    public Map<Long, String> processStaleGameCheck(SESEmailSender emailSender) {
        // TODO

        return new HashMap<>();
    }


    /**
     * Validate the options on create.  Only one here ("first"), must be either "self" or "enemy".
     */
    private String validateOptions(Map<String, String> options) {
        if (!options.containsKey("first")) {
            logger.log("-- create failed, missing required option 'first'.");
            return "The option 'first' is required - specify either 'first:self' or 'first:enemy'";
        }
        if (!options.get("first").equalsIgnoreCase("self") &&
                !options.get("first").equalsIgnoreCase("enemy")) {
            logger.log("-- create failed - bad option specification.");
            return "The option for 'first' must be either 'self' or 'enemy'!";
        }
        return null;
    }

    private void populatePlayerMap(IroncladGamesRecord game) {
        symbolByUser.clear();
        UsersRecord whitePlayer = usersDKO.fetchUserById(game.getWhiteUserId());
        UsersRecord blackPlayer = usersDKO.fetchUserById(game.getBlackUserId());
        symbolByUser.put(whitePlayer, IroncladSide.WHITE);
        symbolByUser.put(blackPlayer, IroncladSide.BLACK);
    }

    private IroncladSide getSideToMove(IroncladGamesRecord game, IroncladSide submittingSide) {
        if (game.getCurrentMovePhase() == IroncladGamesCurrentMovePhase.OPEN_MOVE ||
                game.getForcedMoveOption() == IroncladGamesForcedMoveOption.SELF) {
            return submittingSide;
        }
        return submittingSide == IroncladSide.WHITE ? IroncladSide.BLACK : IroncladSide.WHITE;
    }

    /**
     * Updates the game phase based on the move.  If this is open, players are swapped
     * and the forced move type is set based on the current move type.
     */
    private void advanceGamePhase(IroncladGamesRecord game, IroncladMove move, IroncladSide actorSide) {
        if (game.getCurrentMovePhase() == IroncladGamesCurrentMovePhase.OPEN_MOVE) {
            boolean moveIsRobot = move.moveType() == IroncladMoveType.ROBOT_MOVE ||
                    move.moveType() == IroncladMoveType.ROBOT_FIRE;
            game.setCurrentMovePhase(moveIsRobot ? IroncladGamesCurrentMovePhase.FORCED_STONE : IroncladGamesCurrentMovePhase.FORCED_ROBOT);
            game.setUserIdToMove(actorSide == IroncladSide.WHITE ?
                    symbolByUser.inverse().get(IroncladSide.BLACK).getUserId() : symbolByUser.inverse().get(IroncladSide.WHITE).getUserId());
        } else {
            game.setCurrentMovePhase(IroncladGamesCurrentMovePhase.OPEN_MOVE);
        }
    }

    /**
     * Checks for a game state where a forced robot move phase has no robots.
     */
    private void checkNoForcedRobotPlay(IroncladGamesRecord game, IroncladBoard gameBoard,
                                        IroncladSide nextMoveSide, StringBuilder outputText) {
        if (game.getCurrentMovePhase() == IroncladGamesCurrentMovePhase.FORCED_ROBOT &&
                !gameBoard.sideHasRobots(nextMoveSide)) {
            boolean newActiveUserIsWhite = Objects.equals(game.getUserIdToMove(), game.getWhiteUserId());
            UsersRecord newActiveUser = newActiveUserIsWhite ? symbolByUser.inverse().get(IroncladSide.WHITE) : symbolByUser.inverse().get(IroncladSide.BLACK);
            outputText.append(newActiveUser.getHandle()).append(": ")
                    .append(nextMoveSide.name()).append(" skips a forced robot move due to not having any!\n");
            game.setCurrentMovePhase(IroncladGamesCurrentMovePhase.OPEN_MOVE);
        }
    }

    /**
     * Process a completed game.
     */
    private void processGameOver(IroncladSide winnerSide, IroncladGamesRecord game, IroncladBoard gameBoard,
                                 SESEmailSender emailSender, String boardHeader) {
        game.setGameState(IroncladGamesGameState.COMPLETE);
        game.setUserIdToMove(null);
        game.setLastMoveTimestamp(LocalDateTime.now());
        game.setLastReminderTimestamp(null);

        StringBuilder subject = new StringBuilder("PBEMGS - IRONCLAD Game ID ").append(game.getGameId());
        subject.append(" has ended.  ");
        UsersRecord winPlayer = symbolByUser.inverse().get(winnerSide);
        subject.append("Winner is ").append(winPlayer.getHandle()).append("!");

        // Store to DB and send final email.
        try {
            dslContext.transaction(configuration -> {
                DSLContext trx = DSL.using(configuration);
                IroncladGameDKO txIroncladDKO = new IroncladGameDKO(trx);
                PlayerOutcomesDKO txOutcomesDKO = new PlayerOutcomesDKO(trx);
                for (UsersRecord user : symbolByUser.keySet()) {
                    PlayerOutcomesOutcome outcome = winnerSide == symbolByUser.get(user) ? PlayerOutcomesOutcome.WIN : PlayerOutcomesOutcome.LOSS;
                    txOutcomesDKO.insertOutcome(GameType.IRONCLAD, game.getGameId(), user.getUserId(),
                            outcome, null, null);
                }
                txIroncladDKO.updateGame(game);
            });

            sendBoardStateEmail(emailSender, subject.toString(), boardHeader + "\nFinal Board:\n\n", game, gameBoard, null);
        } catch (Exception e) {
            logger.log("Exception while processing end of Ironclad game: " + e.getMessage());
            throw e;
        }
    }

    /**
     * Sends the board state email.  If toUserId is null, it sends to both players.
     * Sections:
     * - player headers (symbol/handle, notation for current move, email receiver on top)
     * - header (caller's information - last move, request #, completion announcement, etc)
     * - state header (phase info - first or second move, which pieces to move if first)
     * - game board (display of the game board)
     * - robot legend (legend for robot symbols)
     */
    private void sendBoardStateEmail(SESEmailSender emailSender, String subject, String header,
                                     IroncladGamesRecord game, IroncladBoard gameBoard, Long toUserId) {
        String gameHeader = "Ironclad Game ID: " + game.getGameId() + "\n\n";
        UsersRecord whitePlayer = symbolByUser.inverse().get(IroncladSide.WHITE);
        UsersRecord blackPlayer = symbolByUser.inverse().get(IroncladSide.BLACK);
        String whiteHeader = IroncladTextResponseProvider.getPlayerHeader(IroncladSide.WHITE, whitePlayer.getHandle(),
                whitePlayer.getUserId().equals(game.getUserIdToMove()));
        String blackHeader = IroncladTextResponseProvider.getPlayerHeader(IroncladSide.BLACK, blackPlayer.getHandle(),
                blackPlayer.getUserId().equals(game.getUserIdToMove()));

        String stateHeader = game.getGameState() != IroncladGamesGameState.COMPLETE ?
                IroncladTextResponseProvider.getPhaseHeader(
                        game.getCurrentMovePhase(), game.getForcedMoveOption()) : "";

        String gameBoardTextBody = gameBoard.getBoardStateExpandedText() + "\n";

        String robotLegend = gameBoard.getRobotLegendText();

        if (toUserId == null || toUserId.equals(whitePlayer.getUserId())) {
            emailSender.sendEmail(whitePlayer.getEmailAddr(), subject,
                    gameHeader + whiteHeader + blackHeader + "\n" +
                            header + stateHeader + gameBoardTextBody + robotLegend);
        }
        if (toUserId == null || toUserId.equals(blackPlayer.getUserId())) {
            emailSender.sendEmail(blackPlayer.getEmailAddr(), subject,
                    gameHeader + blackHeader + whiteHeader + "\n" +
                            header + stateHeader + gameBoardTextBody + robotLegend);
        }
    }


}
