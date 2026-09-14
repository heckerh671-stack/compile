package com.soulsplugin.listeners;

import com.soulsplugin.data.BloodManager;
import com.soulsplugin.gui.BloodGUI;
import com.soulsplugin.gui.SoulsAdminGUI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class GUIListener implements Listener {

    private final BloodManager bloodManager;
    private final Random random = new Random();

    /** The six possible rewards from a Blood roll, as specified in the blueprint. */
    private final List<PotionEffectType> rollPool;

    public GUIListener(BloodManager bloodManager) {
        this.bloodManager = bloodManager;
        this.rollPool = new ArrayList<>();
        rollPool.add(PotionEffectType.STRENGTH);
        rollPool.add(PotionEffectType.SPEED);
        rollPool.add(PotionEffectType.REGENERATION);
        rollPool.add(PotionEffectType.ABSORPTION);
        rollPool.add(PotionEffectType.HASTE);
        rollPool.add(PotionEffectType.FIRE_RESISTANCE);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof SoulsAdminGUI) {
            // Admin distribution GUI: ops can freely take copies via normal or
            // shift click, since the whole point is fast grab-and-go testing.
            return;
        }

        if (event.getInventory().getHolder() instanceof BloodGUI) {
            event.setCancelled(true);
            if (event.getSlot() != 4 || event.getClickedInventory() == null
                    || !(event.getClickedInventory().getHolder() instanceof BloodGUI)) {
                return;
            }
            handleRoll((Player) event.getWhoClicked());
        }
    }

    private void handleRoll(Player player) {
        if (!bloodManager.spendFragments(player.getUniqueId(), BloodGUI.ROLL_COST)) {
            player.sendMessage(Component.text("You don't have enough Blood Fragments.", NamedTextColor.RED));
            return;
        }

        // Rolling a new permanent effect replaces whatever the player already had.
        PotionEffectType previous = bloodManager.getActiveBloodEffect(player.getUniqueId());
        if (previous != null) {
            player.removePotionEffect(previous);
        }

        PotionEffectType rolled = rollPool.get(random.nextInt(rollPool.size()));
        int amplifier = (rolled == PotionEffectType.HASTE) ? 1 : 0; // "Haste II" per blueprint
        player.addPotionEffect(new PotionEffect(rolled, Integer.MAX_VALUE, amplifier, true, false, true));
        bloodManager.setActiveBloodEffect(player.getUniqueId(), rolled);

        player.sendMessage(Component.text("The ritual grants you permanent ", NamedTextColor.DARK_RED)
                .append(Component.text(rolled.getKey().getKey(), NamedTextColor.RED))
                .append(Component.text(" -- until you die.", NamedTextColor.DARK_RED)));
        player.closeInventory();
    }
}
