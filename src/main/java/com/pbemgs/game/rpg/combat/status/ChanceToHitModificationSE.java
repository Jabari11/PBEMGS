package com.pbemgs.game.rpg.combat.status;

import com.pbemgs.game.rpg.combat.CombatLog;
import com.pbemgs.game.rpg.combat.LogEvent;
import com.pbemgs.game.rpg.combat.LogLevel;
import com.pbemgs.game.rpg.combat.action.ActionType;

import java.util.Set;

/**
 * Status Effect modifies the chance to hit (or be hit).
 */
public class ChanceToHitModificationSE extends StatusEffect {
    boolean actorMod;          // true if this applies from the attacking side, false if it applies to the defending side.
    int scaling;               // how much to adjust the chance to hit by (integer percentage - 1 = 1%)
    Set<ActionType> affectedSet; // Set of Action Types adjusted

    public ChanceToHitModificationSE(String name, Integer sourceId, boolean beneficial, int duration, int charges,
                                     Classification classification,
                                     boolean actorMod, int scaling, Set<ActionType> affectedSet) {
        super(name, beneficial, duration, charges, classification);
        this.actorMod = actorMod;
        this.scaling = scaling;
        this.affectedSet = affectedSet;
        setSourceId(sourceId);
    }

    public int applyToHitModifier(ActionType actionType, boolean isActor, CombatLog logger) {
        if (isActive() && isActor == actorMod) {
            if (affectedSet.contains(ActionType.ALL) || affectedSet.contains(actionType)) {
                logger.log(LogEvent.SE, LogLevel.DEBUG, "- Fetching To-Hit modifier of " + scaling + " from: " + getName() + ".");
                reportChargeUsage();
                return scaling;
            }
        }
        return 0;
    }
}
