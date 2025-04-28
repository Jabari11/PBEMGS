package com.pbemgs.game.rpg.combat.status;

/**
 * Status Effect that affects the chance to act at all for a round.
 */
public class ActionPreventionSE extends StatusEffect {
    int stunChance;  // percentage chance to apply

    public ActionPreventionSE(String name, Integer sourceId, int duration, int charges,
                              Classification classification, int stunChance) {
        super(name, false, duration, charges, classification);
        this.stunChance = stunChance;
        setSourceId(sourceId);
    }

    public int getStunChance() {
        if (isActive()) {
            return stunChance;
        }
        return 0;
    }
}
