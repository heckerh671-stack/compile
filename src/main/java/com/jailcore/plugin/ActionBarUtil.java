package com.soulsplugin.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;

/**
 * Sends the standardized ability-activation action bar message:
 * [Icon] §e§lABILITY ACTIVATED: §b§l[Ability Name] §7(Cooldown: [X]s)
 */
public final class ActionBarUtil {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private ActionBarUtil() {
    }

    public static void sendAbilityActivated(Player player, char icon, String abilityName, int cooldownSeconds) {
        String raw = icon + " §e§lABILITY ACTIVATED: §b§l" + abilityName + " §7(Cooldown: " + cooldownSeconds + "s)";
        Component component = LEGACY.deserialize(raw);
        player.sendActionBar(component);
    }

    public static void sendCooldownWarning(Player player, String abilityName, long remainingSeconds) {
        Component component = LEGACY.deserialize("§c" + abilityName + " is on cooldown: §f" + remainingSeconds + "s");
        player.sendActionBar(component);
    }
}
