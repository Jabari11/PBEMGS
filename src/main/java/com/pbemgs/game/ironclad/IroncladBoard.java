package com.pbemgs.game.ironclad;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.pbemgs.model.Location;
import com.pbemgs.model.MonoSymbol;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.pbemgs.game.ironclad.IroncladDefinitions.BIG_INTER;
import static com.pbemgs.game.ironclad.IroncladDefinitions.FOOTER_ARR;
import static com.pbemgs.game.ironclad.IroncladDefinitions.HEADER_ARR;
import static com.pbemgs.game.ironclad.IroncladDefinitions.HEADER_BIG;

/**
 * Board representation for Ironclad.
 * There are two semi-independent boards here that will keep separate data structures.
 * The Robot board is an 8x6 standard board, while the Stone board is a 9x7 "board"
 * where the locations are the corners of the robot-board squares.
 * The Robot board pieces can have HP from 1 to 3, while stones are just stones.
 */
public class IroncladBoard {

    public record VictoryResult(IroncladSide side, String message) {
        public static VictoryResult none() {
            return new VictoryResult(null, null);
        }
    }
    private static final int GRID_ROWS = 8;  // Robot squares, stone grid goes one larger
    private static final int GRID_COLS = 6;  // Robot squares, stone grid goes one larger

    Set<IroncladRobot> startingRobots = Set.of(
            new IroncladRobot(Location.fromString("C2"), IroncladSide.BLACK, 1),
            new IroncladRobot(Location.fromString("D2"), IroncladSide.BLACK, 1),
            new IroncladRobot(Location.fromString("B1"), IroncladSide.BLACK, 2),
            new IroncladRobot(Location.fromString("C1"), IroncladSide.BLACK, 3),
            new IroncladRobot(Location.fromString("D1"), IroncladSide.BLACK, 3),
            new IroncladRobot(Location.fromString("E1"), IroncladSide.BLACK, 2),
            new IroncladRobot(Location.fromString("C7"), IroncladSide.WHITE, 1),
            new IroncladRobot(Location.fromString("D7"), IroncladSide.WHITE, 1),
            new IroncladRobot(Location.fromString("B8"), IroncladSide.WHITE, 2),
            new IroncladRobot(Location.fromString("C8"), IroncladSide.WHITE, 3),
            new IroncladRobot(Location.fromString("D8"), IroncladSide.WHITE, 3),
            new IroncladRobot(Location.fromString("E8"), IroncladSide.WHITE, 2)
    );

    private final Map<Location, IroncladRobot> robotBoard;
    private final IroncladSide[][] stoneBoard;


    public IroncladBoard() {
        robotBoard = new HashMap<>();
        stoneBoard = new IroncladSide[GRID_ROWS + 1][GRID_COLS + 1];
    }

    public void deserialize(String robotRep, String stoneRep) {
        robotBoard.clear();
        if (!robotRep.isEmpty()) {
            String[] tokens = robotRep.split(",");
            for (String token : tokens) {
                String[] parts = token.split(":");
                if (parts.length != 3) throw new IllegalArgumentException("Invalid robot rep: " + token);
                char sideChar = parts[0].charAt(0);
                IroncladSide side = IroncladSide.deserialize(sideChar);
                Location loc = Location.fromString(parts[1]);
                int hp = Integer.parseInt(parts[2]);
                robotBoard.put(loc, new IroncladRobot(loc, side, hp));
            }
        }
        String[] rows = stoneRep.split("\\|");
        if (rows.length != GRID_ROWS + 1) throw new IllegalArgumentException("Invalid stone rep rows");
        for (int r = 0; r <= GRID_ROWS; ++r) {
            if (rows[r].length() != GRID_COLS + 1) throw new IllegalArgumentException("Invalid stone rep length");
            for (int c = 0; c <= GRID_COLS; ++c) {
                stoneBoard[r][c] = IroncladSide.deserialize(rows[r].charAt(c));
            }
        }
    }

