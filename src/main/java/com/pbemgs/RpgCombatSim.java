package com.pbemgs;

import com.pbemgs.game.rpg.combat.CharSide;
import com.pbemgs.game.rpg.combat.CombatChar;
import com.pbemgs.game.rpg.combat.CombatCharFactory;
import com.pbemgs.game.rpg.combat.CombatEngine;
import com.pbemgs.game.rpg.combat.CombatLog;
import com.pbemgs.game.rpg.combat.LogEvent;
import com.pbemgs.game.rpg.combat.LogLevel;
import com.pbemgs.game.rpg.combat.card.CardManager;
import org.checkerframework.checker.units.qual.C;

import java.io.IOException;
import java.util.List;

public class RpgCombatSim {
    public static void main(String[] args) throws IOException {
        CombatLog combatLog = new CombatLog();
        combatLog.addLogType(LogEvent.ALL);
        combatLog.setLogLevel(LogLevel.DEV);
        CardManager cardManager = new CardManager("./src/main/java/com/pbemgs/game/rpg", combatLog);
        cardManager.loadAll();

        CombatCharFactory charFactory = new CombatCharFactory(cardManager);

        CombatChar f1 = charFactory.getWarrior("Friend Warr", CharSide.FRIEND, 1);
        CombatChar f2 = charFactory.getRogue("Friend Rogue", CharSide.FRIEND, 1);
        CombatChar f3 = charFactory.getPriest("Friend Priest", CharSide.FRIEND, 1);
        CombatChar f4 = charFactory.getWizard("Friend Wizard", CharSide.FRIEND, 1);
        CombatChar e1 = charFactory.getBM("Enemy BM", CharSide.ENEMY, 1);
        CombatChar e1p = charFactory.getBMPet("Enemy Ironhide", CharSide.ENEMY, 1);
        CombatChar e2 = charFactory.getRogue("Enemy Rogue", CharSide.ENEMY, 1);
        CombatChar e3 = charFactory.getPriest("Enemy Priest", CharSide.ENEMY, 1);
        CombatChar e4 = charFactory.getShaman("Enemy Shaman", CharSide.ENEMY, 1);

        CombatEngine ce = new CombatEngine(List.of(f1, f2, f3, f4, e1, e1p, e2, e3, e4), cardManager);

         /*
        CombatChar f1 = charFactory.getBM("Friend BM", CharSide.FRIEND, 1);
        CombatChar f1p = charFactory.getBMPet("Friend Ironhide", CharSide.FRIEND, 1);
        //CombatChar e1 = charFactory.getWarrior("Enemy Warrior", CharSide.ENEMY, 1);
        //CombatChar e2 = charFactory.getShaman("Enemy Shaman", CharSide.ENEMY, 1);
        CombatChar e3 = charFactory.getRogue("Enemy Rogue", CharSide.ENEMY, 1);
        CombatEngine ce = new CombatEngine(List.of(f1, f1p, e3), cardManager);

          */
        ce.execute();
    }
}
