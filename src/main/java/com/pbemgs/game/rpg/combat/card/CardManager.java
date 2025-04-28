package com.pbemgs.game.rpg.combat.card;

import com.pbemgs.game.rpg.combat.CombatLog;
import com.pbemgs.game.rpg.combat.LogEvent;
import com.pbemgs.game.rpg.combat.LogLevel;
import com.pbemgs.game.rpg.combat.action.Action;
import com.pbemgs.game.rpg.loaders.ActionLoaderAdjustResource;
import com.pbemgs.game.rpg.loaders.ActionLoaderApplyActionPreventionSE;
import com.pbemgs.game.rpg.loaders.ActionLoaderApplyActionTriggerSE;
import com.pbemgs.game.rpg.loaders.ActionLoaderApplyAggroModificationSE;
import com.pbemgs.game.rpg.loaders.ActionLoaderApplyChanceToHitModificationSE;
import com.pbemgs.game.rpg.loaders.ActionLoaderApplyDamageModificationSE;
import com.pbemgs.game.rpg.loaders.ActionLoaderApplyPeriodicDamageSE;
import com.pbemgs.game.rpg.loaders.ActionLoaderArmor;
import com.pbemgs.game.rpg.loaders.ActionLoaderDispel;
import com.pbemgs.game.rpg.loaders.ActionLoaderHeal;
import com.pbemgs.game.rpg.loaders.ActionLoaderNonWeaponAttack;
import com.pbemgs.game.rpg.loaders.ActionLoaderSummon;
import com.pbemgs.game.rpg.loaders.ActionLoaderWeaponAttack;
import org.jooq.tools.csv.CSVReader;

import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.pbemgs.game.rpg.loaders.LoaderUtils.parseActionList;
import static com.pbemgs.game.rpg.loaders.LoaderUtils.parseBoolean;
import static com.pbemgs.game.rpg.loaders.LoaderUtils.parseNonNegativeInt;
import static com.pbemgs.game.rpg.loaders.LoaderUtils.parseString;

public class CardManager {
    private final Map<String, Action> actions = new HashMap<>();
    private final Map<String, Card> cards = new HashMap<>();
    private final Map<String, List<String>> actionFileConfigs;
    private final List<String> cardFiles;
    private final CombatLog combatLog;

    public CardManager(String baseDir, CombatLog combatLog) {
        this.combatLog = combatLog;
        this.actionFileConfigs = new LinkedHashMap<>(); // Preserves insertion order
        this.cardFiles = new ArrayList<>();

        // Configure action files (example, adjust paths/names as needed)
        actionFileConfigs.put("WeaponAttack", List.of(
                baseDir + "/data/player_weapon_attacks.csv"
        ));
        actionFileConfigs.put("NonweaponAttack", List.of(
                baseDir + "/data/player_nonweapon_attacks.csv"
        ));
        actionFileConfigs.put("Armor", List.of(
                baseDir + "/data/player_armor.csv"
        ));
        actionFileConfigs.put("Heal", List.of(
                baseDir + "/data/player_heal.csv"));
        actionFileConfigs.put("Summon", List.of(
                baseDir + "/data/player_summon.csv"));
        actionFileConfigs.put("Dispel", List.of(baseDir + "/data/player_dispel.csv"));
        actionFileConfigs.put("ActionPreventionSE", List.of(
                baseDir + "/data/player_status_stun.csv"));
        actionFileConfigs.put("AggroModificationSE", List.of(
                baseDir + "/data/player_status_threatmod.csv"));
        actionFileConfigs.put("ChanceToHitModificationSE", List.of(
                baseDir + "/data/player_status_tohit.csv"));
        actionFileConfigs.put("DamageModificationSE", List.of(
                baseDir + "/data/player_status_DP_mod.csv"));
        actionFileConfigs.put("PeriodicDamageSE", List.of(
                baseDir + "/data/player_status_periodic_DP.csv"));
        actionFileConfigs.put("AdjustResource", List.of(
                baseDir + "/data/action_adjust_resource.csv"));
        actionFileConfigs.put("ActionTriggerSE", List.of(
                baseDir + "/data/player_status_triggered.csv"));

        // Configure card files
        cardFiles.add(baseDir + "/data/system_cards.csv");
        cardFiles.add(baseDir + "/data/player_cards.csv");
        cardFiles.add(baseDir + "/data/summon_cards.csv");
    }

