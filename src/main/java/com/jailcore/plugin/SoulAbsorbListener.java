package com.soulsplugin.listeners;

import com.soulsplugin.SoulType;
import com.soulsplugin.data.PlayerDataManager;
import com.soulsplugin.items.SoulItemFactory;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Handles Soul Procurement: right-clicking a soul item plays a short
 * absorption cutscene (blindness + darkness, a swirl of brown particles,
 * then the soul floating in front of the player as an item before it
 * vanishes) and only THEN actually absorbs it, replacing any previously
 * active soul and consuming the physical item.
 *
 * The {@code absorbing} lock below is what stops a player from absorbing
 * more than one soul at once: while the cutscene for one soul item is
 * playing, every further right-click on a soul item is cancelled and
 * ignored outright, rather than being allowed to queue up or immediately
 * overwrite the soul that's mid-absorption.
 */
public class SoulAbsorbListener implements Listener {

    private final JavaPlugin plugin;
    private final PlayerDataManager dataManager;
    private final SoulItemFactory itemFactory;

    /** Players currently mid-cutscene; further soul right-clicks are ignored until this clears. */
    private final Set<UUID> absorbing = new HashSet<>();

    /** Tracks the floating item display per player so it can be force-removed on disconnect. */
    private final java.util.Map<UUID, ItemDisplay> activeDisplays = new java.util.HashMap<>();

    public SoulAbsorbListener(JavaPlugin plugin, PlayerDataManager dataManager, SoulItemFactory itemFactory) {
        this.plugin = plugin;
        this.dataManager = dataManager;
        this.itemFactory = itemFactory;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return; // avoid double-firing for main+off hand
        }
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack item = event.getItem();
        SoulType soul = itemFactory.readSoulType(item);
        if (soul == null) {
            return;
        }

        event.setCancelled(true);
        Player player = event.getPlayer();

        if (!absorbing.add(player.getUniqueId())) {
            // Already mid-cutscene from an earlier click - ignore this one
            // entirely so a single stack of souls can't be chain-consumed
            // by spamming right-click (or by a duplicate PlayerInteractEvent
            // firing for the same physical click).
            return;
        }

