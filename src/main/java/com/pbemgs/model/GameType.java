package com.pbemgs.model;

public enum GameType {
    NONE("none"),
    TAC("Tac"),
    NINETAC("Ninetac"),
    ATAXX("Ataxx"),
    SURGE("Surge"),
    LOA("LOA"),
    GOMOKU("GoMoku"),
    TRIAD("Triad"),
    IRONCLAD("Ironclad");

    private final String gameName;

    // Constructor to set the gameName
    GameType(String gameName) {
        this.gameName = gameName;
    }

    // Getter to retrieve the gameName
    public String getGameName() {
        return gameName;
    }

    // Optional: Static method to get GameType from gameName (case-insensitive)
    public static GameType fromGameName(String gameName) {
        if (gameName == null) {
            return NONE;
        }
        for (GameType type : GameType.values()) {
            if (type.gameName.equalsIgnoreCase(gameName)) {
                return type;
            }
        }
        return NONE; // Default to NONE if no match is found
    }
}
