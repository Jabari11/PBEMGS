package com.pbemgs.game.rpg.combat.action;

import com.pbemgs.game.rpg.combat.CombatChar;
import com.pbemgs.game.rpg.combat.CombatCharFactory;
import com.pbemgs.game.rpg.combat.CombatLog;
import com.pbemgs.game.rpg.combat.TargetType;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

/**
 * Action that summons one or more creatures of the same type.
 */
public class ActionSummon extends Action {

    private final int count;
    private final String summonName;
    private final int duration;

    public ActionSummon(String name, int count, String summonName, int duration, ActionConditional condition) {
        super(name, ActionType.SUMMON, true, TargetType.SELF, 0, 100, condition);
        this.count = count;
        this.summonName = summonName;
        this.duration = duration;
    }

    /**
     * Create a clone with an updated summon count (used for Decorator wrappers)
     */
    public ActionSummon withSummonCount(int newCount) {
        return new ActionSummon(name, newCount, summonName, duration, conditional);
    }

    public List<CombatChar> process(CombatChar actor, CombatChar target, boolean isCardAction,
                                    Queue<TriggeredAction> actionQueue, CombatLog logger) {
        List<CombatChar> newUnits = new ArrayList<>();

        for (int x = 0; x < count; ++x) {
            // TODO: This will get moved to a "summoned unit factory" eventually.
            newUnits.add(CombatCharFactory.getSummonedUnit(summonName, actor.getDisplayLevel(), actor.getSide(), duration));
        }
        return newUnits;
    }
}
