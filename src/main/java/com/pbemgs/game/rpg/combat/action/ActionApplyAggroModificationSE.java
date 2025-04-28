package com.pbemgs.game.rpg.combat.action;

import com.pbemgs.game.rpg.combat.CombatChar;
import com.pbemgs.game.rpg.combat.TargetType;
import com.pbemgs.game.rpg.combat.status.AggroModificationSE;
import com.pbemgs.game.rpg.combat.status.StatusEffect;

import static com.pbemgs.game.rpg.model.RpgConstants.INFINITE;

public class ActionApplyAggroModificationSE extends ActionApplyStatus {

    float multiplier;

    public ActionApplyAggroModificationSE(String name, TargetType targetType, boolean isFriendly, int splashCount, int chanceToHit,
                                          String seName, StatusEffect.Classification classification,
                                          int duration, float multiplier, ActionConditional condition) {
        super(name, targetType, isFriendly, splashCount, chanceToHit, seName, classification, true,
                duration, INFINITE, condition);
        this.multiplier = multiplier;
    }

    @Override
    public StatusEffect createStatusEffect(CombatChar actor) {
        return new AggroModificationSE(seName, actor.getUnitId(), friendly, duration,  classification, multiplier);
    }
}
