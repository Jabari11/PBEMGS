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
 * A non-weapon heal (spell).  Damage packet is based on a base quantity and
 * addition per character level.  Constructor takes positive numbers, the damage packet itself is negative and
 * always fully piercing.
 */
public class ActionHeal extends Action {

    private final float baseQuantity;

    public ActionHeal(String name, TargetType targetType, int splashCount, int chanceToHit,
                      float baseQuantity, ActionConditional condition) {
        super(name, ActionType.HEALING, true, targetType, splashCount, chanceToHit, condition);
        this.baseQuantity = baseQuantity;
    }

    public List<CombatChar> process(CombatChar actor, CombatChar target, boolean isCardAction,
                                    Queue<TriggeredAction> actionQueue, CombatLog logger) {
        logger.log(LogEvent.ATTACK, LogLevel.DEBUG, "Non-Weapon Heal - " + actor.getName() + " -> " + target.getName() + ".");
        DamagePacket dp = getNonWeaponHealingDP(actor);
        logger.log(LogEvent.ATTACK, LogLevel.DEV, "Base Damage Packet: " + dp.toString());

        dp = processDamagePacketMods(actor, target, dp, logger);
        applyDamagePacket(target, dp, logger);
        checkDamageTriggers(actor, target, dp, isCardAction, actionQueue, logger);
        return new ArrayList<>();
    }

    private DamagePacket getNonWeaponHealingDP(CombatChar actor) {
        // Healing "Damage Packets" are negative damage.
        int amount = Math.round(baseQuantity * (1.0f + LEVEL_SCALING * actor.getLevel()));
        return new DamagePacket(-amount, DamageSource.DIRECT, Set.of(DamageType.HEALING), 1.0f, 0.0f);
    }

}