    /**
     *  Robot serialization: comma-separated "B:A2:3" (side/location/HP)
     */
    public String serializeRobots() {
        return robotBoard.entrySet().stream()
                .map(e -> e.getValue().side().getStoneSerializeChar() + ":" + e.getKey().toString() + ":" + e.getValue().HP())
                .collect(Collectors.joining(","));
    }

    /**
     * Board serialization: grid (B/W/.), pipe-separated rows.
     */
    public String serializeStones() {
        return IntStream.range(0, GRID_ROWS + 1)
                .mapToObj(r -> IntStream.range(0, GRID_COLS + 1)
                        .mapToObj(c -> String.valueOf(stoneBoard[r][c].getStoneSerializeChar()))
                        .collect(Collectors.joining()))
                .collect(Collectors.joining("|"));
    }

    public void initializeNewBoard() {
        // Stone board starts blank
        for (int r = 0; r <= GRID_ROWS; ++r) {
            for (int c = 0; c <= GRID_COLS; ++c) {
                stoneBoard[r][c] = IroncladSide.BLANK;
            }
        }

        // robots in set config.
        for (IroncladRobot robot : startingRobots) {
            robotBoard.put(robot.loc(), robot);
        }
    }

    public boolean sideHasRobots(IroncladSide side) {
        for (IroncladRobot robot : robotBoard.values()) {
            if (robot.side() == side) {
                return true;
            }
        }
        return false;
    }

    /**
     * Validates a robot move.  Must have a correct piece, adjacent sqaure (8-direction), nothing on to-sqaure.
     * Returns null if valid, error message to report if illegal.
     */
    public String validateRobotMove(IroncladSide side, Location from, Location to) {
        if (!isOnRobotBoard(from)) {
            return "From location is off the board.";
        }
        if (!robotBoard.containsKey(from)) {
            return "No robot at from location.";
        }
        IroncladRobot robot = robotBoard.get(from);
        if (robot.side() != side) {
            return "Robot at from location does not belong to the moving side.";
        }
        if (!isOnRobotBoard(to)) {
            return "To location is off the board.";
        }
        int deltaRow = Math.abs(from.row() - to.row());
        int deltaCol = Math.abs(from.col() - to.col());
        if (deltaRow > 1 || deltaCol > 1 || (deltaRow == 0 && deltaCol == 0)) {
            return "To location is not adjacent.";
        }
        if (robotBoard.containsKey(to)) {
            return "To location is not empty.";
        }
        return null;
    }

    /**
     * Validates a robot attack.  Target location must be on the board, contain a robot of the opposite side, and have
     * at least one friendly robot within 2 squares as a queen moves.
     * Returns null if valid, error message to report if illegal.
     */
    public String validateRobotAttack(IroncladSide side, Location target) {
        if (!isOnRobotBoard(target)) {
            return "Target location is off the board.";
        }
        if (!robotBoard.containsKey(target)) {
            return "No robot at target location.";
        }
        IroncladRobot targetRobot = robotBoard.get(target);
        if (targetRobot.side() == side) {
            return "Cannot attack own robot.";
        }
        Set<Location> attackers = getFireFromLocations(target);
        if (attackers.isEmpty()) {
            return "No friendly robot within 2 squares to attack the target.";
        }
        return null;
    }

    /**
     * Validates a stone drop.  Target location must be on the board, empty, and not have a robot at any of the
     * squares the drop location is a corner of.
     * Returns null if valid, error message to report if illegal.
     */
    public String validateStoneDrop(IroncladSide side, Location loc) {
        if (!isOnStoneBoard(loc)) {
            return "Location is off the stone board.";
        }
        if (stoneBoard[loc.row()][loc.col()] != IroncladSide.BLANK) {
            return "Location (stone) is not empty.";
        }
        for (Location adj : getAdjacentRobotLocations(loc)) {
            if (robotBoard.containsKey(adj)) {
                return "Robot adjacent to drop location.";
            }
        }
        return null;
    }

