package com.pbemgs.game.triad;

import com.pbemgs.model.Location;

import java.util.List;
import java.util.Random;

/**
 * A small text formatter to vary the move announcements randomly - just for a bit of fun!
 */
public class TriadMoveFormatter {
    private static final Random rng = new Random();

    public static String formatMoveAnnouncement(String handle, int cardId, Location loc) {
        String cardName = TriadCardSet.getById(cardId).name();
        String location = loc.toString();

        // Different move announcement styles
        List<String> moveFormats = List.of(
                "%s places %s at %s!",
                "%s deploys %s to %s!",
                "%s drops %s into position at %s!",
                "%s sends %s to claim %s!",
                "%s strategically positions %s at %s!",
                "%s plays %s, locking it in at %s!"
        );

        // Pick a random move format
        String format = moveFormats.get(rng.nextInt(moveFormats.size()));

        // Return formatted message
        return String.format(format, handle, cardName, location);
    }
}
