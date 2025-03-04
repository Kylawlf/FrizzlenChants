package org.frizzlenpop.frizzlenChants.impl;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Trident;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.frizzlenpop.frizzlenChants.FrizzlenChants;
import org.frizzlenpop.frizzlenChants.enchant.CustomEnchant;
import org.frizzlenpop.frizzlenChants.enchant.EnchantmentUtils;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Seeker Strike enchantment that turns tridents into homing missiles that seek
 * out nearby enemies, perform a javelin-like trajectory, and explode on impact.
 */
public class SeekerStrikeEnchant extends CustomEnchant {

    private final FrizzlenChants plugin;
    private final Random random = new Random();
    private final Map<UUID, LivingEntity> trackedTargets = new HashMap<>();
    
    // Constants
    private static final String SEEKER_STRIKE_KEY = "seeker_strike_enchant";
    private static final double DETECTION_RANGE = 15.0; // Block range to detect enemies
    private static final double BASE_DAMAGE = 8.0; // Base explosion damage
    private static final double KNOCKBACK_STRENGTH = 1.2; // Base knockback strength
    private static final double EXPLOSION_RADIUS = 3.0; // Base explosion radius
    private static final int TRACKING_TICKS = 200; // Maximum ticks to track (10 seconds)
    
    public SeekerStrikeEnchant() {
        super("SeekerStrike", 3, Material.TRIDENT);
        this.plugin = FrizzlenChants.getPlugin(FrizzlenChants.class);
    }

    @Override
    public void applyEffect(Player player, ItemStack item, int level) {
        // This method is normally triggered by the EnchantmentListener
        // But for the SeekerStrike, we handle the effect in the ProjectileLaunchEvent
        // So we don't need to do anything here
    }
    
    /**
     * Determines if this enchantment can be applied to an item.
     * 
     * @param item The item to check
     * @return true if the enchantment can be applied, false otherwise
     */
    @Override
    public boolean canApplyTo(ItemStack item) {
        // This enchantment can only be applied to tridents
        return item != null && item.getType() == Material.TRIDENT;
    }
    
    /**
     * Gets the formatted display name of this enchantment.
     * 
     * @param level The enchantment level
     * @return The formatted display name
     */
    @Override
    public String getDisplayName(int level) {
        return "§7Seeker Strike " + getRomanNumeral(level);
    }
    
    /**
     * Converts an integer to a Roman numeral.
     *
     * @param num The number to convert
     * @return The Roman numeral as a string
     */
    private String getRomanNumeral(int num) {
        String[] romanNumerals = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        if (num > 0 && num <= romanNumerals.length) {
            return romanNumerals[num - 1];
        }
        return String.valueOf(num);
    }
    
    @EventHandler
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        Projectile projectile = event.getEntity();
        
        // Check if the projectile is a trident
        if (!(projectile instanceof Trident)) {
            return;
        }
        
        Trident trident = (Trident) projectile;
        
        // Check if the trident has a shooter
        if (!(trident.getShooter() instanceof Player)) {
            return;
        }
        
        Player player = (Player) trident.getShooter();
        
        // Get the trident item from the player's inventory
        ItemStack tridentItem = player.getInventory().getItemInMainHand();
        if (tridentItem.getType() != Material.TRIDENT) {
            tridentItem = player.getInventory().getItemInOffHand();
            if (tridentItem.getType() != Material.TRIDENT) {
                return;
            }
        }
        
        // Check if the trident has this enchantment
        int level = EnchantmentUtils.getEnchantmentLevel(tridentItem, this, plugin.getEnchantmentRegistry());
        
        if (level <= 0) {
            return;
        }
        
        // Mark this trident as having the Seeker Strike enchantment
        trident.setMetadata(SEEKER_STRIKE_KEY, new FixedMetadataValue(plugin, level));
        
        // Initial particle effect and sound
        createLaunchEffect(trident.getLocation(), level);
        trident.getWorld().playSound(trident.getLocation(), Sound.ENTITY_GHAST_SHOOT, 0.5f, 1.5f);
        
