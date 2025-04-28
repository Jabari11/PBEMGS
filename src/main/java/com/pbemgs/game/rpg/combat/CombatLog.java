package com.pbemgs.game.rpg.combat;

import java.util.HashSet;
import java.util.Set;

/**
 * A (possibly temporary) class to handle logging during development.  Logs will have a
 * level that defines where they go.
 */
public class CombatLog {
    private Set<LogEvent> logTypes;  // types of events to log
    private LogLevel currLogLevel;   // log things <= this level

    public CombatLog() {
        logTypes = new HashSet<>();
        currLogLevel = LogLevel.DEV;
    }

    public void addLogType(LogEvent type) {
        logTypes.add(type);
    }

    public void setLogLevel(LogLevel newLevel) {
        currLogLevel = newLevel;
    }

    public void log(LogEvent type, LogLevel level, String logstr) {
        if (level.ordinal() <= currLogLevel.ordinal() &&
                (logTypes.contains(type) || logTypes.contains(LogEvent.ALL))) {
            System.out.println(logstr);
        }
    }

}
