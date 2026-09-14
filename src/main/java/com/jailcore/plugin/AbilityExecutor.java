package com.soulsplugin.abilities;

import com.soulsplugin.SoulType;
import com.soulsplugin.util.ActionBarUtil;
import com.soulsplugin.util.CooldownManager;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Executes the concrete gameplay effect of every ability. Cooldown gating
 * happens one layer up in the trigger listeners; by the time a method here
 * runs, the ability is confirmed off-cooldown and is being cast.
 *
 * Every damage number, potion duration/amplifier, radius, and knockback
 * strength is read from {@link AbilityConfig} (backed by config.yml) rather
 * than hard-coded here, so server owners can rebalance abilities without a
 * recompile. The literals passed as the final argument to each config
 * lookup are only fallback defaults used if a key is missing from
 * config.yml - they match the plugin's original balance.
 */
public class AbilityExecutor {

    private final JavaPlugin plugin;
    private final CooldownManager cooldowns;
    private final AbilityConfig abilityConfig;

    // --- Transient combat-state maps used to link a cast to a later event ---

    /** Eldritch Gaze: uuid of caster -> tag expiry millis. */
    private final Map<UUID, Long> eldritchGazeTag = new HashMap<>();

    /** Plague Surge: uuid -> [hits remaining, expiry millis]. */
    private final Map<UUID, int[]> plagueSurgeCrits = new HashMap<>();
    private final Map<UUID, Long> plagueSurgeExpiry = new HashMap<>();

    /** Obsidian Hide: uuid -> expiry millis. Incoming knockback reduced while active. */
    private final Map<UUID, Long> obsidianHideActive = new HashMap<>();

    /** Airstream Sprint: uuid -> expiry millis of the window where jump/crouch triggers the dash. */
    private final Map<UUID, Long> airstreamWindow = new HashMap<>();

    public AbilityExecutor(JavaPlugin plugin, CooldownManager cooldowns, AbilityConfig abilityConfig) {
        this.plugin = plugin;
        this.cooldowns = cooldowns;
        this.abilityConfig = abilityConfig;
    }

    // ------------------------------------------------------------------
    // Public state queries used by other listeners
    // ------------------------------------------------------------------

    public boolean consumeEldritchGazeTag(UUID caster) {
        Long expiry = eldritchGazeTag.get(caster);
        if (expiry == null) return false;
        if (System.currentTimeMillis() > expiry) {
            eldritchGazeTag.remove(caster);
            return false;
        }
        eldritchGazeTag.remove(caster);
        return true;
    }

    public boolean consumePlagueSurgeCrit(UUID attacker) {
        int[] state = plagueSurgeCrits.get(attacker);
        Long expiry = plagueSurgeExpiry.get(attacker);
        if (state == null || expiry == null) return false;
        if (System.currentTimeMillis() > expiry || state[0] <= 0) {
            plagueSurgeCrits.remove(attacker);
            plagueSurgeExpiry.remove(attacker);
            return false;
        }
        state[0]--;
        if (state[0] <= 0) {
            plagueSurgeCrits.remove(attacker);
            plagueSurgeExpiry.remove(attacker);
        }
        return true;
    }

    /** Exposed so CombatListener can read the configured crit damage multiplier. */
    public AbilityConfig abilityConfig() {
        return abilityConfig;
    }

    public double knockbackMultiplierFor(UUID victim) {
        Long expiry = obsidianHideActive.get(victim);
        if (expiry == null) return 1.0;
        if (System.currentTimeMillis() > expiry) {
            obsidianHideActive.remove(victim);
            return 1.0;
        }
        double reductionPercent = abilityConfig.getDouble(SoulType.TITAN, 1, "knockback-reduction-percent", 80.0);
        return 1.0 - (reductionPercent / 100.0);
    }

