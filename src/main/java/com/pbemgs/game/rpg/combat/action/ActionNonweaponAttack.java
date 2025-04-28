package com.pbemgs.game.rpg.combat.action;

import com.pbemgs.game.rpg.combat.CombatChar;
import com.pbemgs.game.rpg.combat.CombatLog;
import com.pbemgs.game.rpg.combat.DamagePacket;
import com.pbemgs.game.rpg.combat.DamageSource;
import com.pbemgs.game.rpg.combat.DamageType;
import com.pbemgs.game.rpg.combat.LogEvent;
import com.pbemgs.game.rpg.combat.LogLevel;
import com.pbemgs.game.rpg.combat.TargetType;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.Set;

import static com.pbemgs.game.rpg.model.RpgConstants.LEVEL_SCALING;

/**
 * A non-weapon attack (spell, claws, etc).  Damage packet is based on integral values - a base quantity and
 * addition per character level.  Damage types, pierce/crush are all specified.
 */
public class ActionNonweaponAttack extends Action {

    private final float baseQuantity;
    private final Set<DamageType> damageTypes;
    private final float piercePct; // 0..1
    private final float crushPct;  // 0..1

    public ActionNonweaponAttack(String name, TargetType targetType, int splashCount, int chanceToHit,
                                 float baseQuantity, Set<DamageType> damageTypes,
                                 float piercePct, float crushPct, ActionConditional condition) {
        super(name, ActionType.NONWEAPON_ATTACK, false, targetType, splashCount, chanceToHit, condition);
        this.baseQuantity = baseQuantity;
        this.damageTypes = damageTypes;
        this.piercePct = piercePct;
        this.crushPct = crushPct;
    }

    public List<CombatChar> process(CombatChar actor, CombatChar target, boolean isCardAction,
                                    Queue<TriggeredAction> actionQueue, CombatLog logger) {
        logger.log(LogEvent.ATTACK, LogLevel.DEBUG, "Non-Weapon Attack - " + actor.getName() + " -> " + target.getName() + ".");
        DamagePacket dp = getNonWeaponDP(actor, logger);
        dp.adjustPiercePct(piercePct);
        dp.adjustCrushPct(crushPct);
        logger.log(LogEvent.ATTACK, LogLevel.DEV, "Base Damage Packet: " + dp.toString());

        dp = processDamagePacketMods(actor, target, dp, logger);
        applyDamagePacket(target, dp, logger);
        checkDamageTriggers(actor, target, dp, isCardAction, actionQueue, logger);
        return new ArrayList<>();
    }

    private DamagePacket getNonWeaponDP(CombatChar actor, CombatLog logger) {
        int amount = Math.round(baseQuantity * (1.0f + LEVEL_SCALING * actor.getLevel()));
        return new DamagePacket(amount, DamageSource.DIRECT, damageTypes);
    }

}
