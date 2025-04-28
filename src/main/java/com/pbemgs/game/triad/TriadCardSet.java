package com.pbemgs.game.triad;

import java.util.HashMap;
import java.util.Map;

/**
 * Card Set definition for Triad Cubed.
 */
public enum TriadCardSet {
    CARD_1(1, "Kobold", new int[]{1, 2, 5, 2}, TriadElement.NONE),
    CARD_2(2, "Slime", new int[]{4, 2, 1, 5}, TriadElement.FIRE),
    CARD_3(3, "Goblin", new int[]{3, 3, 6, 1}, TriadElement.NONE),
    CARD_4(4, "Ghoul", new int[]{2, 5, 3, 4}, TriadElement.LIT),
    CARD_5(5, "Imp", new int[]{6, 4, 2, 3}, TriadElement.ICE),
    CARD_6(6, "Wolf", new int[]{5, 1, 3, 7}, TriadElement.NONE),
    CARD_7(7, "Yeti", new int[]{7, 3, 6, 2}, TriadElement.ICE),
    CARD_8(8, "Orc", new int[]{2, 8, 4, 6}, TriadElement.FIRE),
    CARD_9(9, "Wyvern", new int[]{3, 7, 7, 4}, TriadElement.NONE),
    CARD_10(10, "Zephyr", new int[]{9, 6, 2, 5}, TriadElement.LIT),
    CARD_11(11, "Wraith", new int[]{5, 4, 8, 6}, TriadElement.LIT),
    CARD_12(12, "Cyclops", new int[]{8, 6, 7, 3}, TriadElement.NONE),
    CARD_13(13, "Chimera", new int[]{4, 5, 9, 7}, TriadElement.ICE),
    CARD_14(14, "Dragon", new int[]{7, 7, 4, 9}, TriadElement.FIRE),
    CARD_15(15, "Titan", new int[]{6, 9, 5, 8}, TriadElement.NONE);

    private static final Map<Integer, TriadCard> ID_MAP = new HashMap<>();
    private static final Map<String, TriadCard> NAME_MAP = new HashMap<>();

    static {
        for (TriadCardSet card : values()) {
            ID_MAP.put(card.cardId, card.toTriadCard());
            NAME_MAP.put(card.name.toLowerCase(), card.toTriadCard());
        }
    }

    private final int cardId;
    private final String name;
    private final int[] values;
    private final TriadElement element;

    TriadCardSet(int cardId, String name, int[] values, TriadElement element) {
        this.cardId = cardId;
        this.name = name;
        this.values = values;
        this.element = element;
    }

    public TriadCard toTriadCard() {
        return new TriadCard(cardId, name, values, element);
    }

    public static TriadCard getById(int id) {
        return ID_MAP.get(id);
    }

    public static TriadCard getByName(String name) {
        return NAME_MAP.get(name.toLowerCase());
    }
}
