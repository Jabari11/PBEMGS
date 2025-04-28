package com.pbemgs.game.rpg.combat;

import com.pbemgs.game.rpg.combat.card.Card;
import com.pbemgs.game.rpg.combat.card.CardManager;
import com.pbemgs.game.rpg.combat.status.StatusContainer;
import com.pbemgs.game.rpg.combat.status.StatusEffect;
import com.pbemgs.game.rpg.model.WeaponType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.pbemgs.game.rpg.model.RpgConstants.INFINITE;

public class CombatCharFactory {

    private final CardManager cardManager;
    private final static Map<String, List<Card>> summonDecks = new HashMap<>();


    public CombatCharFactory(CardManager cardManager) {
        this.cardManager = cardManager;
        populateSummonDecks();
    }

    private void populateSummonDecks() {
        if (summonDecks.isEmpty()) {
            summonDecks.put("SPIRIT_WOLF", getWolfDeck());
        }
    }

    private List<Card> getBMDeck() {
        List<Card> deck = new ArrayList<>();
        for (int x = 0; x < 10; ++x) {
            deck.add(cardManager.getCard("Basic Attack"));
        }

        deck.add(cardManager.getCard("Thick Skin"));
        deck.add(cardManager.getCard("Thick Skin"));
        deck.add(cardManager.getCard("Hunter's Mark"));
        deck.add(cardManager.getCard("Blood Frenzy"));
        deck.add(cardManager.getCard("Inner Beast"));
        return deck;
    }

    private  List<Card> getWarriorDeck() {
        List<Card> deck = new ArrayList<>();
        for (int x = 0; x < 10; ++x) {
            deck.add(cardManager.getCard("Basic Attack"));
        }
        deck.add(cardManager.getCard("Warlord's Shout"));
        deck.add(cardManager.getCard("Warlord's Shout"));
        deck.add(cardManager.getCard("Cleave"));
        deck.add(cardManager.getCard("Cleave"));
        deck.add(cardManager.getCard("Cleave"));
        deck.add(cardManager.getCard("Shield Block"));
        deck.add(cardManager.getCard("Shield Block"));
        deck.add(cardManager.getCard("Defensive Stance"));
        deck.add(cardManager.getCard("Paladin's Touch"));
        deck.add(cardManager.getCard("Execute"));
        deck.add(cardManager.getCard("Execute"));
        deck.add(cardManager.getCard("Execute"));

        return deck;
    }

    private List<Card> getRogueDeck() {
        List<Card> deck = new ArrayList<>();
        for (int x = 0; x < 10; ++x) {
            deck.add(cardManager.getCard("Basic Attack"));
        }
        deck.add(cardManager.getCard("Quick Attack"));
        deck.add(cardManager.getCard("Quick Attack"));
        deck.add(cardManager.getCard("Quick Attack"));
        deck.add(cardManager.getCard("Expose Weakness"));
        deck.add(cardManager.getCard("Expose Weakness"));
        deck.add(cardManager.getCard("Expose Weakness"));
        deck.add(cardManager.getCard("Blur"));
        deck.add(cardManager.getCard("Blur"));
        deck.add(cardManager.getCard("Assassinate"));
        deck.add(cardManager.getCard("Assassinate"));
        deck.add(cardManager.getCard("Assassinate"));

        return deck;
    }

    private List<Card> getPriestDeck() {
        List<Card> deck = new ArrayList<>();
        for (int x = 0; x < 10; ++x) {
            deck.add(cardManager.getCard("Basic Attack"));
        }
        deck.add(cardManager.getCard("Light Heal"));
        deck.add(cardManager.getCard("Light Heal"));
        deck.add(cardManager.getCard("Light Heal"));
        deck.add(cardManager.getCard("Renew"));
        deck.add(cardManager.getCard("Renew"));
        deck.add(cardManager.getCard("Renew"));
        deck.add(cardManager.getCard("Resist Fire"));
        deck.add(cardManager.getCard("Resist Fire"));
        deck.add(cardManager.getCard("Fade"));
        deck.add(cardManager.getCard("Fade"));
        deck.add(cardManager.getCard("Fade"));
        deck.add(cardManager.getCard("Purify"));

        return deck;
    }

