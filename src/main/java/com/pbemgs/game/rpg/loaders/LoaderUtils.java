package com.pbemgs.game.rpg.loaders;

import com.pbemgs.game.rpg.combat.DamageType;
import com.pbemgs.game.rpg.combat.action.Action;
import com.pbemgs.game.rpg.combat.action.ActionConditional;
import com.pbemgs.game.rpg.combat.action.ActionType;
import com.pbemgs.game.rpg.combat.status.ActionTriggerSE;
import com.pbemgs.game.rpg.combat.status.StatusEffect;
import com.pbemgs.game.rpg.model.ResourceType;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.pbemgs.game.rpg.model.RpgConstants.INFINITE;

/**
 *  Utility methods for .csv action loaders
 */
public class LoaderUtils {
    public static String parseString(Map<String, String> rowData, String column, int rowNum, boolean required) {
        String value = rowData.get(column);
        if (required && (value == null || value.isEmpty())) {
            throw new IllegalArgumentException("Row " + rowNum + ": Missing or empty " + column);
        }

        // normalize apostrophes and dashes
        return value.replace("’", "'").replace("–", "-");
    }

    public static int parseNonNegativeInt(Map<String, String> rowData, String column, int rowNum) {
        String value = rowData.get(column);
        try {
            int result = Integer.parseInt(value);
            if (result < 0) {
                throw new IllegalArgumentException("Row " + rowNum + ": " + column + " must be non-negative: " + value);
            }
            return result;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Row " + rowNum + ": Invalid " + column + ": " + value);
        }
    }

