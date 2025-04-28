package com.pbemgs.game.rpg.combat;

import com.pbemgs.game.rpg.combat.card.Card;
import com.pbemgs.game.rpg.combat.status.StatusContainer;
import com.pbemgs.game.rpg.combat.status.StatusEffect;
import com.pbemgs.game.rpg.model.CharacterResource;
import com.pbemgs.game.rpg.model.ResourceType;
import com.pbemgs.game.rpg.model.WeaponType;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.pbemgs.game.rpg.model.RpgConstants.INFINITE;
import static com.pbemgs.game.rpg.model.RpgConstants.LEVEL_SCALING;
import static com.pbemgs.game.rpg.model.RpgConstants.SPIRIT_REGEN_RATE;

/**
 * A character in combat
 */
public class CombatChar {
    private Integer unitId;  // id in this combat

    // statics
    private final String name;
    private final CharSide charSide;
    private final int level;
    private final int speed;
    private final WeaponType weapon;

    // resources
    private final Map<ResourceType, CharacterResource> charResources;

    private final StatusContainer statusEffects;
    private final boolean isTemporary;
    private int duration;

    // Combat loop data
    private final List<Card> deck;
    private int nextCardIndex = 0;  // pointer to next card to pop.

    public CombatChar(String name, CharSide charSide, int level, WeaponType weapon,
                      int speed, int maxHP, int maxArmor, int maxSpirit, int rageCap,
                      StatusContainer ses, List<Card> deck, boolean isTemporary, int duration) {
        this.name = name;
        this.charSide = charSide;
        this.level = level;
        this.speed = speed;
        this.weapon = weapon;

        this.charResources = new HashMap<>();
        initializeCharResources(maxHP, maxArmor, maxSpirit, rageCap);

        this.statusEffects = ses;
        this.isTemporary = isTemporary;
        this.duration = duration;

        this.deck = deck;
        nextCardIndex = 0;
        Collections.shuffle(this.deck);
    }

    private void initializeCharResources(int maxHP, int maxArmor, int maxSpirit, int rageCap) {
        charResources.put(ResourceType.HP, new CharacterResource(maxHP, maxHP));
        charResources.put(ResourceType.ARMOR, new CharacterResource(0, maxArmor));
        charResources.put(ResourceType.FOCUS, new CharacterResource(0, 3));
        charResources.put(ResourceType.SPIRIT, new CharacterResource(maxSpirit, maxSpirit));
        charResources.put(ResourceType.RAGE, new CharacterResource(0, rageCap));
        charResources.put(ResourceType.ENRAGE_TIMER, new CharacterResource(0, 3));
    }

    // Setters for changeable values
    public void setUnitId(int unitId) {
        this.unitId = unitId;
    }

    public void shuffleDeck() {
        Collections.shuffle(deck);
        nextCardIndex = 0;
    }

    public void addArmor(int val) {
        charResources.get(ResourceType.ARMOR).adjust(val);
    }

    public void adjustSpirit(float adjustment) {
        charResources.get(ResourceType.SPIRIT).adjust(adjustment);
    }

    public void addRage(int rageIncurred) {
        charResources.get(ResourceType.RAGE).adjust(rageIncurred);
    }

    public void addFocus(int quantity) {
        charResources.get(ResourceType.FOCUS).adjust(quantity);
    }
    /**
     * apply a finalized damage packet to the character.
     */
    public void applyDamagePacket(DamagePacket dp) {
        CharacterResource armor = charResources.get(ResourceType.ARMOR);
        CharacterResource hp = charResources.get(ResourceType.HP);

        // Crushing first - applies to both armor and HP directly
        armor.adjust(-dp.getCrushingDamage());
        hp.adjust(-dp.getCrushingDamage());

        // Piercing - applies to HP
        hp.adjust(-dp.getPiercingDamage());

        // Standard - applies to armor if it exists, HP if not
        int standard = dp.getStandardDamage();
        int toArmor = Math.min(armor.getCurrent(), standard);
        armor.adjust(-toArmor);
        hp.adjust(-(standard - toArmor));
    }