    /**
     * Validates a stone move.  A stone move is only valid if both squares are on the board, and:
     * - there are no legal stone drops available
     * - the piece moved is not the last piece moved (provided)
     * - the move is in a straight line
     * - the move does not jump any other stones
     * Note that a piece may be moved by either player, so the moving player is not passed in!
     * Returns null if valid, error message to report if illegal.
     */
    public String validateStoneMove(Location from, Location to, Location lastStoneMoved) {
        if (hasLegalStoneDrop()) {
            return "Stone move not allowed when stone drops are available.";
        }
        if (!isOnStoneBoard(from) || !isOnStoneBoard(to)) {
            return "From or to location is off the stone board.";
        }
        if (from.equals(to)) {
            return "From and to locations are the same.";
        }
        if (stoneBoard[from.row()][from.col()] == IroncladSide.BLANK) {
            return "No stone at from location.";
        }
        if (stoneBoard[to.row()][to.col()] != IroncladSide.BLANK) {
            return "To location (stone) is not empty.";
        }
        if (lastStoneMoved != null && from.equals(lastStoneMoved)) {
            return "The stone at " + from.toString() + " was the last one moved, and can't be moved again immediately..";
        }
        if (from.row() != to.row() && from.col() != to.col()) {
            return "Stone move must be horizontal or vertical.";
        }
        if (from.row() == to.row()) {
            int r = from.row();
            int c1 = Math.min(from.col(), to.col());
            int c2 = Math.max(from.col(), to.col());
            for (int c = c1 + 1; c < c2; ++c) {
                if (stoneBoard[r][c] != IroncladSide.BLANK) {
                    return "Path blocked by another stone.";
                }
            }
        } else {
            int c = from.col();
            int r1 = Math.min(from.row(), to.row());
            int r2 = Math.max(from.row(), to.row());
            for (int r = r1 + 1; r < r2; ++r) {
                if (stoneBoard[r][c] != IroncladSide.BLANK) {
                    return "Path blocked by another stone.";
                }
            }
        }
        return null;
    }

    public void executeRobotMove(Location from, Location to) {
        IroncladRobot mover = robotBoard.get(from);
        IroncladRobot updated = new IroncladRobot(to, mover.side(), mover.HP());
        robotBoard.remove(from);
        robotBoard.put(to, updated);
    }

    /**
     * Execute a fire command.  Returns a string for the email output consisting of:
     * "Robot at [LOC] fires at [LOC]...  Rolls [ROLL] - Hit/Miss" for each firing robot, plus a line
     * if the target is destroyed.
     */
    public String executeRobotFire(Location target, Random rng) {
        Set<Location> firingRobots = getFireFromLocations(target);
        IroncladRobot targetRobot = robotBoard.get(target);

        // Calculate the target's defense based on stones on its corners
        int defense = getCornerStoneCount(target);


        // Build the output string and track if any hits occur
        StringBuilder output = new StringBuilder();
        output.append("Firing at target robot on ").append(targetRobot.loc().toString());
        output.append(" - target has defense of ").append(defense).append(".\n");

        int hitCount = 0;

        // Execute attack for each robot
        for (Location firingLoc : firingRobots) {
            int roll = rng.nextInt(6) + 1; // Roll a d6 (1-6)
            String result = (roll > defense) ? "Hit!" : "Miss."; // Hit if roll > defense
            if (roll > defense) {
                hitCount++;
            }
            output.append("-- Robot at ").append(firingLoc.toString())
                    .append(" fires!  Rolls a ").append(roll).append(" - ").append(result).append("\n");
        }

        // update robot HP, removing it from the board if destroyed.
        if (hitCount > 0) {
            output.append("Damage Taken: ").append(hitCount);
            int newHp = targetRobot.HP() - hitCount;
            if (newHp > 0) {
                output.append(" - target has ").append(newHp).append(" HP remaining.\n");
                robotBoard.put(target, new IroncladRobot(target, targetRobot.side(), newHp));
            } else {
                robotBoard.remove(target);
                output.append(" - target is destroyed!\n");
            }
        }

        return output.toString();
    }

