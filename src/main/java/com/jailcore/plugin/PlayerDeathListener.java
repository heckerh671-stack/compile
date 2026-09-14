package com.soulsplugin.listeners;

import com.soulsplugin.SoulType;
import com.soulsplugin.data.BloodManager;
import com.soulsplugin.data.PlayerDataManager;
import com.soulsplugin.items.SoulItemFactory;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.potion.PotionEffectType;

public class PlayerDeathListener implements Listener {

    private final PlayerDataManager dataManager;
    private final BloodManager bloodManager;
    private final SoulItemFactory itemFactory;

    public PlayerDeathListener(PlayerDataManager dataManager, BloodManager bloodManager, SoulItemFactory itemFactory) {
        this.dataManager = dataManager;
        this.bloodManager = bloodManager;
        this.itemFactory = itemFactory;
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();

        // --- Soul death penalty ---
        SoulType activeSoul = dataManager.getActiveSoul(victim.getUniqueId());
        if (activeSoul != null) {
            dataManager.clearActiveSoul(victim.getUniqueId());
            // dropItem (not dropItemNaturally) is used so the soul lands at the
            // player's exact death coordinates with no random scatter offset.
            victim.getWorld().dropItem(victim.getLocation(), itemFactory.createSoulItem(activeSoul));
            victim.sendMessage(Component.text("Your " + activeSoul.displayName() + " has been ripped from you and dropped.",
                    NamedTextColor.DARK_RED));
        }

        // --- Blood loss condition: dying strips any active permanent Blood effect ---
        PotionEffectType bloodEffect = bloodManager.getActiveBloodEffect(victim.getUniqueId());
        if (bloodEffect != null) {
            victim.removePotionEffect(bloodEffect);
            bloodManager.clearActiveBloodEffect(victim.getUniqueId());
            victim.sendMessage(Component.text("Your Blood-granted power has faded with your death.", NamedTextColor.DARK_RED));
        }

        // --- Blood fragment allocation on a PvP kill ---
        Player killer = victim.getKiller();
        if (killer != null && killer != victim) {
            bloodManager.addFragment(killer.getUniqueId(), 1);
            killer.sendMessage(Component.text("You harvested a ", NamedTextColor.RED)
                    .append(Component.text("Blood Fragment", NamedTextColor.DARK_RED))
                    .append(Component.text(" from " + victim.getName() + ".", NamedTextColor.RED)));
        }
    }
}
