package com.soulsplugin;

import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;

/**
 * Static definition of every soul in the game: display metadata, texture
 * mapping, and passive effects. Ability *cooldowns, damage, and other
 * tunable numbers* now live in config.yml (see
 * {@link com.soulsplugin.abilities.AbilityConfig}) rather than here, so
 * server owners can rebalance without a recompile. Ability *logic* lives in
 * {@link com.soulsplugin.abilities.AbilityExecutor}.
 */
public enum SoulType {

    LEVIATHAN(
            "Leviathan Soul",
            10001,
            '\uE001',
            "Abalath's Grasp",
            "Inkveil",
            null
    ),
    ABYSSAL(
            "Abyssal Soul",
            10002,
            '\uE002',
            "Void Scent",
            "Shadow Instinct",
            null
    ),
    WRAITH(
            "Wraith Soul",
            10003,
            '\uE003',
            "Decay Breach",
            "Plague Surge",
            null
    ),
    VANGUARD(
            "Vanguard Soul",
            10004,
            '\uE004',
            "Airstream Sprint",
            "Sonic Shockwave",
            null
    ),
    TITAN(
            "Titan Soul",
            10005,
            '\uE005',
            "Obsidian Hide",
            "Seismic Shock",
            null
    ),
    ELDRITCH(
            "Eldritch Soul",
            10006,
            '\uE006',
            "Eldritch Gaze",
            "Eldritch Renewal",
            "Void Inversion"
    );

    private final String displayName;
    private final int customModelData;
    private final char icon;
    private final String ability1Name;
    private final String ability2Name;
    private final String ability3Name; // only ELDRITCH uses a third ability

    SoulType(String displayName, int customModelData, char icon,
             String ability1Name, String ability2Name, String ability3Name) {
        this.displayName = displayName;
        this.customModelData = customModelData;
        this.icon = icon;
        this.ability1Name = ability1Name;
        this.ability2Name = ability2Name;
        this.ability3Name = ability3Name;
    }

    public String displayName() {
        return displayName;
    }

    public int customModelData() {
        return customModelData;
    }

    public char icon() {
        return icon;
    }

    public String abilityName(int slot) {
        return switch (slot) {
            case 1 -> ability1Name;
            case 2 -> ability2Name;
            case 3 -> ability3Name;
            default -> throw new IllegalArgumentException("Invalid ability slot: " + slot);
        };
    }

    public boolean hasThirdAbility() {
        return ability3Name != null;
    }

    /**
     * Permanent passive potion effects applied while this soul is active.
     * Duration is intentionally huge (re-applied periodically by
     * PassiveEffectTask) rather than Integer.MAX_VALUE so effects survive
     * relogs cleanly without overflow oddities in some clients.
     */
    public List<PotionEffect> passiveEffects() {
        List<PotionEffect> effects = new ArrayList<>();
        int dur = 999999;
        switch (this) {
            case LEVIATHAN -> effects.add(new PotionEffect(PotionEffectType.HASTE, dur, 0, true, false, false));
            case ABYSSAL -> {
                effects.add(new PotionEffect(PotionEffectType.SPEED, dur, 1, true, false, false));
                effects.add(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, dur, 0, true, false, false));
            }
            case WRAITH -> effects.add(new PotionEffect(PotionEffectType.WATER_BREATHING, dur, 0, true, false, false));
            case VANGUARD -> effects.add(new PotionEffect(PotionEffectType.SPEED, dur, 0, true, false, false));
            case TITAN -> effects.add(new PotionEffect(PotionEffectType.RESISTANCE, dur, 0, true, false, false));
            case ELDRITCH -> effects.add(new PotionEffect(PotionEffectType.REGENERATION, dur, 0, true, false, false));
        }
        return effects;
    }
}
