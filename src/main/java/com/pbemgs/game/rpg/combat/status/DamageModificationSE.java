package com.pbemgs.game.rpg.combat.status;

import com.pbemgs.game.rpg.combat.CombatLog;
import com.pbemgs.game.rpg.combat.DamagePacket;
import com.pbemgs.game.rpg.combat.DamageSource;
import com.pbemgs.game.rpg.combat.DamageType;
import com.pbemgs.game.rpg.combat.LogEvent;
import com.pbemgs.game.rpg.combat.LogLevel;

import java.util.Set;

/**
 * Status Effect that modifies damage packets.
 * This handles both packets created (actorMod == true), and packets recevied (actorMod == false).
 * <p>
 * Charge-based modifications don't apply to periodic DPs.
 */
public class DamageModificationSE extends StatusEffect {
    boolean actorMod;            // true if this modifies damage packets created, false if it modifies damage packets taken
    float scalingMult;           // percentage to adjust the damage packet by (multiplicative)
    int scalingScalar;           // scalar to adjust the damage packet by
    Set<DamageType> affectedSet; // Set of Damage Classifications modified
    Set<DamageType> addedClasses; // Set of Damage Classifications to add

    public DamageModificationSE(String name, Integer sourceId, boolean friendly, int duration, int charges,
                                Classification classification, boolean actorMod, float scalingMult, int scalingScalar,
                                Set<DamageType> affectedSet, Set<DamageType> addedClasses) {
        super(name, friendly, duration, charges, classification);
        this.actorMod = actorMod;
        this.scalingMult = scalingMult;
        this.scalingScalar = scalingScalar;
        this.affectedSet = affectedSet;
        this.addedClasses = addedClasses;
        setSourceId(sourceId);
    }

    public DamagePacket applyDPModifier(DamagePacket dp, boolean isActor, CombatLog logger) {
        if (isActive() && isActor == actorMod && hasOverlap(affectedSet, dp.getDamageTypes())) {
            // If this modifier uses charges and is trying to modify a periodic effect, don't.
            if (dp.getDamageSource() == DamageSource.PERIODIC && usesCharges()) {
                return dp;
            }
            DamagePacket current = dp.copy();
            current.adjustDamage(scalingMult, scalingScalar);
            if (addedClasses != null) {
                current.addDamageTypes(addedClasses);
            }
            logger.log(LogEvent.SE, LogLevel.DEBUG, "- Applying Damage Packet Modifier from: " + getName() + ".");
            reportChargeUsage();
            return current;
        }
        return dp;  // unmodified
    }


    private static <T> boolean hasOverlap(Set<T> a, Set<T> b) {
        if (a.isEmpty() || b.isEmpty()) return false;
        Set<T> small = a.size() <= b.size() ? a : b;
        Set<T> big = a.size() > b.size() ? a : b;
        return small.stream().anyMatch(big::contains);
    }
}
