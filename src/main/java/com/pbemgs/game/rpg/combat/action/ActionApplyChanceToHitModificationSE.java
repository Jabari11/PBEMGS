package com.pbemgs.game.rpg.combat.action;

import com.pbemgs.game.rpg.combat.CombatChar;
import com.pbemgs.game.rpg.combat.TargetType;
import com.pbemgs.game.rpg.combat.status.ChanceToHitModificationSE;
import com.pbemgs.game.rpg.combat.status.StatusEffect;

import java.util.Set;

public class ActionApplyChanceToHitModificationSE extends ActionApplyStatus {

    private final int scaling;               // how much to adjust the chance to hit by (integer percentage - 1 = 1%)
    private final Set<ActionType> affectedSet; // Set of Action Types adjusted

    public ActionApplyChanceToHitModificationSE(String name, TargetType targetType, boolean isFriendly, int splashCount, int chanceToHit,
                                                String seName, StatusEffect.Classification classification, boolean appliesAsActor,
                                                int duration, int charges, int scaling, Set<ActionType> affectedSet,
                                                ActionConditional condition) {
        super(name, targetType, isFriendly, splashCount, chanceToHit, seName, classification, appliesAsActor,
                duration, charges, condition);
        this.scaling = scaling;
        this.affectedSet = affectedSet;
    }

    @Override
    public StatusEffect createStatusEffect(CombatChar actor) {
        return new ChanceToHitModificationSE(seName, actor.getUnitId(), friendly, duration, charges,
                classification, appliesAsActor, scaling, affectedSet);
    }
}
