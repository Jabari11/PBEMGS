package com.pbemgs.game.ironclad;

import com.pbemgs.generated.tables.records.UsersRecord;
import com.pbemgs.model.MonoSymbol;

import java.util.List;

public enum IroncladSide {
    WHITE(MonoSymbol.WHITE_CIRCLE.getSymbol(), 'W',
            List.of(MonoSymbol.CURRENCY.getSymbol(), MonoSymbol.THETA.getSymbol(), MonoSymbol.BIG_NULL.getSymbol())),
    BLACK(MonoSymbol.BLACK_CIRCLE.getSymbol(), 'B',
            List.of(MonoSymbol.BLACK_TRIANGLE_UP.getSymbol(), MonoSymbol.INVERSE_CIRCLE.getSymbol(), MonoSymbol.BLACK_SQUARE.getSymbol())),
    BLANK(' ', '.', List.of(' ', ' ', ' '));

    private final char stoneDispChar;
    private final char serializeChar;
    private final List<Character> robotDispChars;

    IroncladSide(char stoneDispChar, char serializeChar, List<Character> robotDispChars) {
        this.stoneDispChar = stoneDispChar;
        this.serializeChar = serializeChar;
        this.robotDispChars = robotDispChars;
    }

    public char getStoneDisplayChar() {
        return stoneDispChar;
    }

    public char getStoneSerializeChar() {
        return serializeChar;
    }

    public char getRobotDisplayChar(int HP) {
        return robotDispChars.get(HP - 1);
    }


    public static IroncladSide deserialize(char c) {
        for (IroncladSide side : values()) {
            if (side.serializeChar == c) {
                return side;
            }
        }
        throw new IllegalArgumentException("Invalid ironclad side indicator character: " + c);
    }

}
