package com.pbemgs.game.surge;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

public class SurgeBoardTest {
    private static final LambdaLogger mockLogger = mock(LambdaLogger.class);

    private SurgeBoard gateTestBoard;
    private SurgeBoard flowTestBoard;

    @BeforeEach
    public void setUp() {

        // Redirect log() calls to System.out.printf()
        doAnswer(invocation -> {
            Object arg = invocation.getArgument(0);
            System.out.printf("[log] %s%n", arg);
            return null;
        }).when(mockLogger).log(anyString());

        // Initialize a sample SurgeBoard with a known layout
        String gateBoardState = "100:2:CO,0:0:CC,250:1:OO,420:1:OO,810:1:CO|" +
                "380:2:OO,130:2:OC,220:1:CC,50:1:CC,780:1:CO|" +
                "830:2:OC,830:2:CO,X,0:0:CC,130:1:CO|" +
                "0:0:CC,550:2:CO,0:0:CC,0:0:CC,0:0:CC|" +
                "250:2:OC,450:2:OC,100:2:CC,0:0:CC,1000:0:CC";
        String gateGeyserState = "A3:H,E1:H,E5:M";
        gateTestBoard = new SurgeBoard(5, 5, SurgeBoard.PROD_COEFFS, mockLogger);
        gateTestBoard.deserialize(gateBoardState, gateGeyserState, "", "");
        gateTestBoard.buildMomentumMap(300);
    }

    @Test
    public void testMapGeneration() {
        try {
            SurgeBoard m1_2p = new SurgeBoard(SurgeMapProvider.MAP_2P_1, 2, SurgeBoard.PROD_COEFFS, mockLogger);
            SurgeBoard m2_2p = new SurgeBoard(SurgeMapProvider.MAP_2P_2, 2, SurgeBoard.PROD_COEFFS, mockLogger);
            SurgeBoard m3_2p = new SurgeBoard(SurgeMapProvider.MAP_2P_3, 2, SurgeBoard.PROD_COEFFS, mockLogger);
            SurgeBoard m4_2p = new SurgeBoard(SurgeMapProvider.MAP_2P_4, 2, SurgeBoard.PROD_COEFFS, mockLogger);
            SurgeBoard m5_2p = new SurgeBoard(SurgeMapProvider.MAP_2P_5, 2, SurgeBoard.PROD_COEFFS, mockLogger);
            SurgeBoard m6_2p = new SurgeBoard(SurgeMapProvider.MAP_2P_6, 2, SurgeBoard.PROD_COEFFS, mockLogger);
            SurgeBoard m1_3p = new SurgeBoard(SurgeMapProvider.MAP_3P_1, 3, SurgeBoard.PROD_COEFFS, mockLogger);
            SurgeBoard m1_4p = new SurgeBoard(SurgeMapProvider.MAP_4P_1, 4, SurgeBoard.PROD_COEFFS, mockLogger);
            SurgeBoard m2_4p = new SurgeBoard(SurgeMapProvider.MAP_4P_2, 4, SurgeBoard.PROD_COEFFS, mockLogger);
            SurgeBoard m3_4p = new SurgeBoard(SurgeMapProvider.MAP_4P_3, 4, SurgeBoard.PROD_COEFFS, mockLogger);
            SurgeBoard m4_4p = new SurgeBoard(SurgeMapProvider.MAP_4P_4, 4, SurgeBoard.PROD_COEFFS, mockLogger);
        } catch (Exception e) {
            String stackTrace = Arrays.stream(e.getStackTrace())
                    .map(StackTraceElement::toString)
                    .collect(Collectors.joining("\n"));
            fail("Exception generating maps - " + stackTrace);
        }
    }

    @Test
    public void testValidGateCommands() {
        // Test a few valid, non-conflicting commands.  Some of which leave the existing state.
        String cStr = "OA1E,CA1S,CC2W,OB4N";
        Set<SurgeCommand> commands = new HashSet<>(SurgeCommand.parseCommandList(cStr));

        gateTestBoard.processGateCommands(commands);

        // Verify that gates were updated correctly on both cells.
        // OA1E - Open East Gate at A1 (0,0)
        assertTrue(gateTestBoard.isGateOpen(0, 0, SurgeDirection.EAST), "Gate A1E should be open.");
        assertTrue(gateTestBoard.isGateOpen(0, 1, SurgeDirection.WEST), "Gate B1W should be open.");

        // CA1S - Close South Gate at A1 (0,0)
        assertFalse(gateTestBoard.isGateOpen(0, 0, SurgeDirection.SOUTH), "Gate A1S should be closed.");
        assertFalse(gateTestBoard.isGateOpen(1, 0, SurgeDirection.NORTH), "Gate A2N should be closed.");

        // CC2W - Close West Gate at C2 (1,2)
        assertFalse(gateTestBoard.isGateOpen(1, 2, SurgeDirection.WEST), "Gate C2W should be closed.");
        assertFalse(gateTestBoard.isGateOpen(1, 1, SurgeDirection.EAST), "Gate B2E should be closed.");

        // OB4N - Open North Gate at B4 (3,1)
        assertTrue(gateTestBoard.isGateOpen(3, 1, SurgeDirection.NORTH), "Gate B4N should be open.");
        assertTrue(gateTestBoard.isGateOpen(2, 1, SurgeDirection.SOUTH), "Gate B3S should be open.");
    }

