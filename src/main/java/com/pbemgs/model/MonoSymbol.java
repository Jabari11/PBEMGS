package com.pbemgs.model;

public enum MonoSymbol {
    // Squares
    BLACK_SQUARE("Black Square", '\u25A0'),
    WHITE_SQUARE("White Square", '\u25A1'),
    INVERSE_CIRCLE("Inverse Circle", '\u25D9'),
    BLACK_LOZENGE("Black Lozenge", '\u25CA'),

    // Circles
    BLACK_CIRCLE("Black Circle", '\u25CF'),
    WHITE_CIRCLE("White Circle", '\u25CB'),
    MIDDLE_DOT("Middle Dot", '\u00B7'),
    BULLET("Bullet", '\u2022'),
    WHITE_BULLET("White Bullet", '\u25E6'),
    BULLET_OPERATOR("Bullet Operator", '\u2219'),

    // Triangles
    BLACK_TRIANGLE_UP("Black Triangle Up", '\u25B2'),
    BLACK_TRIANGLE_DOWN("Black Triangle Down", '\u25BC'),
    BLACK_TRIANGLE_RIGHT("Black Triangle Right", '\u25BA'),
    BLACK_TRIANGLE_LEFT("Black Triangle Left", '\u25C4'),

    // Other math symbology
    ALMOST_EQUAL_TO("Almost Equal To", '\u2248'),
    IDENTICAL_TO("Identical To", '\u2261'),
    CURRENCY("Currency", '\u00A4'),
    BIG_NULL("Big Null", '\u00D8'),
    SMALL_NULL("Small Null", '\u00F8'),
    THETA("Theta", '\u03F4'),

    // Block chars
    LIGHT_SHADE("Light Shade", '\u2591'),
    MEDIUM_SHADE("Medium Shade", '\u2592'),
    DARK_SHADE("Dark Shade", '\u2593'),
    FULL_SHADE("Full Shade", '\u2588'),

    // Arrows
    LEFTWARDS_ARROW("Left Arrow", '\u2190'),
    UPWARDS_ARROW("Up Arrow", '\u2191'),
    RIGHTWARDS_ARROW("Right Arrow", '\u2192'),
    DOWNWARDS_ARROW("Down Arrow", '\u2193'),
    LEFT_RIGHT_ARROW("Left-Right Arrow", '\u2194'),
    UP_DOWN_ARROW("Up-Down Arrow", '\u2195'),

    // Misc
    HOUSE("House", '\u2302'),
    DOUBLE_DAGGER("Double Dagger", '\u2021'),
    HORIZONTAL_BAR("Horizontal Bar", '\u2015'),
    DOUBLE_HORIZONTAL("Double Horizontal", '\u2550'),
    UP_DOWN_PIPE_DOUBLE("Up-Down Double Pipe", '\u2551'),

    // Grid chars
    GRID_HORIZONTAL("Horizontal Line", '\u2500'),
    GRID_VERTICAL("Vertical Line", '\u2502'),
    GRID_TOP_LEFT("Top Left Corner", '\u250C'),
    GRID_TOP_RIGHT("Top Right Corner", '\u2510'),
    GRID_BOTTOM_LEFT("Bottom Left Corner", '\u2514'),
    GRID_BOTTOM_RIGHT("Bottom Right Corner", '\u2518'),
    GRID_T_DOWN("T-Intersection Down", '\u252C'),
    GRID_T_UP("T-Intersection Up", '\u2534'),
    GRID_T_LEFT("T-Intersection Left", '\u251C'),
    GRID_T_RIGHT("T-Intersection Right", '\u2524'),
    GRID_CROSS("Cross Intersection", '\u253C'),

    ;

    private final String name;
    private final char symbol;

    MonoSymbol(String name, char symbol) {
        this.name = name;
        this.symbol = symbol;
    }

    public String getName() {
        return name;
    }

    public char getSymbol() {
        return symbol;
    }
}
