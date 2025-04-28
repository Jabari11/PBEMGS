package com.pbemgs.game.triad;

import com.pbemgs.model.MonoSymbol;

public class TriadDisplayDefs {
    public static final String TOP_BORDER = String.valueOf(MonoSymbol.GRID_TOP_LEFT.getSymbol()) +
            MonoSymbol.GRID_HORIZONTAL.getSymbol() + MonoSymbol.GRID_HORIZONTAL.getSymbol() +
            MonoSymbol.GRID_HORIZONTAL.getSymbol() + MonoSymbol.GRID_TOP_RIGHT.getSymbol();
    public static final String BOT_BORDER = String.valueOf(MonoSymbol.GRID_BOTTOM_LEFT.getSymbol()) +
            MonoSymbol.GRID_HORIZONTAL.getSymbol() + MonoSymbol.GRID_HORIZONTAL.getSymbol() +
            MonoSymbol.GRID_HORIZONTAL.getSymbol() + MonoSymbol.GRID_BOTTOM_RIGHT.getSymbol();
    public static final String BLANK_INNER = String.valueOf(MonoSymbol.GRID_VERTICAL.getSymbol()) + "   " +
            MonoSymbol.GRID_VERTICAL.getSymbol();
    public static final char SHADING_CHAR = MonoSymbol.LIGHT_SHADE.getSymbol();


}
