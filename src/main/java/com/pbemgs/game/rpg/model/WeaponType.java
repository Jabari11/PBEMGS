package com.pbemgs.game.rpg.model;

import com.pbemgs.game.rpg.combat.DamageType;

public enum WeaponType {
    SWORD(24, 0f, 0f, DamageType.PHYSICAL),
    DAGGER(18, 0.6f, 0f, DamageType.PHYSICAL),
    MACE(20, 0f, 0.4f, DamageType.PHYSICAL),
    STAFF(20, 0f, 0f, DamageType.MAGICAL),
    WAND(15, 1.0f, 0f, DamageType.MAGICAL),
    SCEPTER(12, 1.0f, 0f, DamageType.HOLY);

    private final int baseDamage;
    private final float piercePct;
    private final float crushPct;
    private final DamageType damageType;

    WeaponType(int base, float pierce, float crush, DamageType damageType) {
        baseDamage = base;
        piercePct = pierce;
        crushPct = crush;
        this.damageType = damageType;
    }

    public int getBaseDamage() {
        return baseDamage;
    }

    public float getPiercePct() {
        return piercePct;
    }

    public float getCrushPct() {
        return crushPct;
    }

    public DamageType getDamageType() {
        return damageType;
    }
}
