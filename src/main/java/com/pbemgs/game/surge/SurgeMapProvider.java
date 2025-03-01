package com.pbemgs.game.surge;

import com.pbemgs.model.Location;

import java.util.List;
import java.util.Random;

public class SurgeMapProvider {
    public record SurgeMap(int rows, int cols, List<Location> playerStarts, String geysers,
                           List<String> obstacles, int maxCommands) {}

    public static SurgeMap getMap(int numPlayers, int maxCommands) {
        return switch (numPlayers) {
            case 2 -> getMap2(maxCommands);
            case 3 -> getMap3(maxCommands);
            case 4 -> getMap4(maxCommands);
            default -> null;
        };
    }

    public static final SurgeMap MAP_2P_1 = new SurgeMap(8, 8, List.of(new Location(0, 3), new Location(7, 4)),
            "A1:S,G1:S,A5:S,D5:S,E4:S,H4:S,B8:S,H8:S",
            List.of("H1","B2","F4","C5", "G7", "A8"),
            6);
    public static final SurgeMap MAP_2P_2 = new SurgeMap(9, 9, List.of(new Location(0, 8), new Location(8, 0)),
            "F1:S,A4:M,E4:S,E6:S,D5:S,F5:S,I6:M,D9:S",
            List.of("A1","B1","A2","A3","F3","G4","C6","D7","I7","I8","H9","I9"),
            3);
    public static final SurgeMap MAP_2P_3 = new SurgeMap(10, 6, List.of(new Location(0, 3), new Location(9, 2)),
            "A1:S,F1:M,B3:S,C5:S,D6:S,E8:S,F10:S,A10:M",
            List.of("C3","E4","A5","F6","B7","D8"),
            4);
    public static final SurgeMap MAP_2P_4 = new SurgeMap(7, 11, List.of(new Location(2, 5), new Location(4, 5)),
            "C1:M,I1:M,E3:S,G3:S,A4:M,K4:M,E5:S,G5:S,C7:M,I7:M",
            List.of("E4","F4","G4","C4","I4"),
            5);
    public static final SurgeMap MAP_2P_5 = new SurgeMap(7, 7, List.of(new Location(6, 0), new Location(0, 6)),
            "A3:M,G5:M,D4:M,D1:S,D7:S",
            List.of("A1","G7"),
            6);
    public static final SurgeMap MAP_2P_6 = new SurgeMap(8, 8, List.of(new Location(0, 7), new Location(7, 0)),
            "F3:M,C6:M,C1:S,A3:S,C3:S,H6:S,F6:S,F8:S",
            List.of("A1","B1","A2","D1","A4","H5","E8","G8","H8","H7"),
            5);

    private static SurgeMap getMap2(int maxCommands) {
        List<SurgeMap> maps = List.of(MAP_2P_1, MAP_2P_2, MAP_2P_3, MAP_2P_4, MAP_2P_5, MAP_2P_6);
        List<SurgeMap> filtMaps = maps.stream().filter(m -> maxCommands <= m.maxCommands()).toList();
        if (filtMaps.isEmpty()) {
            return null;
        }

        final Random rng = new Random();
        return filtMaps.get(rng.nextInt(filtMaps.size()));
    }

    public static final SurgeMap MAP_3P_1 = new SurgeMap(15, 12,
            List.of(new Location(1, 3), new Location(8, 10), new Location(11, 1)),
            "D8:L,A3:M,L6:M,C15:M,B6:S,H5:S,G11:S",
            List.of("G1","H1","I1","J1","K1","L1","I2","J2","K2","L2","K3","L3","L4",
                    "L11","K12","L12","I13","J13","K13","L13","G14","H14","I14","J14","K14","L14",
                    "F15","G15","H15","I15","J15","K15","L15"),
            6);


    private static SurgeMap getMap3(int maxCommands) {
        List<SurgeMap> maps = List.of(MAP_3P_1);
        List<SurgeMap> filtMaps = maps.stream().filter(m -> maxCommands <= m.maxCommands()).toList();
        if (filtMaps.isEmpty()) {
            return null;
        }

        final Random rng = new Random();
        return filtMaps.get(rng.nextInt(filtMaps.size()));
    }

    public static final SurgeMap MAP_4P_1 = new SurgeMap(13, 13,
            List.of(new Location(2, 2), new Location(2, 10),
                    new Location(10, 2), new Location(10, 10)),
            "A1:L,M1:L,A13:L,M13:L," +
                    "G2:S,B7:S,L7:S,G12:S," +
                    "E5:M,I5:M,E9:M,I9:M",
            List.of("D4","E4","D5","I4","J4","J5","G7","D9","D10","E10","J9","J10","I10"),
            5);

    public static final SurgeMap MAP_4P_2 = new SurgeMap(11, 11,
            List.of(new Location(0, 0), new Location(0, 10),
                    new Location(10, 0), new Location(10, 10)),
            "D1:S,K4:S,I11:S,A8:S,G1:M,K7:M,E11:M,A5:M,D4:S,H4:S,H8:S,D8:S",
            List.of("D3","C4","H3","I4","C8","D9","H9","I8"),
            3);

    public static final SurgeMap MAP_4P_3 = new SurgeMap(12, 12,
            List.of(new Location(0, 6), new Location(5, 0),
                    new Location(6, 11), new Location(11, 5)),
            "A1:L,L1:L,A12:L,L12:L,D4:S,I4:S,D9:S,I9:S,F5:M,H6:M,E7:M,G8:M",
            List.of("C4","D3","I3","J4","C9","D10","I10","J9"),
            6);

    public static final SurgeMap MAP_4P_4 = new SurgeMap(11, 11,
            List.of(new Location(0, 5), new Location(5, 0),
                    new Location(5, 10), new Location(10, 5)),
            "F2:S,B6:S,J6:S,F10:S,C3:S,I3:S,C9:S,I9:S,F6:L",
            List.of("A1","B1","C1","A2","B2","A3","I1","J1","K1","J2","K2","K3",
                    "F4","D6","H6","F8",
                    "A9","A10","A11","B10","B11","C11","K9","K10","K11","J10","J11","I11"),
            6);


    private static SurgeMap getMap4(int maxCommands) {
        List<SurgeMap> maps = List.of(MAP_4P_1, MAP_4P_2, MAP_4P_3, MAP_4P_4);
        List<SurgeMap> filtMaps = maps.stream().filter(m -> maxCommands <= m.maxCommands()).toList();
        if (filtMaps.isEmpty()) {
            return null;
        }

        final Random rng = new Random();
        return filtMaps.get(rng.nextInt(filtMaps.size()));
    }


}
