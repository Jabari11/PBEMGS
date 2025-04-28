package com.pbemgs;

import com.pbemgs.game.surge.Surge;
import com.pbemgs.game.surge.SurgeBoard;
import com.pbemgs.game.surge.SurgeCommand;
import com.pbemgs.game.surge.SurgeDirection;
import com.pbemgs.model.TestLogger;

import java.io.IOException;
import java.util.Set;

/**
 * Simulator for testing multiple update steps.
 */
public class Simulator {
    public static void main(String[] args) throws IOException {
        TestLogger mockLogger = new TestLogger();

        SurgeBoard.Coeffs ORIG_COEFFS = new SurgeBoard.Coeffs(1.0f, 1.0f,
                0.7f, 0.5f, 300, 250, 3, 0.33f, 0.75f,
                100, 800, 10);

        String flowBoardState =
                "0:0:CC,0:0:CC,1000:2:OO,1000:2:OO,1000:2:CO|" +
                        "0:0:CC,0:0:CC,500:2:OC,500:2:OC,500:2:CC|" +
                        "0:0:CC,0:0:CC,0:0:CC,0:0:CC,0:0:CC|" +
                        "0:0:CO,0:0:CC,0:0:CC,0:0:CC,0:0:CC|" +
                        "1000:1:OC,0:0:CC,0:0:CC,0:0:CC,0:0:CC";

        String flowGeyserState = "A5:H,E1:H,E5:M";
        SurgeBoard board1 = new SurgeBoard(5, 5, SurgeBoard.PROD_COEFFS, mockLogger);
        SurgeBoard board2 = new SurgeBoard(5, 5, ORIG_COEFFS, mockLogger);
        board1.deserialize(flowBoardState, flowGeyserState, "", "");
        board2.deserialize(flowBoardState, flowGeyserState, "", "");
        board1.buildMomentumMap(300);
        board2.buildMomentumMap(300);

        System.out.println("Initial board:\n\n");
        System.out.println(board1.getSimulatorDisplay());

        board1.processUpdateStep(2);
        board2.processUpdateStep(2);
        System.out.println("After 1 update:\n\n");
        System.out.println(board1.getSimulatorDisplay());

        SurgeCommand cmd1 = new SurgeCommand(3, 0, SurgeDirection.NORTH, true);
        SurgeCommand cmd2 = new SurgeCommand(4, 1, SurgeDirection.NORTH, true);

        board1.processGateCommands(Set.of(cmd1, cmd2));
        board2.processGateCommands(Set.of(cmd1, cmd2));

        board1.processUpdateStep(2);
        board2.processUpdateStep(2);
        System.out.println("After 2 updates:\n\n");
        System.out.println(board1.getSimulatorDisplay());

        board1.processUpdateStep(2);
        board2.processUpdateStep(2);
        System.out.println("After 3 updates:\n\n");
        System.out.println(board1.getSimulatorDisplay());

        board1.processUpdateStep(2);
        board2.processUpdateStep(2);
        System.out.println("After 4 updates:\n\n");
        System.out.println(board1.getSimulatorDisplay());

        cmd1 = new SurgeCommand(2, 0, SurgeDirection.NORTH, true);
        cmd2 = new SurgeCommand(3, 1, SurgeDirection.EAST, true);
        board1.processGateCommands(Set.of(cmd1, cmd2));
        board2.processGateCommands(Set.of(cmd1, cmd2));

        board1.processUpdateStep(2);
        board2.processUpdateStep(2);
        System.out.println("After 5 updates:\n\n");
        System.out.println(board1.getSimulatorDisplay());

        //cmd1 = new SurgeCommand(4, 0, SurgeDirection.EAST, false);
        //board1.processGateCommands(Set.of(cmd1));

        board1.processUpdateStep(2);
        board2.processUpdateStep(2);
        System.out.println("After 6 updates:\n\n");
        System.out.println(board1.getSimulatorDisplay());

        board1.processUpdateStep(2);
        board2.processUpdateStep(2);
        System.out.println("After 7 updates:\n\n");
        System.out.println(board1.getSimulatorDisplay());

        cmd1 = new SurgeCommand(1, 0, SurgeDirection.EAST, true);
        cmd2 = new SurgeCommand(3, 2, SurgeDirection.NORTH, true);

        board1.processGateCommands(Set.of(cmd1, cmd2));
        board2.processGateCommands(Set.of(cmd1, cmd2));

        board1.processUpdateStep(2);
        board2.processUpdateStep(2);
        System.out.println("After 8 updates:\n\n");
        System.out.println(board1.getSimulatorDisplay());

        board1.processUpdateStep(2);
        board2.processUpdateStep(2);
        System.out.println("After 9 updates:\n\n");
        System.out.println(board1.getSimulatorDisplay());

        cmd1 = new SurgeCommand(1, 1, SurgeDirection.EAST, true);

        board1.processGateCommands(Set.of(cmd1));
        board2.processGateCommands(Set.of(cmd1));

        board1.processUpdateStep(2);
        board2.processUpdateStep(2);
        System.out.println("After 10 updates:\n\n");
        System.out.println(board1.getSimulatorDisplay());

        cmd1 = new SurgeCommand(2, 2, SurgeDirection.NORTH, true);

        board1.processGateCommands(Set.of(cmd1));
        board2.processGateCommands(Set.of(cmd1));

        board1.processUpdateStep(2);
        board2.processUpdateStep(2);
        System.out.println("After 11 updates:\n\n");
        System.out.println(board1.getSimulatorDisplay());

        board1.processUpdateStep(2);
        board2.processUpdateStep(2);
        System.out.println("After 12 updates:\n\n");
        System.out.println(board1.getSimulatorDisplay());

        cmd1 = new SurgeCommand(1, 2, SurgeDirection.NORTH, false);
        board1.processGateCommands(Set.of(cmd1));

        board1.processUpdateStep(2);
        board2.processUpdateStep(2);
        System.out.println("After 13 updates:\n\n");
        System.out.println(board1.getSimulatorDisplay());

        cmd1 = new SurgeCommand(2, 2, SurgeDirection.EAST, true);
        board1.processGateCommands(Set.of(cmd1));

        board1.processUpdateStep(2);
        board2.processUpdateStep(2);
        System.out.println("After 14 updates:\n\n");
        System.out.println(board1.getSimulatorDisplay());

        cmd1 = new SurgeCommand(2, 3, SurgeDirection.NORTH, true);
        board1.processGateCommands(Set.of(cmd1));

        board1.processUpdateStep(2);
        board2.processUpdateStep(2);
        System.out.println("After 15 updates:\n\n");
        System.out.println(board1.getSimulatorDisplay());

        board1.processUpdateStep(2);
        board2.processUpdateStep(2);
        System.out.println("After 16 updates:\n\n");
        System.out.println(board1.getSimulatorDisplay());

        board1.processUpdateStep(2);
        board2.processUpdateStep(2);
        System.out.println("After 17 updates:\n\n");
        System.out.println(board1.getSimulatorDisplay());

        board1.processUpdateStep(2);
        board2.processUpdateStep(2);
        System.out.println("After 18 updates:\n\n");
        System.out.println(board1.getSimulatorDisplay());

        board1.processUpdateStep(2);
        board2.processUpdateStep(2);
        System.out.println("After 19 updates:\n\n");
        System.out.println(board1.getSimulatorDisplay());

        board1.processUpdateStep(2);
        board2.processUpdateStep(2);
        System.out.println("After 20 updates:\n\n");
        System.out.println(board1.getSimulatorDisplay());

        System.out.println("Default Coefs board:");
        System.out.println(board2.getSimulatorDisplay());

        OpenHtml.write(board1.getBoardTextHtml() + "\n\n" + Surge.generateSymbolKeyText(), "b");

    }


}
