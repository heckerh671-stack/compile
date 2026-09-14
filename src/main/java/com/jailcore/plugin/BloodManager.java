package com.soulsplugin.data;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Virtual ledger for Blood Fragments. Fragments never occupy a physical
 * inventory slot -- they are a pure integer counter per player, persisted
 * to blood_data.yml. This class also tracks which permanent-until-death
 * potion effect (if any) a player currently holds, so the death-loss
 * condition can remove it cleanly.
 */
public class BloodManager {

    private final JavaPlugin plugin;
    private final File dataFile;
    private final FileConfiguration dataConfig;

    private final Map<UUID, Integer> fragments = new HashMap<>();
    private final Map<UUID, PotionEffectType> activeBloodEffect = new HashMap<>();

    public BloodManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "blood_data.yml");
        if (!dataFile.exists()) {
            plugin.getDataFolder().mkdirs();
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not create blood_data.yml", e);
            }
        }
        this.dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        loadAll();
    }

    private void loadAll() {
        if (dataConfig.getConfigurationSection("players") == null) {
            return;
        }
        for (String key : dataConfig.getConfigurationSection("players").getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                int amount = dataConfig.getInt("players." + key + ".fragments", 0);
                fragments.put(uuid, amount);
                String effectName = dataConfig.getString("players." + key + ".activeEffect");
                if (effectName != null) {
                    PotionEffectType type = PotionEffectType.getByName(effectName);
                    if (type != null) {
                        activeBloodEffect.put(uuid, type);
                    }
                }
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Skipping malformed blood_data.yml entry: " + key);
            }
        }
    }

    public void save() {
        for (Map.Entry<UUID, Integer> entry : fragments.entrySet()) {
            dataConfig.set("players." + entry.getKey() + ".fragments", entry.getValue());
        }
        for (Map.Entry<UUID, PotionEffectType> entry : activeBloodEffect.entrySet()) {
            dataConfig.set("players." + entry.getKey() + ".activeEffect", entry.getValue().getKey().getKey());
        }
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save blood_data.yml", e);
        }
    }

    public int getFragments(UUID uuid) {
        return fragments.getOrDefault(uuid, 0);
    }

    public void addFragment(UUID uuid, int amount) {
        fragments.merge(uuid, amount, Integer::sum);
        save();
    }

    /** Attempts to spend {@code cost} fragments. Returns false if the player cannot afford it. */
    public boolean spendFragments(UUID uuid, int cost) {
        int current = getFragments(uuid);
        if (current < cost) {
            return false;
        }
        fragments.put(uuid, current - cost);
        save();
        return true;
    }

    public PotionEffectType getActiveBloodEffect(UUID uuid) {
        return activeBloodEffect.get(uuid);
    }

    public void setActiveBloodEffect(UUID uuid, PotionEffectType type) {
        activeBloodEffect.put(uuid, type);
        save();
    }

    public void clearActiveBloodEffect(UUID uuid) {
        activeBloodEffect.remove(uuid);
        dataConfig.set("players." + uuid + ".activeEffect", null);
        save();
    }
}