    public void executeStoneDrop(IroncladSide side, Location loc) {
        stoneBoard[loc.row()][loc.col()] = side;
    }

    public void executeStoneMove(Location from, Location to) {
        stoneBoard[to.row()][to.col()] = stoneBoard[from.row()][from.col()];
        stoneBoard[from.row()][from.col()] = IroncladSide.BLANK;
    }

    /**
     * Check victory conditions.
     * 1. A robot has reached the opposite side of the board (black robot to GRID_ROWS - 1, white robot to 0)
     * 2. A single connected string of stones from one side of the board to the other
     * 2a. Either direction (N/S or E/W)
     * 2b. Cardinal adjacency only, not diagonals.
     * This returns a winning side, or null if none.
     */
    public VictoryResult checkVictoryConditions() {
        // Check robot victory locations
        for (IroncladRobot robot : robotBoard.values()) {
            if (robot.side() == IroncladSide.BLACK && robot.loc().row() == GRID_ROWS - 1) {
                return new VictoryResult(IroncladSide.BLACK, "Black robot has reached " + robot.loc().toString() + "!");
            }
            if (robot.side() == IroncladSide.WHITE && robot.loc().row() == 0) {
                return new VictoryResult(IroncladSide.WHITE, "White robot has reached " + robot.loc().toString() + "!");
            }
        }

        // Check stone path victory
        VictoryResult whitePath = checkStonePathVictory(IroncladSide.WHITE);
        if (whitePath.side() != null) {
            return whitePath;
        }
        VictoryResult blackPath = checkStonePathVictory(IroncladSide.BLACK);
        if (blackPath.side() != null) {
            return blackPath;
        }

        return VictoryResult.none();
    }

    // Helpers

    /**
     * Returns the set of attackers (squares) of a target location.  This is all opposing robots within
     * 2 squares as a queen moves from the target location (occupied squares do not block lasers).
     * If there is no robot at the target location or no attackers, this returns an empty set.
     */
    private Set<Location> getFireFromLocations(Location target) {
        Set<Location> attackers = new HashSet<>();
        if (!isOnRobotBoard(target) || !robotBoard.containsKey(target)) return attackers;
        IroncladSide targetSide = robotBoard.get(target).side();
        IroncladSide friendlySide = (targetSide == IroncladSide.WHITE) ? IroncladSide.BLACK : IroncladSide.WHITE;
        for (int dr = -2; dr <= 2; ++dr) {
            for (int dc = -2; dc <= 2; ++dc) {
                if (dr == 0 && dc == 0) continue;
                if (dr != 0 && dc != 0 && Math.abs(dr) != Math.abs(dc)) continue;
                int r = target.row() + dr;
                int c = target.col() + dc;
                if (r >= 0 && r < GRID_ROWS && c >= 0 && c < GRID_COLS) {
                    Location loc = new Location(r, c);
                    if (robotBoard.containsKey(loc) && robotBoard.get(loc).side() == friendlySide) {
                        attackers.add(loc);
                    }
                }
            }
        }
        return attackers;
    }

    private boolean isOnRobotBoard(Location loc) {
        return loc.row() >= 0 && loc.row() < GRID_ROWS &&
                loc.col() >= 0 && loc.col() < GRID_COLS;
    }

    private boolean isOnStoneBoard(Location loc) {
        return loc.row() >= 0 && loc.row() <= GRID_ROWS &&
                loc.col() >= 0 && loc.col() <= GRID_COLS;
    }

    private VictoryResult checkStonePathVictory(IroncladSide side) {
        // Check north-south path
        VictoryResult nsResult = checkPathWithEndpoints(side, 0, GRID_ROWS, true);
        if (nsResult.side() != null) {
            return nsResult;
        }
        // Check east-west path
        return checkPathWithEndpoints(side, 0, GRID_COLS, false);
    }