    /**
     * Loads all actions and cards from configured .csv files.
     */
    public void loadAll() {
        // Load actions in order (ActionTriggerSE last)
        for (Map.Entry<String, List<String>> entry : actionFileConfigs.entrySet()) {
            String actionType = entry.getKey();
            List<String> files = entry.getValue();
            for (String file : files) {
                System.out.println("fetching from file: " + file);
                switch (actionType) {
                    case "WeaponAttack":
                        actions.putAll(ActionLoaderWeaponAttack.loadWeaponAttacks(file, combatLog));
                        break;
                    case "NonweaponAttack":
                        actions.putAll(ActionLoaderNonWeaponAttack.loadNonweaponAttacks(file, combatLog));
                        break;
                    case "Armor":
                        actions.putAll(ActionLoaderArmor.loadArmorActions(file, combatLog));
                        break;
                    case "Heal":
                        actions.putAll(ActionLoaderHeal.loadHealActions(file, combatLog));
                        break;
                    case "Summon":
                        actions.putAll(ActionLoaderSummon.loadSummonActions(file, combatLog));
                        break;
                    case "Dispel":
                        actions.putAll(ActionLoaderDispel.loadDispelActions(file, combatLog));
                        break;
                    case "ActionPreventionSE":
                        actions.putAll(ActionLoaderApplyActionPreventionSE.loadActionPreventionSEActions(file, combatLog));
                        break;
                    case "AggroModificationSE":
                        actions.putAll(ActionLoaderApplyAggroModificationSE.loadAggroModificationSEActions(file, combatLog));
                        break;
                    case "ChanceToHitModificationSE":
                        actions.putAll(ActionLoaderApplyChanceToHitModificationSE.loadChanceToHitModificationSEActions(file, combatLog));
                        break;
                    case "DamageModificationSE":
                        actions.putAll(ActionLoaderApplyDamageModificationSE.loadDamageModificationSEActions(file, combatLog));
                        break;
                    case "PeriodicDamageSE":
                        actions.putAll(ActionLoaderApplyPeriodicDamageSE.loadPeriodicDamageSEActions(file, combatLog));
                        break;
                    case "AdjustResource":
                        actions.putAll(ActionLoaderAdjustResource.loadAdjustResourceActions(file, combatLog));
                        break;
                    case "ActionTriggerSE":
                        actions.putAll(ActionLoaderApplyActionTriggerSE.loadActionTriggerSEActions(file, actions, combatLog));
                        break;
                    default:
                        throw new IllegalStateException("Unknown action type: " + actionType);
                }
            }
        }

        // Load cards
        for (String cardFile : cardFiles) {
            loadCards(cardFile);
        }
    }

    /**
     * Loads cards from a .csv file.
     * Expected columns: cardname,actions,isCantrip,spiritCost,rageIncurred
     */
    private void loadCards(String csvFilePath) {
        String[] expectedHeaders = {"cardname", "actions", "isCantrip", "spiritCost", "rageIncurred"};

        try (CSVReader reader = new CSVReader(new FileReader(csvFilePath), ',', '"', 0)) {
            // Read and validate header
            String[] headers = reader.readNext();
            if (headers == null || !Arrays.equals(headers, expectedHeaders)) {
                throw new IllegalArgumentException("Invalid .csv header. Expected: " + String.join(",", expectedHeaders));
            }

            // Read rows
            String[] row;
            int rowNum = 1;
            while ((row = reader.readNext()) != null) {
                rowNum++;
                try {
                    if (row.length != expectedHeaders.length) {
                        combatLog.log(LogEvent.PHASE, LogLevel.ERROR, "Row " + rowNum + ": Invalid column count: " + row.length);
                        continue;
                    }

                    // Parse row into a map
                    Map<String, String> rowData = new HashMap<>();
                    for (int i = 0; i < headers.length; i++) {
                        rowData.put(headers[i], row[i].trim());
                    }

                    // Validate and create card
                    String name = parseString(rowData, "cardname", rowNum, true);
                    if (cards.containsKey(name)) {
                        combatLog.log(LogEvent.PHASE, LogLevel.ERROR, "Row " + rowNum + ": Duplicate card name: " + name);
                        throw new IllegalArgumentException("Duplicate card name: " + name);
                    }

                    List<Action> cardActions = parseActionList(rowData, "actions", rowNum, actions);
                    boolean isCantrip = parseBoolean(rowData, "isCantrip", rowNum);
                    int spiritCost = parseNonNegativeInt(rowData, "spiritCost", rowNum);
                    int rageIncurred = parseNonNegativeInt(rowData, "rageIncurred", rowNum);

                    Card card = new Card(name, 0, cardActions, isCantrip, spiritCost, rageIncurred);
                    cards.put(name, card);
                     combatLog.log(LogEvent.PHASE, LogLevel.DEV, "Loaded card: " + name);
                } catch (IllegalArgumentException e) {
                    combatLog.log(LogEvent.PHASE, LogLevel.ERROR, "Row " + rowNum + ": " + e.getMessage());
                    throw e;
                }
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to read .csv file: " + csvFilePath, e);
        }
    }

    public Map<String, Action> getActions() {
        return Collections.unmodifiableMap(actions);
    }

    public Map<String, Card> getCards() {
        return Collections.unmodifiableMap(cards);
    }

    public Action getAction(String name) {
        return actions.get(name);
    }

    public Card getCard(String name) {
        if (!cards.containsKey(name)) {
            throw new IllegalArgumentException("Card " + name + " not found!");
        }
        return cards.get(name);
    }
}
