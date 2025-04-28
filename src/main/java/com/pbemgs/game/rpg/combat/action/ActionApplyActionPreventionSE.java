package com.pbemgs.game.rpg.combat.action;

import com.pbemgs.game.rpg.combat.CombatChar;
import com.pbemgs.game.rpg.combat.TargetType;
import com.pbemgs.game.rpg.combat.status.ActionPreventionSE;
import com.pbemgs.game.rpg.combat.status.StatusEffect;

public class ActionApplyActionPreventionSE extends ActionApplyStatus {

    private final int stunChance;

    public ActionApplyActionPreventionSE(String name, TargetType targetType, int splashCount, int chanceToHit,
                                         String seName, StatusEffect.Classification classification,
                                         int duration, int charges, int stunChance, ActionConditional condition) {
        super(name, targetType, false, splashCount, chanceToHit, seName, classification, true,
                duration, charges, condition);
        this.stunChance = stunChance;
    }

    @Override
    public StatusEffect createStatusEffect(CombatChar actor) {
        return new ActionPreventionSE(seName, actor.getUnitId(), duration, charges, classification, stunChance);
    }
}