        playAbsorptionCutscene(player, soul, item);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        absorbing.remove(uuid);
        ItemDisplay display = activeDisplays.remove(uuid);
        if (display != null && display.isValid()) {
            display.remove();
        }
    }

    private void playAbsorptionCutscene(Player player, SoulType soul, ItemStack item) {
        var config = plugin.getConfig();
        int swirlTicks = config.getInt("soul-absorption.swirl-duration-ticks", 40);
        int swirlParticlesPerTick = config.getInt("soul-absorption.swirl-particles-per-tick", 12);
        double swirlRadius = config.getDouble("soul-absorption.swirl-max-radius", 1.2);
        int floatTicks = config.getInt("soul-absorption.float-duration-ticks", 40);
        double floatDistance = config.getDouble("soul-absorption.float-distance", 1.3);
        double floatHeightOffset = config.getDouble("soul-absorption.float-height-offset", 0.1);
        double blindnessDarknessSeconds = config.getDouble("soul-absorption.blindness-darkness-duration-seconds", 4.0);

        int totalDurationTicks = (int) Math.round(blindnessDarknessSeconds * 20.0);
        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, totalDurationTicks, 0, true, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, totalDurationTicks, 0, true, false, false));
        player.getWorld().playSound(player.getLocation(), org.bukkit.Sound.ENTITY_WITHER_AMBIENT, 0.5f, 0.6f);

        Particle.DustOptions brownDust = new Particle.DustOptions(Color.fromRGB(101, 67, 33), 1.3f);
        ItemStack soulItemCopy = item.clone();
        soulItemCopy.setAmount(1);

        new BukkitRunnable() {
            int ticksElapsed = 0;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    finishWithoutAbsorbing(player);
                    cancel();
                    return;
                }
                if (ticksElapsed >= swirlTicks) {
                    cancel();
                    spawnFloatingSoul(player, soul, item, soulItemCopy, floatTicks, floatDistance, floatHeightOffset);
                    return;
                }

                Location center = player.getLocation().add(0, 1.0, 0);
                double progress = ticksElapsed / (double) swirlTicks;
                double radius = swirlRadius * progress;
                double angleStep = 360.0 / Math.max(1, swirlParticlesPerTick);
                double spin = ticksElapsed * 18.0;
                for (int i = 0; i < swirlParticlesPerTick; i++) {
                    double angle = Math.toRadians(spin + (i * angleStep));
                    double x = Math.cos(angle) * radius;
                    double z = Math.sin(angle) * radius;
                    double y = (progress * 1.5) - 0.5;
                    center.getWorld().spawnParticle(Particle.DUST, center.clone().add(x, y, z), 1, 0, 0, 0, 0, brownDust);
                }
                ticksElapsed++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void spawnFloatingSoul(Player player, SoulType soul, ItemStack realItem, ItemStack displayItem,
                                    int floatTicks, double floatDistance, double floatHeightOffset) {
        if (!player.isOnline()) {
            finishWithoutAbsorbing(player);
            return;
        }

        Vector direction = player.getEyeLocation().getDirection().clone();
        direction.setY(0);
        if (direction.lengthSquared() < 1.0E-6) {
            direction = new Vector(0, 0, 1);
        } else {
            direction.normalize();
        }
        Location spawnLoc = player.getEyeLocation().add(direction.multiply(floatDistance));
        spawnLoc.add(0, floatHeightOffset, 0);

        ItemDisplay display = player.getWorld().spawn(spawnLoc, ItemDisplay.class, d -> {
            d.setItemStack(displayItem);
            d.setBillboard(ItemDisplay.Billboard.FIXED);
            d.setGravity(false);
            d.setPersistent(false);
        });
        activeDisplays.put(player.getUniqueId(), display);
        player.getWorld().playSound(spawnLoc, org.bukkit.Sound.ITEM_TRIDENT_RETURN, 0.6f, 1.6f);

        new BukkitRunnable() {
            int ticksElapsed = 0;

            @Override
            public void run() {
                if (!player.isOnline() || !display.isValid()) {
                    if (display.isValid()) {
                        display.remove();
                    }
                    activeDisplays.remove(player.getUniqueId());
                    if (!player.isOnline()) {
                        finishWithoutAbsorbing(player);
                    }
                    cancel();
                    return;
                }
                if (ticksElapsed >= floatTicks) {
                    display.remove();
                    activeDisplays.remove(player.getUniqueId());
                    finishAbsorption(player, soul, realItem);
                    cancel();
                    return;
                }

                // Slow spin + gentle bob to sell the "floating" look.
                float rotationDegrees = ticksElapsed * 6f;
                float bob = (float) Math.sin(ticksElapsed / 6.0) * 0.05f;
                Transformation transform = display.getTransformation();
                transform.getTranslation().set(0f, bob, 0f);
                transform.getLeftRotation().set(new AxisAngle4f((float) Math.toRadians(rotationDegrees), 0f, 1f, 0f));
                display.setTransformation(transform);

                if (ticksElapsed % 5 == 0) {
                    display.getWorld().spawnParticle(Particle.SOUL, display.getLocation(), 3, 0.15, 0.15, 0.15, 0.01);
                }
                ticksElapsed++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void finishAbsorption(Player player, SoulType soul, ItemStack realItem) {
        absorbing.remove(player.getUniqueId());

        dataManager.setActiveSoul(player.getUniqueId(), soul);
        realItem.setAmount(realItem.getAmount() - 1);

        player.sendMessage(Component.text("You have absorbed the ", NamedTextColor.GRAY)
                .append(Component.text(soul.displayName(), NamedTextColor.LIGHT_PURPLE))
                .append(Component.text(".", NamedTextColor.GRAY)));
        player.getWorld().playSound(player.getLocation(), org.bukkit.Sound.ENTITY_WITHER_AMBIENT, 0.6f, 1.8f);
    }

    /** Cleanup path if the player disconnects mid-cutscene: don't grant the soul, just release the lock. */
    private void finishWithoutAbsorbing(Player player) {
        absorbing.remove(player.getUniqueId());
    }
}
