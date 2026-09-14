package com.soulsplugin;

import com.soulsplugin.abilities.AbilityConfig;
import com.soulsplugin.abilities.AbilityExecutor;
import com.soulsplugin.commands.BloodCommand;
import com.soulsplugin.commands.SoulsCommand;
import com.soulsplugin.data.BloodManager;
import com.soulsplugin.data.PlayerDataManager;
import com.soulsplugin.items.SoulItemFactory;
import com.soulsplugin.listeners.AbilityTriggerListener;
import com.soulsplugin.listeners.CombatListener;
import com.soulsplugin.listeners.GUIListener;
import com.soulsplugin.listeners.PlayerDeathListener;
import com.soulsplugin.listeners.SoulAbsorbListener;
import com.soulsplugin.util.CooldownManager;
import com.soulsplugin.util.PassiveEffectTask;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

public final class SoulsPlugin extends JavaPlugin {

    /** Static, offline, hard-coded license literal. Not fetched from any network source. */
    private static final String REQUIRED_LICENSE_KEY = "w2e3k4s5o6m7";

    private PlayerDataManager playerDataManager;
    private BloodManager bloodManager;
    private SoulItemFactory soulItemFactory;
    private CooldownManager cooldownManager;
    private AbilityConfig abilityConfig;
    private AbilityExecutor abilityExecutor;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        if (!validateLicense()) {
            getLogger().severe("=================================================");
            getLogger().severe(" SoulsPlugin license validation FAILED.");
            getLogger().severe(" The 'license-key' value in config.yml does not");
            getLogger().severe(" match the required key. Disabling plugin.");
            getLogger().severe("=================================================");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.playerDataManager = new PlayerDataManager(this);
        this.bloodManager = new BloodManager(this);
        this.soulItemFactory = new SoulItemFactory(this);
        this.cooldownManager = new CooldownManager();
        this.abilityConfig = new AbilityConfig(getConfig());
        this.abilityExecutor = new AbilityExecutor(this, cooldownManager, abilityConfig);

        registerListeners();
        registerCommands();

        long refreshInterval = getConfig().getLong("passive-refresh-interval-ticks", 100L);
        new PassiveEffectTask(playerDataManager).runTaskTimer(this, 0L, refreshInterval);

        getLogger().info("SoulsPlugin enabled -- license verified, all systems online.");
    }

    @Override
    public void onDisable() {
        if (playerDataManager != null) {
            playerDataManager.save();
        }
        if (bloodManager != null) {
            bloodManager.save();
        }
        getLogger().info("SoulsPlugin disabled -- data flushed to disk.");
    }

    private boolean validateLicense() {
        String configured = getConfig().getString("license-key", "");
        return REQUIRED_LICENSE_KEY.equals(configured);
    }

    private void registerListeners() {
        var pm = getServer().getPluginManager();
        pm.registerEvents(new SoulAbsorbListener(this, playerDataManager, soulItemFactory), this);
        pm.registerEvents(new AbilityTriggerListener(playerDataManager, cooldownManager, abilityExecutor), this);
        pm.registerEvents(new CombatListener(abilityExecutor, abilityConfig), this);
        pm.registerEvents(new PlayerDeathListener(playerDataManager, bloodManager, soulItemFactory), this);
        pm.registerEvents(new GUIListener(bloodManager), this);
    }

    private void registerCommands() {
        var soulsCmd = getCommand("souls");
        if (soulsCmd != null) {
            soulsCmd.setExecutor(new SoulsCommand(soulItemFactory));
        } else {
            getLogger().log(Level.WARNING, "Could not register /souls -- check plugin.yml");
        }

        var bloodCmd = getCommand("blood");
        if (bloodCmd != null) {
            bloodCmd.setExecutor(new BloodCommand(bloodManager));
        } else {
            getLogger().log(Level.WARNING, "Could not register /blood -- check plugin.yml");
        }
    }
}
