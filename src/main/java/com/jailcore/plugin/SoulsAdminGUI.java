package com.soulsplugin.gui;

import com.soulsplugin.SoulType;
import com.soulsplugin.items.SoulItemFactory;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * The single admin-facing GUI opened by /souls. Contains one physical copy
 * of each of the six soul items for quick grabbing/testing/distribution.
 */
public class SoulsAdminGUI implements InventoryHolder {

    public static final String TITLE = "Soul Distribution";

    private final Inventory inventory;

    public SoulsAdminGUI(SoulItemFactory itemFactory) {
        this.inventory = Bukkit.createInventory(this, 9, Component.text(TITLE, NamedTextColor.DARK_PURPLE));
        int slot = 1;
        for (SoulType type : SoulType.values()) {
            inventory.setItem(slot, itemFactory.createSoulItem(type));
            slot++;
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public static void open(Player op, SoulItemFactory itemFactory) {
        op.openInventory(new SoulsAdminGUI(itemFactory).getInventory());
    }
}