    // End of turn time tick - returns TRUE if this character should be deleted
    public boolean tickDuration() {
        if (duration == INFINITE) {
            return false;
        }
        duration--;
        return duration <= 0;
    }

    // TODO: as needed

    // Getters

    /**
     * Get the next Card - returns null if none left.
     */
    public Card drawCard() {
        if (nextCardIndex < deck.size()) {
            Card cardToReturn = deck.get(nextCardIndex);
            ++nextCardIndex;
            return cardToReturn;
        }
        return null;
    }

    public String getName() {
        return name;
    }

    public Integer getUnitId() {
        return unitId;
    }

    public CharSide getSide() {
        return charSide;
    }

    /**
     *  Gets the character level to use in calculations (i.e. base + add/level).
     *  Returns actual level - 1.
     */
    public int getLevel() {
        return level - 1;
    }

    public int getDisplayLevel() {
        return level;
    }

    public Integer getSpeed() {
        return speed;
    }

    public boolean isAlive() {
        return !charResources.get(ResourceType.HP).isZero();
    }

    public int getResourceVal(ResourceType type) {
        return charResources.get(type).getCurrent();
    }

    public float getResourcePct(ResourceType type) {
        return charResources.get(type).getCurrentPct();
    }

    public void checkEnragedState() {
        if (charResources.get(ResourceType.ENRAGE_TIMER).getCurrent() > 0) {
            charResources.get(ResourceType.ENRAGE_TIMER).adjust(-1.0f);
            if (charResources.get(ResourceType.ENRAGE_TIMER).getCurrent() == 0) {
                charResources.get(ResourceType.RAGE).clear();
            }
        } else if (charResources.get(ResourceType.RAGE).getMax() > 0 &&
                charResources.get(ResourceType.RAGE).isFull()) {
            charResources.get(ResourceType.ENRAGE_TIMER).adjust(3.0f);
        }
    }

    public void regenerateSpirit() {
        if (charResources.get(ResourceType.SPIRIT).getMax() > 0) {
            charResources.get(ResourceType.SPIRIT).adjustPctOfMax(SPIRIT_REGEN_RATE);
        }
    }

    public void clearFocus() {
        charResources.get(ResourceType.FOCUS).clear();
    }

    public WeaponType getWeaponType() {
        return weapon;
    }

    public float getWeaponMultiplier() {
        return 1.0f + (getLevel() * LEVEL_SCALING);
    }

    public StatusContainer getStatusEffects() {
        return statusEffects;
    }

    public boolean isTemporary() {
        return isTemporary;
    }

    // TODO: as needed


    public String getCharDevLog(double rolledSpeed) {
        StringBuilder sb = new StringBuilder();
        sb.append("- ID ").append(unitId).append(" (").append(name).append("): HP/Armor: ");
        sb.append(getResourceVal(ResourceType.HP)).append("/").append(getResourceVal(ResourceType.ARMOR));
        if (charResources.get(ResourceType.SPIRIT).getMax() > 0) {
            sb.append(" (SPIRIT: ").append(charResources.get(ResourceType.SPIRIT).toString()).append(")");
        }
        if (charResources.get(ResourceType.RAGE).getMax() > 0) {
            sb.append(" (RAGE: ").append(charResources.get(ResourceType.RAGE).toString()).append(")");
        }
        if (charResources.get(ResourceType.FOCUS).getCurrent() > 0) {
            sb.append(" (FOCUS: ").append(charResources.get(ResourceType.FOCUS).toString()).append(")");
        }
        if (duration != INFINITE) {
            sb.append(" (duration: ").append(duration).append(")");
        }
        sb.append("\n").append(statusEffects.toString()).append("\n");
        return sb.toString();
    }

}