        // Notify the player
        player.sendActionBar(net.kyori.adventure.text.Component.text(
            "§6Seeker Strike §7activated!"
        ));
        
        // Start the tracking process
        startTrackingNearbyEntities(trident, player, level);
    }
    
    /**
     * Starts tracking nearby entities for the trident to target.
     *
     * @param trident The trident to track with
     * @param player The player who threw the trident
     * @param level The enchantment level
     */
    private void startTrackingNearbyEntities(Trident trident, Player player, int level) {
        // Calculate enhanced tracking values based on level
        final double trackingRange = DETECTION_RANGE + (level * 2); // 15-21 blocks
        final int turnDelay = Math.max(5, 15 - (level * 5)); // Initial delay before homing: 10-5 ticks
        final double turnStrength = 0.1 + (level * 0.05); // 0.15-0.25 turn strength
        
        // Start tracking task
        new BukkitRunnable() {
            int ticks = 0;
            boolean hasTarget = false;
            LivingEntity currentTarget = null;
            boolean isAscending = false;
            boolean isDescending = false;
            int maneuverTicks = 0;
            final int ascendDuration = 10;
            final int descendDuration = 15;
            
            @Override
            public void run() {
                // Stop if the trident is no longer valid or we've tracked too long
                if (!trident.isValid() || trident.isDead() || ticks >= TRACKING_TICKS) {
                    if (currentTarget != null) {
                        trackedTargets.remove(trident.getUniqueId());
                    }
                    this.cancel();
                    return;
                }
                
                // Create trail effect
                createTrailEffect(trident, level, ticks);
                
                // Wait for initial delay before targeting
                if (ticks < turnDelay) {
                    ticks++;
                    return;
                }
                
                // Find or verify target
                if (!hasTarget || currentTarget == null || !isValidTarget(currentTarget, trident, player)) {
                    // Try to find a new target
                    currentTarget = findNearestTarget(trident, player, trackingRange);
                    
                    if (currentTarget != null) {
                        hasTarget = true;
                        trackedTargets.put(trident.getUniqueId(), currentTarget);
                        
                        // Play targeting sound
                        trident.getWorld().playSound(trident.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f, 2.0f);
                        
                        // Begin ascent phase of javelin trajectory
                        isAscending = true;
                        maneuverTicks = 0;
                        
                        // Create target-locked effect
                        createTargetLockEffect(trident.getLocation(), currentTarget.getLocation());
                    }
                }
                
                // If we have a target, guide the trident
                if (hasTarget && currentTarget != null) {
                    // Javelin-like trajectory
                    if (isAscending) {
                        // Rising phase
                        Vector currentVelocity = trident.getVelocity();
                        double speed = currentVelocity.length();
                        
                        // Calculate upward vector with slight movement toward target
                        Vector targetDirection = currentTarget.getLocation().toVector()
                            .subtract(trident.getLocation().toVector())
                            .normalize();
                        
                        // Combine upward movement with slight targeting
                        Vector newDirection = new Vector(
                            targetDirection.getX() * 0.2,
                            0.8, // Strong upward component
                            targetDirection.getZ() * 0.2
                        ).normalize().multiply(speed);
                        
                        trident.setVelocity(newDirection);
                        
                        // After ascend duration, switch to descent
                        maneuverTicks++;
                        if (maneuverTicks >= ascendDuration) {
                            isAscending = false;
                            isDescending = true;
                            maneuverTicks = 0;
                            
                            // Play dive sound
                            trident.getWorld().playSound(trident.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 0.8f, 1.2f);
                        }
                    } else if (isDescending) {
                        // Diving phase - strong tracking toward target
                        Vector currentVelocity = trident.getVelocity();
                        double speed = Math.max(currentVelocity.length(), 1.5); // Maintain minimum speed
                        
                        // Calculate direction to target with downward angle
                        Vector targetPos = currentTarget.getLocation().add(0, 1, 0).toVector();
                        Vector tridentPos = trident.getLocation().toVector();
                        Vector directPath = targetPos.subtract(tridentPos).normalize();
                        
                        // Enhance downward component for a steeper dive
                        directPath.setY(directPath.getY() - 0.3);
                        directPath.normalize().multiply(speed);
                        
                        trident.setVelocity(directPath);
                        
                        // After max descent duration, switch to direct tracking
                        maneuverTicks++;
                        if (maneuverTicks >= descendDuration) {
                            isDescending = false;
                        }
                    } else {
                        // Direct tracking phase - precisely follow target
                        Vector currentVelocity = trident.getVelocity();
                        double speed = currentVelocity.length();
                        
                        // Calculate direction to target
                        Vector targetDirection = currentTarget.getLocation().add(0, 1, 0).toVector()
                            .subtract(trident.getLocation().toVector())
                            .normalize()
                            .multiply(speed + 0.1); // Slightly increase speed for final approach
                        
                        // Gradually adjust velocity toward target
                        Vector newVelocity = currentVelocity.multiply(1 - turnStrength).add(targetDirection.multiply(turnStrength));
                        
                        // Ensure we maintain speed
                        newVelocity = newVelocity.normalize().multiply(speed);
                        
                        trident.setVelocity(newVelocity);
                    }
                    
                    // Check if we've hit the target
                    if (trident.getLocation().distanceSquared(currentTarget.getLocation()) < 3.0) {
                        handleExplosion(trident, player, level);
                        return;
                    }
                }
                
                ticks++;
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }
    
    /**
     * Finds the nearest valid target within range.
     *
     * @param trident The trident seeking a target
     * @param player The player who threw the trident
     * @param range The detection range
     * @return The nearest valid target, or null if none found
     */
    private LivingEntity findNearestTarget(Trident trident, Player player, double range) {
        World world = trident.getWorld();
        Location tridentLoc = trident.getLocation();
        
        // Get all nearby entities and filter them
        List<LivingEntity> nearbyTargets = world.getNearbyEntities(tridentLoc, range, range, range).stream()
            .filter(entity -> entity instanceof LivingEntity)
            .filter(entity -> entity != player) // Not the owner
            .filter(entity -> entity instanceof Monster || (entity instanceof Player && 
                             plugin.getConfig().getBoolean("pvp.enabled", false))) // Monsters or players if PvP enabled
            .map(entity -> (LivingEntity) entity)
            .sorted(Comparator.comparingDouble(entity -> entity.getLocation().distanceSquared(tridentLoc)))
            .collect(Collectors.toList());
        
        // Return the closest target, or null if none found
        return nearbyTargets.isEmpty() ? null : nearbyTargets.get(0);
    }
    
    /**
     * Checks if a target is still valid.
     *
     * @param target The target to check
     * @param trident The trident tracking the target
     * @param player The player who threw the trident
     * @return true if the target is valid, false otherwise
     */
    private boolean isValidTarget(LivingEntity target, Trident trident, Player player) {
        return target.isValid() && !target.isDead() && 
               target.getWorld().equals(trident.getWorld()) &&
               target.getLocation().distanceSquared(trident.getLocation()) <= 
                    DETECTION_RANGE * DETECTION_RANGE * 1.5; // 1.5x the detection range
    }
    
    /**
     * Handles the explosion when the trident hits a target.
     *
     * @param trident The trident that hit
     * @param player The player who threw the trident
     * @param level The enchantment level
     */
    private void handleExplosion(Trident trident, Player player, int level) {
        Location location = trident.getLocation();
        World world = location.getWorld();
        
        // Remove from tracking
        trackedTargets.remove(trident.getUniqueId());
        
        // Create explosion effect
        createExplosionEffect(location, level);
        
        // Play explosion sound
        world.playSound(location, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.2f);
        
        // Calculate effect values based on level
        double radius = EXPLOSION_RADIUS + (level * 0.5); // 3.5-4.5 block radius
        double damage = BASE_DAMAGE + (level * 2); // 10-14 damage
        double knockback = KNOCKBACK_STRENGTH + (level * 0.2); // 1.4-1.8 knockback
        
        // Apply damage and knockback to nearby entities
        for (Entity entity : world.getNearbyEntities(location, radius, radius, radius)) {
            // Skip if not a living entity or is the player
            if (!(entity instanceof LivingEntity) || entity.equals(player)) {
                continue;
            }
            
            LivingEntity livingEntity = (LivingEntity) entity;
            
            // Calculate damage based on distance (more damage closer to epicenter)
            double distance = location.distance(livingEntity.getLocation());
            double distanceFactor = 1.0 - (distance / radius);
            double finalDamage = damage * Math.max(0.3, distanceFactor);
            
            // Apply damage
            livingEntity.damage(finalDamage, player);
            
            // Calculate knockback direction (away from explosion)
            Vector knockbackDir = livingEntity.getLocation().toVector()
                .subtract(location.toVector())
                .normalize()
                .multiply(knockback * distanceFactor);
            
            // Apply knockback
            livingEntity.setVelocity(livingEntity.getVelocity().add(knockbackDir));
            
            // Create hit effect
            world.spawnParticle(
                Particle.FLAME,
                livingEntity.getLocation().add(0, 1, 0),
                10,
                0.3, 0.3, 0.3,
                0.05
            );
        }
        
        // Remove the trident (it's been consumed in the explosion)
        trident.remove();
    }
    
    /**
     * Creates a launch effect when the trident is first thrown.
     *
     * @param location The location to create the effect at
     * @param level The enchantment level
     */
    private void createLaunchEffect(Location location, int level) {
        World world = location.getWorld();
        
        // Create particle burst
        world.spawnParticle(
            Particle.FLAME,
            location,
            10 + (level * 5),
            0.2, 0.2, 0.2,
            0.05
        );
        
        // Create electric sparks for higher levels
        if (level >= 2) {
            world.spawnParticle(
                Particle.END_ROD,
                location,
                5 + (level * 3),
                0.2, 0.2, 0.2,
                0.1
            );
        }
    }
    
    /**
     * Creates a trail effect behind the trident.
     *
     * @param trident The trident to create the trail for
     * @param level The enchantment level
     * @param tick The current tick count
     */
    private void createTrailEffect(Trident trident, int level, int tick) {
        World world = trident.getWorld();
        Location location = trident.getLocation();
        
        // Determine trail color based on level
        Color trailColor;
        switch (level) {
            case 1:
                trailColor = Color.fromRGB(255, 170, 0); // Orange
                break;
            case 2:
                trailColor = Color.fromRGB(255, 50, 0); // Red-orange
                break;
            case 3:
                trailColor = Color.fromRGB(220, 0, 0); // Deep red
                break;
            default:
                trailColor = Color.fromRGB(255, 170, 0);
        }
        
        // Main trail particles using VisualEffectManager
        plugin.getVisualEffectManager().spawnColoredParticles(
            location,
            Particle.DUST,
            trailColor,
            1.0f,
            3
        );
        
        // Add flame trails
        world.spawnParticle(
            Particle.FLAME,
            location,
            1,
            0.1, 0.1, 0.1,
            0.01
        );
        
        // Add special effects based on level
        if (level >= 2) {
            // Smoke trail
            world.spawnParticle(
                Particle.CAMPFIRE_COSY_SMOKE,
                location,
                1,
                0, 0, 0,
                0.02
            );
        }
        
        if (level >= 3) {
            // End rod particles for highest level
            if (tick % 3 == 0) {
                world.spawnParticle(
                    Particle.END_ROD,
                    location,
                    1,
                    0, 0, 0,
                    0.01
                );
            }
        }
        
        // Occasional sound effects
        if (tick % 10 == 0) {
            world.playSound(location, Sound.BLOCK_FIRE_AMBIENT, 0.3f, 1.5f);
        }
        
        // Check if it's in a targeting phase and add targeting particles
        LivingEntity target = trackedTargets.get(trident.getUniqueId());
        if (target != null && tick % 5 == 0) {
            // Create occasional targeting line
            createTargetingLine(location, target.getLocation().add(0, 1, 0), level);
        }
    }
    
    /**
     * Creates a targeting line between the trident and its target.
     *
     * @param from The trident location
     * @param to The target location
     * @param level The enchantment level
     */
    private void createTargetingLine(Location from, Location to, int level) {
        World world = from.getWorld();
        
        // If they're not in the same world, don't try to create a line
        if (world != to.getWorld()) {
            return;
        }
        
        Vector direction = to.toVector().subtract(from.toVector());
        double distance = direction.length();
        direction.normalize();
        
        // Create fewer particles for performance
        int particles = Math.min(10, (int) (distance / 2) + 1);
        double interval = distance / particles;
        
        for (int i = 0; i < particles; i++) {
            double d = i * interval;
            Vector pos = from.toVector().add(direction.clone().multiply(d));
            Location particleLoc = pos.toLocation(world);
            
            // Target lock particles
            world.spawnParticle(
                Particle.END_ROD,
                particleLoc,
                1,
                0, 0, 0,
                0.01
            );
        }
    }
    
    /**
     * Creates a target lock effect when the trident acquires a target.
     *
     * @param from The trident location
     * @param to The target location
     */
    private void createTargetLockEffect(Location from, Location to) {
        World world = from.getWorld();
        
        // Create a burst of particles at the target
        world.spawnParticle(
            Particle.END_ROD,
            to.add(0, 1, 0),
            10,
            0.5, 0.5, 0.5,
            0.05
        );
        
        // Create a circle of particles at the target
        double radius = 1.0;
        int particles = 12;
        for (int i = 0; i < particles; i++) {
            double angle = 2 * Math.PI * i / particles;
            double x = radius * Math.cos(angle);
            double z = radius * Math.sin(angle);
            Location particleLoc = to.clone().add(x, 0, z);
            
            world.spawnParticle(
                Particle.FLAME,
                particleLoc,
                1,
                0, 0, 0,
                0.01
            );
        }
    }
    
    /**
     * Creates an explosion effect at the given location.
     *
     * @param location The location to create the explosion at
     * @param level The enchantment level
     */
    private void createExplosionEffect(Location location, int level) {
        World world = location.getWorld();
        
        // Main explosion cloud
        world.spawnParticle(
            Particle.CLOUD,
            location,
            30 + (level * 10),
            1.0, 1.0, 1.0,
            0.1
        );
        
        // Fire particles
        world.spawnParticle(
            Particle.FLAME,
            location,
            40 + (level * 15),
            1.2, 1.2, 1.2,
            0.2
        );
        
        // Smoke trail
        new BukkitRunnable() {
            int tick = 0;
            final int duration = 10; // Half second of smoke
            
            @Override
            public void run() {
                if (tick >= duration) {
                    this.cancel();
                    return;
                }
                
                world.spawnParticle(
                    Particle.CAMPFIRE_COSY_SMOKE,
                    location.clone().add(0, 0.5 + (tick * 0.2), 0),
                    3,
                    0.3, 0.1, 0.3,
                    0.02
                );
                
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
        
        // Create shockwave effect
        new BukkitRunnable() {
            double radius = 0.5;
            final double maxRadius = 3.0 + level;
            final double expansionRate = 0.3;
            
            @Override
            public void run() {
                if (radius >= maxRadius) {
                    this.cancel();
                    return;
                }
                
                int particles = (int) (radius * 8);
                double angleIncrement = 2 * Math.PI / particles;
                
                for (int i = 0; i < particles; i++) {
                    double angle = i * angleIncrement;
                    double x = Math.cos(angle) * radius;
                    double z = Math.sin(angle) * radius;
                    Location particleLoc = location.clone().add(x, 0.1, z);
                    
                    world.spawnParticle(
                        Particle.FLAME,
                        particleLoc,
                        1,
                        0.05, 0.05, 0.05,
                        0.01
                    );
                }
                
                radius += expansionRate;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
    
    @Override
    public boolean conflictsWith(CustomEnchant other) {
        // Conflicts with other trident-targeting or explosion enchantments
        return other.getName().toLowerCase().contains("seeker") ||
               other.getName().toLowerCase().contains("homing") ||
               other.getName().toLowerCase().contains("missile") ||
               other.getName().toLowerCase().contains("tracking") ||
               other instanceof SentinelTurretEnchant;
    }
} 