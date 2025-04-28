package com.pbemgs.game.rpg.combat.status;

import com.pbemgs.game.rpg.combat.CombatLog;
import com.pbemgs.game.rpg.combat.LogEvent;
import com.pbemgs.game.rpg.combat.LogLevel;

import static com.pbemgs.game.rpg.model.RpgConstants.INFINITE;

/**
 * Status Effect that affects aggro level (chance to target).  These are multiplicative.
 * Always time-based - no concept of "limited charges" here.
 */
public class AggroModificationSE extends StatusEffect {
    float multiplier;  // scale to apply - 50% = 0.5f, 200% = 2.0f

    public AggroModificationSE(String name, Integer sourceId, boolean beneficial, int duration,
                               Classification classification, float multiplier) {
        super(name, beneficial, duration, INFINITE, classification);
        this.multiplier = multiplier;
        setSourceId(sourceId);
    }

    public float getAggroMultiplier(CombatLog logger) {
        logger.log(LogEvent.SE, LogLevel.DEBUG, "- Fetching Aggro Multiplier of " + multiplier + "x from: " + getName() + ".");
        return multiplier;
    }
}
