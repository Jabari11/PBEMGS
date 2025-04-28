package com.pbemgs.game.surge;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.pbemgs.VisibleForTesting;
import com.pbemgs.model.Location;
import com.pbemgs.model.MonoSymbol;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;

public class SurgeBoard {

    // Movement and combat knobs.  "gate" is gate-opening related, "mom" is momentum related
    public record Coeffs(float gateQtyFactor, float gateInflowFactor,
                         float momGrowthMin, float momSlope, int momInflectPt, int momInflectIncr,
                         float linearSteps, float momReducePct, float momReversePct,
                         int momFloor, int momCeiling,
                         int updateIter) {
    }

    public static Coeffs PROD_COEFFS = new SurgeBoard.Coeffs(0.5f, 1.0f, 0.5f,
            0.38f, 300, 187, 4.0f, 0.30f, 0.75f,
            100, 600, 8);


    // Records for various combinations of location/player num, qty that are used as map keys
    public record Army(Integer playerNum, Integer force) {
    }

    private record ForceMove(int r, int c, int playerNum) {
    }  // Location is the move-to square

    private final int rows;
    private final int cols;
    private final SurgeSquare[][] grid;
    private final Map<Location, SurgeGeyser> geysers;

    private int[][] pressure;  // actual incoming quantity last turn.
    private Map<SurgeGate, Integer> momentumByGate;
    private final LambdaLogger logger;

    // coefficients
    private final Coeffs coeffs;

