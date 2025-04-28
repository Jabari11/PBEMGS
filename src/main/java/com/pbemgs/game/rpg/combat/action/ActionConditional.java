package com.pbemgs.game.rpg.combat.action;

import com.pbemgs.game.rpg.combat.CombatChar;
import com.pbemgs.game.rpg.model.ResourceType;

/**
 *  Data for when an Action is conditional on the state of either the actor or target.
 *  Checks are done as (resource >= threshold) either true or false. (So, >= or <).
 *  Percentage thresholds are integer resolution.  (i.e. 5% is fine, 5.5% is not available.)
 */
public class ActionConditional {
    private final boolean conditionalOnActor;  // actor vs target
    private final ResourceType conditionalOn;
    private final boolean validIfGreaterEqual;
    private final int threshold;
    private final boolean thresholdIsPercentage;

    public ActionConditional(boolean conditionalOnActor, ResourceType conditionalOn, boolean validIfGreaterEqual,
                             int threshold, boolean thresholdIsPercentage) {
        this.conditionalOnActor = conditionalOnActor;
        this.conditionalOn = conditionalOn;
        this.validIfGreaterEqual = validIfGreaterEqual;
        this.threshold = threshold;
        this.thresholdIsPercentage = thresholdIsPercentage;
    }

    public boolean isActionValid(CombatChar actor, CombatChar target) {
        CombatChar checkChar = conditionalOnActor ? actor : target;
        int currVal = checkChar.getResourceVal(conditionalOn);
        float currPct = checkChar.getResourcePct(conditionalOn);
        if (thresholdIsPercentage) {
            return (currPct >= (float)threshold * 0.01f) == validIfGreaterEqual;
        }
        return (currVal >= threshold) == validIfGreaterEqual;
    }

}
