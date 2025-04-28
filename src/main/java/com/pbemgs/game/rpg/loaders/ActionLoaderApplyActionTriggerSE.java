package com.pbemgs.game.rpg.loaders;

import com.pbemgs.game.rpg.combat.CombatLog;
import com.pbemgs.game.rpg.combat.DamageType;
import com.pbemgs.game.rpg.combat.LogEvent;
import com.pbemgs.game.rpg.combat.LogLevel;
import com.pbemgs.game.rpg.combat.TargetType;
import com.pbemgs.game.rpg.combat.action.Action;
import com.pbemgs.game.rpg.combat.action.ActionApplyActionTriggerSE;
import com.pbemgs.game.rpg.combat.action.ActionConditional;
import com.pbemgs.game.rpg.combat.action.ActionType;
import com.pbemgs.game.rpg.combat.status.ActionTriggerSE;
import com.pbemgs.game.rpg.combat.status.StatusEffect;
import org.jooq.tools.csv.CSVReader;

import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.pbemgs.game.rpg.loaders.LoaderUtils.parseActionList;
import static com.pbemgs.game.rpg.loaders.LoaderUtils.parseActionTypeSet;
import static com.pbemgs.game.rpg.loaders.LoaderUtils.parseBoolean;
import static com.pbemgs.game.rpg.loaders.LoaderUtils.parseCondition;
import static com.pbemgs.game.rpg.loaders.LoaderUtils.parseDamageTypeSet;
import static com.pbemgs.game.rpg.loaders.LoaderUtils.parseEnum;
import static com.pbemgs.game.rpg.loaders.LoaderUtils.parseNonNegativeInt;
import static com.pbemgs.game.rpg.loaders.LoaderUtils.parseString;
import static com.pbemgs.game.rpg.loaders.LoaderUtils.parseTriggerType;
import static com.pbemgs.game.rpg.loaders.LoaderUtils.parseUncappedInt;

/**
 *  Loader for Action Trigger Status Effects (chases/counters)
 *  Note that 1 is added to duration so that partial rounds are not part of the timing.
 */
public class ActionLoaderApplyActionTriggerSE {
    public static Map<String, Action> loadActionTriggerSEActions(String csvFilePath, Map<String, Action> existingActions, CombatLog combatLog) {
        Map<String, Action> actions = new HashMap<>();
        String[] expectedHeaders = {
                "ActionApplyActionTriggerSE", "targetType", "isFriendly", "splashCount", "chanceToHit",
                "seName", "classification", "duration", "charges", "actions", "triggerType",
                "actionFilter", "damageTypeFilter", "condition"
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
                    String name = parseString(rowData, "ActionApplyActionTriggerSE", rowNum, true);
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
                    int duration = parseUncappedInt(rowData, "duration", rowNum);
                    int charges = parseUncappedInt(rowData, "charges", rowNum);
                    List<Action> actionsList = parseActionList(rowData, "actions", rowNum, existingActions);
                    ActionTriggerSE.TriggerTypes triggerType = parseTriggerType(rowData, "triggerType", rowNum);
                    Set<ActionType> actionFilter = parseActionTypeSet(rowData, "actionFilter", rowNum);
                    Set<DamageType> damageTypeFilter = parseDamageTypeSet(rowData, "damageTypeFilter", rowNum);
                    ActionConditional condition = parseCondition(rowData.get("condition"), rowNum);

                    Action action = new ActionApplyActionTriggerSE(
                            name, targetType, isFriendly, splashCount, chanceToHit, seName, classification,
                            LoaderUtils.getActualStatusDuration(duration), charges, actionsList, triggerType,
                            actionFilter, damageTypeFilter, condition);
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