    private VictoryResult checkPathWithEndpoints(IroncladSide side, int start, int end, boolean isRow) {
        Set<Location> visited = new HashSet<>();
        Queue<Location> queue = new LinkedList<>();
        Map<Location, Location> parent = new HashMap<>(); // Track path for endpoints

        // Seed starting positions
        if (isRow) {
            for (int c = 0; c <= GRID_COLS; ++c) {
                if (stoneBoard[start][c] == side) {
                    Location loc = new Location(start, c);
                    queue.add(loc);
                    visited.add(loc);
                }
            }
        } else {
            for (int r = 0; r <= GRID_ROWS; ++r) {
                if (stoneBoard[r][start] == side) {
                    Location loc = new Location(r, start);
                    queue.add(loc);
                    visited.add(loc);
                }
            }
        }

        Location endLoc = null;
        while (!queue.isEmpty() && endLoc == null) {
            Location current = queue.poll();
            if ((isRow ? current.row() : current.col()) == end) {
                endLoc = current; // Found an endpoint
                break;
            }
            for (Location neighbor : getStoneNeighbors(current)) {
                if (stoneBoard[neighbor.row()][neighbor.col()] == side && !visited.contains(neighbor)) {
                    queue.add(neighbor);
                    visited.add(neighbor);
                    parent.put(neighbor, current); // Track how we got here
                }
            }
        }

        if (endLoc != null) {
            // Reconstruct path to find start point
            Location startLoc = endLoc;
            while (parent.containsKey(startLoc)) {
                startLoc = parent.get(startLoc);
            }
            String sideName = side == IroncladSide.WHITE ? "White" : "Black";
            String direction = isRow ? "top to bottom" : "left to right";
            return new VictoryResult(side,
                    String.format("%s has connected stones from %s to %s - %s!",
                            sideName, startLoc.toString(), endLoc.toString(), direction));
        }
        return VictoryResult.none();
    }

    private List<Location> getStoneNeighbors(Location loc) {
        List<Location> neighbors = new ArrayList<>();
        int r = loc.row(), c = loc.col();
        if (r > 0) neighbors.add(new Location(r - 1, c));
        if (r < GRID_ROWS) neighbors.add(new Location(r + 1, c));
        if (c > 0) neighbors.add(new Location(r, c - 1));
        if (c < GRID_COLS) neighbors.add(new Location(r, c + 1));
        return neighbors;
    }

    private boolean hasLegalStoneDrop() {
        for (int r = 0; r <= GRID_ROWS; ++r) {
            for (int c = 0; c <= GRID_COLS; ++c) {
                if (stoneBoard[r][c] == IroncladSide.BLANK) {
                    Location loc = new Location(r, c);
                    boolean hasRobot = getAdjacentRobotLocations(loc).stream().anyMatch(robotBoard::containsKey);
                    if (!hasRobot) return true;
                }
            }
        }
        return false;
    }

    private List<Location> getAdjacentRobotLocations(Location stoneLoc) {
        List<Location> adj = new ArrayList<>();
        int r = stoneLoc.row(), c = stoneLoc.col();
        if (r > 0 && c > 0) adj.add(new Location(r - 1, c - 1));
        if (r > 0 && c < GRID_COLS) adj.add(new Location(r - 1, c));
        if (r < GRID_ROWS && c > 0) adj.add(new Location(r, c - 1));
        if (r < GRID_ROWS && c < GRID_COLS) adj.add(new Location(r, c));
        return adj;
    }

    /**
     * Calculate the defense of the target based on the number of stones on the corners of its square.
     */
    private int getCornerStoneCount(Location robotLoc) {
        int count = 0;
        int row = robotLoc.row();
        int col = robotLoc.col();
        // Define the four corner (diagonal) locations
        Location[] corners = {
                new Location(row, col), // top-left
                new Location(row, col + 1), // top-right
                new Location(row + 1, col), // bot-left
                new Location(row + 1, col + 1)  // bot-right
        };
        // Count stones on valid corner locations
        for (Location corner : corners) {
            if (stoneBoard[corner.row()][corner.col()] != IroncladSide.BLANK) {
                count++;
            }
        }
        return count;
    }