    /**
     * Parses a signed integer (positive or negative).
     */
    public static int parseInt(Map<String, String> rowData, String column, int rowNum) {
        String value = rowData.get(column);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Row " + rowNum + ": Missing or empty " + column);
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Row " + rowNum + ": Invalid " + column + ": " + value);
        }
    }

    /**
     * Parses a integer value that may be infinite or non-negative.
     * Format: "INF" for infinite or a non-negative integer.
     */
    public static int parseUncappedInt(Map<String, String> rowData, String column, int rowNum) {
        String value = rowData.get(column);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Row " + rowNum + ": Missing or empty " + column);
        }
        if (value.equals("INF")) {
            return INFINITE; // INFINITE constant
        }
        try {
            int result = Integer.parseInt(value);
            if (result < 0) {
                throw new IllegalArgumentException("Row " + rowNum + ": " + column + " must be non-negative or INF: " + value);
            }
            return result;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Row " + rowNum + ": Invalid " + column + ": " + value);
        }
    }

    public static float parseNonNegativeFloat(Map<String, String> rowData, String column, int rowNum) {
        String value = rowData.get(column);
        try {
            float result = Float.parseFloat(value);
            if (result < 0) {
                throw new IllegalArgumentException("Row " + rowNum + ": " + column + " must be non-negative: " + value);
            }
            return result;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Row " + rowNum + ": Invalid " + column + ": " + value);
        }
    }

    /**
     * Parses a signed float (positive or negative).
     */
    public static float parseSignedFloat(Map<String, String> rowData, String column, int rowNum) {
        String value = rowData.get(column);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Row " + rowNum + ": Missing or empty " + column);
        }
        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Row " + rowNum + ": Invalid " + column + ": " + value);
        }
    }

    /**
     * Parses a boolean value from 'yes' or 'no'.
     */
    public static boolean parseBoolean(Map<String, String> rowData, String column, int rowNum) {
        String value = rowData.get(column);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Row " + rowNum + ": Missing or empty " + column);
        }
        if (value.equalsIgnoreCase("yes")) {
            return true;
        } else if (value.equalsIgnoreCase("no")) {
            return false;
        } else {
            throw new IllegalArgumentException("Row " + rowNum + ": Invalid " + column + " value, expected yes/no: " + value);
        }
    }

    public static <T extends Enum<T>> T parseEnum(Map<String, String> rowData, String column, Class<T> enumClass, int rowNum) {
        String value = rowData.get(column);
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("Row " + rowNum + ": Missing " + column);
        }
        try {
            return Enum.valueOf(enumClass, value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Row " + rowNum + ": Invalid " + column + ": " + value);
        }
    }

    /**
     * Parses a condition string into an ActionConditional object.
     * Format: "Actor/Target ResourceType Operator Threshold[%]" (e.g., "T HP < 30%" or "A ARMOR > 50").
     * Returns null if the condition string is blank.
     */
    public static ActionConditional parseCondition(String conditionStr, int rowNum) {
        if (conditionStr == null || conditionStr.trim().isEmpty()) {
            return null; // No condition
        }

        String[] parts = conditionStr.trim().split(" ");
        if (parts.length != 4) {
            throw new IllegalArgumentException("Row " + rowNum + ": Invalid condition format: " + conditionStr);
        }

        String actorOrTarget = parts[0];
        String resourceTypeStr = parts[1];
        String operator = parts[2];
        String thresholdStr = parts[3];

        if (!actorOrTarget.equals("A") && !actorOrTarget.equals("T")) {
            throw new IllegalArgumentException("Row " + rowNum + ": Invalid Actor/Target in condition: " + actorOrTarget);
        }
        // Always conditional on target for now (adjust if needed)
        boolean conditionalOnActor = actorOrTarget.equals("A");

        // Parse ResourceType
        ResourceType conditionalOn;
        try {
            conditionalOn = ResourceType.valueOf(resourceTypeStr);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Row " + rowNum + ": Invalid ResourceType in condition: " + resourceTypeStr);
        }

        // Parse operator
        boolean validIfGreaterEqual;
        if (operator.equals(">")) {
            validIfGreaterEqual = true;
        } else if (operator.equals("<")) {
            validIfGreaterEqual = false;
        } else {
            throw new IllegalArgumentException("Row " + rowNum + ": Invalid operator in condition: " + operator);
        }

        // Parse threshold
        boolean thresholdIsPercentage = thresholdStr.endsWith("%");
        if (thresholdIsPercentage) {
            thresholdStr = thresholdStr.substring(0, thresholdStr.length() - 1);
        }
        int threshold;
        try {
            threshold = Integer.parseInt(thresholdStr);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Row " + rowNum + ": Invalid threshold in condition: " + thresholdStr);
        }

        return new ActionConditional(conditionalOnActor, conditionalOn, validIfGreaterEqual, threshold, thresholdIsPercentage);
    }

    /**
     * Parses a pipe-separated string of DamageType enum values into a Set<DamageType>.
     * Empty cells return a null object.
     * Format: "DamageType1|DamageType2" (e.g., "FIRE|MAGICAL").
     */
    public static Set<DamageType> parseDamageTypeSet(Map<String, String> rowData, String column, int rowNum) {
        String value = rowData.get(column);
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            Set<DamageType> damageTypes = Arrays.stream(value.split("\\|"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(DamageType::valueOf)
                    .collect(Collectors.toSet());
            if (damageTypes.isEmpty()) {
                throw new IllegalArgumentException("Row " + rowNum + ": " + column + " must not be empty: " + value);
            }
            return damageTypes;
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Row " + rowNum + ": Invalid " + column + ": " + value);
        }
    }

    /**
     * Parses a pipe-separated string of StatusEffect.Classification enum values into a Set<StatusEffect.Classification>.
     * Format: "Classification1|Classification2" (e.g., "POISON|BLEED"). Empty set is allowed.
     */
    public static Set<StatusEffect.Classification> parseClassificationSet(Map<String, String> rowData, String column, int rowNum) {
        String value = rowData.get(column);
        if (value == null || value.trim().isEmpty()) {
            return Set.of();
        }
        try {
            return Arrays.stream(value.split("\\|"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(StatusEffect.Classification::valueOf)
                    .collect(Collectors.toSet());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Row " + rowNum + ": Invalid " + column + ": " + value);
        }
    }

    /**
     * Parses a pipe-separated string of ActionType enum values into a Set<ActionType>.
     * Format: "ActionType1|ActionType2" (e.g., "WEAPON_ATTACK|NONWEAPON_ATTACK") or "ALL" for all types.
     * Empty set is allowed.
     */
    public static Set<ActionType> parseActionTypeSet(Map<String, String> rowData, String column, int rowNum) {
        String value = rowData.get(column);
        if (value == null || value.trim().isEmpty()) {
            return Set.of();
        }
        try {
            return Arrays.stream(value.split("\\|"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(ActionType::valueOf)
                    .collect(Collectors.toSet());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Row " + rowNum + ": Invalid " + column + ": " + value);
        }
    }

    /**
     * Parses an ActionTriggerSE.TriggerTypes enum value.
     */
    public static ActionTriggerSE.TriggerTypes parseTriggerType(Map<String, String> rowData, String column, int rowNum) {
        String value = rowData.get(column);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Row " + rowNum + ": Missing or empty " + column);
        }
        try {
            return ActionTriggerSE.TriggerTypes.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Row " + rowNum + ": Invalid " + column + ": " + value);
        }
    }

    public static List<Integer> parseFinisherValues(Map<String, String> rowData, String column, int rowNum) {
        String value = rowData.get(column);
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            String[] parts = value.split("\\|");
            if (parts.length != 4) {
                throw new IllegalArgumentException("Expected 4 pipe-separated ints");
            }

            return Arrays.stream(parts)
                    .map(String::trim)
                    .map(Integer::parseInt)
                    .peek(v -> {
                        if (v <= 0) {
                            throw new IllegalArgumentException(
                                    "Values must be positive: " + v);
                        }
                    }).toList();
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Row " + rowNum + ": Invalid " +
                    column + ": " + value, e);
        }
    }

    /**
     * Parses a pipe-separated string of action names into a List<Action>, validating against existing actions.
     * Format: "ActionName1|ActionName2" (e.g., "DOT_BLOOD_FRENZY_BLEED"). Non-empty list required.
     */
    public static List<Action> parseActionList(Map<String, String> rowData, String column, int rowNum, Map<String, Action> existingActions) {
        String value = rowData.get(column);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Row " + rowNum + ": " + column + " must not be empty");
        }
        try {
            List<Action> result = Arrays.stream(value.split("\\|"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(name -> {
                        Action action = existingActions.get(name);
                        if (action == null) {
                            throw new IllegalArgumentException("Row " + rowNum + ": Invalid action in " + column + ": " + name + " not found");
                        }
                        return action;
                    })
                    .collect(Collectors.toList());
            if (result.isEmpty()) {
                throw new IllegalArgumentException("Row " + rowNum + ": " + column + " must contain at least one valid action: " + value);
            }
            return result;
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Row " + rowNum + ": Invalid " + column + ": " + value);
        }
    }

    /**
     *  Handle the status duration field for status effects that can have a partial-round effect.
     *  i.e. for Blur (-25% to get hit for 3 rounds), the round where Blur is flipped should not
     *  count toward the 3-turn duration, as it is short a round if actor acts last during the casting turn.
     *  Each status effect type loader needs to determine whether it falls into this category.
     */
    public static int getActualStatusDuration(int duration) {
        if (duration == INFINITE) {
            return INFINITE;
        }
        return duration + 1;
    }

}
