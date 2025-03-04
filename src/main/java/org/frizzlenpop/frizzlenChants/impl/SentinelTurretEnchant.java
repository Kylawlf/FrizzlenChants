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
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.frizzlenpop.frizzlenChants.FrizzlenChants;
import org.frizzlenpop.frizzlenChants.enchant.CustomEnchant;
import org.frizzlenpop.frizzlenChants.enchant.EnchantmentUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Sentinel Turret enchantment that creates a magical turret on thrown tridents
 * which automatically targets and fires at nearby enemies.
 */
public class SentinelTurretEnchant extends CustomEnchant {

    private final FrizzlenChants plugin;
    private final Random random = new Random();
    private final Map<UUID, TurretData> activeTurrets = new HashMap<>();
    
    // Constants
    private static final String SENTINEL_TURRET_KEY = "sentinel_turret_enchant";
    private static final double PROJECTILE_SPEED = 1.5; // Speed of turret projectiles
    private static final double BASE_DAMAGE = 2.0; // Base damage for turret projectiles
    
    public SentinelTurretEnchant() {
        super("SentinelTurret", 3, Material.TRIDENT);
        this.plugin = FrizzlenChants.getPlugin(FrizzlenChants.class);
    }

    @Override
    public void applyEffect(Player player, ItemStack item, int level) {
        // Implementation handled in event handlers
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
        
        // Mark this trident as having the Sentinel Turret enchantment
        trident.setMetadata(SENTINEL_TURRET_KEY, new FixedMetadataValue(plugin, level));
        
        // Notify the player
        player.sendActionBar(net.kyori.adventure.text.Component.text(
            "§3Sentinel Turret §7armed and ready!"
        ));
    }
    
    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        Projectile projectile = event.getEntity();
        
        // Check if the projectile is a trident with our enchantment
        if (!(projectile instanceof Trident) || !projectile.hasMetadata(SENTINEL_TURRET_KEY)) {
            return;
        }
        
        Trident trident = (Trident) projectile;
        
        // Get the enchantment level
        int level = trident.getMetadata(SENTINEL_TURRET_KEY).get(0).asInt();
        
