package com.pbemgs.game.rpg.combat.card;

import com.pbemgs.game.rpg.combat.action.Action;

import java.util.List;

public class Card {
    private final String name;
    private final int id;
    private final int spiritCost;
    private final int rageIncurred;

    private final List<Action> actions;
    private final boolean isCantrip;

    public Card(String name, int id,
                List<Action> actions, boolean isCantrip, int spiritCost, int rageIncurred) {
        this.name = name;
        this.id = id;
        this.spiritCost = spiritCost;
        this.rageIncurred = rageIncurred;
        this.actions = actions;
        this.isCantrip = isCantrip;
    }


    // TODO: Getters

    public String getName() {
        return name;
    }

    public int getSpiritCost() {
        return spiritCost;
    }

    public int getRageIncurred() {
        return rageIncurred;
    }

    public List<Action> getActions() {
        return actions;
    }

    public boolean isCantrip() {
        return isCantrip;
    }

}
