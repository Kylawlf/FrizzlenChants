package org.frizzlenpop.frizzlenChants.impl;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.frizzlenpop.frizzlenChants.FrizzlenChants;
import org.frizzlenpop.frizzlenChants.enchant.CustomEnchant;
import org.frizzlenpop.frizzlenChants.enchant.EnchantmentUtils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Spectral Blade enchantment that creates damaging ghostly trails
 * when hitting enemies.
 */
public class SpectralBladeEnchant extends CustomEnchant {

    private final FrizzlenChants plugin;
    private final Random random = new Random();
    private final Map<UUID, Set<UUID>> trailAffectedEntities = new HashMap<>();
    
    // Constants
    private static final String SPECTRAL_TRAIL_KEY = "spectral_trail";
    private static final int TRAIL_CHECK_INTERVAL = 5; // Ticks between damage checks
    
    public SpectralBladeEnchant() {
        super("SpectralBlade", 3, 
              Material.WOODEN_SWORD, Material.STONE_SWORD, Material.IRON_SWORD, 
              Material.GOLDEN_SWORD, Material.DIAMOND_SWORD, Material.NETHERITE_SWORD,
              Material.WOODEN_AXE, Material.STONE_AXE, Material.IRON_AXE,
              Material.GOLDEN_AXE, Material.DIAMOND_AXE, Material.NETHERITE_AXE);
        this.plugin = FrizzlenChants.getPlugin(FrizzlenChants.class);
    }

    @Override
    public void applyEffect(Player player, ItemStack item, int level) {
        // Implementation handled in the event handler below
    }
    
    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        // Check if the attacker is a player
        if (!(event.getDamager() instanceof Player)) {
            return;
        }
        
        Player player = (Player) event.getDamager();
        
        // Check if the player has a weapon with this enchantment
        ItemStack weapon = player.getInventory().getItemInMainHand();
        
        if (weapon == null) {
            return;
        }
        
        // Check if the weapon has this enchantment
        int level = EnchantmentUtils.getEnchantmentLevel(weapon, this, plugin.getEnchantmentRegistry());
        
        if (level <= 0) {
            return;
        }
        
        // Check if the target is a living entity
        if (!(event.getEntity() instanceof LivingEntity)) {
            return;
        }
        
        LivingEntity target = (LivingEntity) event.getEntity();
        