    public SurgeBoard(int rows, int cols, Coeffs coeffs, LambdaLogger logger) {
        this.coeffs = coeffs;
        this.logger = logger;

        this.rows = rows;
        this.cols = cols;
        this.grid = new SurgeSquare[rows][cols];
        this.geysers = new HashMap<>();

        this.pressure = new int[rows][cols];
        this.momentumByGate = new HashMap<>();

        // Initialize board with empty squares
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                grid[r][c] = new SurgeSquare(r, c); // Neutral empty squares
                pressure[r][c] = 0;
            }
        }
    }

    /**
     * Create a clean map from the provided data.
     * Start location is randomized, and a max-power geyser is initialized at each start
     * (does not need to be in the map's geyser list).  This will also open each gate
     * connected to each start location so that an immediate first step doesn't overflow.
     */
    public SurgeBoard(SurgeMapProvider.SurgeMap newMap, int numPlayers, Coeffs coeffs, LambdaLogger logger) {
        this(newMap.rows(), newMap.cols(), coeffs, logger);

        assert numPlayers == newMap.playerStarts().size() :
                "Map data given has " + newMap.playerStarts().size() + " doesn't match number of players " + numPlayers;


        // Obstacles
        for (String ob : newMap.obstacles()) {
            Location obLoc = Location.fromString(ob);
            grid[obLoc.row()][obLoc.col()].setAsObstacle();
        }
        // Geysers
        deserializeGeysers(newMap.geysers());

        // Player starts, randomized
        List<Integer> playerOrder = new ArrayList<>();
        for (int x = 1; x <= numPlayers; ++x) {
            playerOrder.add(x);
        }
        Collections.shuffle(playerOrder);

        for (int x = 0; x < numPlayers; ++x) {
            int r = newMap.playerStarts().get(x).row();
            int c = newMap.playerStarts().get(x).col();
            grid[r][c].update(playerOrder.get(x), 1000);
            geysers.put(new Location(r, c), new SurgeGeyser(r, c, SurgeGeyser.GeyserType.HOME));
            // open neighboring gates (not edge or obstacle)
            if (r != 0 && !grid[r - 1][c].isObstacle()) {
                grid[r][c].setGate(SurgeDirection.NORTH, true);
                grid[r - 1][c].setGate(SurgeDirection.SOUTH, true);
            }
            if (c != 0 && !grid[r][c - 1].isObstacle()) {
                grid[r][c].setGate(SurgeDirection.WEST, true);
                grid[r][c - 1].setGate(SurgeDirection.EAST, true);
            }
            if (r != rows - 1 && !grid[r + 1][c].isObstacle()) {
                grid[r][c].setGate(SurgeDirection.SOUTH, true);
                grid[r + 1][c].setGate(SurgeDirection.NORTH, true);
            }
            if (c != cols - 1 && !grid[r][c + 1].isObstacle()) {
                grid[r][c].setGate(SurgeDirection.EAST, true);
                grid[r][c + 1].setGate(SurgeDirection.WEST, true);
            }
        }

        buildMomentumMap(300);
    }

    /**
     * Serializes just the board state - format is Location:owner:gates (gates = East then South, 'O' or 'C')
     */
    public String serializeBoardState() {
        StringBuilder sb = new StringBuilder();

        // Serialize board state
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                sb.append(grid[r][c].serialize());
                if (c < cols - 1) sb.append(",");
            }
            if (r < rows - 1) {
                sb.append("|");
            }
        }
        return sb.toString();
    }

    /**
     * Serializes the geysers as comma-separated Location:value pairs.
     */
    public String serializeGeyserState() {
        return geysers.values().stream()
                .map(SurgeGeyser::serialize) // Directly call serialize() on each geyser
                .collect(Collectors.joining(",")); // No trailing comma
    }

    /**
     * Serializes the pressure grid as a comma-separated string of ints (rows separated by semicolons)
     */
    public String serializePressure() {
        return Arrays.stream(pressure)
                .map(row -> Arrays.stream(row).mapToObj(Integer::toString).collect(Collectors.joining(",")))
                .collect(Collectors.joining(";"));
    }

    /**
     * Serializes the momentum data as comma-separated Location:Dir:Value sets.
     */
    public String serializeMomentum() {
        return momentumByGate.entrySet().stream()
                .map(entry -> String.format("%s:%s:%d",
                        new Location(entry.getKey().r(), entry.getKey().c()).toString(),
                        entry.getKey().dir().toChar(),
                        entry.getValue()))
                .collect(Collectors.joining(";"));
    }

    /**
     * Deserializes a SurgeBoard from separate board & geyser data.
     */
    public void deserialize(String boardData, String geyserData, String pressureData, String momentumData) {
        String[] lines = boardData.split("\\|");

        logger.log("Deserializing board state.  String state rows: " + lines.length);

        // Deserialize board state
        for (int r = 0; r < rows; r++) {
            String[] squares = lines[r].split(",");
            for (int c = 0; c < cols; c++) {
                grid[r][c].deserialize(squares[c]);

                // Set the N and W gates of the adjacent squares if open.  Defaults to
                // false and edges should never be open so no need to check.
                if (grid[r][c].isGateOpen(SurgeDirection.SOUTH)) {
                    if (r + 1 == rows) {
                        logger.log("Serialization error - open gate off the south border");
                    }
                    grid[r + 1][c].setGate(SurgeDirection.NORTH, true);
                }
                if (grid[r][c].isGateOpen(SurgeDirection.EAST)) {
                    if (c + 1 == cols) {
                        logger.log("Serialization error - open gate off the east border");
                    }
                    grid[r][c + 1].setGate(SurgeDirection.WEST, true);
                }
            }
        }

        logger.log("Done deserializing grid.  Deserializing Pressure...");
        if (!pressureData.isEmpty()) {
            String[] rows = pressureData.split(";");
            for (int r = 0; r < rows.length; r++) {
                String[] values = rows[r].split(",");
                for (int c = 0; c < values.length; c++) {
                    pressure[r][c] = Integer.parseInt(values[c]);
                }
            }
        }

        logger.log("Done deserializing pressure.  Deserializing Momentum...");
        momentumByGate.clear();
        if (!momentumData.isEmpty()) {
            for (String entry : momentumData.split(";")) {
                String[] parts = entry.split(":");
                if (parts.length != 3) {
                    throw new IllegalArgumentException("Invalid GateCapacity serialization: " + entry);
                }

                Location loc = Location.fromString(parts[0]);
                SurgeDirection dir = SurgeDirection.fromChar(parts[1].charAt(0));
                int momentum = Integer.parseInt(parts[2]);

                momentumByGate.put(new SurgeGate(loc.row(), loc.col(), dir), momentum);
            }
        }

        logger.log("Done deserializing board - deserializing Geysers...");
        deserializeGeysers(geyserData);
    }

    private void deserializeGeysers(String geyserData) {
        if (geyserData != null && !geyserData.isEmpty()) {
            String[] geyserLines = geyserData.split(",");
            for (String line : geyserLines) {
                if (line.trim().isEmpty()) continue;
                SurgeGeyser geyser = SurgeGeyser.deserialize(line);
                geysers.put(new Location(geyser.getRow(), geyser.getCol()), geyser);
            }
        }
    }

    // Accessors for validity checking
    public int getSquareOwner(int r, int c) {
        return grid[r][c].getPlayerNum();
    }

    public boolean squareIsObstacle(int r, int c) {
        return grid[r][c].isObstacle();
    }

    /**
     * Process the set of gate commands.  This will return a string with the opened, closed, and conflicted gates.
     * Gates with commands that are not executed because they are already in the commanded state are not
     * returned in the text.
     */
    public String processGateCommands(Set<SurgeCommand> commands) {
        if (commands.isEmpty()) {
            return "No Gate Commands Processed.";
        }

        StringBuilder executedOpens = new StringBuilder();
        StringBuilder executedCloses = new StringBuilder();
        StringBuilder conflicts = new StringBuilder();

        // Conflicting command checker
        Map<SurgeGate, Boolean> commandMap = new HashMap<>();
        for (SurgeCommand cmd : commands) {
            commandMap.put(new SurgeGate(cmd.getRow(), cmd.getCol(), cmd.getDirection()), cmd.isOpen());
        }

        // Use a queue to safely process commands without modifying the map during iteration
        Queue<SurgeCommand> queue = new LinkedList<>(commands);

        while (!queue.isEmpty()) {
            SurgeCommand command = queue.poll(); // Retrieve and remove from queue
            SurgeGate thisSurgeGate = new SurgeGate(command.getRow(), command.getCol(), command.getDirection());
            SurgeGate oppSurgeGate = new SurgeGate(command.getDirection().getAdjacentRow(command.getRow()),
                    command.getDirection().getAdjacentCol(command.getCol()),
                    command.getDirection().getOpposite());

            // if mirror command doesn't exist or is the same as the one being processed,
            // execute this one (otherwise there are conflicts and it is skipped)
            // An executed command is on both this gate and the mirror.
            Location commandLoc = new Location(thisSurgeGate.r(), thisSurgeGate.c());
            if (commandMap.getOrDefault(oppSurgeGate, command.isOpen()) == command.isOpen()) {
                boolean currState = grid[command.getRow()][command.getCol()].isGateOpen(command.getDirection());
                if (currState != command.isOpen()) {
                    grid[command.getRow()][command.getCol()].setGate(command.getDirection(), command.isOpen());
                    grid[oppSurgeGate.r()][oppSurgeGate.c()].setGate(oppSurgeGate.dir(), command.isOpen());
                    if (command.isOpen()) {
                        int newThisSide = computeInitialMomentum(commandLoc);
                        int newThatSide = computeInitialMomentum(new Location(oppSurgeGate.r(), oppSurgeGate.c()));
                        momentumByGate.put(thisSurgeGate, newThisSide);
                        momentumByGate.put(oppSurgeGate, newThatSide);
                        System.out.println("Opening gate: " + thisSurgeGate.toString() + " to " + newThisSide + " - factors: qty: " +
                                grid[command.getRow()][command.getCol()].getQuantity() + ", pressure: " +
                                pressure[command.getRow()][command.getCol()]);
                        System.out.println("Opening gate: " + oppSurgeGate.toString() + " to " + newThatSide + " - factors: qty: " +
                                grid[oppSurgeGate.r()][oppSurgeGate.c()].getQuantity() + ", pressure: " +
                                pressure[oppSurgeGate.r()][oppSurgeGate.c()]);
                        if (!executedOpens.isEmpty()) {
                            executedOpens.append(", ");
                        }
                        executedOpens.append(commandLoc.toString()).append('-').append(thisSurgeGate.dir().toChar());
                    } else {
                        // Close the gate on both side.  Not going to redirect inflow/pressure for now.
                        momentumByGate.remove(thisSurgeGate);
                        momentumByGate.remove(oppSurgeGate);
                        if (!executedCloses.isEmpty()) {
                            executedCloses.append(", ");
                        }
                        executedCloses.append(commandLoc.toString()).append('-').append(thisSurgeGate.dir().toChar());
                    }
                }  // if gate state changed
            } else {
                if (!conflicts.isEmpty()) {
                    conflicts.append(", ");
                }
                conflicts.append(commandLoc.toString()).append('-').append(thisSurgeGate.dir().toChar());
            }
        }
        StringBuilder textReturn = new StringBuilder();
        textReturn.append("Gate Commands:\n");
        if (!executedOpens.isEmpty()) {
            textReturn.append("Gates Opened: ").append(executedOpens).append("\n");
        }
        if (!executedCloses.isEmpty()) {
            textReturn.append("Gates Closed: ").append(executedCloses).append("\n");
        }
        if (!conflicts.isEmpty()) {
            textReturn.append("Conflicting Commands (not executed): ").append(conflicts).append("\n");
        }
        return textReturn.toString();
    }

    private int computeInitialMomentum(Location loc) {
        // Notes:
        // Initial gate capacity should be based on the combination of current node quantity and last turn's
        // pressure, and be full range (floor..ceiling).
        // The total factor can be a maximum of 1000 + (2 * momCeiling) but the latter having 2 incoming is
        // pretty unrealistic.
        // Let's try scaling off of 1 open gate as maxFactor, and then a pure linear scaling.
        /*
        float maxFactor = coeffs.gateQtyFactor() * 1000 + coeffs.gateInflowFactor() * coeffs.momCeiling();
        float qtyFactor = coeffs.gateQtyFactor() * grid[loc.row()][loc.col()].getQuantity();
        float infFactor = coeffs.gateInflowFactor() * pressure[loc.row()][loc.col()];
        float pressurePct = (qtyFactor + infFactor) / maxFactor;
        return Math.max(Math.min((int) (pressurePct * coeffs.momCeiling()), coeffs.momCeiling()), coeffs.momFloor());

         */
        return 400;
    }

    /**
     * Process an update step for the force on the board.  This tries to achieve equilibrium across the map
     * for each player, limited by the flow rate through each gate.  Accessible enemy-controlled squares
     * count as an initial population of 0 for that player - these "conflict" quantities are collected
     * separately.
     * Geysers create force equal to their value on their square, which can go above max (1000).  Pushing
     * force through the network can also go above max, but is truncated down at the end of the update.
     * The combat step is handled after the movement is complete, and cuts force in all contested squares
     * down until only one (at most) player remains.
     */
    public void processUpdateStep(int numPlayers) {
        LocalDateTime start = LocalDateTime.now();

        // First, initialize all gate data.  Gate flow done is per player (including neutral player 0).
        List<Map<SurgeGate, Integer>> gateFlowPerPlayer = new ArrayList<>(numPlayers + 1);
        for (int x = 0; x < numPlayers + 1; ++x) {
            gateFlowPerPlayer.add(new HashMap<>());
        }
        Map<Location, Map<Integer, Integer>> combatForces = new HashMap<>();  // forceByOwnerByLocation

        // Process geyser force additions
        for (SurgeGeyser geyser : geysers.values()) {
            grid[geyser.getRow()][geyser.getCol()].update(
                    grid[geyser.getRow()][geyser.getCol()].getPlayerNum(),
                    grid[geyser.getRow()][geyser.getCol()].getQuantity() + geyser.getPower()
            );
        }

        // SurgeSquares to process - ordered from highest quantity to lowest.
        // Initialize with all non-empty/non-obstacle squares.
        PriorityQueue<SurgeSquare> toProcess = new PriorityQueue<>(Comparator.comparingInt(SurgeSquare::getQuantity).reversed());
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (!grid[r][c].isObstacle() && grid[r][c].getQuantity() > 0) {
                    toProcess.add(grid[r][c]);
                }
            }
        }

        for (int iteration = 0; iteration < coeffs.updateIter(); ++iteration) {
            Set<Location> nextToProcess = new HashSet<>();  // what to process next iteration
            logger.log("");
            logger.log("Update iteration: " + iteration);

            // Collect quantity changes and apply at the end of the step.
            Map<ForceMove, Integer> qtyChangeByForce = new HashMap<>();

            // Cache effective quantities so they don't need to be recalculated
            Map<Location, Map<Integer, Integer>> effQtyByOwnerByLoc = setEffectiveQuantities(combatForces);

            while (!toProcess.isEmpty()) {
                SurgeSquare tile = toProcess.poll();
                Location loc = tile.getLocation();
                int locOwner = tile.getPlayerNum();
                int thisQty = tile.getQuantity();  // this square's current qty
                int totalQty = tile.getQuantity(); // total of this and lower adjacent nodes
                logger.log("- Processing loc: " + loc.toString() + ", qty: " + thisQty);
                Map<SurgeDirection, Integer> pushTo = new HashMap<>();  // direction to adjacent quantity
                for (SurgeDirection dir : SurgeDirection.values()) {
                    if (tile.isGateOpen(dir)) {
                        Location adj = dir.getAdjacentLoc(loc);
                        int adjQty = effQtyByOwnerByLoc.get(adj).getOrDefault(locOwner, 0);
                        if (adjQty < thisQty) {
                            totalQty += adjQty;
                            pushTo.put(dir, adjQty);
                        }
                    }
                }

                logger.log("-- dir map: " + pushTo.toString());
                // Push to an average target quantity, respecting the gate capacity.
                if (!pushTo.isEmpty()) {
                    boolean updated = false;  // if any updates made, add appropriate squares for next pass
                    int equilibQty = Math.round((float) totalQty / (pushTo.size() + 1));
                    // equilibQty = Math.min(equilibQty, 1000);  // Don't move force unless there's actually room
                    logger.log("-- equilibrium qty: " + equilibQty);
                    Map<SurgeGate, Integer> ownerFlowByGate = gateFlowPerPlayer.get(locOwner);
                    for (SurgeDirection dir : pushTo.keySet()) {
                        SurgeGate pushSurgeGate = new SurgeGate(loc.row(), loc.col(), dir);
                        Location pushTarget = dir.getAdjacentLoc(loc);
                        SurgeGate pullSurgeGate = new SurgeGate(pushTarget.row(), pushTarget.col(), dir.getOpposite());
                        int currPushTargetQty = pushTo.get(dir);
                        Location fromLoc = new Location(loc.row(), loc.col());
                        Location toLoc = new Location(pushTarget.row(), pushTarget.col());
                        int diff = 0;
                        if (equilibQty > currPushTargetQty) {
                            // raising the adjacent loc to equilibrium
                            int distToTarget = equilibQty - currPushTargetQty;
                            int remainingGateLimit = momentumByGate.get(pushSurgeGate) - ownerFlowByGate.getOrDefault(pushSurgeGate, 0);

                            diff = Math.min(distToTarget, remainingGateLimit);
                            logger.log("--- push - Dir of: " + dir.name() + ", diff: " + diff);
                        } else if (equilibQty < currPushTargetQty) {
                            // adjacent loc < current square, but > equil.  Pull from that square to here instead.
                            int distToTarget = currPushTargetQty - equilibQty;
                            int remainingGateLimit = momentumByGate.get(pullSurgeGate) - ownerFlowByGate.getOrDefault(pullSurgeGate, 0);

                            diff = Math.min(distToTarget, remainingGateLimit);
                            fromLoc = new Location(pushTarget.row(), pushTarget.col());
                            toLoc = new Location(loc.row(), loc.col());
                            pushSurgeGate = new SurgeGate(pushTarget.row(), pushTarget.col(), dir.getOpposite());
                            pullSurgeGate = new SurgeGate(loc.row(), loc.col(), dir);
                            ;

                            logger.log("--- pull Dir of: " + dir.name() + ", diff: " + diff);
                        }

                        if (diff > 0) {
                            // Set square diffs
                            ForceMove deltaFrom = new ForceMove(fromLoc.row(), fromLoc.col(), locOwner);
                            ForceMove deltaTo = new ForceMove(toLoc.row(), toLoc.col(), locOwner);
                            qtyChangeByForce.merge(deltaFrom, -diff, Integer::sum);
                            qtyChangeByForce.merge(deltaTo, diff, Integer::sum);

                            // update flow tracking - positive on the current gate, negative on the receiving side.
                            ownerFlowByGate.merge(pushSurgeGate, diff, Integer::sum);
                            ownerFlowByGate.merge(pullSurgeGate, -diff, Integer::sum);
                            updated = true;
                        }
                    }  // end for (push mechanism directions)

                    // If any moves were made from this square, the next iteration needs to process this square
                    // and all adjacent ones with an open gate.
                    if (updated) {
                        for (SurgeDirection dir : SurgeDirection.values()) {
                            if (tile.isGateOpen(dir)) {
                                nextToProcess.add(dir.getAdjacentLoc(loc));
                            }
                        }
                        nextToProcess.add(loc);
                    }
                }  // end if (somewhere needed to push)
            }  // end while (processing squares)

            logger.log("-- Qty Differences: " + qtyChangeByForce.toString());
            logger.log("-- GateFlow List of Maps: " + gateFlowPerPlayer.toString());
            // Apply quantity diffs, checking for combat.
            for (ForceMove delta : qtyChangeByForce.keySet()) {
                // Note here: moving into an unoccupied square will put this in the combat map instead of
                //            directly populating here, as we don't want "carry-through" from a newly-occupied square.
                if (grid[delta.r()][delta.c()].getPlayerNum() != delta.playerNum()) {
                    // Retrieve the existing list of armies for this location, or create a new one if absent, then add.
                    combatForces.computeIfAbsent(new Location(delta.r(), delta.c()), k -> new HashMap<>())
                            .merge(delta.playerNum(), qtyChangeByForce.get(delta), Integer::sum);
                    logger.log("---- Adding Combat Force: " + delta.toString() + " - " + qtyChangeByForce.get(delta));
                } else {
                    int newTotal = grid[delta.r()][delta.c()].getQuantity() + qtyChangeByForce.get(delta);
                    grid[delta.r()][delta.c()].update(delta.playerNum(), newTotal);
                    logger.log("applying delta " + delta.toString() + " qty: " + qtyChangeByForce.get(delta) +
                            " - new val: " + grid[delta.r()][delta.c()].getQuantity());
                }
            }

            // Set the priority queue for the next iteration.
            // This needs to add all nextToProcess squares, as well as add temporary (non grid[][])
            // SurgeSquares for force kept in the combatForces data.
            toProcess.clear();
            for (Location loc : nextToProcess) {
                toProcess.add(grid[loc.row()][loc.col()]);
                if (combatForces.containsKey(loc)) {
                    queueCombatForces(loc, combatForces.get(loc), toProcess);
                }  // end if (handling combat forces at loc-to-process
            }  // end for (setting toProcess)
            nextToProcess.clear();
        }  // end for (one full iteration)

        // Truncate everything to max (100%) before combat
        for (int r = 0; r < rows; ++r) {
            for (int c = 0; c < cols; ++c) {
                grid[r][c].truncate();
            }
        }

        // Combat step:
        // - Go through the combatForces map.  For each location:
        //   - add the SurgeSquare owner's data, run the algorithm, and apply the result.
        // TODO remove logging data (?)
        Map<Integer, Integer> combatLosses = new HashMap<>();
        for (Location fightLoc : combatForces.keySet()) {
            Map<Integer, Integer> armies = combatForces.get(fightLoc);

            if (grid[fightLoc.row()][fightLoc.col()].getQuantity() > 0) {
                armies.put(grid[fightLoc.row()][fightLoc.col()].getPlayerNum(),
                        grid[fightLoc.row()][fightLoc.col()].getQuantity());
            }
            logger.log("handling combat at: " + fightLoc.toString() + " - contesting: " + armies.toString());

            Army result = resolveCombat(armies, grid[fightLoc.row()][fightLoc.col()].getPlayerNum(), combatLosses);
            grid[fightLoc.row()][fightLoc.col()].update(result.playerNum(), result.force());
        }

        // Post-combat data collection/aggregation step
        computeUpdatedPressures(gateFlowPerPlayer);
        computeUpdatedMomentum(gateFlowPerPlayer);
        System.out.println("combat losses: " + combatLosses.toString());

        logger.log("Update time: " + Duration.between(start, LocalDateTime.now()).toMillis() + "ms");
    }

    // Cache effective quantities - this is a per-player/per-location force count.
    // Set a quantity for the cell owner, then one for each current combat force.
    private Map<Location, Map<Integer, Integer>> setEffectiveQuantities(Map<Location, Map<Integer, Integer>> combatForces) {
        Map<Location, Map<Integer, Integer>> cache = new HashMap<>();

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                Location loc = new Location(r, c);
                cache.put(loc, new HashMap<>());
                if (grid[r][c].getQuantity() > 0) {
                    cache.get(loc).put(grid[r][c].getPlayerNum(), grid[r][c].getQuantity());
                }
            }
        }
        for (Location loc : combatForces.keySet()) {
            for (Integer owner : combatForces.get(loc).keySet()) {
                cache.get(loc).merge(owner, combatForces.get(loc).get(owner), Integer::sum);
            }
        }
        return cache;
    }

    /**
     * Test support method to initialze an edge capacity map to the given momentum for all open gates.
     */
    public void buildMomentumMap(int flowRate) {
        momentumByGate.clear();

        // Iterate over the grid to find open gates and set their capacities
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                // Check each direction for an open gate
                for (SurgeDirection dir : SurgeDirection.values()) {
                    if (grid[r][c].isGateOpen(dir)) {
                        // Create a Gate entry for the current gate
                        SurgeGate surgeGate = new SurgeGate(r, c, dir);
                        momentumByGate.put(surgeGate, flowRate); // Set the capacity to the provided flow rate
                    }
                }
            }
        }
    }

    /**
     * Combat Forces need to be treated as SurgeSquares that are not in the main grid.
     * This method takes all the combat forces at a location and creates temporary
     * SurgeSquare objects to add to the next-to-process queue.
     * For these, the only usable open gates are ones connecting back to a square actually
     * owned by the same player.
     */
    private void queueCombatForces(Location loc, Map<Integer, Integer> forceByOwner, PriorityQueue<SurgeSquare> toProcess) {
        for (Integer owner : forceByOwner.keySet()) {
            SurgeSquare tempSquare = new SurgeSquare(loc.row(), loc.col());
            tempSquare.update(owner, forceByOwner.get(owner));
            for (SurgeDirection dir : SurgeDirection.values()) {
                if (grid[loc.row()][loc.col()].isGateOpen(dir)) {
                    Location adj = dir.getAdjacentLoc(loc);
                    if (grid[adj.row()][adj.col()].getPlayerNum() == owner) {
                        tempSquare.setGate(dir, true);
                    }
                }
            }
            toProcess.add(tempSquare);
        }
    }


    /**
     * Update the pressure[][] data given the actual gate usage
     */
    private void computeUpdatedPressures(List<Map<SurgeGate, Integer>> gateFlowPerPlayer) {
        Map<Location, Integer> incomingByLoc = new HashMap<>();

        // Aggregate all incoming force movements (negative values) into incomingByLoc
        gateFlowPerPlayer.forEach(playerMap ->
                playerMap.forEach((surgeGate, val) -> {
                    if (val < 0) {
                        incomingByLoc.merge(new Location(surgeGate.r(), surgeGate.c()), -val, Integer::sum);
                    }
                })
        );

        // Set the pressure[][] grid based on incoming
        for (int r = 0; r < rows; ++r) {
            for (int c = 0; c < cols; ++c) {
                int newPressure = incomingByLoc.getOrDefault(new Location(r, c), 0);
                pressure[r][c] = newPressure;
            }
        }
    }

    private void computeUpdatedMomentum(List<Map<SurgeGate, Integer>> gateFlowPerPlayer) {
        for (Map.Entry<SurgeGate, Integer> entry : momentumByGate.entrySet()) {
            SurgeGate thisSurgeGate = entry.getKey();
            int currMomentum = entry.getValue();

            // Get movement from owning player only
            int owner = grid[thisSurgeGate.r()][thisSurgeGate.c()].getPlayerNum();
            int move = gateFlowPerPlayer.get(owner).getOrDefault(thisSurgeGate, 0);

            if (move > (int) (coeffs.momGrowthMin() * currMomentum)) {
                // Increase momentum using piecewise linear scaling
                int newMomentum = computeIncreasedMomentum(currMomentum, move, grid[thisSurgeGate.r()][thisSurgeGate.c()].getQuantity());
                momentumByGate.put(thisSurgeGate, Math.min(newMomentum, coeffs.momCeiling())); // Enforce max cap
            } else {
                // Reduce capacity based on usage (or reversal)
                int newMomentum = computeReducedMomentum(currMomentum, move);
                momentumByGate.put(thisSurgeGate, Math.max(newMomentum, coeffs.momFloor())); // Enforce min cap
            }
        }
    }

    // Helper function for edge capacity increase
    // limit is capped externally.
    private int computeIncreasedMomentum(int currMomentum, int move, int nodeQty) {
        float maxStep = (float) (coeffs.momCeiling() - coeffs.momFloor()) / coeffs.linearSteps();
        float pctCapacity = (float) move / currMomentum;  // % of full capacity used
        float incrRange = 1.0f - coeffs.momGrowthMin();   // the "increase momemtum" range
        float pctRangeUsed = (pctCapacity - coeffs.momGrowthMin()) / (incrRange);
        int increase = Math.round(maxStep * pctRangeUsed);

        return currMomentum + increase;
    }

    // Helper function for capacity reduction
    // - move a percentage of the diff, with a much higher % when reversed.
    private int computeReducedMomentum(int currMomentum, int move) {
        float diff = (float) currMomentum - move;
        float redFactor = move < 0 ? coeffs.momReversePct() : coeffs.momReducePct();
        float reduction = diff * redFactor;
        return currMomentum - Math.round(reduction);
    }

    static final double defenderPenalty = 0.925;
    static final double exponent = 1.15;

    /**
     * Resolves combat among multiple forces.
     **/
    public Army resolveCombat(Map<Integer, Integer> forceByPlayer, int defender, Map<Integer, Integer> combatLosses) {
        if (forceByPlayer == null || forceByPlayer.isEmpty()) {
            throw new IllegalArgumentException("resolveCombat requires at least 1 force!");
        }

        logger.log("Start combat - forces: " + forceByPlayer.toString() + ", defender: " + defender);

        // First compute the effective force and combat index for each player.
        // effectiveForce = F or F * defenderPenalty (if that player is defending)
        // combatIndex = (effectiveForce)^exponent.
        Map<Integer, Double> combatIndices = new HashMap<>();
        double totalCombatIndex = 0.0;

        for (Map.Entry<Integer, Integer> entry : forceByPlayer.entrySet()) {
            int playerId = entry.getKey();
            int force = entry.getValue();

            // Apply defender penalty if applicable.
            double effectiveForce = (playerId == defender) ? force * defenderPenalty : force;

            // Compute the non-linear combat index.
            double combatIndex = Math.pow(effectiveForce, exponent);
            combatIndices.put(playerId, combatIndex);
            totalCombatIndex += combatIndex;
        }

        // Determine if any player dominates the others.
        // A player wins if his combat index is greater than the sum of the opponents’.
        Integer winningPlayer = null;
        double winningRemainingForce = 0.0;

        for (Map.Entry<Integer, Double> entry : combatIndices.entrySet()) {
            int playerId = entry.getKey();
            double playerIndex = entry.getValue();
            double opponentsIndex = totalCombatIndex - playerIndex;

            if (playerIndex > opponentsIndex) {
                // The advantage is the excess of the player's combat index over the opponents.
                double advantage = playerIndex - opponentsIndex;
                // Invert the non-linear scaling to determine the remaining force.
                double remainingForce = Math.pow(advantage, 1.0 / exponent);

                // If more than one candidate qualifies, we choose the one with the highest remaining force.
                if (winningPlayer == null || remainingForce > winningRemainingForce) {
                    winningPlayer = playerId;
                    winningRemainingForce = remainingForce;
                }
            }
        }

        // If no player is dominant, then the result is a full wipe (mutual destruction).
        if (winningPlayer == null) {
            logger.log("Combat result: wipe!");
            for (int player : forceByPlayer.keySet()) {
                combatLosses.merge(player, forceByPlayer.get(player), Integer::sum);
            }
            return new Army(0, 0);
        }

        // Round the remaining force to the nearest integer.
        int remainingForceInt = (int) Math.round(winningRemainingForce);
        Army result = new Army(winningPlayer, remainingForceInt);
        for (int player : forceByPlayer.keySet()) {
            if (player != winningPlayer) {
                combatLosses.merge(player, forceByPlayer.get(player), Integer::sum);
            } else {
                combatLosses.merge(player, forceByPlayer.get(player) - remainingForceInt, Integer::sum);
            }
        }

        logger.log("Combat result: " + result.toString());
        return result;
    }

    @VisibleForTesting
    public boolean isGateOpen(int r, int c, SurgeDirection surgeDirection) {
        return grid[r][c].isGateOpen(surgeDirection);
    }

    private boolean isOnMap(Location loc) {
        return (loc.row() >= 0 && loc.row() < rows &&
                loc.col() >= 0 && loc.col() < cols && !grid[loc.row()][loc.col()].isObstacle());
    }

    public Map<Integer, Integer> getTotalForceMap() {
        Map<Integer, Integer> forceByPlayerId = new HashMap<>();
        for (int r = 0; r < rows; ++r) {
            for (int c = 0; c < cols; ++c) {
                if (grid[r][c].getPlayerNum() != 0) {
                    forceByPlayerId.merge(grid[r][c].getPlayerNum(), grid[r][c].getQuantity(), Integer::sum);
                }
            }
        }
        return forceByPlayerId;
    }

    /**
     * Eliminates a player by converting all of their remaining force to player 0 (neutral)
     */
    public void eliminatePlayer(int playerId) {
        for (int r = 0; r < rows; ++r) {
            for (int c = 0; c < cols; ++c) {
                if (grid[r][c].getPlayerNum() == playerId) {
                    grid[r][c].update(0, grid[r][c].getQuantity());
                }
            }
        }
    }


    // Display methods
    public String getBoardTextHtml() {
        StringBuilder sb = new StringBuilder();

        // Column Headers
        sb.append("      ");
        for (int c = 0; c < cols; c++) {
            sb.append((char) ('A' + c)).append("   ");
        }
        sb.append("\n\n");

        for (int r = 0; r < rows; r++) {
            // Row Border (Top Gate Line)
            sb.append("    ").append(r == 0 ? MonoSymbol.GRID_TOP_LEFT.getSymbol() : MonoSymbol.GRID_T_LEFT.getSymbol());
            for (int c = 0; c < cols; c++) {
                sb.append(MonoSymbol.GRID_HORIZONTAL.getSymbol());
                sb.append(getHorizontalGateChar(grid[r][c]));
                sb.append(MonoSymbol.GRID_HORIZONTAL.getSymbol());
                if (c != cols - 1) {
                    sb.append(r == 0 ? MonoSymbol.GRID_T_DOWN.getSymbol() : MonoSymbol.GRID_CROSS.getSymbol());
                } else {
                    sb.append(r == 0 ? MonoSymbol.GRID_TOP_RIGHT.getSymbol() : MonoSymbol.GRID_T_RIGHT.getSymbol());
                }
            }
            sb.append("\n");

            // Row Content (Squares + Vertical Gates)
            sb.append(String.format("%2d  ", r + 1)).append(MonoSymbol.GRID_VERTICAL.getSymbol()); // Row Label
            for (int c = 0; c < cols; c++) {
                Location loc = new Location(r, c);
                if (geysers.containsKey(loc)) {
                    sb.append(geysers.get(loc).toHtmlDisplay(grid[r][c].getPlayerNum()));
                } else {
                    sb.append(grid[r][c].toHtmlDisplay());
                }
                sb.append(getVerticalGateChar(grid[r][c]));
            }
            sb.append(String.format("  %2d\n", r + 1));
        }

        // Bottom border of grid
        sb.append("    ").append(MonoSymbol.GRID_BOTTOM_LEFT.getSymbol());
        for (int c = 0; c < cols; c++) {
            sb.append(MonoSymbol.GRID_HORIZONTAL.getSymbol()).append(MonoSymbol.GRID_HORIZONTAL.getSymbol()).append(MonoSymbol.GRID_HORIZONTAL.getSymbol());
            sb.append(c != cols - 1 ? MonoSymbol.GRID_T_UP.getSymbol() : MonoSymbol.GRID_BOTTOM_RIGHT.getSymbol());
        }
        sb.append("\n");

        // Column Footers
        sb.append("      ");
        for (int c = 0; c < cols; c++) {
            sb.append((char) ('A' + c)).append("   ");
        }
        sb.append("\n");

        return sb.toString();
    }

    static final int arrowMin = 450;  // minimum momentum to display an arrow.

    // Get the display char for a gate along the horizontal gate row (a square and the one north of it)
    private String getHorizontalGateChar(SurgeSquare south) {
        if (!south.isGateOpen(SurgeDirection.NORTH)) {
            return String.valueOf(MonoSymbol.GRID_HORIZONTAL.getSymbol());
        }
        Location locNorth = SurgeDirection.NORTH.getAdjacentLoc(south.getLocation());
        SurgeGate surgeGateStoN = new SurgeGate(south.getLocation().row(), south.getLocation().col(), SurgeDirection.NORTH);
        SurgeGate surgeGateNtoS = new SurgeGate(locNorth.row(), locNorth.col(), SurgeDirection.SOUTH);
        int momStoN = momentumByGate.getOrDefault(surgeGateStoN, 0);
        int momNtoS = momentumByGate.getOrDefault(surgeGateNtoS, 0);

        int ownerSouth = south.getPlayerNum();
        int ownerNorth = grid[locNorth.row()][locNorth.col()].getPlayerNum();

        // Default to black (neutral) if no strong push exists
        String arrowColor = SurgeColor.COLOR.get(0);

        if (momStoN >= arrowMin) {
            if (momNtoS >= arrowMin) {
                return "<span style='color:" + arrowColor + ";'>" + MonoSymbol.UP_DOWN_ARROW.getSymbol() + "</span>";
            }
            arrowColor = SurgeColor.COLOR.get(ownerSouth);
            return "<span style='color:" + arrowColor + ";'>" + MonoSymbol.UPWARDS_ARROW.getSymbol() + "</span>";
        }
        if (momNtoS > arrowMin) {
            arrowColor = SurgeColor.COLOR.get(ownerNorth);
            return "<span style='color:" + arrowColor + ";'>" + MonoSymbol.DOWNWARDS_ARROW.getSymbol() + "</span>";
        }
        return " ";
    }

    // Get the display char for a gate along a vertical gate row (a square and the one east of it)
    private String getVerticalGateChar(SurgeSquare west) {
        if (!west.isGateOpen(SurgeDirection.EAST)) {
            return String.valueOf(MonoSymbol.GRID_VERTICAL.getSymbol());
        }

        Location locEast = SurgeDirection.EAST.getAdjacentLoc(west.getLocation());
        SurgeGate surgeGateWtoE = new SurgeGate(west.getLocation().row(), west.getLocation().col(), SurgeDirection.EAST);
        SurgeGate surgeGateEtoW = new SurgeGate(locEast.row(), locEast.col(), SurgeDirection.WEST);
        int momWtoE = momentumByGate.getOrDefault(surgeGateWtoE, 0);
        int momEtoW = momentumByGate.getOrDefault(surgeGateEtoW, 0);

        int ownerWest = west.getPlayerNum();
        int ownerEast = grid[locEast.row()][locEast.col()].getPlayerNum();

        // Default to black (neutral) if no strong push exists
        String arrowColor = SurgeColor.COLOR.get(0);

        if (momWtoE >= arrowMin) {
            if (momEtoW >= arrowMin) {
                return "<span style='color:" + arrowColor + ";'>" + MonoSymbol.LEFT_RIGHT_ARROW.getSymbol() + "</span>";
            }
            arrowColor = SurgeColor.COLOR.get(ownerWest);
            return "<span style='color:" + arrowColor + ";'>" + MonoSymbol.RIGHTWARDS_ARROW.getSymbol() + "</span>";
        }
        if (momEtoW > arrowMin) {
            arrowColor = SurgeColor.COLOR.get(ownerEast);
            return "<span style='color:" + arrowColor + ";'>" + MonoSymbol.LEFTWARDS_ARROW.getSymbol() + "</span>";
        }
        return " ";
    }

    /**
     * Since the force at geyser squares is not displayed, this is a utility to find geyers that are
     * "threatened" (have a next-door neighbor of a different owner) and return what the force levels
     * are for those geysers.  Gate state doesn't matter.
     */
    public String getThreatenedGeyserList() {
        StringBuilder sb = new StringBuilder();
        for (Location geyserLoc : geysers.keySet()) {
            int geyserOwner = grid[geyserLoc.row()][geyserLoc.col()].getPlayerNum();
            for (SurgeDirection dir : SurgeDirection.values()) {
                Location adjLoc = dir.getAdjacentLoc(geyserLoc);
                if (isOnMap(adjLoc) && grid[adjLoc.row()][adjLoc.col()].getPlayerNum() != 0 &&
                        grid[adjLoc.row()][adjLoc.col()].getPlayerNum() != geyserOwner) {
                    sb.append(geyserLoc.toString()).append(" : ")
                            .append(grid[geyserLoc.row()][geyserLoc.col()].getQuantity() / 10).append("% full.\n");
                    break;
                }
            }
        }
        return sb.toString();
    }

    // Returns the board display for simulation testing - this is much-expanded with written out
    // edge capacities and text instead of symbols.  Each cell takes 5 rows of text.
    public String getSimulatorDisplay() {
        StringBuilder sb = new StringBuilder();

        for (int r = 0; r < rows; ++r) {
            for (int dispR = 0; dispR < 5; ++dispR) {
                for (int c = 0; c < cols; ++c) {
                    if (dispR == 0) {
                        sb.append("+-");
                        if (grid[r][c].isGateOpen(SurgeDirection.NORTH)) {
                            sb.append(String.format("%3s", momentumByGate.get(new SurgeGate(r, c, SurgeDirection.NORTH))));
                        } else {
                            sb.append("---");
                        }
                        sb.append("-+");
                    }  // row 0
                    if (dispR == 1) {
                        if (grid[r][c].isObstacle()) {
                            sb.append("|XXXXX|");
                            continue;
                        }
                        if (grid[r][c].isGateOpen(SurgeDirection.WEST))
                            sb.append(String.format("%1s", Integer.toString(momentumByGate.get(new SurgeGate(r, c, SurgeDirection.WEST)) / 100)));
                        else sb.append("|");

                        sb.append("  ").append(Integer.toString(grid[r][c].getPlayerNum())).append("  ");
                        if (grid[r][c].isGateOpen(SurgeDirection.EAST))
                            sb.append(String.format("%1s", Integer.toString(momentumByGate.get(new SurgeGate(r, c, SurgeDirection.EAST)) / 100)));
                        else sb.append("|");
                    }
                    if (dispR == 2) {
                        if (grid[r][c].isObstacle()) {
                            sb.append("|XXXXX|");
                            continue;
                        }
                        if (grid[r][c].isGateOpen(SurgeDirection.WEST))
                            sb.append(String.format("%1s", Integer.toString(momentumByGate.get(new SurgeGate(r, c, SurgeDirection.WEST)) % 100 / 10)));
                        else sb.append("|");

                        if (grid[r][c].getQuantity() == 1000) sb.append(" FFF ");
                        else sb.append(String.format(" %03d ", grid[r][c].getQuantity()));
                        if (grid[r][c].isGateOpen(SurgeDirection.EAST))
                            sb.append(String.format("%1s", Integer.toString(momentumByGate.get(new SurgeGate(r, c, SurgeDirection.EAST)) % 100 / 10)));
                        else sb.append("|");
                    }  // 2
                    if (dispR == 3) {
                        if (grid[r][c].isObstacle()) {
                            sb.append("|XXXXX|");
                            continue;
                        }
                        if (grid[r][c].isGateOpen(SurgeDirection.WEST))
                            sb.append(String.format("%1s", Integer.toString(momentumByGate.get(new SurgeGate(r, c, SurgeDirection.WEST)) % 10)));
                        else sb.append("|");

                        sb.append("     ");
                        if (grid[r][c].isGateOpen(SurgeDirection.EAST))
                            sb.append(String.format("%1s", Integer.toString(momentumByGate.get(new SurgeGate(r, c, SurgeDirection.EAST)) % 10)));
                        else sb.append("|");
                    }  // 3
                    if (dispR == 4) {
                        sb.append("+-");
                        if (grid[r][c].isGateOpen(SurgeDirection.SOUTH)) {
                            sb.append(String.format("%3s", momentumByGate.get(new SurgeGate(r, c, SurgeDirection.SOUTH))));
                        } else {
                            sb.append("---");
                        }
                        sb.append("-+");
                    }
                }
                sb.append("\n");
            }
        }  // end for (rows)

        return sb.toString();
    }
}
