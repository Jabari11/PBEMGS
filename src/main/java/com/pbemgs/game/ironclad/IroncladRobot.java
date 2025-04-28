package com.pbemgs.game.ironclad;

import com.pbemgs.model.Location;

/**
 * A robot in Ironclad.  Has a location, side, and HP.
 */
public record IroncladRobot(Location loc, IroncladSide side, int HP) {
}
