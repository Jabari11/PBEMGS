package com.pbemgs.game.rpg.combat;

import java.util.HashSet;
import java.util.Set;

/**
 * A "packet" of damage.  Damage can be negative (i.e. healing)
 */
public class DamagePacket {
    private final int baseAmount;
    private int adjustedAmount;
    private final Set<DamageType> damageTypes;
    private final DamageSource damageSource;
    private float piercePct;
    private float crushPct;

    public DamagePacket(int amount, DamageSource source, Set<DamageType> types) {
        this.baseAmount = amount;
        this.adjustedAmount = amount;
        this.damageTypes = new HashSet<>(types);
        this.damageSource = source;
        this.piercePct = 0.0f;
        this.crushPct = 0.0f;
    }

    public DamagePacket(int amount, DamageSource source, Set<DamageType> types, float pierce, float crush) {
        this.baseAmount = amount;
        this.adjustedAmount = amount;
        this.damageTypes = new HashSet<>(types);
        this.damageSource = source;
        this.piercePct = pierce;
        this.crushPct = crush;
    }

    public DamagePacket copy() {
        DamagePacket cloned = new DamagePacket(this.baseAmount, this.damageSource, new HashSet<>(this.damageTypes),
                this.piercePct, this.crushPct);
        cloned.adjustedAmount = this.adjustedAmount;
        return cloned;
    }

    public void adjustPiercePct(float adjustment) {
        piercePct += adjustment;
    }

    public void adjustCrushPct(float adjustment) {
        crushPct += adjustment;
    }

    /**
     * Adjust the damage by both a multiplicative and scalar value.  The multiplicative
     * value of calculated off the base damage so that modifiers are order agnostic.
     */
    public void adjustDamage(float adjustMult, int adjustScalar) {
        adjustedAmount += Math.round(adjustMult * baseAmount) + adjustScalar;
    }

    /**
     * Modify the damage packet by adding new damage type(s).
     */
    public void addDamageTypes(Set<DamageType> addedClasses) {
        damageTypes.addAll(addedClasses);
    }

    /**
     * adjust percentages to make sense (0 to 1, then crush + pierce <= 1)
     * clamp damage to the correct side (damage vs healing)
     */
    public void finalizeAdjustments() {
        piercePct = Math.max(0.0f, Math.min(1.0f, piercePct));
        crushPct = Math.max(0.0f, Math.min(1.0f, crushPct));
        if (crushPct + piercePct > 1.0f) {
            piercePct = 1.0f - crushPct;
        }
        if (baseAmount > 0 && adjustedAmount < 0 ||
                baseAmount < 0 && adjustedAmount > 0) {
            adjustedAmount = 0;
        }
    }

    public float getBaseAmount() {
        return baseAmount;
    }

    /**
     * "standard" damage that applies to armor
     */
    public int getStandardDamage() {
        return adjustedAmount - getPiercingDamage() - getCrushingDamage();
    }

    public int getPiercingDamage() {
        return Math.round((float) adjustedAmount * piercePct);
    }

    public int getCrushingDamage() {
        return Math.round((float) adjustedAmount * crushPct);
    }

    public DamageSource getDamageSource() {
        return damageSource;
    }

    public Set<DamageType> getDamageTypes() {
        return damageTypes;
    }

    @Override
    public String toString() {
        return "base: " + baseAmount + ", curr: " + adjustedAmount + ", source: " + damageSource.toString() +
                ", types: " + damageTypes.toString() + ", P%: " + piercePct * 100.0 + ", C%: " + crushPct * 100.0;
    }

}
