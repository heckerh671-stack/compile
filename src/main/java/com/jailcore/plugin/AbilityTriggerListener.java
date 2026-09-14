package com.soulsplugin.listeners;

import com.destroystokyo.paper.event.player.PlayerJumpEvent;
import com.soulsplugin.SoulType;
import com.soulsplugin.abilities.AbilityExecutor;
import com.soulsplugin.data.PlayerDataManager;
import com.soulsplugin.util.ActionBarUtil;
import com.soulsplugin.util.CooldownManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;

import java.util.UUID;

/**
 * Wires the physical input triggers to soul abilities:
 *
 *   Ability 1 -> offhand swap key (F by default) pressed while standing
 *   Ability 2 -> offhand swap key pressed WHILE CROUCHING (not crouch alone -
 *                crouching by itself no longer fires anything, so normal
 *                sneaking to peek over a ledge, sneak-mine, etc. never
 *                accidentally casts an ability)
 *   Ability 3 -> jump, gated on an empty offhand so normal shield/totem
 *                offhand usage is never intercepted
 *
 * All ability-bound trigger events are cancelled/neutralised for players
 * with an active soul so no vanilla item-swap or shield-equip behaviour
 * occurs as a side effect of casting.
 */
public class AbilityTriggerListener implements Listener {

    private final PlayerDataManager dataManager;
    private final CooldownManager cooldowns;
    private final AbilityExecutor abilities;

    public AbilityTriggerListener(PlayerDataManager dataManager, CooldownManager cooldowns, AbilityExecutor abilities) {
        this.dataManager = dataManager;
        this.cooldowns = cooldowns;
        this.abilities = abilities;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        SoulType soul = dataManager.getActiveSoul(player.getUniqueId());
        if (soul == null) {
            return; // no active soul: allow normal vanilla offhand swap
        }
        // Anti-shield flag: always cancel the actual item swap while a soul is
        // active so this control exclusively drives abilities and never lets a
        // shield/totem slide into the offhand slot as a side effect.
        event.setCancelled(true);

        // Combo trigger: offhand key while crouching fires Ability 2 instead
        // of Ability 1. This is intentionally checked here (not on the
        // sneak-toggle event) so Ability 2 requires the deliberate two-input
        // combo rather than firing off crouching alone.
        if (player.isSneaking()) {
            fireAbility(player, soul, 2);
        } else {
            fireAbility(player, soul, 1);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onToggleSneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) {
            return; // only fire on the press, not the release
        }
        Player player = event.getPlayer();
        SoulType soul = dataManager.getActiveSoul(player.getUniqueId());
        if (soul == null) {
            return;
        }

        // Vanguard's Airstream Sprint opens a short window where the next
        // crouch or jump becomes a forward dash instead of a normal ability.
        // This is the one crouch-alone interaction that remains: it's a
        // follow-up to an ability the player already cast deliberately, not
        // a fresh cast triggered by crouching on its own.
        if (soul == SoulType.VANGUARD) {
            abilities.tryConsumeAirstreamWindow(player);
        }

        // Ability 2 itself no longer fires from crouch alone - see
        // onSwapHands() above for the offhand+crouch combo trigger.
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onJump(PlayerJumpEvent event) {
        Player player = event.getPlayer();
        SoulType soul = dataManager.getActiveSoul(player.getUniqueId());
        if (soul == null) {
            return;
        }

        if (soul == SoulType.VANGUARD && abilities.tryConsumeAirstreamWindow(player)) {
            return;
        }

        if (!player.getInventory().getItemInOffHand().getType().isAir()) {
            return;
        }

        // Ability slot 3 is reserved for jump-bound effects. Only Eldritch
        // currently defines one (Void Inversion); other souls have no
        // jump-triggered ability and simply ignore this event.
        if (soul.hasThirdAbility()) {
            fireAbility(player, soul, 3);
        }
    }

    private void fireAbility(Player player, SoulType soul, int slot) {
        UUID uuid = player.getUniqueId();
        String key = soul.name() + "_ABILITY_" + slot;
        long remaining = cooldowns.getRemainingSeconds(uuid, key);
        if (remaining > 0) {
            ActionBarUtil.sendCooldownWarning(player, soul.abilityName(slot), remaining);
            return;
        }
        abilities.execute(player, soul, slot);
    }
}
