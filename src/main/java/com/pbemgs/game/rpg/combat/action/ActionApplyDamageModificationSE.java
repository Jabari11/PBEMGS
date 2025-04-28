package com.pbemgs.game.rpg.combat.action;

import com.pbemgs.game.rpg.combat.CombatChar;
import com.pbemgs.game.rpg.combat.DamageType;
import com.pbemgs.game.rpg.combat.TargetType;
import com.pbemgs.game.rpg.combat.status.DamageModificationSE;
import com.pbemgs.game.rpg.combat.status.StatusEffect;

import java.util.Set;

import static com.pbemgs.game.rpg.model.RpgConstants.LEVEL_SCALING;

public class ActionApplyDamageModificationSE extends ActionApplyStatus {

    private final float scalingMult;           // percentage to adjust the damage packet by (multiplicative)
    private final float scalarBase;            // additive scalar to adjust the damage packet by
    private final Set<DamageType> affectedTypes; // Set of Damage Classifications modified
    private final Set<DamageType> addedTypes; // Set of Damage Classifications to add

    public ActionApplyDamageModificationSE(String name, TargetType targetType, boolean isFriendly, int splashCount, int chanceToHit,
                                           String seName, StatusEffect.Classification classification, boolean appliesAsActor,
                                           int duration, int charges, Set<DamageType> affectedTypes, Set<DamageType> addedTypes,
                                           float scalingMult, float scalarBase, ActionConditional condition) {
        super(name, targetType, isFriendly, splashCount, chanceToHit, seName, classification, appliesAsActor,
                duration, charges, condition);
        this.scalingMult = scalingMult;
        this.scalarBase = scalarBase;
        this.affectedTypes = affectedTypes;
        this.addedTypes = addedTypes;
    }

    @Override
    public StatusEffect createStatusEffect(CombatChar actor) {
        int scalar = Math.round(scalarBase * (1.0f + LEVEL_SCALING * actor.getLevel()));
        return new DamageModificationSE(seName, actor.getUnitId(), friendly, duration, charges,
                classification, appliesAsActor, scalingMult, scalar, affectedTypes, addedTypes);
    }
}
