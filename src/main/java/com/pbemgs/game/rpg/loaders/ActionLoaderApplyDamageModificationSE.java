package com.pbemgs.game.rpg.loaders;

import com.pbemgs.game.rpg.combat.CombatLog;
import com.pbemgs.game.rpg.combat.DamageType;
import com.pbemgs.game.rpg.combat.LogEvent;
import com.pbemgs.game.rpg.combat.LogLevel;
import com.pbemgs.game.rpg.combat.TargetType;
import com.pbemgs.game.rpg.combat.action.Action;
import com.pbemgs.game.rpg.combat.action.ActionApplyDamageModificationSE;
import com.pbemgs.game.rpg.combat.action.ActionConditional;
import com.pbemgs.game.rpg.combat.status.StatusEffect;
import org.jooq.tools.csv.CSVReader;

import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static com.pbemgs.game.rpg.loaders.LoaderUtils.parseBoolean;
import static com.pbemgs.game.rpg.loaders.LoaderUtils.parseCondition;
import static com.pbemgs.game.rpg.loaders.LoaderUtils.parseDamageTypeSet;
import static com.pbemgs.game.rpg.loaders.LoaderUtils.parseEnum;
import static com.pbemgs.game.rpg.loaders.LoaderUtils.parseInt;
import static com.pbemgs.game.rpg.loaders.LoaderUtils.parseNonNegativeInt;
import static com.pbemgs.game.rpg.loaders.LoaderUtils.parseSignedFloat;
import static com.pbemgs.game.rpg.loaders.LoaderUtils.parseString;
import static com.pbemgs.game.rpg.loaders.LoaderUtils.parseUncappedInt;

public class ActionLoaderApplyDamageModificationSE {
    public static Map<String, Action> loadDamageModificationSEActions(String csvFilePath, CombatLog combatLog) {
        Map<String, Action> actions = new HashMap<>();
        String[] expectedHeaders = {
                "ActionApplyDamageModificationSE", "targetType", "isFriendly", "splashCount", "chanceToHit",
                "seName", "classification", "appliesAsActor", "duration", "charges", "affectedTypes",
                "addedTypes", "scalingMult", "scalarBase", "condition"
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
                    String name = parseString(rowData, "ActionApplyDamageModificationSE", rowNum, true);
                    if (actions.containsKey(name)) {
                        combatLog.log(LogEvent.PHASE, LogLevel.ERROR, "Row " + rowNum + ": Duplicate action name: " + name);
                        throw new IllegalArgumentException("Duplicate action name: " + name);
                    }

                    TargetType targetType = parseEnum(rowData, "targetType", TargetType.class, rowNum);
                    boolean isFriendly = parseBoolean(rowData, "isFriendly", rowNum);
                    int splashCount = parseNonNegativeInt(rowData, "splashCount", rowNum);
                    int chanceToHit = parseNonNegativeInt(rowData, "chanceToHit", rowNum);
                    String seName = parseString(rowData, "seName", rowNum, true);
                    StatusEffect.Classification classification = parseEnum(rowData, "classification", StatusEffect.Classification.class, rowNum);
                    String appliesAsActorStr = parseString(rowData, "appliesAsActor", rowNum, true);
                    boolean appliesAsActor;
                    if (appliesAsActorStr.equals("T")) {
                        appliesAsActor = true;
                    } else if (appliesAsActorStr.equals("F")) {
                        appliesAsActor = false;
                    } else {
                        throw new IllegalArgumentException("Row " + rowNum + ": Invalid appliesAsActor value, expected T/F: " + appliesAsActorStr);
                    }
                    int duration = parseUncappedInt(rowData, "duration", rowNum);
                    int charges = parseUncappedInt(rowData, "charges", rowNum);
                    Set<DamageType> affectedTypes = parseDamageTypeSet(rowData, "affectedTypes", rowNum);
                    Set<DamageType> addedTypes = parseDamageTypeSet(rowData, "addedTypes", rowNum);
                    float scalingMult = parseSignedFloat(rowData, "scalingMult", rowNum);
                    float scalarBase = parseSignedFloat(rowData, "scalarBase", rowNum);
                    ActionConditional condition = parseCondition(rowData.get("condition"), rowNum);

                    Action action = new ActionApplyDamageModificationSE(
                            name, targetType, isFriendly, splashCount, chanceToHit, seName, classification,
                            appliesAsActor, LoaderUtils.getActualStatusDuration(duration), charges, affectedTypes,
                            addedTypes, scalingMult, scalarBase, condition);
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
