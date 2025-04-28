package com.pbemgs.game.rpg.combat.status;

import com.pbemgs.game.rpg.combat.CombatChar;

import static com.pbemgs.game.rpg.model.RpgConstants.INFINITE;

/**
 * Base abstract status effect class.  Holds the generic data common for all status effects,
 * and abstract methods that do nothing for all possible requests of status effects.
 * <p>
 * Status effects don't stack, based on the same name and source ID (unit ID).  New instances replace old ones.
 * <p>
 * "charged" SEs use a charge per card, not a charge per action - to handle this, SE calls that use up
 * a charge will ping back that usage to the holding container, which will be called to tick down usages
 * at the end of the card processing for everything used.
 */
public abstract class StatusEffect {

    public enum Classification {MAGIC, POISON, BLEED, PHYSICAL, MENTAL, INNATE}  // "type" of effect for dispel purposes.

    private final String name;
    private Integer sourceId;  // unit id of caster
    private final boolean beneficial;  // is a positive status effect
    private int duration;  // remaining duration in turns
    private int charges;   // remaining charges
    private final Classification classification;
    private StatusContainer holder;  // holding container.

    public StatusEffect(String name, boolean beneficial, int duration, int charges, Classification classification) {
        this.name = name;
        this.sourceId = null;
        this.beneficial = beneficial;
        this.duration = duration;
        this.charges = charges;
        this.classification = classification;
    }

    /**
     * Source ID is set when attached (actor unit ID).
     * Used for checking stacking.
     */
    public void setSourceId(int sourceId) {
        this.sourceId = sourceId;
    }

    public void setHolder(CombatChar holder) {
        this.holder = holder.getStatusEffects();
    }

    /**
     * Ticks the duration down one, returns true if it is expired.
     */
    public boolean tick() {
        if (duration == INFINITE) {
            return false;
        }
        duration--;
        return duration <= 0;
    }

    /**
     * Removes a usage charge.  Should only be called from the container.
     */
    public boolean removeUse() {
        if (charges == INFINITE) {
            return false;
        }
        charges--;
        return charges <= 0;
    }

    public void reportChargeUsage() {
        if (usesCharges()) {
            holder.reportChargeUsage(this);
        }
    }

    public String getName() {
        return name;
    }

    public Classification getClassification() {
        return classification;
    }

    public boolean isBeneficial() {
        return beneficial;
    }

    public Integer getSourceId() {
        return sourceId;
    }

    public boolean isActive() {
        return (duration == INFINITE || duration > 0) &&
                (charges == INFINITE || charges > 0);
    }

    public boolean usesCharges() {
        return charges != INFINITE;
    }

    public String getDisplay() {
        return name + "(" + (duration != INFINITE ? (duration + "T") : "") + (charges != INFINITE ? (charges + "C") : "") + ")";
    }

}