    @Test
    public void testConflictingGateCommands() {
        // Test conflicting commands (opposite sides of the same gate commanded different directions),
        // against both an existing open and closed gate. Verify both are unchanged.
        String cStr = "CD2S,OD3N,CB2E,OC2W";
        Set<SurgeCommand> commands = new HashSet<>(SurgeCommand.parseCommandList(cStr));

        gateTestBoard.processGateCommands(commands);

        // Verify that gates were NOT updated on both cells.
        // D2S/D3N - was closed, should stay closed.
        assertFalse(gateTestBoard.isGateOpen(1, 3, SurgeDirection.SOUTH), "Gate D2S should remain closed.");
        assertFalse(gateTestBoard.isGateOpen(2, 3, SurgeDirection.NORTH), "Gate D3N should remain closed.");

        // B2E/C2W - was open, should remain open.
        assertTrue(gateTestBoard.isGateOpen(1, 1, SurgeDirection.EAST), "Gate B2E should remain open.");
        assertTrue(gateTestBoard.isGateOpen(1, 2, SurgeDirection.WEST), "Gate C2W should remain open.");
    }

    @Test
    public void testRedundantSameCommands() {
        // Test redundant commands (opposite sides of the same gate commanded the same direction).
        String cStr = "OD2S,OD3N,CB2E,CC2W";
        Set<SurgeCommand> commands = new HashSet<>(SurgeCommand.parseCommandList(cStr));

        gateTestBoard.processGateCommands(commands);

        // Verify that gates were updated on both cells.
        // D2S/D3N - was closed, should now be open.
        assertTrue(gateTestBoard.isGateOpen(1, 3, SurgeDirection.SOUTH), "Gate D2S should be open.");
        assertTrue(gateTestBoard.isGateOpen(2, 3, SurgeDirection.NORTH), "Gate D3N should be open.");

        // B2E/C2W - was open, should now be closed.
        assertFalse(gateTestBoard.isGateOpen(1, 1, SurgeDirection.EAST), "Gate B2E should be closed.");
        assertFalse(gateTestBoard.isGateOpen(1, 2, SurgeDirection.WEST), "Gate C2W should be closed.");
    }

