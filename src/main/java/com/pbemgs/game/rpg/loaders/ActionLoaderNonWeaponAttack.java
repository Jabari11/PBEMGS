package com.pbemgs.game.rpg.loaders;

import com.pbemgs.game.rpg.combat.CombatLog;
import com.pbemgs.game.rpg.combat.DamageType;
import com.pbemgs.game.rpg.combat.LogEvent;
import com.pbemgs.game.rpg.combat.LogLevel;
import com.pbemgs.game.rpg.combat.TargetType;
import com.pbemgs.game.rpg.combat.action.Action;
import com.pbemgs.game.rpg.combat.action.ActionConditional;
import com.pbemgs.game.rpg.combat.action.ActionNonweaponAttack;
import org.jooq.tools.csv.CSVReader;

import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static com.pbemgs.game.rpg.loaders.LoaderUtils.parseCondition;
import static com.pbemgs.game.rpg.loaders.LoaderUtils.parseDamageTypeSet;
import static com.pbemgs.game.rpg.loaders.LoaderUtils.parseEnum;
import static com.pbemgs.game.rpg.loaders.LoaderUtils.parseNonNegativeFloat;
import static com.pbemgs.game.rpg.loaders.LoaderUtils.parseNonNegativeInt;
import static com.pbemgs.game.rpg.loaders.LoaderUtils.parseString;

public class ActionLoaderNonWeaponAttack {

    public static Map<String, Action> loadNonweaponAttacks(String csvFilePath, CombatLog combatLog) {
        Map<String, Action> actions = new HashMap<>();
        String[] expectedHeaders = {
                "ActionNonWeaponAttack", "targetType", "splashCount", "chanceToHit", "baseQuantity",
                "damageTypes", "piercePct", "crushPct", "condition"
        };

        try (CSVReader reader = new CSVReader(new FileReader(csvFilePath), ',', '"', 0)) {
            // Read and validate header
            String[] headers = reader.readNext();
            if (headers == null || !java.util.Arrays.equals(headers, expectedHeaders)) {
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

                    // Validate and create action
                    String name = parseString(rowData, "ActionNonWeaponAttack", rowNum, true);
                    if (actions.containsKey(name)) {
                        combatLog.log(LogEvent.PHASE, LogLevel.ERROR, "Row " + rowNum + ": Duplicate action name: " + name);
                        throw new IllegalArgumentException("Duplicate action name: " + name);
                    }

                    TargetType targetType = parseEnum(rowData, "targetType", TargetType.class, rowNum);
                    int splashCount = parseNonNegativeInt(rowData, "splashCount", rowNum);
                    int chanceToHit = parseNonNegativeInt(rowData, "chanceToHit", rowNum);
                    float baseQuantity = parseNonNegativeFloat(rowData, "baseQuantity", rowNum);
                    Set<DamageType> damageTypes = parseDamageTypeSet(rowData, "damageTypes", rowNum);
                    float piercePct = parseNonNegativeFloat(rowData, "piercePct", rowNum);
                    if (piercePct > 1.0f) {
                        throw new IllegalArgumentException("Row " + rowNum + ": piercePct must be in [0, 1]: " + piercePct);
                    }
                    float crushPct = parseNonNegativeFloat(rowData, "crushPct", rowNum);
                    if (crushPct > 1.0f) {
                        throw new IllegalArgumentException("Row " + rowNum + ": crushPct must be in [0, 1]: " + crushPct);
                    }
                    ActionConditional condition = parseCondition(rowData.get("condition"), rowNum);

                    Action action = new ActionNonweaponAttack(name, targetType, splashCount, chanceToHit, baseQuantity,
                            damageTypes, piercePct, crushPct, condition);
                    actions.put(name, action);
                    combatLog.log(LogEvent.PHASE, LogLevel.DEV, "Loaded action: " + name);
                } catch (IllegalArgumentException e) {
                    combatLog.log(LogEvent.PHASE, LogLevel.ERROR, "Row " + rowNum + ": " + e.getMessage());
                    throw e;
                }
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to read .csv file: " + csvFilePath, e);
        }

        return actions;
    }
}
