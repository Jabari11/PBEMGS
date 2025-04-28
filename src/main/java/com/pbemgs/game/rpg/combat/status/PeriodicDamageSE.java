package com.pbemgs.game.rpg.combat.status;

import com.pbemgs.game.rpg.combat.CombatChar;
import com.pbemgs.game.rpg.combat.CombatLog;
import com.pbemgs.game.rpg.combat.DamagePacket;
import com.pbemgs.game.rpg.combat.LogEvent;
import com.pbemgs.game.rpg.combat.LogLevel;
import com.pbemgs.game.rpg.combat.action.Action;

import static com.pbemgs.game.rpg.model.RpgConstants.INFINITE;

/**
 * Status Effect that does periodic damage (or healing) at either the start or end of each round.
 * No notion of "charges" on these - the turn counter is the number of charges.
 */
public class PeriodicDamageSE extends StatusEffect {

    DamagePacket dp;      // Damage (or healing) to apply if appropriate

    public PeriodicDamageSE(String name, Integer sourceId, boolean beneficial, int duration,
                            Classification classification, DamagePacket dp) {
        super(name, beneficial, duration, INFINITE, classification);
        this.dp = dp;
        setSourceId(sourceId);
    }

    public void applyPeriodicSE(CombatChar target, CombatLog logger) {
                logger.log(LogEvent.SE, LogLevel.DEBUG, "- Applying " + getName() + " to " + target.getName() +
                        " - Base DP: " + dp.toString());
                DamagePacket dpToApply = Action.processDamagePacketMods(null, target, dp, logger);
                Action.applyDamagePacket(target, dpToApply, logger);
    }

}
