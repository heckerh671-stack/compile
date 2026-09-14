package com.soulsplugin.abilities;

import com.soulsplugin.SoulType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Reads every tunable number an ability uses - cooldowns, damage, radii,
 * potion durations/amplifiers, knockback strengths - out of config.yml
 * instead of having them hard-coded in {@link AbilityExecutor}.
 *
 * Config layout:
 * <pre>
 * abilities:
 *   LEVIATHAN:
 *     ability1:
 *       cooldown-seconds: 60
 *       radius: 5
 *       ...
 *     ability2:
 *       ...
 * </pre>
 *
 * Every getter takes a hard-coded default matching the plugin's original
 * balance, so a config.yml that's missing a key (or an older config from
 * before this existed) still behaves exactly like before - the config only
 * overrides values that are actually present.
 */
public class AbilityConfig {

    private final FileConfiguration config;

    public AbilityConfig(FileConfiguration config) {
        this.config = config;
    }

    public int cooldownSeconds(SoulType soul, int slot, int defaultValue) {
        return getInt(soul, slot, "cooldown-seconds", defaultValue);
    }

    public double getDouble(SoulType soul, int slot, String key, double defaultValue) {
        ConfigurationSection section = abilitySection(soul, slot);
        return section == null ? defaultValue : section.getDouble(key, defaultValue);
    }

    public int getInt(SoulType soul, int slot, String key, int defaultValue) {
        ConfigurationSection section = abilitySection(soul, slot);
        return section == null ? defaultValue : section.getInt(key, defaultValue);
    }

    /** Convenience for potion durations expressed in seconds in config, converted to ticks. */
    public int getDurationTicks(SoulType soul, int slot, String key, double defaultSeconds) {
        double seconds = getDouble(soul, slot, key, defaultSeconds);
        return (int) Math.round(seconds * 20.0);
    }

    private ConfigurationSection abilitySection(SoulType soul, int slot) {
        ConfigurationSection abilities = config.getConfigurationSection("abilities");
        if (abilities == null) {
            return null;
        }
        ConfigurationSection soulSection = abilities.getConfigurationSection(soul.name());
        if (soulSection == null) {
            return null;
        }
        return soulSection.getConfigurationSection("ability" + slot);
    }
}