    private List<Card> getShamanDeck() {
        List<Card> deck = new ArrayList<>();
        for (int x = 0; x < 10; ++x) {
            deck.add(cardManager.getCard("Basic Attack"));
        }
        deck.add(cardManager.getCard("Flame Weapon"));
        deck.add(cardManager.getCard("Flame Weapon"));
        deck.add(cardManager.getCard("Prismatic Barrier"));
        deck.add(cardManager.getCard("Summon Spirit Wolf"));
        deck.add(cardManager.getCard("Summon Spirit Wolf"));
        deck.add(cardManager.getCard("Summon Spirit Wolf"));
        deck.add(cardManager.getCard("Warde's Spirit"));
        deck.add(cardManager.getCard("Warde's Spirit"));
        deck.add(cardManager.getCard("Guiding Sigil"));
        deck.add(cardManager.getCard("Guiding Sigil"));
        deck.add(cardManager.getCard("Stone Blast"));
        deck.add(cardManager.getCard("Stone Blast"));

        return deck;
    }

    private List<Card> getMageDeck() {
        List<Card> deck = new ArrayList<>();
        for (int x = 0; x < 10; ++x) {
            deck.add(cardManager.getCard("Basic Attack"));
        }
        deck.add(cardManager.getCard("Magic Missiles"));
        deck.add(cardManager.getCard("Magic Missiles"));
        deck.add(cardManager.getCard("Magic Missiles"));
        deck.add(cardManager.getCard("Fire Bolt"));
        deck.add(cardManager.getCard("Fire Bolt"));
        deck.add(cardManager.getCard("Fire Bolt"));
        deck.add(cardManager.getCard("Fireball"));
        deck.add(cardManager.getCard("Fireball"));
        deck.add(cardManager.getCard("Blink"));
        deck.add(cardManager.getCard("Blink"));
        deck.add(cardManager.getCard("Mage Armor"));
        deck.add(cardManager.getCard("Static Field"));

        return deck;
    }


    public CombatChar getWarrior(String name, CharSide side, int level) {
        return new CombatChar(name, side, level, WeaponType.SWORD, 100 + level,119 + 10 + (level * 23),
                40, 0, 0, new StatusContainer(), getWarriorDeck(), false, INFINITE);
    }

    public CombatChar getBM(String name, CharSide side, int level) {
        return new CombatChar(name, side, level, WeaponType.MACE, 95, 119 + 8 + (level * 22),
                60, 0, 50, new StatusContainer(), getBMDeck(), false, INFINITE);
    }

    public CombatChar getBMPet(String name, CharSide side, int level) {
        return new CombatChar(name, side, level, WeaponType.MACE, 100, 45 + (level * 5),
                30, 0, 0, new StatusContainer(), getIronhideDeck(), false, INFINITE);
    }

    public CombatChar getRogue(String name, CharSide side, int level) {
        return new CombatChar(name, side, level, WeaponType.DAGGER, 105, 119 + 6 + (level * 20),
                30, 0, 0, new StatusContainer(), getRogueDeck(), false, INFINITE);
    }

    public CombatChar getPriest(String name, CharSide side, int level) {
        return new CombatChar(name, side, level, WeaponType.SCEPTER, 99 + level, 119 + 2 + (level * 18),
                20 + 2 * level, 0, 0, new StatusContainer(), getPriestDeck(), false, INFINITE);
    }

    public CombatChar getShaman(String name, CharSide side, int level) {
        return new CombatChar(name, side, level, WeaponType.STAFF, 100, 119 + 4 + (level * 20),
                30, 20, 0, new StatusContainer(), getShamanDeck(), false, INFINITE);
    }

    public CombatChar getWizard(String name, CharSide side, int level) {
        return new CombatChar(name, side, level, WeaponType.WAND, 100, 119 + (level * 17),
                20,0, 0, new StatusContainer(), getMageDeck(), false, INFINITE);
    }

    public static CombatChar getSummonedUnit(String summonId, int level, CharSide side, int duration) {
        return switch (summonId) {
            case "SPIRIT_WOLF" -> new CombatChar("Spirit Wolf", side, level, null, 105 + level,
                    29 + 6 * level, 10, 0, 0, new StatusContainer(),
                    summonDecks.get(summonId), true, duration);
            default -> null;
        };
    }

    private List<Card> getWolfDeck() {
        List<Card> deck = new ArrayList<>();
        for (int x = 0; x < 6; ++x) {
            deck.add(cardManager.getCard("Wolf Claw"));
        }
        for (int x = 0; x < 2; ++x) {
            deck.add(cardManager.getCard("Wolf Pounce"));
        }

        return deck;
    }


    private List<Card> getIronhideDeck() {
        List<Card> deck = new ArrayList<>();
        for (int x = 0; x < 10; ++x) {
            deck.add(cardManager.getCard("Ironhide Basic"));
        }
        for (int x = 0; x < 3; ++x) {
            //deck.add(CardFactory.createThickSkin());
            deck.add(cardManager.getCard("Ironhide Gore"));
        }
        return deck;
    }
}
