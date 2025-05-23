package com.pbemgs;

import com.pbemgs.game.surge.SurgeBoard;
import com.pbemgs.game.surge.SurgeCommand;
import com.pbemgs.game.surge.SurgeDirection;
import com.pbemgs.model.Location;
import com.pbemgs.model.TestLogger;

import java.io.IOException;
import java.util.Set;

import static com.pbemgs.game.surge.SurgeBoard.PROD_COEFFS;

/**
 * Simulator for testing multiple update steps.
 */
public class SimulatorLongLines {
    public static void main(String[] args) throws IOException {
        TestLogger mockLogger = new TestLogger();

        SurgeBoard.Coeffs TEST_COEFFS = new SurgeBoard.Coeffs(0.5f, 1.0f, 0.5f,
                0.38f, 300, 187, 4.0f, 0.30f, 0.75f,
                100, 700, 6);

        String flowBoardState =
                "1000:1:OC,1000:1:OC,1000:1:OC,1000:1:OC,1000:1:OC,1000:1:OC,1000:1:CC,0:0:CC,0:0:CC,0:0:CC,0:0:CC,0:0:CC,0:0:CC,0:0:CC,0:0:CC|" +
                        "500:2:OC,500:2:OC,500:2:OC,500:2:OC,500:2:OC,500:2:OC,500:2:CC,0:0:CC,0:0:CC,0:0:CC,0:0:CC,0:0:CC,0:0:CC,0:0:CC,0:0:CC|" +
                        "1000:1:OC,1000:1:OC,1000:1:OC,1:1:OC,1:1:OC,1:1:OC,1:1:OC,1:1:OC,1:1:OC,1:1:OC,1:1:CC,1000:2:CC,0:0:CC,0:0:CC,0:0:CC|" +
                        "1000:3:OO,1000:3:OO,1000:3:OO,1:3:OO,1:3:OO,1:3:OO,1:3:OO,1:3:OO,1:3:OO,1:3:OO,1:3:CO,1000:4:CC,0:0:CC,0:0:CC,0:0:CC|" +
                        "1000:3:OC,1000:3:OC,1000:3:OC,1:3:OC,1:3:OC,1:3:OC,1:3:OC,1:3:OC,1:3:OC,1:3:OC,1:3:CC,1000:4:CC,0:0:CC,0:0:CC,0:0:CC";

        String flowGeyserState = "A2:M,L3:L,L4:L,A3:M,A5:M";
        SurgeBoard boardTest = new SurgeBoard(5, 15, TEST_COEFFS, mockLogger);
        SurgeBoard boardProd = new SurgeBoard(5, 15, PROD_COEFFS, mockLogger);
        boardTest.deserialize(flowBoardState, flowGeyserState, "", "");
        boardProd.deserialize(flowBoardState, flowGeyserState, "", "");
        boardTest.buildMomentumMap(300);
        boardProd.buildMomentumMap(300);

        System.out.println("Initial board:\n\n");
        System.out.println(boardTest.getSimulatorDisplay());

        boardTest.processUpdateStep(4);
        boardProd.processUpdateStep(4);
        System.out.println("After 1 update:\n\n");
        System.out.println(boardTest.getSimulatorDisplay());

        SurgeCommand cmd1 = new SurgeCommand(Location.fromString("G1"), SurgeDirection.EAST, true);
        SurgeCommand cmd2 = new SurgeCommand(Location.fromString("G2"), SurgeDirection.EAST, true);
        boardTest.processGateCommands(Set.of(cmd1, cmd2));
        boardProd.processGateCommands(Set.of(cmd1, cmd2));

        boardTest.processUpdateStep(4);
        boardProd.processUpdateStep(4);
        System.out.println("After 2 updates:\n\n");
        System.out.println(boardTest.getSimulatorDisplay());

        cmd1 = new SurgeCommand(Location.fromString("H1"), SurgeDirection.EAST, true);
        cmd2 = new SurgeCommand(Location.fromString("H2"), SurgeDirection.EAST, true);

        boardTest.processGateCommands(Set.of(cmd1, cmd2));
        boardProd.processGateCommands(Set.of(cmd1, cmd2));

        boardTest.processUpdateStep(4);
        boardProd.processUpdateStep(4);
        System.out.println("After 3 updates:\n\n");
        System.out.println(boardTest.getSimulatorDisplay());

        for (int x = 4; x <= 9; ++x) {
            boardTest.processUpdateStep(4);
            boardProd.processUpdateStep(4);
            System.out.println("After " + x + " updates:\n\n");
            System.out.println(boardTest.getSimulatorDisplay());
        }

        cmd1 = new SurgeCommand(Location.fromString("L3"), SurgeDirection.WEST, true);
        cmd2 = new SurgeCommand(Location.fromString("L4"), SurgeDirection.WEST, true);
        boardTest.processGateCommands(Set.of(cmd1, cmd2));
        boardProd.processGateCommands(Set.of(cmd1, cmd2));

        for (int x = 1; x <= 10; ++x) {
            boardTest.processUpdateStep(4);
            boardProd.processUpdateStep(4);
            System.out.println("After " + x + " combat updates:\n\n");
            System.out.println(boardTest.getSimulatorDisplay());
        }

        System.out.println("Prod Coeffs board:");
        System.out.println(boardProd.getSimulatorDisplay());

        // OpenHtml.write(boardTest.getBoardTextHtml() + "\n\n" + Surge.generateSymbolKeyText(), "b");
    }


}
