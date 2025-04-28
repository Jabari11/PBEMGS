package com.pbemgs.game.rpg.model;

/**
 *  A "pool" of a character resource - current and max values.
 *  Current value represented as a float internally for evenly handling percent-of-max adjustments.
 */
public class CharacterResource {
    private static final float EPSILON = 0.0001f;

    private float currVal;
    private int maxVal;

    public CharacterResource(int initial, int max) {
        currVal = (float) initial;
        maxVal = max;
    }

    public int getCurrent() {
        return Math.round(currVal);
    }

    public int getMax() {
        return maxVal;
    }

    /**
     * [0..1] percentage
     */
    public float getCurrentPct() {
        if (maxVal == 0) {
            return 0.0f;
        }
        return currVal / (float) maxVal;
    }

    public boolean canSpend(int cost) {
        return getCurrent() >= cost;
    }

    public boolean gainWillCap(int gain) {
        return getCurrent() + gain >= maxVal;
    }

    public boolean isZero() {
        return currVal < EPSILON;
    }

    public boolean isFull() {
        return Math.abs(currVal - maxVal) < EPSILON;
    }

    /**
     *  Discrete adjustment
     */
    public void adjust(float gain) {
        currVal += (float)gain;
        clamp();
    }

    /**
     *  Adjusts by percentage of max value.
     *  i.e. 0.05f = add 5% of max.  -0.1 = subtract 10% of max.
     */
    public void adjustPctOfMax(float pctAdjust) {
        currVal += pctAdjust * maxVal;
        clamp();
    }

    /**
     * Adjust by percent of current value
     */
    public void adjustPctOfCurrent(float pctAdjust) {
        currVal += pctAdjust * currVal;
        clamp();
    }

    public void clear() {
        currVal = 0.0f;
    }

    private void clamp() {
        if (currVal > (float) maxVal) {
            currVal = (float) maxVal;
        }
        if (currVal < 0.0f) {
            currVal = 0.0f;
        }
    }


    public float getRaw() {
        return currVal;
    }

    @Override
    public String toString() {
        return getCurrent() + "/" + getMax();
    }

}
