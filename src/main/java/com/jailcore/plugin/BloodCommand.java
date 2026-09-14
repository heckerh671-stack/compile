package com.soulsplugin.commands;

import com.soulsplugin.data.BloodManager;
import com.soulsplugin.gui.BloodGUI;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Player-facing command trigger for the Blood Fragment consumption menu
 * (blueprint section 3). This is separate from the single admin command
 * mandated in section 4 ("/souls"), which stays the only OP-restricted
 * command in the plugin.
 */
public class BloodCommand implements CommandExecutor {

    private final BloodManager bloodManager;

    public BloodCommand(BloodManager bloodManager) {
        this.bloodManager = bloodManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }
        BloodGUI.open(player, bloodManager);
        return true;
    }
}