    /**
     * Called by the trigger listener when a player sneaks or jumps while the
     * Airstream Sprint window is open. Returns true if it consumed the
     * window (meaning the caller should NOT also fire a normal ability).
     */
    public boolean tryConsumeAirstreamWindow(Player player) {
        Long expiry = airstreamWindow.get(player.getUniqueId());
        if (expiry == null || System.currentTimeMillis() > expiry) {
            airstreamWindow.remove(player.getUniqueId());
            return false;
        }
        airstreamWindow.remove(player.getUniqueId());
        launchForward(player);
        return true;
    }

    // ------------------------------------------------------------------
    // Ability dispatch
    // ------------------------------------------------------------------

    public void execute(Player player, SoulType soul, int slot) {
        switch (soul) {
            case LEVIATHAN -> {
                if (slot == 1) abalathsGrasp(player);
                else inkveil(player);
            }
            case ABYSSAL -> {
                if (slot == 1) voidScent(player);
                else shadowInstinct(player);
            }
            case WRAITH -> {
                if (slot == 1) decayBreach(player);
                else plagueSurge(player);
            }
            case VANGUARD -> {
                if (slot == 1) airstreamSprint(player);
                else sonicShockwave(player);
            }
            case TITAN -> {
                if (slot == 1) obsidianHide(player);
                else seismicShock(player);
            }
            case ELDRITCH -> {
                if (slot == 1) eldritchGaze(player);
                else if (slot == 2) eldritchRenewal(player);
                else voidInversion(player);
            }
        }
        int cd = abilityConfig.cooldownSeconds(soul, slot, defaultCooldown(soul, slot));
        cooldowns.startCooldown(player.getUniqueId(), soul.name() + "_ABILITY_" + slot, cd);
        ActionBarUtil.sendAbilityActivated(player, soul.icon(), soul.abilityName(slot), cd);
    }

    /** Fallback cooldowns matching the plugin's original balance, used only if config.yml is missing a value. */
    private int defaultCooldown(SoulType soul, int slot) {
        return switch (soul) {
            case LEVIATHAN -> slot == 1 ? 60 : 120;
            case ABYSSAL -> slot == 1 ? 90 : 180;
            case WRAITH -> slot == 1 ? 45 : 120;
            case VANGUARD -> slot == 1 ? 45 : 60;
            case TITAN -> slot == 1 ? 90 : 120;
            case ELDRITCH -> switch (slot) {
                case 1 -> 90;
                case 2 -> 120;
                default -> 120;
            };
        };
    }

    private int ticks(SoulType soul, int slot, String key, double defaultSeconds) {
        return abilityConfig.getDurationTicks(soul, slot, key, defaultSeconds);
    }

    private double num(SoulType soul, int slot, String key, double defaultValue) {
        return abilityConfig.getDouble(soul, slot, key, defaultValue);
    }

    private int amp(SoulType soul, int slot, String key, int defaultValue) {
        return abilityConfig.getInt(soul, slot, key, defaultValue);
    }

    // ------------------------------------------------------------------
    // Leviathan
    // ------------------------------------------------------------------

