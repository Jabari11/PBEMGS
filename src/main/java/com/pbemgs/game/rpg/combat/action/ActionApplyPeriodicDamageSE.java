package com.pbemgs.game.rpg.combat.action;

import com.pbemgs.game.rpg.combat.CombatChar;
import com.pbemgs.game.rpg.combat.DamagePacket;
import com.pbemgs.game.rpg.combat.DamageSource;
import com.pbemgs.game.rpg.combat.DamageType;
import com.pbemgs.game.rpg.combat.TargetType;
import com.pbemgs.game.rpg.combat.status.PeriodicDamageSE;
import com.pbemgs.game.rpg.combat.status.StatusEffect;

import java.util.Set;

import static com.pbemgs.game.rpg.model.RpgConstants.INFINITE;
import static com.pbemgs.game.rpg.model.RpgConstants.LEVEL_SCALING;

/**
 * Creates an action to apply a DoT/HoT.  Heals need to be sent negative quantity here!
 */
public class ActionApplyPeriodicDamageSE extends ActionApplyStatus {

    private final float baseAmount;
    private final Set<DamageType> damageTypes;
    private final float piercePct;
    private final float crushPct;

    public ActionApplyPeriodicDamageSE(String name, TargetType targetType, boolean isFriendly, int splashCount, int chanceToHit,
                                       String seName, StatusEffect.Classification classification,
                                       int duration, float baseAmount, Set<DamageType> damageTypes,
                                       float piercePct, float crushPct, ActionConditional condition) {
        super(name, targetType, isFriendly, splashCount, chanceToHit, seName, classification, true,
                duration, INFINITE, condition);
        this.baseAmount = baseAmount;
        this.damageTypes = damageTypes;
        this.piercePct = piercePct;
        this.crushPct = crushPct;
    }

    @Override
    public StatusEffect createStatusEffect(CombatChar actor) {
        int dpAmount = Math.round(baseAmount * (1.0f + LEVEL_SCALING * actor.getLevel()));
        return new PeriodicDamageSE(seName, actor.getUnitId(), friendly, duration, classification,
                new DamagePacket(dpAmount, DamageSource.PERIODIC, damageTypes, piercePct, crushPct));
    }
}
