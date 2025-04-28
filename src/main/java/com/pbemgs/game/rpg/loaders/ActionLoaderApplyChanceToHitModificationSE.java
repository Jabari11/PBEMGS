package com.pbemgs.game.rpg.loaders;

import com.pbemgs.game.rpg.combat.CombatLog;
import com.pbemgs.game.rpg.combat.LogEvent;
import com.pbemgs.game.rpg.combat.LogLevel;
import com.pbemgs.game.rpg.combat.TargetType;
import com.pbemgs.game.rpg.combat.action.Action;
import com.pbemgs.game.rpg.combat.action.ActionApplyChanceToHitModificationSE;
import com.pbemgs.game.rpg.combat.action.ActionConditional;
import com.pbemgs.game.rpg.combat.action.ActionType;
import com.pbemgs.game.rpg.combat.status.StatusEffect;
import org.jooq.tools.csv.CSVReader;

import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static com.pbemgs.game.rpg.loaders.LoaderUtils.parseActionTypeSet;
import static com.pbemgs.game.rpg.loaders.LoaderUtils.parseBoolean;
import static com.pbemgs.game.rpg.loaders.LoaderUtils.parseCondition;
import static com.pbemgs.game.rpg.loaders.LoaderUtils.parseEnum;
import static com.pbemgs.game.rpg.loaders.LoaderUtils.parseInt;
import static com.pbemgs.game.rpg.loaders.LoaderUtils.parseNonNegativeInt;
import static com.pbemgs.game.rpg.loaders.LoaderUtils.parseString;
import static com.pbemgs.game.rpg.loaders.LoaderUtils.parseUncappedInt;

public class ActionLoaderApplyChanceToHitModificationSE {
    public static Map<String, Action> loadChanceToHitModificationSEActions(String csvFilePath, CombatLog combatLog) {
        Map<String, Action> actions = new HashMap<>();
        String[] expectedHeaders = {
                "ActionApplyChanceToHitModificationSE", "targetType", "isFriendly", "splashCount", "chanceToHit",
                "seName", "classification", "appliesAsActor", "duration", "charges", "scaling", "affectedSet", "condition"
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
                    String name = parseString(rowData, "ActionApplyChanceToHitModificationSE", rowNum, true);
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
                    boolean appliesAsActor = parseBoolean(rowData, "appliesAsActor", rowNum);
                    int duration = parseUncappedInt(rowData, "duration", rowNum);
                    int charges = parseUncappedInt(rowData, "charges", rowNum);
                    int scaling = parseInt(rowData, "scaling", rowNum);
                    Set<ActionType> affectedSet = parseActionTypeSet(rowData, "affectedSet", rowNum);
                    ActionConditional condition = parseCondition(rowData.get("condition"), rowNum);

                    Action action = new ActionApplyChanceToHitModificationSE(
                            name, targetType, isFriendly, splashCount, chanceToHit, seName, classification,
                            appliesAsActor, LoaderUtils.getActualStatusDuration(duration), charges, scaling,
                            affectedSet, condition);
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
