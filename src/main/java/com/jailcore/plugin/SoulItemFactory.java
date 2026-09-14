package com.soulsplugin.items;

import com.soulsplugin.SoulType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the physical Gunpowder items that represent each soul. Every item
 * carries a PersistentDataContainer flag identifying which SoulType it is,
 * so absorption logic never has to rely on display-name string matching.
 */
public class SoulItemFactory {

    private final NamespacedKey soulTypeKey;

    public SoulItemFactory(JavaPlugin plugin) {
        this.soulTypeKey = new NamespacedKey(plugin, "soul_type");
    }

    public NamespacedKey soulTypeKey() {
        return soulTypeKey;
    }

    public ItemStack createSoulItem(SoulType type) {
        ItemStack item = new ItemStack(Material.GUNPOWDER);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text(type.displayName(), NamedTextColor.LIGHT_PURPLE)
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, true));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("A fragment of an ancient soul.", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(Component.text("Right-click to absorb.", NamedTextColor.DARK_PURPLE)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Ability 1: " + type.abilityName(1), NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Ability 2: " + type.abilityName(2), NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false));
        if (type.hasThirdAbility()) {
            lore.add(Component.text("Ability 3: " + type.abilityName(3), NamedTextColor.AQUA)
                    .decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lore);

        meta.setCustomModelData(type.customModelData());
        meta.getPersistentDataContainer().set(soulTypeKey, PersistentDataType.STRING, type.name());

        item.setItemMeta(meta);
        return item;
    }

    /** Returns the SoulType encoded on this item, or null if it isn't a soul item. */
    public SoulType readSoulType(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        String raw = meta.getPersistentDataContainer().get(soulTypeKey, PersistentDataType.STRING);
        if (raw == null) {
            return null;
        }
        try {
            return SoulType.valueOf(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