    @Test
    public void testGatesStaySameWithoutCommands() {
        Set<SurgeCommand> commands = new HashSet<>();  // Empty set, no commands

        String boardBefore = gateTestBoard.serializeBoardState();
        gateTestBoard.processGateCommands(commands);
        String boardAfter = gateTestBoard.serializeBoardState();

        assertEquals(boardBefore, boardAfter, "Board state should remain unchanged with no commands");
    }

/*
    // In-dev experimentation with the algo - testing basic movement.
    @Test
    public void flowTest1() {
        String flowBoardState =
                "1000:1:OC,0:0:CC,1000:1:CC|" +  // basic gate capacity
                "500:1:OC,500:1:CC,0:0:CC|" +    // steady-state, no move
                "700:1:OC,1000:1:CC,0:0:CC|" +   // push below edge capacity
                "1000:1:OC,0:0:OC,0:0:CC|" +      // 2-tile chain
                "0:0:OC,1000:1:OC,0:0:CC|" +     // 2-direction push
                "1300:1:OC,950:1:CC,0:0:CC|" +   // geyser oversaturation
                "1300:1:OC,1000:1:OC,500:1:CC|" + // geyser above push-through
                "1000:1:OO,500:1:CO,0:0:CC|" +   // 4-tile square
                "500:1:OC,0:0:CC,0:0:CC";

        String flowGeyserState = null;
        flowTestBoard = new SurgeBoard(7, 3, SurgeBoard.DEFAULT_COEFFS, mockLogger);
        flowTestBoard.deserialize(flowBoardState, flowGeyserState, "", "");
        flowTestBoard.buildMomentumMap(300);

        flowTestBoard.processUpdateStep(1);

        // verifications later.
    }

/*
    // In-dev experimentation - testing movement into combat.
    @Test
    public void flowTest2() {
        String flowBoardState =
            "500:1:OC,0:0:OC,600:2:CC|" +
            "800:1:OC,800:2:CC,0:0:CC|" +
            "600:1:OC,600:2:OC,600:3:CC";
        String flowGeyserState = null;
        flowTestBoard = new SurgeBoard(3, 3, SurgeBoard.DEFAULT_COEFFS, mockLogger);
        flowTestBoard.deserialize(flowBoardState, flowGeyserState);

        flowTestBoard.processUpdateStep(300, 3);
    }

*/



//In-dev experimentation with the algo
    @Test
    public void testCombat() {
        // A few basic 2-player combat tests.  Just looking at logs before validating.
        Map<Integer, Integer> test0 = Map.of(1, 400);
        Map<Integer, Integer> test1 = Map.of(1, 300, 2, 300);
        Map<Integer, Integer> test2 = Map.of(1, 1000, 2, 100);
        Map<Integer, Integer> test3 = Map.of(1, 400, 2, 500);
        Map<Integer, Integer> test4 = Map.of(1, 700, 2, 500);
        Map<Integer, Integer> test5 = Map.of(1, 700, 2, 300);

        SurgeBoard board = new SurgeBoard(1, 1, SurgeBoard.PROD_COEFFS, mockLogger);
        SurgeBoard.Army result0 = board.resolveCombat(test0, 0);
        SurgeBoard.Army result1 = board.resolveCombat(test1, 0);
        SurgeBoard.Army result1b = board.resolveCombat(test1, 1);
        SurgeBoard.Army result1c = board.resolveCombat(test1, 2);
        SurgeBoard.Army result2 = board.resolveCombat(test2, 0);
        SurgeBoard.Army result2b = board.resolveCombat(test2, 1);
        SurgeBoard.Army result2c = board.resolveCombat(test2, 2);
        SurgeBoard.Army result3 = board.resolveCombat(test3,0);
        SurgeBoard.Army result3b = board.resolveCombat(test3,1);
        SurgeBoard.Army result3c = board.resolveCombat(test3,2);
        SurgeBoard.Army result4 = board.resolveCombat(test4,0);
        SurgeBoard.Army result4b = board.resolveCombat(test4,1);
        SurgeBoard.Army result4c = board.resolveCombat(test4,2);
        SurgeBoard.Army result5 = board.resolveCombat(test5,0);
        SurgeBoard.Army result5b = board.resolveCombat(test5,1);
        SurgeBoard.Army result5c = board.resolveCombat(test5,2);

        // Multi-player combat testing
        Map<Integer, Integer> test6 = Map.of(1, 1000, 2, 100,3, 100);
        Map<Integer, Integer> test7 = Map.of(1, 700, 2, 300, 3, 200);
        Map<Integer, Integer> test8 = Map.of(1, 500, 2, 200, 3, 500);
        Map<Integer, Integer> test9 = Map.of(1, 200, 2, 300, 3, 400, 4, 500);
        Map<Integer, Integer> test10 = Map.of(1, 100,2, 200, 3, 1000, 4, 400);
        Map<Integer, Integer> test11 = Map.of(1, 1000, 2, 600, 3, 300, 4, 100);

        SurgeBoard.Army result6 = board.resolveCombat(test6, 0);
        SurgeBoard.Army result6b = board.resolveCombat(test6, 1);
        SurgeBoard.Army result6c = board.resolveCombat(test6, 2);
        SurgeBoard.Army result7 = board.resolveCombat(test7, 0);
        SurgeBoard.Army result7b = board.resolveCombat(test7, 1);
        SurgeBoard.Army result8 = board.resolveCombat(test8, 0);
        SurgeBoard.Army result8b = board.resolveCombat(test8, 1);

        SurgeBoard.Army result9 = board.resolveCombat(test9, 0);
        SurgeBoard.Army result9b = board.resolveCombat(test9, 3);
        SurgeBoard.Army result9c = board.resolveCombat(test9, 4);
        SurgeBoard.Army result10 = board.resolveCombat(test10, 0);
        SurgeBoard.Army result10b = board.resolveCombat(test10, 3);
        SurgeBoard.Army result11 = board.resolveCombat(test11, 0);
        SurgeBoard.Army result11b = board.resolveCombat(test11, 1);

    }



}
