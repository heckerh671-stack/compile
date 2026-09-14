package com.soulsplugin.commands;

import com.soulsplugin.gui.SoulsAdminGUI;
import com.soulsplugin.items.SoulItemFactory;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * The single exposed admin command. Strictly OP-gated -- both by the
 * plugin.yml permission default and a defensive runtime check here.
 */
public class SoulsCommand implements CommandExecutor {

    private final SoulItemFactory itemFactory;

    public SoulsCommand(SoulItemFactory itemFactory) {
        this.itemFactory = itemFactory;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }
        if (!player.isOp()) {
            player.sendMessage(Component.text("You must be a Server Operator to use this command.", NamedTextColor.RED));
            return true;
        }
        SoulsAdminGUI.open(player, itemFactory);
        return true;
    }
}
