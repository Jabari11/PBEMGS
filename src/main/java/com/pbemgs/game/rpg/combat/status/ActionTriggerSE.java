package com.pbemgs.game.rpg.combat.status;

import com.pbemgs.game.rpg.combat.CombatLog;
import com.pbemgs.game.rpg.combat.DamageType;
import com.pbemgs.game.rpg.combat.LogEvent;
import com.pbemgs.game.rpg.combat.LogLevel;
import com.pbemgs.game.rpg.combat.TargetType;
import com.pbemgs.game.rpg.combat.action.Action;
import com.pbemgs.game.rpg.combat.action.ActionType;
import com.pbemgs.game.rpg.combat.action.TriggeredAction;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * A reaction ("triggered effect") status effect.
 * This has the reaction type, filters, and list of Actions to perform along with the action metadata
 * (targetType or specific target).
 */
public class ActionTriggerSE extends StatusEffect {

    public enum TriggerTypes {ON_HIT, ON_GET_HIT}

    List<Action> actions;
    TriggerTypes triggerType;
    Set<ActionType> actionFilter;
    Set<DamageType> damageTypeFilter;

    public ActionTriggerSE(String name, int sourceId, boolean beneficial, int duration, int charges,
                           Classification classification, TriggerTypes triggerType, Set<ActionType> actionFilter,
                           Set<DamageType> damageTypeFilter, List<Action> actionList) {
        super(name, beneficial, duration, charges, classification);
        this.actions = actionList;
        this.triggerType = triggerType;
        this.actionFilter = actionFilter;
        this.damageTypeFilter = damageTypeFilter;
        this.setSourceId(sourceId);
    }

    /**
     * Check if this SE applies to the given triggering info.  If so, return the action to queue with the forced target.
     */
    public List<TriggeredAction> checkReaction(TriggerTypes type, ActionType actionType, Set<DamageType> damageTypes,
                                               int thisCharUnitId, int otherCharUnitId, CombatLog logger) {
        List<TriggeredAction> results = new ArrayList<>();
        if (triggerType.equals(type)) {
            if (actionFilter.contains(ActionType.ALL) || actionFilter.contains(actionType)) {
                if (damageTypes == null || damageTypeFilter == null || hasOverlap(damageTypes, damageTypeFilter)) {
                    logger.log(LogEvent.TRIGGER, LogLevel.DEV, "-- Matching action trigger " + getName() + " - adding to action queue.");
                    for (Action react : actions) {
                        if (react.getTargetType().equals(TargetType.REACTION)) {
                            results.add(new TriggeredAction(react, thisCharUnitId, false, otherCharUnitId));
                        } else {
                            results.add(new TriggeredAction(react, thisCharUnitId, false, null));
                        }
                    }
                    reportChargeUsage();
                }
            }
        }
        return results;
    }

    private static <T> boolean hasOverlap(Set<T> a, Set<T> b) {
        if (a.isEmpty() || b.isEmpty()) return false;
        Set<T> small = a.size() <= b.size() ? a : b;
        Set<T> big = a.size() > b.size() ? a : b;
        return small.stream().anyMatch(big::contains);
    }
}
