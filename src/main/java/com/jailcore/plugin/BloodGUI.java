package com.soulsplugin.gui;

import com.soulsplugin.data.BloodManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * The Blood Fragment consumption menu opened by /blood. Spending
 * {@link #ROLL_COST} fragments rolls one random permanent-until-death
 * potion effect from the configured pool.
 */
public class BloodGUI implements InventoryHolder {

    public static final String TITLE = "Blood Ritual";
    public static final int ROLL_COST = 1;

    private final Inventory inventory;

    public BloodGUI(BloodManager bloodManager, Player player) {
        this.inventory = Bukkit.createInventory(this, 9, Component.text(TITLE, NamedTextColor.DARK_RED));

        int fragments = bloodManager.getFragments(player.getUniqueId());

        ItemStack rollItem = new ItemStack(Material.REDSTONE);
        ItemMeta meta = rollItem.getItemMeta();
        meta.displayName(Component.text("Spill Blood", NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, true));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Fragments: " + fragments, NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Cost: " + ROLL_COST + " fragment(s)", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(Component.text("Click to roll a random permanent effect.", NamedTextColor.DARK_RED)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);

        rollItem.setItemMeta(meta);
        inventory.setItem(4, rollItem);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public static void open(Player player, BloodManager bloodManager) {
        player.openInventory(new BloodGUI(bloodManager, player).getInventory());
    }
}
