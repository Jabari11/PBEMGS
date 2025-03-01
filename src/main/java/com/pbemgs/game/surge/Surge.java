package com.pbemgs.game.surge;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.pbemgs.controller.SESEmailSender;
import com.pbemgs.dko.SurgeGamesDKO;
import com.pbemgs.dko.SurgePlayersDKO;
import com.pbemgs.dko.UsersDKO;
import com.pbemgs.game.GameInterface;
import com.pbemgs.game.GameMessageMailer;
import com.pbemgs.game.OptionParser;
import com.pbemgs.generated.enums.SurgeGamesGameState;
import com.pbemgs.generated.enums.SurgeGamesGameTimezone;
import com.pbemgs.generated.tables.records.SurgeGamesRecord;
import com.pbemgs.generated.tables.records.SurgePlayersRecord;
import com.pbemgs.generated.tables.records.UsersRecord;
import com.pbemgs.model.Location;
import com.pbemgs.model.S3Email;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public class Surge implements GameInterface {
    private static final String GAME_NAME = "Surge";
    private static final int PLAYER_GAME_LIMIT = 3;
    private static final int MAX_OPEN_GAMES = 15;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("h:mm a z (M/d/yy)");
    public static final Map<String, ZoneId> GAME_TIME_ZONES = Map.of(
            "ET", ZoneId.of("America/New_York"), // Eastern Time (US)
            "GMT", ZoneId.of("Etc/GMT"),        // GMT (Europe)
            "TK", ZoneId.of("Asia/Tokyo"),      // Tokyo (Japan)
            "SH", ZoneId.of("Asia/Shanghai")    // Shanghai (China)
    );

    private record TextBodyParseResult(List<SurgeCommand> commands, boolean success, String error) {
    }

    private final LambdaLogger logger;
    private final DSLContext dslContext;
    private final SurgeGamesDKO surgeGamesDKO;
    private final SurgePlayersDKO surgePlayersDKO;
    private final UsersDKO usersDKO;

    public Surge(DSLContext dslContext, LambdaLogger logger) {
        this.dslContext = dslContext;
        this.logger = logger;
        this.surgeGamesDKO = new SurgeGamesDKO(dslContext);
        this.surgePlayersDKO = new SurgePlayersDKO(dslContext);
        this.usersDKO = new UsersDKO(dslContext);
    }

    @Override
    public void processCreateGame(UsersRecord user, S3Email email, SESEmailSender emailSender) {
        List<SurgeGamesRecord> userGames = surgeGamesDKO.getActiveGamesForUser(user.getUserId());
        if (userGames.size() >= PLAYER_GAME_LIMIT) {
            GameMessageMailer.gameLimitReached(emailSender, user.getEmailAddr(), "create_game", GAME_NAME);
            return;
        }

        List<SurgeGamesRecord> openGames = surgeGamesDKO.getOpenGames();
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
                emailSender.sendEmail(user.getEmailAddr(), "PBEMGS - create_game surge failed (options)",
                        SurgeTextResponseProvider.getBadOptionsText());
                return;
            }

            // Validation catches exceptions on these conversions.
            int players = Integer.parseInt(options.get("players"));
            int ticks = Integer.parseInt(options.get("ticks"));
            int cLimit = Integer.parseInt(options.get("limit"));
            String zone = options.getOrDefault("zone", "ET");
            SurgeGamesGameTimezone dbZone = SurgeGamesGameTimezone.valueOf(zone);

            // Create: Initial board and player records
            SurgeMapProvider.SurgeMap newMap = SurgeMapProvider.getMap(players, cLimit);
            if (newMap == null) {
                logger.log("Creation failed, no map for options.");
                emailSender.sendEmail(user.getEmailAddr(), "PBEMGS - create_game surge failed (no map)",
                        SurgeTextResponseProvider.getNoValidMapsText());
                return;
            }
            SurgeBoard newBoard = new SurgeBoard(newMap, players, SurgeBoard.PROD_COEFFS, logger);

            // Write the newly-created game data and player data to the DB in one transaction.
            AtomicLong gameIdHolder = new AtomicLong();
            dslContext.transaction(configuration -> {
                DSLContext trx = DSL.using(configuration);

                // Create game and get the new game ID
                long gameId = new SurgeGamesDKO(trx).createNewGame(players, cLimit, dbZone, ticks,
                        newMap.rows(), newMap.cols(), newBoard.serializeBoardState(), newBoard.serializeGeyserState(),
                        newBoard.serializePressure(), newBoard.serializeMomentum());

                // Add player to the game
                new SurgePlayersDKO(trx).addPlayer(gameId, user.getUserId(), 1);
                gameIdHolder.set(gameId);
            });

            GameMessageMailer.createSuccess(emailSender, user.getEmailAddr(), GAME_NAME, gameIdHolder.get());
            return;
        } catch (Exception e) {
            logger.log("Exception parsing options from create_game text body or creating game: " + e.getMessage());
            emailSender.sendEmail(user.getEmailAddr(), "PBEMGS - create_game surge failed",
                    "Exception parsing options or creating game - please send feedback with the following error: " + e.getMessage());
            return;
        }
    }

    @Override
    public void processJoinGame(UsersRecord user, long gameId, SESEmailSender emailSender) {
        List<SurgeGamesRecord> userGames = surgeGamesDKO.getActiveGamesForUser(user.getUserId());
        if (userGames.size() >= PLAYER_GAME_LIMIT) {
            GameMessageMailer.gameLimitReached(emailSender, user.getEmailAddr(), "join_game", GAME_NAME);
            return;
        }
        SurgeGamesRecord game = surgeGamesDKO.getGameById(gameId);

        if (game == null || game.getGameState() != SurgeGamesGameState.OPEN) {
            GameMessageMailer.joinNonopenGame(emailSender, user.getEmailAddr(), GAME_NAME, gameId);
            return;
        }

        List<SurgePlayersRecord> players = surgePlayersDKO.getPlayersForGame(gameId);
        Set<Long> playerIds = players.stream().map(SurgePlayersRecord::getUserId).collect(Collectors.toSet());
        if (playerIds.contains(user.getUserId())) {
            GameMessageMailer.joinSelf(emailSender, user.getEmailAddr(), GAME_NAME);
            return;
        }

        boolean isFinalPlayer = players.size() + 1 == game.getNumPlayers();

        // Single DB transaction for adding the new player (always) and staring the game (if ready)
        try {
            dslContext.transaction(configuration -> {
                DSLContext trx = DSL.using(configuration);

                new SurgePlayersDKO(trx).addPlayer(gameId, user.getUserId(), players.size() + 1);

                if (isFinalPlayer) {
                    game.setLastTimeStep(LocalDateTime.now());
                    game.setGameState(SurgeGamesGameState.IN_PROGRESS);
                    new SurgeGamesDKO(trx).updateGame(game);
                }
            });

            if (isFinalPlayer) {
                List<UsersRecord> userList = getUserList(game);
                sendGameStateEmail(emailSender, game, userList, "PBEMGS - Surge game has started!  Game ID:", "");
            } else {
                GameMessageMailer.joinSuccess(emailSender, user.getEmailAddr(), GAME_NAME, gameId);
            }
        } catch (Exception e) {
            logger.log("Error in join_game transaction: " + e.getMessage() + "\n" +
                    Arrays.stream(e.getStackTrace())
                            .map(StackTraceElement::toString)
                            .collect(Collectors.joining("\n")));
            emailSender.sendEmail(user.getEmailAddr(), "PBEMGS - join_game surge failed",
                    "An internal error occurred while joining the game. Please try again later.");
        }
    }

    @Override
    public void processMove(UsersRecord user, long gameId, S3Email emailBody, SESEmailSender emailSender) {
        SurgeGamesRecord game = surgeGamesDKO.getGameById(gameId);
        if (game == null || game.getGameState() != SurgeGamesGameState.IN_PROGRESS) {
            GameMessageMailer.moveGameNotValid(emailSender, user.getEmailAddr(), GAME_NAME, gameId);
            return;
        }

        List<SurgePlayersRecord> players = surgePlayersDKO.getPlayersForGame(gameId);
        if (players.stream().map(SurgePlayersRecord::getUserId).noneMatch(id -> id == user.getUserId())) {
            emailSender.sendEmail(user.getEmailAddr(), "PBEMGS - move surge failed.",
                    SurgeTextResponseProvider.getMoveNotPlayerText(gameId));
            return;
        }

        TextBodyParseResult move = parseMoveFromEmail(emailBody);
        if (!move.success()) {
            emailSender.sendEmail(user.getEmailAddr(), "PBEMGS - move surge failed (parsing).",
                    SurgeTextResponseProvider.getMoveFailedParseText(gameId, move.error()));
            return;
        }

        if (move.commands().size() > game.getCommandLimit()) {
            emailSender.sendEmail(user.getEmailAddr(), "PBEMGS - move surge failed (limit).",
                    SurgeTextResponseProvider.getMoveFailedLimitText(gameId, move.commands().size(), game.getCommandLimit()));
            return;
        }

        SurgeBoard gameBoard = new SurgeBoard(game.getBoardRows(), game.getBoardCols(), SurgeBoard.PROD_COEFFS, logger);
        gameBoard.deserialize(game.getBoardState(), game.getGeyserState(), game.getPressureState(), game.getMomentumState());

        int userPlayerNum = getPlayerNumber(user, players);

        // Used to check for multiple commands on a single (one-sided) gate - this prevents a player from
        // freezing a gate in its current state by commanding it both open and closed from the same square.
        Set<SurgeGate> gateCmds = new HashSet<>();

        // Collect validation errors instead of failing on the first issue
        List<String> errors = new ArrayList<>();
        for (SurgeCommand command : move.commands()) {
            String validationError = validateCommand(gameBoard, command, userPlayerNum, game.getBoardRows(), game.getBoardCols());
            if (validationError != null) {
                errors.add(validationError);
            }
            SurgeGate thisGate = new SurgeGate(command.getRow(), command.getCol(), command.getDirection());
            if (!gateCmds.add(thisGate)) { // If add() returns false, it's a duplicate
                Location loc = new Location(command.getRow(), command.getCol());
                errors.add("Multiple commands on the gate " + loc + " - " + command.getDirection().name() + " is not allowed.");
            }
        }

        if (!errors.isEmpty()) {
            emailSender.sendEmail(user.getEmailAddr(), "PBEMGS - move surge failed (invalid commands).",
                    SurgeTextResponseProvider.getIllegalMoveText(gameId, String.join("\n", errors)));
            return;
        }

        // Store the move
        String commandString = move.commands().stream().map(SurgeCommand::serialize).collect(Collectors.joining(","));
        try {
            surgePlayersDKO.updatePlayerCommand(user.getUserId(), gameId, commandString);
        } catch (Exception e) {
            logger.log("Surge processMove() failed to write command to the DB: " + e.getMessage());
            emailSender.sendEmail(user.getEmailAddr(), "PBEMGS - move surge failed (exception).",
                    "Your surge move command failed due to an internal error - please try again\n" +
                            "in a bit.  Please send feedback on repeated failures.");
            return;
        }

        // Success Email
        StringBuilder sb = new StringBuilder();
        sb.append("Current Gate Commands:\n");
        for (SurgeCommand command : move.commands()) {
            sb.append(" - ").append(command.getPrettyString()).append("\n");
        }

        emailSender.sendEmail(user.getEmailAddr(), "PBEMGS - move surge success!",
                "Your surge move command for game ID " + gameId + " has been accepted and stored.\n\n" +
                sb.toString());
    }

    @Override
    public void processStatus(UsersRecord user, long gameId, SESEmailSender emailSender) {
        // Validate part of game
        SurgeGamesRecord reqGame = surgeGamesDKO.getGameById(gameId);
        if (reqGame == null || reqGame.getGameState() == SurgeGamesGameState.OPEN ||
                reqGame.getGameState() == SurgeGamesGameState.ABANDONED) {
            emailSender.sendEmail(user.getEmailAddr(), "PBEMGS - game_status surge command failed",
                    SurgeTextResponseProvider.getStatusFailedNoGameText(gameId));
            return;
        }

        SurgePlayersRecord playerForUser = surgePlayersDKO.getPlayerByUserId(gameId, user.getUserId());
        if (playerForUser == null) {
            emailSender.sendEmail(user.getEmailAddr(), "PBEMGS - game_status surge failed.",
                    SurgeTextResponseProvider.getStatusFailedNotPlayerText(gameId));
            return;
        }
        StringBuilder commandString = new StringBuilder();
        if (playerForUser.getCurrentCommand() == null || playerForUser.getCurrentCommand().isEmpty()) {
            commandString.append("No current gate commands set!\n");
        } else {
            commandString.append("Current Gate Commands:\n");
            List<SurgeCommand> commands = SurgeCommand.parseCommandList(playerForUser.getCurrentCommand());
            for (SurgeCommand command : commands) {
                commandString.append(" - ").append(command.getPrettyString()).append("\n");
            }
        }

        sendGameStateEmail(emailSender, reqGame, List.of(user),
                "PBEMGS - game_status of surge Game ID", commandString.toString());
    }

    @Override
    public String getOpenGamesTextBody() {
        List<SurgeGamesRecord> openGames = surgeGamesDKO.getOpenGames();
        if (openGames.isEmpty()) {
            return SurgeTextResponseProvider.getNoOpenGamesText();
        }

        // Collect user IDs per game in a single pass
        Map<Long, Set<Long>> usersByGameMap = openGames.stream()
                .collect(Collectors.toMap(
                        SurgeGamesRecord::getGameId, // Key: game ID
                        game -> surgePlayersDKO.getPlayersForGame(game.getGameId()).stream()
                                .map(SurgePlayersRecord::getUserId)
                                .collect(Collectors.toSet()) // Collect user IDs as a set
                ));

        // Flatten all user IDs from the map into a set for batch fetching
        Set<Long> userIds = usersByGameMap.values().stream()
                .flatMap(Set::stream).collect(Collectors.toSet());

        // Fetch all user records at once
        Map<Long, UsersRecord> usersById = usersDKO.fetchUsersByIds(userIds);

        // Construct the response
        StringBuilder sb = new StringBuilder();
        sb.append(SurgeTextResponseProvider.getOpenGamesHeaderText(openGames.size()));

        // Stream over games and collect users per game
        openGames.forEach(game -> {
            List<UsersRecord> thisGameUsers = usersByGameMap.get(game.getGameId()).stream()
                    .map(usersById::get).collect(Collectors.toList());
            sb.append(SurgeTextResponseProvider.getOpenGameDescription(game, thisGameUsers));
        });

        return sb.toString();
    }

    @Override
    public String getRulesTextBody() {
        String sampleBoard = "100:2:CO,0:0:CC,250:1:OO,420:1:OO,810:1:CO|" +
                "380:2:OO,130:2:OC,220:1:CC,50:1:CC,780:1:CO|" +
                "830:2:OC,830:2:CO,X,0:0:CC,130:1:CO|" +
                "0:0:CC,550:2:CO,0:0:CC,0:0:CC,0:0:CC|" +
                "250:2:OC,450:2:OC,100:2:CC,0:0:CC,1000:0:CC";

        String sampleGeyser = "A3:600,E1:600,E5:75";
        String sampleMomentum = "B3:S:500;B4:S:500;A3:N:700;A2:E:500;B2:E:500;C2:W:500;E1:W:600;E2:S:500;B5:E:500;B5:W:500";

        SurgeBoard board = new SurgeBoard(5, 5, SurgeBoard.PROD_COEFFS, logger);
        board.deserialize(sampleBoard, sampleGeyser, "", sampleMomentum);

        SurgeMapProvider.SurgeMap map2 = SurgeMapProvider.getMap(2, 2);
        SurgeBoard board2 = new SurgeBoard(map2, 2, SurgeBoard.PROD_COEFFS, logger);

        return SurgeTextResponseProvider.getSurgeRulesText() + "\n\nSample Board:\n\n" + board.getBoardTextHtml() +
                "\n\nTest Starting Board:\n\n" + board2.getBoardTextHtml();
    }

    @Override
    public String getMyGamesTextBody(long userId) {
        StringBuilder sb = new StringBuilder();
        sb.append("SURGE:\n");
        List<SurgeGamesRecord> myGames = surgeGamesDKO.getActiveGamesForUser(userId);
        if (myGames.isEmpty()) {
            sb.append(" - No active Surge games.");
        }
        for (SurgeGamesRecord game : myGames) {
            sb.append("-- Game ID: ").append(game.getGameId());
            if (game.getGameState() == SurgeGamesGameState.OPEN) {
                sb.append(" is waiting for opponent(s).\n");
            } else {
                ZonedDateTime nextUpdateTime = getNextUpdateTime(game.getTicksPerDay(), GAME_TIME_ZONES.get(game.getGameTimezone().getLiteral()), game.getLastTimeStep());
                String updateTimeStr = nextUpdateTime.format(TIME_FORMATTER);
                String durationUntilNextUpdateStr = getUntilNextUpdateString(nextUpdateTime);
                sb.append(" is in progress.  Next update time is: ");
                sb.append(updateTimeStr);
                sb.append(" (in ").append(durationUntilNextUpdateStr).append(") ");
                SurgePlayersRecord thisPlayer = surgePlayersDKO.getPlayerByUserId(game.getGameId(), userId);
                if (thisPlayer.getCurrentCommand() == null || thisPlayer.getCurrentCommand().isEmpty()) {
                    sb.append(" - No Current Gate Commands!");
                }
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    private List<UsersRecord> getUserList(SurgeGamesRecord game) {
        List<SurgePlayersRecord> playerRecs = surgePlayersDKO.getPlayersForGame(game.getGameId());

        // Sort by player number
        playerRecs.sort(Comparator.comparingInt(SurgePlayersRecord::getPlayerNumber));

        Map<Long, UsersRecord> usersById = usersDKO.fetchUsersByIds(
                playerRecs.stream().map(SurgePlayersRecord::getUserId).collect(Collectors.toSet())
        );

        return playerRecs.stream()
                .map(player -> usersById.get(player.getUserId())) // Map to UsersRecord
                .toList();
    }

    private int getPlayerNumber(UsersRecord user, List<SurgePlayersRecord> players) {
        return players.stream()
                .filter(p -> p.getUserId() == user.getUserId())
                .findFirst()
                .map(SurgePlayersRecord::getPlayerNumber)
                .orElseThrow(() -> new IllegalStateException("Player not found in game!"));
    }

    private boolean validateOptions(Map<String, String> options) {
        if (!options.containsKey("players") || !options.containsKey("ticks") || !options.containsKey("limit")) {
            logger.log("-- create failed, missing required option(s).");
            return false;
        }

        try {
            // Validate Players (Limited to 2-4 for testing)
            int players = Integer.parseInt(options.get("players"));
            if (players < 2 || players > 4) {
                logger.log("-- create failed, invalid number of players. Only 2-4 player games are currently available.");
                return false;
            }

            // Validate Command Limit (Must be between 2 and 10)
            int climit = Integer.parseInt(options.get("limit"));
            if (climit < 3 || climit > 6) {
                logger.log("-- create failed, invalid command limit. Must be between 3 and 6.");
                return false;
            }

            // Validate Ticks (Valid values: 1, 2, 3, 4)
            int ticks = Integer.parseInt(options.get("ticks"));
            if (ticks < 1 || ticks > 4) {
                logger.log("-- create failed, invalid ticks value. Must be 1, 2, 3, or 4.");
                return false;
            }

        } catch (NumberFormatException e) {
            logger.log("-- create failed, invalid numeric value for players, flow, command limit, or ticks.");
            return false;
        }

        // Validate Time Zone (Optional, defaults to "ET")
        String zone = options.getOrDefault("zone", "ET").toUpperCase();
        Set<String> validZones = Set.of("ET", "GMT", "SH", "TK");  // SH = Shanghai, TK = Tokyo
        if (!validZones.contains(zone)) {
            logger.log("-- create failed, invalid zone. Valid options: ET, GMT, SH (Shanghai), TK (Tokyo).");
            return false;
        }

        return true;
    }  // end validationOptions()

    // Gate command (move) parsing methods
    // Move parsing methods.  This converts from 1-based to 0-based ("A2" becomes (0, 1) in the result).
    private TextBodyParseResult parseMoveFromEmail(S3Email email) {
        try {
            String text = email.getEmailBodyText(logger).trim();
            if (text.isEmpty()) {
                return new TextBodyParseResult(null, false, "No text detected in email body.");
            }

            // Split on spaces, commas, colons, or dashes, but preserve line breaks
            String[] lines = text.split("\n");
            List<SurgeCommand> commands = new ArrayList<>();

            for (String line : lines) {
                String currentCommandType = null; // "open" or "close"

                String[] tokens = line.trim().split("[, :]+");
                for (String token : tokens) {
                    token = token.trim().toUpperCase();
                    if (token.isEmpty()) continue;

                    // First token must be "open" or "close" - if not, skip this line and go on.
                    if (currentCommandType == null) {
                        if (!token.equals("OPEN") && !token.equals("CLOSE")) {
                            break;
                        }
                        currentCommandType = token;
                        continue;
                    }

                    // If we get a new "open" or "close", reset the command type
                    if (token.equals("OPEN") || token.equals("CLOSE")) {
                        currentCommandType = token;
                        continue;
                    }

                    // Expecting a location + direction (e.g., A4N)
                    if (token.length() < 3) {
                        return new TextBodyParseResult(null, false, "Invalid move token: " + token + " (expected [location][direction], e.g., A4N).");
                    }

                    String locationPart = token.substring(0, token.length() - 1);
                    char directionChar = token.charAt(token.length() - 1);
                    try {
                        SurgeDirection direction = SurgeDirection.fromChar(directionChar);
                        Location location;
                        location = Location.fromString(locationPart);
                        if (location == null) {
                            return new TextBodyParseResult(null, false, "Invalid location format: " + locationPart);
                        }

                        commands.add(new SurgeCommand(location.row(), location.col(), direction, currentCommandType.equals("OPEN")));
                    } catch (IllegalArgumentException e) {
                        return new TextBodyParseResult(null, false, "Invalid direction in move: " + directionChar);
                    }
                }  // end for (tokens on a line)
            }  // end for (lines)

            if (commands.isEmpty()) {
                return new TextBodyParseResult(null, false, "No valid gate commands found.");
            }

            return new TextBodyParseResult(commands, true, null);
        } catch (Exception e) {
            logger.log("Error parsing move from email: " + e.getMessage());
            return new TextBodyParseResult(null, false, "Internal error while parsing move.");
        }
    }

    private String validateCommand(SurgeBoard gameBoard, SurgeCommand command, int userPlayerNum, int maxRows, int maxCols) {
        int r = command.getRow();
        int c = command.getCol();
        String locStr = (char) (c + 'A') + String.valueOf(r + 1);

        if (r < 0 || r >= maxRows || c < 0 || c >= maxCols) {
            return "Square " + locStr + " is off the map.";
        }

        if (gameBoard.getSquareOwner(r, c) != userPlayerNum) {
            return "Square " + locStr + " is not controlled by you.";
        }

        int adjR = command.getDirection().getAdjacentRow(r);
        int adjC = command.getDirection().getAdjacentCol(c);
        if (adjR < 0 || adjR >= maxRows || adjC < 0 || adjC >= maxCols) {
            return "Command for square " + locStr + ", direction " + command.getDirection().name() + " attempts to change the edge of the map.";
        }

        if (gameBoard.squareIsObstacle(adjR, adjC)) {
            return "Command for square " + locStr + ", direction " + command.getDirection().name() + " is against an obstacle.";
        }

        return null; // No error
    }

    // Methods for the update step

    /**
     * API Endpoint for the overall periodic update for all Surge games
     * Checks all active games for updates that need to happen at the moment, and runs the update
     * for those individually.
     */
    @Override
    public void processPeriodicUpdate(SESEmailSender emailSender) {
        logger.log("Processing periodic update step for Surge games.");
        List<SurgeGamesRecord> surgeGames = surgeGamesDKO.getActiveGames();

        for (SurgeGamesRecord game : surgeGames) {
            ZonedDateTime nextUpdateTime = getNextUpdateTime(game.getTicksPerDay(),
                    GAME_TIME_ZONES.get(game.getGameTimezone().getLiteral()), game.getLastTimeStep());

            ZonedDateTime now = ZonedDateTime.now();
            logger.log("checking game ID: " + game.getGameId() + " - lastUpdateTime: " + game.getLastTimeStep() + " - nextUpdateTime: " + nextUpdateTime.toString() +
                    " - now: " + now.toString());

            if (now.isAfter(nextUpdateTime)) {
                logger.log("Updating Surge Game ID: " + game.getGameId());

                SurgeBoard board = new SurgeBoard(game.getBoardRows(), game.getBoardCols(), SurgeBoard.PROD_COEFFS, logger);
                board.deserialize(game.getBoardState(), game.getGeyserState(), game.getPressureState(), game.getMomentumState());
                List<SurgePlayersRecord> players = surgePlayersDKO.getPlayersForGame(game.getGameId());

                Set<SurgeCommand> commands = players.stream()
                        .map(SurgePlayersRecord::getCurrentCommand)  // Extract command string
                        .filter(Objects::nonNull)  // Ensure we skip null commands
                        .flatMap(commandStr -> SurgeCommand.parseCommandList(commandStr).stream())  // Parse and flatten lists
                        .collect(Collectors.toSet());  // Collect into a Set

                String commandString = board.processGateCommands(commands);
                board.processUpdateStep(game.getNumPlayers());

                // Update game data representation - geysers are static, no need to re-serialize those
                game.setLastTimeStep(LocalDateTime.now());
                game.setBoardState(board.serializeBoardState());
                game.setPressureState(board.serializePressure());
                game.setMomentumState(board.serializeMomentum());
                surgeGamesDKO.updateGame(game);

                // Update player data (wipe commands)
                surgePlayersDKO.clearAllCommandsForGame(game.getGameId());

                // TODO: Check for eliminated players and update their state (and send sad-trombone email)

                // Email out
                List<UsersRecord> users = getUserList(game);
                sendGameStateEmail(emailSender, game, users, "MOVE SURGE", commandString);
            }
        }
    }

    // Timing methods
    public static ZonedDateTime getNextUpdateTime(int ticks, ZoneId gameZone, LocalDateTime lastUpdateUTC) {
        ZonedDateTime lastUpdate = lastUpdateUTC.atZone(ZoneOffset.UTC).withZoneSameInstant(gameZone);

        List<LocalTime> updateTimes = switch (ticks) {
            case 1 -> List.of(LocalTime.of(12, 0));
            case 2 -> List.of(LocalTime.of(1, 0), LocalTime.of(13, 0));
            case 3 -> List.of(LocalTime.of(11, 0), LocalTime.of(17, 0), LocalTime.of(22, 0));
            case 4 -> List.of(LocalTime.of(0, 0), LocalTime.of(10, 0), LocalTime.of(15, 0), LocalTime.of(20, 0));
            default -> throw new IllegalArgumentException("Invalid tick count: " + ticks);
        };

        // Look for the next tick after lastUpdate
        for (LocalTime tickTime : updateTimes) {
            ZonedDateTime potential = lastUpdate.with(tickTime);
            if (potential.isAfter(lastUpdate)) {
                return potential;
            }
        }

        // No valid tick today, so get the first tick tomorrow
        return lastUpdate.plusDays(1).with(updateTimes.get(0));
    }

    /**
     *  Utility to get a formatted string of the remaining time until the next update (from now).
     */
    private String getUntilNextUpdateString(ZonedDateTime nextUpdateTime) {
        Duration durationUntilNextUpdate = Duration.between(ZonedDateTime.now(nextUpdateTime.getZone()), nextUpdateTime);

        long hours = durationUntilNextUpdate.toHours();
        long minutes = durationUntilNextUpdate.toMinutesPart();

        String durationFormatted;
        if (hours > 0) {
            durationFormatted = String.format("%d hour%s, %d minute%s", hours, (hours == 1 ? "" : "s"), minutes, (minutes == 1 ? "" : "s"));
        } else {
            durationFormatted = String.format("%d minute%s", minutes, (minutes == 1 ? "" : "s"));
        }
        return durationFormatted;
    }

    // Game state email methods
    private void sendGameStateEmail(SESEmailSender emailSender, SurgeGamesRecord game, List<UsersRecord> userList,
                                    String subjectHeader, String commandWrite) {
        SurgeBoard gameBoard = new SurgeBoard(game.getBoardRows(), game.getBoardCols(), SurgeBoard.PROD_COEFFS, logger);
        gameBoard.deserialize(game.getBoardState(), game.getGeyserState(), game.getPressureState(), game.getMomentumState());
        Map<Integer, Integer> forceByPlayerId = gameBoard.getTotalForceMap();

        String htmlHeader = generatePlayerDisplayHtml(game, forceByPlayerId);
        String boardTextHtml = gameBoard.getBoardTextHtml();
        String symbolKeyTextHtml = generateSymbolKeyText();
        String threatenedGeyserString = gameBoard.getThreatenedGeyserList();
        String threatOutput = threatenedGeyserString.isEmpty() ? "" : "\n\nThreatened Geyser Force:\n" + threatenedGeyserString;

        for (UsersRecord user : userList) {
            emailSender.sendEmail(user.getEmailAddr(), subjectHeader + " " + game.getGameId(),
                    htmlHeader + "\n\n" + commandWrite +
                            "\n\nBoard State:\n\n" + boardTextHtml + "\n\n" + threatOutput +
                            "\n\n" + symbolKeyTextHtml);
        }
    }

    private String generatePlayerDisplayHtml(SurgeGamesRecord game, Map<Integer, Integer> forceByPlayerId) {
        List<UsersRecord> usersInGameOrdered = getUserList(game);
        StringBuilder sb = new StringBuilder();
        sb.append("Current board state for Surge Game # ").append(game.getGameId()).append(".\n");
        sb.append("(Command Limit: ").append(game.getCommandLimit()).append(")\n\n");
        ZonedDateTime nextUpdateTime = getNextUpdateTime(game.getTicksPerDay(), GAME_TIME_ZONES.get(game.getGameTimezone().getLiteral()), game.getLastTimeStep());
        String timeHeader = nextUpdateTime.format(TIME_FORMATTER);

        sb.append("Time of next board update: ").append(timeHeader)
                .append(" (in: ").append(getUntilNextUpdateString(nextUpdateTime)).append(")").append("\n\n");
        sb.append("Players:\n");

        for (int x = 0; x < usersInGameOrdered.size(); ++x) {
            sb.append("<span style='color:")
                    .append(SurgeColor.COLOR.get(x + 1)) // Get player color
                    .append(";'> ")
                    .append(SurgeSquare.getLiquidSymbol(1000))
                    .append(": ")
                    .append(usersInGameOrdered.get(x).getHandle())
                    .append("</span>  Total: ")
                    .append(forceByPlayerId.getOrDefault(x + 1, 0))
                    .append("\n");
        }
        return sb.toString();
    }

    public static String generateSymbolKeyText() {
        StringBuilder sb = new StringBuilder();
        sb.append("Symbol Legend:\n");
        sb.append("Liquid:\t\t\tGeyser:\n\n");
        sb.append(SurgeSquare.getLiquidSymbol(900)).append(": 81-100% full\t\t");
        sb.append(SurgeGeyser.getLegendDisplayForType(SurgeGeyser.GeyserType.HOME)).append("\n");
        sb.append(SurgeSquare.getLiquidSymbol(700)).append(":  61-80% full\t\t");
        sb.append(SurgeGeyser.getLegendDisplayForType(SurgeGeyser.GeyserType.LARGE)).append("\n");
        sb.append(SurgeSquare.getLiquidSymbol(500)).append(":  41-60% full\t\t");
        sb.append(SurgeGeyser.getLegendDisplayForType(SurgeGeyser.GeyserType.MEDIUM)).append("\n");
        sb.append(SurgeSquare.getLiquidSymbol(300)).append(":  21-40% full\t\t");
        sb.append(SurgeGeyser.getLegendDisplayForType(SurgeGeyser.GeyserType.SMALL)).append("\n");
        sb.append(SurgeSquare.getLiquidSymbol(100)).append(":   1-20% full\t\t");

        return sb.toString();
    }

}
