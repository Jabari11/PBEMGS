package com.pbemgs.game.rpg.combat.action;

import com.pbemgs.game.rpg.combat.CombatChar;
import com.pbemgs.game.rpg.combat.CombatLog;
import com.pbemgs.game.rpg.combat.DamagePacket;
import com.pbemgs.game.rpg.combat.DamageSource;
import com.pbemgs.game.rpg.combat.DamageType;
import com.pbemgs.game.rpg.combat.LogEvent;
import com.pbemgs.game.rpg.combat.LogLevel;
import com.pbemgs.game.rpg.combat.TargetType;
import com.pbemgs.game.rpg.model.WeaponType;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.Set;

/**
 * A weapon attack action.  Base damage is from the character's weapon type, with a multiplier.
 */
public class ActionWeaponAttack extends Action {

    private final float multiplier;

    public ActionWeaponAttack(String name, TargetType targetType, int splashCount, int chanceToHit, float multiplier,
                              ActionConditional condition) {
        super(name, ActionType.WEAPON_ATTACK, false, targetType, splashCount, chanceToHit, condition);
        this.multiplier = multiplier;
    }

    /**
     * Create a clone with an updated multiplier (used for Decorator wrappers)
     */
    public ActionWeaponAttack withNewMultiplier(float newMultiplier) {
        return new ActionWeaponAttack(name, targetType, splashCount, chanceToHit, newMultiplier, conditional);
    }

    public List<CombatChar> process(CombatChar actor, CombatChar target, boolean isCardAction,
                                    Queue<TriggeredAction> actionQueue, CombatLog logger) {
        logger.log(LogEvent.ATTACK, LogLevel.DEBUG, "Attack - " + actor.getName() + " -> " + target.getName() +
                " - weapon: " + actor.getWeaponType().name());
        DamagePacket dp = getWeaponDP(actor);
        logger.log(LogEvent.ATTACK, LogLevel.DEV, "Base Damage Packet: " + dp.toString());

        dp = processDamagePacketMods(actor, target, dp, logger);
        applyDamagePacket(target, dp, logger);
        checkDamageTriggers(actor, target, dp, isCardAction, actionQueue, logger);
        return new ArrayList<>();
    }

    protected DamagePacket getWeaponDP(CombatChar actor) {
        // Create the base damage packet from the weapon data and actor modifiers.
        // Damage = (weapon base) * (actor weapon mult) * (action mult)
        WeaponType weapon = actor.getWeaponType();
        float baseDamage = (float) weapon.getBaseDamage() * actor.getWeaponMultiplier() * multiplier;
        return new DamagePacket(Math.round(baseDamage), DamageSource.DIRECT,
                Set.of(weapon.getDamageType(), DamageType.WEAPON), weapon.getPiercePct(), weapon.getCrushPct());
    }

}
