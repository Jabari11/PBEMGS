package com.pbemgs.game.ironclad;

import com.pbemgs.model.MonoSymbol;

public class IroncladDefinitions {
    public static final String HEADER_BIG = "      A   B   C   D   E   F\n";
    public static final String BIG_INTER  = "    a   b   c   d   e   f   g\n";
    public static final String HEADER_ARR = "   " + generateHeaderArrows(MonoSymbol.DOWNWARDS_ARROW.getSymbol()) + "\n";
    public static final String FOOTER_ARR = "   " + generateHeaderArrows(MonoSymbol.UPWARDS_ARROW.getSymbol()) + "\n";

    private static String generateHeaderArrows(char c) {
        StringBuilder sb = new StringBuilder();
        for (int x = 0; x < 6; ++x) {
            sb.append("   ").append(c);
        }
        return sb.toString();
    }

}