        // Create spectral trail at the target's location
        createSpectralTrail(player, target.getLocation(), level);
    }
    
    /**
     * Creates a spectral trail at the given location.
     *
     * @param player The player who created the trail
     * @param location The location to create the trail at
     * @param level The enchantment level
     */
    private void createSpectralTrail(Player player, Location location, int level) {
        // Calculate trail parameters based on level
        int durationTicks = 100 + (level * 20); // 5-7 seconds
        double radius = 1.5 + (level * 0.5); // 2-3 block radius
        double damage = 2.0 + (level * 1.0); // 3-5 damage per hit
        
        World world = location.getWorld();
        Location trailLoc = location.clone();
        
        // Move trail location to ground level
        trailLoc.setY(findGroundLevel(trailLoc));
        
        // Generate a unique ID for this trail
        UUID trailId = UUID.randomUUID();
        
        // Create a set to track entities affected by this trail
        trailAffectedEntities.put(trailId, new HashSet<>());
        
        // Create visual trail effect
        createTrailVisuals(trailLoc, radius, durationTicks, level);
        
        // Play sound effect
        world.playSound(trailLoc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.5f);
        
        // Schedule damage checks for entities in the trail
        new BukkitRunnable() {
            int ticksRemaining = durationTicks;
            
            @Override
            public void run() {
                if (ticksRemaining <= 0) {
                    trailAffectedEntities.remove(trailId);
                    this.cancel();
                    return;
                }
                
                // Check for entities in the trail and damage them
                for (Entity entity : world.getNearbyEntities(trailLoc, radius, radius, radius)) {
                    if (!(entity instanceof LivingEntity) || entity.equals(player)) {
                        continue;
                    }
                    
                    LivingEntity livingEntity = (LivingEntity) entity;
                    
                    // Check if this entity has already been affected by this trail
                    if (trailAffectedEntities.get(trailId).contains(entity.getUniqueId())) {
                        continue;
                    }
                    
                    // Mark entity as affected
                    trailAffectedEntities.get(trailId).add(entity.getUniqueId());
                    
                    // Apply damage
                    livingEntity.damage(damage, player);
                    
                    // Apply visual effect to the damaged entity
                    createDamageEffect(livingEntity.getLocation(), level);
                    
                    // Play sound effect
                    world.playSound(livingEntity.getLocation(), Sound.ENTITY_VEX_HURT, 0.5f, 1.2f);
                }
                
                // Decrement remaining time
                ticksRemaining -= TRAIL_CHECK_INTERVAL;
            }
        }.runTaskTimer(plugin, 0L, TRAIL_CHECK_INTERVAL);
    }
    
    /**
     * Creates the visual effects for the spectral trail.
     *
     * @param location The location of the trail
     * @param radius The radius of the trail
     * @param durationTicks How long the trail lasts
     * @param level The enchantment level
     */
    private void createTrailVisuals(Location location, double radius, int durationTicks, int level) {
        // Create duration in server ticks (20 ticks = 1 second)
        long effectDuration = durationTicks / 2; // Visual effect updates less frequently
        
        // Trail color based on level
        final Color trailColor;
        switch (level) {
            case 1:
                trailColor = Color.fromRGB(180, 180, 240); // Light blue-white
                break;
            case 2:
                trailColor = Color.fromRGB(130, 150, 255); // Medium blue
                break;
            case 3:
                trailColor = Color.fromRGB(80, 100, 255);  // Deep blue
                break;
            default:
                trailColor = Color.fromRGB(130, 150, 255);
        }
        
        // Schedule trail effect task
        new BukkitRunnable() {
            private int tick = 0;
            private final int maxTicks = (int) effectDuration;
            
            @Override
            public void run() {
                if (tick >= maxTicks) {
                    this.cancel();
                    return;
                }
                
                // Create spiral/circle pattern on the ground
                createGroundPattern(location, radius, trailColor, level, tick);
                
                // Create vertical ghostly flames
                if (tick % 5 == 0) {
                    createGhostlyFlames(location, radius, trailColor, level);
                }
                
                tick += 2; // Update every 2 ticks to reduce particle spam
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }
    
    /**
     * Creates a circular/spiral pattern on the ground for the trail.
     *
     * @param center The center location
     * @param radius The radius of the pattern
     * @param color The color of the trail
     * @param level The enchantment level
     * @param tick The current tick counter
     */
    private void createGroundPattern(Location center, double radius, Color color, int level, int tick) {
        World world = center.getWorld();
        
        // Number of particles scales with radius and level
        int particleCount = (int) (radius * 10) + (level * 5);
        
        // Create spiral pattern
        for (int i = 0; i < particleCount; i++) {
            // Calculate position in spiral
            double angle = ((double) tick / 5 + (double) i / particleCount) * 2 * Math.PI;
            double spiral = (double) i / particleCount; // Spiral factor
            double currentRadius = radius * spiral;
            
            double x = Math.cos(angle) * currentRadius;
            double z = Math.sin(angle) * currentRadius;
            
            // Position just above ground
            Location particleLoc = center.clone().add(x, 0.05, z);
            
            // Send colored dust particle
            plugin.getVisualEffectManager().spawnColoredParticles(
                particleLoc,
                Particle.DUST,
                color,
                1.0f,
                1
            );
            
            // Add some end rod particles for "spectral" effect on higher levels
            if (level >= 2 && i % 5 == 0) {
                world.spawnParticle(
                    Particle.END_ROD,
                    particleLoc,
                    1,
                    0, 0, 0,
                    0.02
                );
            }
        }
        
        // Add a subtle glow to the entire area
        if (tick % 10 == 0) {
            for (int i = 0; i < 5; i++) {
                double x = random.nextDouble() * radius * 2 - radius;
                double z = random.nextDouble() * radius * 2 - radius;
                
                // Only spawn if within radius
                if (x*x + z*z <= radius*radius) {
                    Location glowLoc = center.clone().add(x, 0.1, z);
                    
                    world.spawnParticle(
                        Particle.SOUL_FIRE_FLAME,
                        glowLoc,
                        1,
                        0, 0, 0,
                        0.01
                    );
                }
            }
        }
    }
    
    /**
     * Creates ghostly flame effects rising from the trail.
     *
     * @param center The center location
     * @param radius The radius of the trail
     * @param color The color of the trail
     * @param level The enchantment level
     */
    private void createGhostlyFlames(Location center, double radius, Color color, int level) {
        World world = center.getWorld();
        
        // Number of flame columns
        int flameCount = 1 + level;
        
        for (int i = 0; i < flameCount; i++) {
            // Random position within radius
            double angle = random.nextDouble() * 2 * Math.PI;
            double distance = random.nextDouble() * radius;
            
            double x = Math.cos(angle) * distance;
            double z = Math.sin(angle) * distance;
            
            Location flameLoc = center.clone().add(x, 0, z);
            
            // Create rising particles
            for (int y = 0; y < 10; y++) {
                Location particleLoc = flameLoc.clone().add(0, y * 0.2, 0);
                
                // Soul flame particles
                world.spawnParticle(
                    Particle.SOUL_FIRE_FLAME,
                    particleLoc,
                    1,
                    0.05, 0, 0.05,
                    0.01
                );
                
                // Add colored dust at intervals
                if (y % 3 == 0) {
                    plugin.getVisualEffectManager().spawnColoredParticles(
                        particleLoc,
                        Particle.DUST,
                        color,
                        0.7f,
                        1
                    );
                }
                
                // Add end rod particles for higher levels
                if (level >= 3 && y % 4 == 0) {
                    world.spawnParticle(
                        Particle.END_ROD,
                        particleLoc,
                        1,
                        0, 0, 0,
                        0.02
                    );
                }
            }
        }
    }
    
    /**
     * Creates a visual effect when an entity is damaged by the trail.
     *
     * @param location The location of the damaged entity
     * @param level The enchantment level
     */
    private void createDamageEffect(Location location, int level) {
        World world = location.getWorld();
        
        // Create a burst of particles around the entity
        world.spawnParticle(
            Particle.SOUL,
            location.clone().add(0, 1, 0),
            10 + (level * 5),
            0.5, 0.5, 0.5,
            0.05
        );
        
        // Add some spectral particles
        world.spawnParticle(
            Particle.END_ROD,
            location.clone().add(0, 1, 0),
            5 + level,
            0.3, 0.3, 0.3,
            0.1
        );
    }
    
    /**
     * Finds the ground level at the given location.
     *
     * @param location The location to check
     * @return The Y coordinate of the ground
     */
    private double findGroundLevel(Location location) {
        World world = location.getWorld();
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();
        
        // Check blocks from the given Y down to bedrock
        for (int i = y; i > 0; i--) {
            Material material = world.getBlockAt(x, i, z).getType();
            Material materialAbove = world.getBlockAt(x, i + 1, z).getType();
            
            // If this block is solid and the one above is air/passable
            if (material.isSolid() && !materialAbove.isSolid()) {
                return i + 1.05; // Just above the ground
            }
        }
        
        // If no solid ground found, return original Y
        return y;
    }
    
    @Override
    public boolean conflictsWith(CustomEnchant other) {
        // Conflicts with other ground-effect enchantments
        return other.getName().toLowerCase().contains("trail") ||
               other.getName().toLowerCase().contains("spectral") ||
               other.getName().toLowerCase().contains("ground");
    }
} 