    /**
     * Gets the (intertwined) board text representation for email output.
     */
    static final String HORIZ3 = String.valueOf(MonoSymbol.GRID_HORIZONTAL.getSymbol()) + MonoSymbol.GRID_HORIZONTAL.getSymbol() + MonoSymbol.GRID_HORIZONTAL.getSymbol();
    public String getBoardStateExpandedText() {
        StringBuilder sb = new StringBuilder();

        sb.append(HEADER_BIG).append(HEADER_ARR).append(BIG_INTER);

        // go by row and col numbers - the last one only has the stone grid.
        for (int r = 0; r <= GRID_ROWS; ++r) {
            char leftSide = r == 0 ? MonoSymbol.GRID_TOP_LEFT.getSymbol() :
                    (r == GRID_ROWS ? MonoSymbol.GRID_BOTTOM_LEFT.getSymbol() : MonoSymbol.GRID_T_LEFT.getSymbol());
            char rightSide = r == 0 ? MonoSymbol.GRID_TOP_RIGHT.getSymbol() :
                    (r == GRID_ROWS ? MonoSymbol.GRID_BOTTOM_RIGHT.getSymbol() : MonoSymbol.GRID_T_RIGHT.getSymbol());
            char divider = r == 0 ? MonoSymbol.GRID_T_DOWN.getSymbol() :
                    (r == GRID_ROWS ? MonoSymbol.GRID_T_UP.getSymbol() : MonoSymbol.GRID_CROSS.getSymbol());

            // stone row
            sb.append("  ").append(r + 1).append(" ");
            for (int c = 0; c <= GRID_COLS; ++c) {
                if (stoneBoard[r][c] == IroncladSide.BLANK) {
                    sb.append(c == 0 ? leftSide : (c == GRID_COLS ? rightSide : divider));
                } else {
                    sb.append(stoneBoard[r][c].getStoneDisplayChar());
                }
                if (c != GRID_COLS) {
                    sb.append(HORIZ3);
                }
            }
            sb.append(" ").append(r + 1).append("\n");

            // Robot row
            if (r < GRID_ROWS) {
                sb.append(r + 1).append(MonoSymbol.RIGHTWARDS_ARROW.getSymbol()).append("  ").append(MonoSymbol.GRID_VERTICAL.getSymbol());
                for (int c = 0; c < GRID_COLS; ++c) {
                    Location loc = new Location(r, c);
                    if (robotBoard.containsKey(loc)) {
                        IroncladRobot thisRobot = robotBoard.get(loc);
                        sb.append(" ").append(thisRobot.side().getRobotDisplayChar(thisRobot.HP())).append(" ");
                    } else {
                        sb.append("   ");
                    }
                    sb.append(MonoSymbol.GRID_VERTICAL.getSymbol());
                }
                sb.append("  ").append(MonoSymbol.LEFTWARDS_ARROW.getSymbol()).append(r + 1).append("\n");
            }
        }

        sb.append(BIG_INTER).append(FOOTER_ARR).append(HEADER_BIG);
        return sb.toString();
    }

    public String getRobotLegendText() {
        return "Legend of Symbols:\n\n" +
            "Side:\tWhite\tBlack\n" +
            "Stone:\t" + IroncladSide.WHITE.getStoneDisplayChar() + "\t" + IroncladSide.BLACK.getStoneDisplayChar() + "\n" +
            "R-3HP:\t" + IroncladSide.WHITE.getRobotDisplayChar(3) + "\t" + IroncladSide.BLACK.getRobotDisplayChar(3) + "\n" +
            "R-2HP:\t" + IroncladSide.WHITE.getRobotDisplayChar(2) + "\t" + IroncladSide.BLACK.getRobotDisplayChar(2) + "\n" +
            "R-1HP:\t" + IroncladSide.WHITE.getRobotDisplayChar(1) + "\t" + IroncladSide.BLACK.getRobotDisplayChar(1) + "\n";
    }



}