    private void abalathsGrasp(Player caster) {
        int particles = amp(SoulType.LEVIATHAN, 1, "particle-count", 60);
        caster.getWorld().spawnParticle(Particle.SPLASH, caster.getLocation(), particles, 2, 1, 2, 0.05);
        caster.getWorld().playSound(caster.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1f, 0.8f);

        double radius = num(SoulType.LEVIATHAN, 1, "radius", 5);
        int slowDuration = ticks(SoulType.LEVIATHAN, 1, "slowness-duration-seconds", 6);
        int slowAmp = amp(SoulType.LEVIATHAN, 1, "slowness-amplifier", 1);
        int fatigueDuration = ticks(SoulType.LEVIATHAN, 1, "fatigue-duration-seconds", 6);
        int fatigueAmp = amp(SoulType.LEVIATHAN, 1, "fatigue-amplifier", 0);

        for (LivingEntity target : nearbyEnemies(caster, radius)) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, slowDuration, slowAmp));
            target.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, fatigueDuration, fatigueAmp));
        }
    }

    private void inkveil(Player caster) {
        int particles = amp(SoulType.LEVIATHAN, 2, "particle-count", 100);
        caster.getWorld().spawnParticle(Particle.SQUID_INK, caster.getLocation(), particles, 5, 2, 5, 0.1);
        caster.getWorld().playSound(caster.getLocation(), Sound.ENTITY_GUARDIAN_AMBIENT, 1f, 0.5f);

        double radius = num(SoulType.LEVIATHAN, 2, "radius", 15);
        int blindDuration = ticks(SoulType.LEVIATHAN, 2, "blindness-duration-seconds", 6);
        int blindAmp = amp(SoulType.LEVIATHAN, 2, "blindness-amplifier", 2);
        int slowDuration = ticks(SoulType.LEVIATHAN, 2, "slowness-duration-seconds", 6);
        int slowAmp = amp(SoulType.LEVIATHAN, 2, "slowness-amplifier", 2);

        for (LivingEntity target : nearbyEnemies(caster, radius)) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, blindDuration, blindAmp));
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, slowDuration, slowAmp));
        }
    }

    // ------------------------------------------------------------------
    // Abyssal
    // ------------------------------------------------------------------

    private void voidScent(Player caster) {
        int duration = ticks(SoulType.ABYSSAL, 1, "strength-duration-seconds", 4);
        int amplifier = amp(SoulType.ABYSSAL, 1, "strength-amplifier", 2);
        caster.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, duration, amplifier));

        int particles = amp(SoulType.ABYSSAL, 1, "particle-count", 40);
        caster.getWorld().spawnParticle(Particle.SOUL, caster.getLocation(), particles, 0.5, 1, 0.5, 0.02);
        caster.getWorld().playSound(caster.getLocation(), Sound.ENTITY_WITHER_AMBIENT, 0.7f, 1.4f);
    }

    private void shadowInstinct(Player caster) {
        int particles = amp(SoulType.ABYSSAL, 2, "particle-count", 80);
        caster.getWorld().spawnParticle(Particle.SMOKE, caster.getLocation(), particles, 5, 3, 5, 0.05);
        caster.getWorld().playSound(caster.getLocation(), Sound.ENTITY_PHANTOM_AMBIENT, 1f, 0.6f);

        double radius = num(SoulType.ABYSSAL, 2, "radius", 30);
        int glowDuration = ticks(SoulType.ABYSSAL, 2, "glowing-duration-seconds", 3);
        for (Player target : nearbyPlayers(caster, radius)) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, glowDuration, 0));
        }
    }

    // ------------------------------------------------------------------
    // Wraith
    // ------------------------------------------------------------------

    private void decayBreach(Player caster) {
        double radius = num(SoulType.WRAITH, 1, "radius", 7);
        LivingEntity target = nearestEnemy(caster, radius);
        if (target == null) {
            return;
        }
        int particles = amp(SoulType.WRAITH, 1, "particle-count", 20);
        caster.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, caster.getLocation(), particles, 0.3, 0.3, 0.3, 0.05);
        caster.getWorld().playSound(caster.getLocation(), Sound.ENTITY_WITHER_SHOOT, 1f, 1f);

        double pullDistance = num(SoulType.WRAITH, 1, "pull-distance", 3.0);
        Vector pull = caster.getLocation().toVector().subtract(target.getLocation().toVector());
        double distance = pull.length();
        double pullAmount = Math.min(pullDistance, distance);
        if (distance > 0.01) {
            Vector pullVector = pull.normalize().multiply(pullAmount / 1.5);
            pullVector.setY(Math.max(pullVector.getY(), 0.1));
            target.setVelocity(pullVector);
        }

        int cageTicks = amp(SoulType.WRAITH, 1, "cage-particle-ticks", 15);
        cageParticles(target.getLocation(), cageTicks);

        // Delivered as true damage (bypasses armor/resistance by applying raw
        // health reduction rather than routing through EntityDamageEvent).
        double damage = num(SoulType.WRAITH, 1, "damage", 3.0);
        double newHealth = Math.max(0, target.getHealth() - damage);
        target.setHealth(newHealth);
        target.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, target.getLocation().add(0, 1, 0), 5);
    }

    private void cageParticles(Location center, int totalTicks) {
        new BukkitRunnable() {
            int ticksElapsed = 0;

            @Override
            public void run() {
                if (ticksElapsed >= totalTicks) {
                    cancel();
                    return;
                }
                for (int i = 0; i < 360; i += 30) {
                    double rad = Math.toRadians(i);
                    double x = Math.cos(rad) * 0.8;
                    double z = Math.sin(rad) * 0.8;
                    center.getWorld().spawnParticle(Particle.SOUL, center.clone().add(x, 0.1 + (ticksElapsed * 0.1), z), 1, 0, 0, 0, 0);
                }
                ticksElapsed++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void plagueSurge(Player caster) {
        caster.removePotionEffect(PotionEffectType.SLOWNESS);
        caster.setFireTicks(0);

        int speedDuration = ticks(SoulType.WRAITH, 2, "speed-duration-seconds", 5);
        int speedAmp = amp(SoulType.WRAITH, 2, "speed-amplifier", 1);
        caster.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, speedDuration, speedAmp));

        int critCount = amp(SoulType.WRAITH, 2, "crit-count", 3);
        double windowSeconds = num(SoulType.WRAITH, 2, "window-seconds", 7);
        plagueSurgeCrits.put(caster.getUniqueId(), new int[]{critCount});
        plagueSurgeExpiry.put(caster.getUniqueId(), System.currentTimeMillis() + (long) (windowSeconds * 1000));

        int particles = amp(SoulType.WRAITH, 2, "particle-count", 40);
        caster.getWorld().spawnParticle(Particle.WITCH, caster.getLocation(), particles, 0.5, 1, 0.5, 0.05);
        caster.getWorld().playSound(caster.getLocation(), Sound.ENTITY_WITHER_HURT, 0.6f, 1.6f);
    }

    // ------------------------------------------------------------------
    // Vanguard
    // ------------------------------------------------------------------

    private void airstreamSprint(Player caster) {
        int speedDuration = ticks(SoulType.VANGUARD, 1, "speed-duration-seconds", 4);
        int speedAmp = amp(SoulType.VANGUARD, 1, "speed-amplifier", 2);
        caster.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, speedDuration, speedAmp));

        double windowSeconds = num(SoulType.VANGUARD, 1, "window-seconds", 4);
        airstreamWindow.put(caster.getUniqueId(), System.currentTimeMillis() + (long) (windowSeconds * 1000));

        int particles = amp(SoulType.VANGUARD, 1, "particle-count", 30);
        caster.getWorld().spawnParticle(Particle.CLOUD, caster.getLocation(), particles, 0.5, 0.2, 0.5, 0.05);
        caster.getWorld().playSound(caster.getLocation(), Sound.ENTITY_BREEZE_WIND_BURST, 1f, 1f);
    }

    private void launchForward(Player player) {
        double dashPower = num(SoulType.VANGUARD, 1, "dash-power", 2.2);
        double minY = num(SoulType.VANGUARD, 1, "dash-min-y", 0.3);
        double yMultiplier = num(SoulType.VANGUARD, 1, "dash-y-multiplier", 1.5);
        double hitRadius = num(SoulType.VANGUARD, 1, "dash-hit-radius", 1.8);
        double dashKnockback = num(SoulType.VANGUARD, 1, "dash-knockback", 1.4);
        int particleTicks = amp(SoulType.VANGUARD, 1, "dash-particle-ticks", 8);

        // Vector's normalize()/multiply() mutate in place, so capture the
        // original unit direction's Y component before scaling it into the
        // launch vector to avoid aliasing surprises.
        Vector direction = player.getLocation().getDirection().normalize();
        double originalY = direction.getY();
        Vector launch = direction.clone().multiply(dashPower);
        launch.setY(Math.max(minY, originalY * yMultiplier));
        player.setVelocity(launch);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_BREEZE_JUMP, 1f, 1f);

        new BukkitRunnable() {
            int ticksElapsed = 0;

            @Override
            public void run() {
                if (ticksElapsed >= particleTicks || !player.isOnline()) {
                    cancel();
                    return;
                }
                player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation(), 5, 0.2, 0.1, 0.2, 0.01);
                for (LivingEntity hit : nearbyEnemies(player, hitRadius)) {
                    Vector kb = hit.getLocation().toVector().subtract(player.getLocation().toVector());
                    if (kb.lengthSquared() < 0.0001) {
                        kb = direction.clone();
                    }
                    kb.normalize().multiply(dashKnockback);
                    kb.setY(0.4);
                    hit.setVelocity(hit.getVelocity().add(kb));
                }
                ticksElapsed++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void sonicShockwave(Player caster) {
        caster.getWorld().spawnParticle(Particle.SONIC_BOOM, caster.getLocation(), 1);
        caster.getWorld().playSound(caster.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 1f, 1.2f);

        double radius = num(SoulType.VANGUARD, 2, "radius", 4);
        double knockbackPower = num(SoulType.VANGUARD, 2, "knockback-power", 1.8);
        double knockbackY = num(SoulType.VANGUARD, 2, "knockback-y", 0.5);
        int slowDuration = ticks(SoulType.VANGUARD, 2, "slowness-duration-seconds", 2);
        int slowAmp = amp(SoulType.VANGUARD, 2, "slowness-amplifier", 1);

        for (LivingEntity target : nearbyEnemies(caster, radius)) {
            Vector direction = target.getLocation().toVector().subtract(caster.getLocation().toVector());
            if (direction.lengthSquared() < 0.0001) {
                direction = new Vector(0, 0, 1);
            }
            direction.normalize().multiply(knockbackPower);
            direction.setY(knockbackY);
            target.setVelocity(direction);
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, slowDuration, slowAmp));
        }
    }

    // ------------------------------------------------------------------
    // Titan
    // ------------------------------------------------------------------

    private void obsidianHide(Player caster) {
        int resistDuration = ticks(SoulType.TITAN, 1, "resistance-duration-seconds", 5);
        int resistAmp = amp(SoulType.TITAN, 1, "resistance-amplifier", 2);
        int slowDuration = ticks(SoulType.TITAN, 1, "slowness-duration-seconds", 5);
        int slowAmp = amp(SoulType.TITAN, 1, "slowness-amplifier", 0);
        double effectSeconds = num(SoulType.TITAN, 1, "effect-duration-seconds", 5);

        caster.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, resistDuration, resistAmp));
        caster.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, slowDuration, slowAmp));
        obsidianHideActive.put(caster.getUniqueId(), System.currentTimeMillis() + (long) (effectSeconds * 1000));

        int particles = amp(SoulType.TITAN, 1, "particle-count", 60);
        caster.getWorld().spawnParticle(Particle.BLOCK, caster.getLocation(), particles, 0.5, 1, 0.5, 0,
                org.bukkit.Material.OBSIDIAN.createBlockData());
        caster.getWorld().playSound(caster.getLocation(), Sound.BLOCK_STONE_PLACE, 1f, 0.7f);
    }

    private void seismicShock(Player caster) {
        Location loc = caster.getLocation();
        int particles = amp(SoulType.TITAN, 2, "particle-count", 100);
        loc.getWorld().spawnParticle(Particle.EXPLOSION, loc, 3);
        loc.getWorld().spawnParticle(Particle.BLOCK, loc, particles, 3, 0.2, 3, 0,
                org.bukkit.Material.STONE.createBlockData());
        loc.getWorld().playSound(loc, Sound.ENTITY_RAVAGER_ROAR, 1f, 0.6f);

        double radius = num(SoulType.TITAN, 2, "radius", 6);
        double knockbackPower = num(SoulType.TITAN, 2, "knockback-power", 2.0);
        double knockbackY = num(SoulType.TITAN, 2, "knockback-y", 0.7);
        double damage = num(SoulType.TITAN, 2, "damage", 6.0);

        for (LivingEntity target : nearbyEnemies(caster, radius)) {
            Vector direction = target.getLocation().toVector().subtract(loc.toVector());
            if (direction.lengthSquared() < 0.0001) {
                direction = new Vector(0, 0, 1);
            }
            direction.normalize().multiply(knockbackPower);
            direction.setY(knockbackY);
            target.setVelocity(direction);
            // Unlike Wraith's true damage, Seismic Shock is a normal physical
            // blow and is routed through the standard damage pipeline so armor
            // and enchantments still apply.
            target.damage(damage, caster);
        }
    }

    // ------------------------------------------------------------------
    // Eldritch
    // ------------------------------------------------------------------

    private void eldritchGaze(Player caster) {
        double tagSeconds = num(SoulType.ELDRITCH, 1, "tag-duration-seconds", 5);
        eldritchGazeTag.put(caster.getUniqueId(), System.currentTimeMillis() + (long) (tagSeconds * 1000));

        int particles = amp(SoulType.ELDRITCH, 1, "particle-count", 20);
        caster.getWorld().spawnParticle(Particle.SOUL, caster.getLocation().add(0, 1.6, 0), particles, 0.3, 0.3, 0.3, 0.02);
        caster.getWorld().playSound(caster.getLocation(), Sound.ENTITY_WARDEN_NEARBY_CLOSE, 1f, 1.3f);
    }

    private void eldritchRenewal(Player caster) {
        int regenDuration = ticks(SoulType.ELDRITCH, 2, "regen-duration-seconds", 3);
        int regenAmp = amp(SoulType.ELDRITCH, 2, "regen-amplifier", 2);
        int satDuration = ticks(SoulType.ELDRITCH, 2, "saturation-duration-seconds", 3);

        caster.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, regenDuration, regenAmp));
        caster.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, satDuration, 0));

        int particles = amp(SoulType.ELDRITCH, 2, "particle-count", 10);
        caster.getWorld().spawnParticle(Particle.HEART, caster.getLocation().add(0, 1.5, 0), particles, 0.3, 0.3, 0.3, 0.02);
    }

    private void voidInversion(Player caster) {
        int invisDuration = ticks(SoulType.ELDRITCH, 3, "invis-duration-seconds", 3);
        int speedDuration = ticks(SoulType.ELDRITCH, 3, "speed-duration-seconds", 3);
        int speedAmp = amp(SoulType.ELDRITCH, 3, "speed-amplifier", 1);

        caster.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, invisDuration, 0));
        caster.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, speedDuration, speedAmp));

        int particles = amp(SoulType.ELDRITCH, 3, "particle-count", 40);
        caster.getWorld().spawnParticle(Particle.PORTAL, caster.getLocation(), particles, 0.5, 1, 0.5, 0.1);
    }

    // ------------------------------------------------------------------
    // Targeting helpers
    // ------------------------------------------------------------------

    private List<LivingEntity> nearbyEnemies(Player caster, double radius) {
        // Built with a plain ArrayList (never List.of()/.toList()'s immutable
        // factory) per the engine safeguard against Commodore remapper crashes.
        List<LivingEntity> result = new ArrayList<>();
        for (org.bukkit.entity.Entity e : caster.getWorld().getNearbyEntities(caster.getLocation(), radius, radius, radius)) {
            if (e instanceof LivingEntity living && e != caster) {
                result.add(living);
            }
        }
        return result;
    }

    private List<Player> nearbyPlayers(Player caster, double radius) {
        List<Player> result = new ArrayList<>();
        for (org.bukkit.entity.Entity e : caster.getWorld().getNearbyEntities(caster.getLocation(), radius, radius, radius)) {
            if (e instanceof Player p && e != caster) {
                result.add(p);
            }
        }
        return result;
    }

    private LivingEntity nearestEnemy(Player caster, double radius) {
        return nearbyEnemies(caster, radius).stream()
                .min((a, b) -> Double.compare(
                        a.getLocation().distanceSquared(caster.getLocation()),
                        b.getLocation().distanceSquared(caster.getLocation())))
                .orElse(null);
    }
}
