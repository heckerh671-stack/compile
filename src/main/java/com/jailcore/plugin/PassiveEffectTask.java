package com.soulsplugin.util;

import com.soulsplugin.SoulType;
import com.soulsplugin.data.PlayerDataManager;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Runs on a fixed interval and silently re-applies each online player's
 * active-soul passive effects. This avoids ever needing Integer.MAX_VALUE
 * durations and guarantees the passive survives potion-milk-clearing edge
 * cases, dimension changes, etc.
 */
public class PassiveEffectTask extends BukkitRunnable {

    private final PlayerDataManager dataManager;

    public PassiveEffectTask(PlayerDataManager dataManager) {
        this.dataManager = dataManager;
    }

    @Override
    public void run() {
        for (Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
            SoulType active = dataManager.getActiveSoul(player.getUniqueId());
            if (active == null) {
                continue;
            }
            for (PotionEffect effect : active.passiveEffects()) {
                player.addPotionEffect(effect);
            }
        }
    }
}
