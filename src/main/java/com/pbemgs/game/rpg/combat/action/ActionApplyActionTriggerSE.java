package com.pbemgs.game.rpg.combat.action;

import com.pbemgs.game.rpg.combat.CombatChar;
import com.pbemgs.game.rpg.combat.DamageType;
import com.pbemgs.game.rpg.combat.TargetType;
import com.pbemgs.game.rpg.combat.status.ActionTriggerSE;
import com.pbemgs.game.rpg.combat.status.StatusEffect;

import java.util.List;
import java.util.Set;

/**
 * Creates an action to apply a DoT/HoT.  Heals need to be sent negative quantity here!
 */
public class ActionApplyActionTriggerSE extends ActionApplyStatus {

    List<Action> actions;
    ActionTriggerSE.TriggerTypes triggerType;
    Set<ActionType> actionFilter;
    Set<DamageType> damageTypeFilter;

    public ActionApplyActionTriggerSE(String name, TargetType targetType, boolean isFriendly, int splashCount, int chanceToHit,
                                      String seName, StatusEffect.Classification classification,
                                      int duration, int charges, List<Action> actions, ActionTriggerSE.TriggerTypes triggerType,
                                      Set<ActionType> actionFilter, Set<DamageType> damageTypeFilter,
                                      ActionConditional condition) {
        super(name, targetType, isFriendly, splashCount, chanceToHit, seName, classification, true,
                duration, charges, condition);
        this.actions = actions;
        this.triggerType = triggerType;
        this.actionFilter = actionFilter;
        this.damageTypeFilter = damageTypeFilter;
    }

    @Override
    public StatusEffect createStatusEffect(CombatChar actor) {
        return new ActionTriggerSE(seName, actor.getUnitId(), friendly, duration, charges,
                classification, triggerType, actionFilter, damageTypeFilter, actions);
    }
}
