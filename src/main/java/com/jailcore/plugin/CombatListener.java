package com.soulsplugin.listeners;

import com.soulsplugin.SoulType;
import com.soulsplugin.abilities.AbilityConfig;
import com.soulsplugin.abilities.AbilityExecutor;
import io.papermc.paper.event.entity.EntityKnockbackByEntityEvent;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Links a soul ability cast to a subsequent combat event:
 *   - Eldritch Gaze: next melee hit within the tag window inflicts extreme Slowness
 *   - Plague Surge: next few hits within the window are forced critical hits
 *   - Obsidian Hide: incoming knockback is reduced while active
 *
 * Previously this listened on the deprecated org.bukkit.event.entity.EntityKnockbackEvent,
 * which Paper flags at startup ("has registered a listener for ... Deprecated ...
 * Server performance will be affected"). Paper's replacement for
 * entity-caused knockback is EntityKnockbackByEntityEvent, used below instead -
 * it fires only for knockback caused by another entity (which is the only
 * case Obsidian Hide cares about), and carries the knockback as a Vector
 * that can be scaled and written back directly.
 */
public class CombatListener implements Listener {

    private final AbilityExecutor abilities;
    private final AbilityConfig abilityConfig;

    public CombatListener(AbilityExecutor abilities, AbilityConfig abilityConfig) {
        this.abilities = abilities;
        this.abilityConfig = abilityConfig;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) {
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity victim)) {
            return;
        }

        // Eldritch Gaze: landing this melee hit applies extreme Slowness for a
        // configured duration/amplifier.
        if (abilities.consumeEldritchGazeTag(attacker.getUniqueId())) {
            int duration = abilityConfig.getDurationTicks(SoulType.ELDRITCH, 1, "proc-slowness-duration-seconds", 5);
            int amplifier = abilityConfig.getInt(SoulType.ELDRITCH, 1, "proc-slowness-amplifier", 9);
            victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, duration, amplifier));
            victim.getWorld().spawnParticle(Particle.SOUL, victim.getLocation().add(0, 1, 0), 30, 0.4, 0.6, 0.4, 0.02);
            victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_WARDEN_ROAR, 1f, 1.5f);
        }

        // Plague Surge: force this hit to be a guaranteed critical, at the configured multiplier.
        if (abilities.consumePlagueSurgeCrit(attacker.getUniqueId())) {
            double multiplier = abilityConfig.getDouble(SoulType.WRAITH, 2, "crit-damage-multiplier", 1.5);
            event.setDamage(event.getDamage() * multiplier);
            victim.getWorld().spawnParticle(Particle.CRIT, victim.getLocation().add(0, 1, 0), 15, 0.3, 0.3, 0.3, 0.1);
            victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 1f);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onKnockback(EntityKnockbackByEntityEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        double multiplier = abilities.knockbackMultiplierFor(player.getUniqueId());
        if (multiplier >= 1.0) {
            return;
        }
        event.setKnockback(event.getKnockback().clone().multiply(multiplier));
    }
}