        // Create a turret if the trident has hit something or landed
        if (trident.isValid() && !trident.isDead()) {
            createTurret(trident, level);
        }
    }
    
    /**
     * Creates a sentinel turret at the trident's location.
     *
     * @param trident The trident that has been thrown
     * @param level The enchantment level
     */
    private void createTurret(Trident trident, int level) {
        // Get the owner of the trident
        if (!(trident.getShooter() instanceof Player)) {
            return;
        }
        
        Player player = (Player) trident.getShooter();
        World world = trident.getWorld();
        Location turretLoc = trident.getLocation().clone().add(0, 0.5, 0);
        
        // Calculate turret parameters based on level
        int durationTicks = 100 + (level * 50); // 5-10 seconds
        double range = 8.0 + (level * 2.0); // 10-14 block targeting range
        int firingDelay = Math.max(10, 20 - (level * 5)); // 15-5 ticks between shots
        double damage = BASE_DAMAGE + (level * 1.0); // 3-5 damage per shot
        
        // Generate a unique ID for this turret
        UUID turretId = UUID.randomUUID();
        
        // Store turret data
        activeTurrets.put(turretId, new TurretData(player, trident, level, damage));
        
        // Initial turret formation effect
        createTurretFormationEffect(turretLoc, level);
        
        // Play formation sound
        world.playSound(turretLoc, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.5f);
        world.playSound(turretLoc, Sound.BLOCK_CONDUIT_ACTIVATE, 0.5f, 1.2f);
        
        // Slow down the return of the trident
        if (trident.hasGravity()) {
            trident.setGravity(false); // Keep the trident from falling
        }
        
        // Schedule turret operation
        new BukkitRunnable() {
            int ticksRemaining = durationTicks;
            int ticksUntilNextShot = 0;
            LivingEntity currentTarget = null;
            
            @Override
            public void run() {
                // Check if the trident is still valid
                if (!trident.isValid() || trident.isDead() || ticksRemaining <= 0) {
                    // Clean up
                    removeActiveTurret(turretId, turretLoc);
                    this.cancel();
                    return;
                }
                
                // Update turret location in case the trident moved
                Location updatedLoc = trident.getLocation().clone().add(0, 0.5, 0);
                
                // Create ambient particles
                createTurretAmbientEffect(updatedLoc, level);
                
                // Check if we need to find a new target
                if (currentTarget == null || !isValidTarget(currentTarget, updatedLoc, range)) {
                    currentTarget = findNewTarget(player, updatedLoc, range);
                    
                    if (currentTarget != null) {
                        // Target acquired sound
                        world.playSound(updatedLoc, Sound.BLOCK_NOTE_BLOCK_BIT, 0.8f, 1.8f);
                        
                        // Target lock-on effect
                        createTargetLockEffect(updatedLoc, currentTarget.getLocation().add(0, 1, 0));
                    }
                }
                
                // Fire at target if we have one and it's time to shoot
                if (currentTarget != null && ticksUntilNextShot <= 0) {
                    fireTurretProjectile(updatedLoc, currentTarget, damage, level);
                    ticksUntilNextShot = firingDelay;
                    
                    // At level 3, possibly target additional mobs
                    if (level >= 3 && random.nextInt(3) == 0) {
                        LivingEntity secondaryTarget = findNewTarget(player, updatedLoc, range, currentTarget);
                        if (secondaryTarget != null) {
                            // Fire with slight delay
                            new BukkitRunnable() {
                                @Override
                                public void run() {
                                    if (trident.isValid() && !trident.isDead()) {
                                        fireTurretProjectile(updatedLoc, secondaryTarget, damage * 0.7, level);
                                    }
                                }
                            }.runTaskLater(plugin, 3L);
                        }
                    }
                }
                
                // Decrement timers
                ticksRemaining--;
                if (ticksUntilNextShot > 0) {
                    ticksUntilNextShot--;
                }
            }
        }.runTaskTimer(plugin, 5L, 1L);
    }
    
    /**
     * Finds a new target for the turret.
     *
     * @param player The owner of the turret
     * @param turretLoc The location of the turret
     * @param range The targeting range
     * @return The new target, or null if none found
     */
    private LivingEntity findNewTarget(Player player, Location turretLoc, double range) {
        return findNewTarget(player, turretLoc, range, null);
    }
    
    /**
     * Finds a new target for the turret, optionally excluding a specific entity.
     *
     * @param player The owner of the turret
     * @param turretLoc The location of the turret
     * @param range The targeting range
     * @param exclude An entity to exclude from targeting
     * @return The new target, or null if none found
     */
    private LivingEntity findNewTarget(Player player, Location turretLoc, double range, LivingEntity exclude) {
        double closestDistSq = range * range;
        LivingEntity closestEntity = null;
        
        for (Entity entity : turretLoc.getWorld().getNearbyEntities(turretLoc, range, range, range)) {
            // Skip if not a living entity, is the player, or is the excluded entity
            if (!(entity instanceof LivingEntity) || entity.equals(player) || entity.equals(exclude)) {
                continue;
            }
            
            LivingEntity livingEntity = (LivingEntity) entity;
            
            // Only target monsters or players in PvP (if configured)
            if (!(livingEntity instanceof Monster) && 
                !(livingEntity instanceof Player && plugin.getConfig().getBoolean("pvp.enabled", false))) {
                continue;
            }
            
            // Skip players in same team
            if (livingEntity instanceof Player && player.getScoreboard().getTeam(player.getName()) != null && 
                player.getScoreboard().getTeam(player.getName()).hasEntry(livingEntity.getName())) {
                continue;
            }
            
            // Check line of sight
            if (!hasLineOfSight(turretLoc, livingEntity.getLocation().add(0, 1, 0))) {
                continue;
            }
            
            double distSq = turretLoc.distanceSquared(livingEntity.getLocation());
            if (distSq < closestDistSq) {
                closestDistSq = distSq;
                closestEntity = livingEntity;
            }
        }
        
        return closestEntity;
    }
    
    /**
     * Checks if there is a clear line of sight between two locations.
     *
     * @param from The starting location
     * @param to The ending location
     * @return true if there is a clear line of sight, false otherwise
     */
    private boolean hasLineOfSight(Location from, Location to) {
        // Simple ray cast - in a real implementation we'd use a more sophisticated approach
        return true;
    }
    
    /**
     * Checks if a target is still valid.
     *
     * @param target The target to check
     * @param turretLoc The location of the turret
     * @param range The maximum range
     * @return true if the target is valid, false otherwise
     */
    private boolean isValidTarget(LivingEntity target, Location turretLoc, double range) {
        return target.isValid() && !target.isDead() && 
               turretLoc.distanceSquared(target.getLocation()) <= range * range &&
               hasLineOfSight(turretLoc, target.getLocation().add(0, 1, 0));
    }
    
    /**
     * Fires a projectile from the turret at a target.
     *
     * @param turretLoc The location of the turret
     * @param target The target to fire at
     * @param damage The damage to deal
     * @param level The enchantment level
     */
    private void fireTurretProjectile(Location turretLoc, LivingEntity target, double damage, int level) {
        World world = turretLoc.getWorld();
        
        // Calculate direction to target
        Vector direction = target.getLocation().add(0, 1, 0).toVector().subtract(turretLoc.toVector()).normalize();
        
        // Firing sound
        world.playSound(turretLoc, Sound.ENTITY_DRAGON_FIREBALL_EXPLODE, 0.3f, 2.0f);
        
        // Create projectile effect
        new BukkitRunnable() {
            Location currentLoc = turretLoc.clone();
            Vector trajectory = direction.clone().multiply(PROJECTILE_SPEED);
            int distance = 0;
            final int maxDistance = 30; // Maximum distance in blocks
            
            @Override
            public void run() {
                // Check if we've hit something or gone too far
                if (distance >= maxDistance || 
                    !currentLoc.getBlock().isPassable() || 
                    currentLoc.distanceSquared(target.getLocation()) < 1.0) {
                    
                    // Hit effect
                    createProjectileImpactEffect(currentLoc, level);
                    
                    // Play impact sound
                    world.playSound(currentLoc, Sound.ENTITY_GENERIC_EXPLODE, 0.4f, 1.8f);
                    
                    // Apply damage if close enough to target
                    if (currentLoc.distanceSquared(target.getLocation()) < 4.0) {
                        target.damage(damage);
                    }
                    
                    this.cancel();
                    return;
                }
                
                // Move projectile forward
                currentLoc.add(trajectory);
                distance++;
                
                // Create projectile trail
                createProjectileTrailEffect(currentLoc, level);
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
    
    /**
     * Creates the initial turret formation effect.
     *
     * @param location The location of the turret
     * @param level The enchantment level
     */
    private void createTurretFormationEffect(Location location, int level) {
        World world = location.getWorld();
        
        // Circle of ascending particles
        for (int i = 0; i < 3; i++) {
            final int layer = i;
            new BukkitRunnable() {
                @Override
                public void run() {
                    double radius = 0.5;
                    int particles = 12;
                    double angleIncrement = 2 * Math.PI / particles;
                    
                    for (int j = 0; j < particles; j++) {
                        double angle = j * angleIncrement;
                        double x = Math.cos(angle) * radius;
                        double z = Math.sin(angle) * radius;
                        
                        Location particleLoc = location.clone().add(x, layer * 0.3, z);
                        
                        // Core crystal particles
                        world.spawnParticle(
                            Particle.END_ROD,
                            particleLoc,
                            1,
                            0, 0, 0,
                            0.02
                        );
                    }
                }
            }.runTaskLater(plugin, i * 5L);
        }
        
        // Central core formation
        new BukkitRunnable() {
            @Override
            public void run() {
                // Central energy core
                world.spawnParticle(
                    Particle.SOUL_FIRE_FLAME,
                    location.clone().add(0, 0.5, 0),
                    10,
                    0.1, 0.1, 0.1,
                    0.02
                );
                
                // Energy burst
                world.spawnParticle(
                    Particle.FLASH,
                    location.clone().add(0, 0.5, 0),
                    1,
                    0, 0, 0,
                    0
                );
                
                // Play activation sound
                world.playSound(location, Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 1.8f);
            }
        }.runTaskLater(plugin, 15L);
    }
    
    /**
     * Creates ambient particles for an active turret.
     *
     * @param location The location of the turret
     * @param level The enchantment level
     */
    private void createTurretAmbientEffect(Location location, int level) {
        World world = location.getWorld();
        
        // Determine turret color based on level
        Color turretColor;
        switch (level) {
            case 1:
                turretColor = Color.fromRGB(0, 150, 220); // Light blue
                break;
            case 2:
                turretColor = Color.fromRGB(0, 100, 255); // Medium blue
                break;
            case 3:
                turretColor = Color.fromRGB(30, 30, 255); // Deep blue
                break;
            default:
                turretColor = Color.fromRGB(0, 150, 220);
        }
        
        // Rotating particles
        double time = (double) (System.currentTimeMillis() % 2000) / 2000;
        double angle = time * 2 * Math.PI;
        double radius = 0.5;
        
        for (int i = 0; i < level + 1; i++) {
            double offsetAngle = angle + (i * (2 * Math.PI / (level + 1)));
            double x = Math.cos(offsetAngle) * radius;
            double z = Math.sin(offsetAngle) * radius;
            
            Location particleLoc = location.clone().add(x, 0, z);
            
            // Colored dust particles
            plugin.getVisualEffectManager().spawnColoredParticles(
                particleLoc,
                Particle.DUST,
                turretColor,
                0.8f,
                1
            );
        }
        
        // Occasional central energy pulse
        if (random.nextInt(20) == 0) {
            world.spawnParticle(
                Particle.END_ROD,
                location,
                3,
                0.1, 0.1, 0.1,
                0.02
            );
        }
    }
    
    /**
     * Creates a target lock-on effect.
     *
     * @param from The turret location
     * @param to The target location
     */
    private void createTargetLockEffect(Location from, Location to) {
        World world = from.getWorld();
        
        // Calculate direction vector
        Vector direction = to.toVector().subtract(from.toVector());
        double distance = direction.length();
        direction.normalize();
        
        // Create a beam of particles
        int particles = (int) distance * 2;
        for (int i = 0; i < particles; i++) {
            double t = (double) i / particles;
            Vector position = from.toVector().add(direction.clone().multiply(distance * t));
            
            Location particleLoc = position.toLocation(world);
            
            // Thin targeting beam
            world.spawnParticle(
                Particle.END_ROD,
                particleLoc,
                1,
                0, 0, 0,
                0
            );
        }
    }
    
    /**
     * Creates a projectile trail effect.
     *
     * @param location The current location of the projectile
     * @param level The enchantment level
     */
    private void createProjectileTrailEffect(Location location, int level) {
        World world = location.getWorld();
        
        // Core projectile
        world.spawnParticle(
            Particle.SOUL_FIRE_FLAME,
            location,
            1,
            0, 0, 0,
            0.01
        );
        
        // Trail effect
        world.spawnParticle(
            Particle.END_ROD,
            location,
            1,
            0, 0, 0,
            0.01
        );
        
        // Additional trail for higher levels
        if (level >= 2) {
            world.spawnParticle(
                Particle.DRAGON_BREATH,
                location,
                1,
                0.05, 0.05, 0.05,
                0.01
            );
        }
    }
    
    /**
     * Creates an impact effect when a projectile hits.
     *
     * @param location The impact location
     * @param level The enchantment level
     */
    private void createProjectileImpactEffect(Location location, int level) {
        World world = location.getWorld();
        
        // Explosion effect
        world.spawnParticle(
            Particle.CLOUD,
            location,
            3,
            0.2, 0.2, 0.2,
            0.02
        );
        
        // Water-themed impact (fitting for trident)
        world.spawnParticle(
            Particle.DRAGON_BREATH,
            location,
            15,
            0.3, 0.3, 0.3,
            0.5
        );
        
        // Energy dispersal
        for (int i = 0; i < 8; i++) {
            double angle = i * (Math.PI / 4);
            double x = Math.cos(angle) * 0.5;
            double z = Math.sin(angle) * 0.5;
            
            Location particleLoc = location.clone().add(x, 0, z);
            
            world.spawnParticle(
                Particle.SOUL_FIRE_FLAME,
                particleLoc,
                1,
                0.1, 0.1, 0.1,
                0.05
            );
        }
        
        // Larger impact for higher levels
        if (level >= 3) {
            world.spawnParticle(
                Particle.FLASH,
                location,
                1,
                0, 0, 0,
                0
            );
        }
    }
    
    /**
     * Removes an active turret and performs cleanup.
     *
     * @param turretId The ID of the turret to remove
     * @param location The location of the turret
     */
    private void removeActiveTurret(UUID turretId, Location location) {
        World world = location.getWorld();
        
        // Get turret data if available
        TurretData data = activeTurrets.remove(turretId);
        
        // Play deactivation sound
        world.playSound(location, Sound.BLOCK_BEACON_DEACTIVATE, 0.8f, 1.5f);
        
        // Create deactivation effect
        world.spawnParticle(
            Particle.CLOUD,
            location,
            10,
            0.3, 0.3, 0.3,
            0.1
        );
        
        // If we still have turret data, notify the player
        if (data != null && data.getOwner() != null && data.getOwner().isOnline()) {
            data.getOwner().sendActionBar(net.kyori.adventure.text.Component.text(
                "§3Sentinel Turret §7deactivated"
            ));
        }
    }
    
    /**
     * Inner class to store turret data.
     */
    private class TurretData {
        private final Player owner;
        private final Trident trident;
        private final int level;
        private final double damage;
        
        public TurretData(Player owner, Trident trident, int level, double damage) {
            this.owner = owner;
            this.trident = trident;
            this.level = level;
            this.damage = damage;
        }
        
        public Player getOwner() {
            return owner;
        }
        
        public Trident getTrident() {
            return trident;
        }
        
        public int getLevel() {
            return level;
        }
        
        public double getDamage() {
            return damage;
        }
    }
    
    @Override
    public boolean conflictsWith(CustomEnchant other) {
        // Conflicts with other trident enchantments that modify thrown behavior
        return other.getName().toLowerCase().contains("sentinel") ||
               other.getName().toLowerCase().contains("turret") ||
               other.getName().toLowerCase().contains("artillery");
    }
} 