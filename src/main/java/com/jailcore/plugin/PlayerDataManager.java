package com.soulsplugin.data;

import com.soulsplugin.SoulType;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Tracks each player's single active soul. Backed by a clean in-memory
 * UUID map for zero-latency lookups during combat, and mirrored to a
 * dedicated data file (souls_data.yml) so the active soul survives a
 * server restart.
 */
public class PlayerDataManager {

    private final JavaPlugin plugin;
    private final File dataFile;
    private final FileConfiguration dataConfig;

    /** Clean UUID -> active soul map. This is the source of truth at runtime. */
    private final Map<UUID, SoulType> activeSouls = new HashMap<>();

    public PlayerDataManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "souls_data.yml");
        if (!dataFile.exists()) {
            plugin.getDataFolder().mkdirs();
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not create souls_data.yml", e);
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
                String soulName = dataConfig.getString("players." + key + ".activeSoul");
                if (soulName != null) {
                    activeSouls.put(uuid, SoulType.valueOf(soulName));
                }
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Skipping malformed souls_data.yml entry: " + key);
            }
        }
    }

    public void save() {
        for (Map.Entry<UUID, SoulType> entry : activeSouls.entrySet()) {
            dataConfig.set("players." + entry.getKey() + ".activeSoul", entry.getValue().name());
        }
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save souls_data.yml", e);
        }
    }

    public SoulType getActiveSoul(UUID uuid) {
        return activeSouls.get(uuid);
    }

    public boolean hasActiveSoul(UUID uuid) {
        return activeSouls.containsKey(uuid);
    }

    /** Sets (or overwrites) the player's active soul. Only one may be active at a time. */
    public void setActiveSoul(UUID uuid, SoulType soul) {
        activeSouls.put(uuid, soul);
        dataConfig.set("players." + uuid + ".activeSoul", soul.name());
        save();
    }

    /** Removes the player's active soul entirely (used on the death penalty path). */
    public void clearActiveSoul(UUID uuid) {
        activeSouls.remove(uuid);
        dataConfig.set("players." + uuid + ".activeSoul", null);
        save();
    }
}
