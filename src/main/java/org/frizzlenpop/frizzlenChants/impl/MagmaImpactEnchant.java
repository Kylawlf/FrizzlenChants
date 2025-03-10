package org.frizzlenpop.frizzlenChants.impl;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.frizzlenpop.frizzlenChants.FrizzlenChants;
import org.frizzlenpop.frizzlenChants.enchant.CustomEnchant;
import org.frizzlenpop.frizzlenChants.enchant.EnchantmentUtils;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Magma Impact enchantment that summons a meteor shower with multiple
 * meteors falling at angles across a wide area, causing explosions and damage.
 */
public class MagmaImpactEnchant extends CustomEnchant {

    private final FrizzlenChants plugin;
    private final Random random = new Random();
    private final Map<UUID, Long> lastProcTime = new HashMap<>();
    
    // Constants
    private static final int COOLDOWN_MILLIS = 10000; // 10 seconds cooldown
    private static final double BASE_DAMAGE = 4.0; // Base damage per meteor
    private static final float KNOCKBACK_STRENGTH = 1.2f; // Knockback strength
    private static final int SUMMON_HEIGHT = 7; // Max height above target
    private static final int SCATTER_RADIUS = 10; // Area radius for meteor shower
    private static final Material[] METEOR_MATERIALS = {
        Material.MAGMA_BLOCK, Material.NETHERRACK, Material.BLACKSTONE, Material.OBSIDIAN
    };
    
    public MagmaImpactEnchant() {
        super("MagmaImpact", 3, 
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
        
        // Check cooldown
        UUID playerId = player.getUniqueId();
        long currentTime = System.currentTimeMillis();
        
        if (lastProcTime.containsKey(playerId)) {
            long lastProc = lastProcTime.get(playerId);
            if (currentTime - lastProc < COOLDOWN_MILLIS) {
                return; // Still on cooldown
            }
        }
        
        // Calculate proc chance (15% + 5% per level)
        int procChance = 15 + (level * 5);
        
        // Roll for proc
        if (random.nextInt(100) < procChance) {
            // Update last proc time
            lastProcTime.put(playerId, Long.valueOf(currentTime));
            
            // Summon the meteor shower
            summonMeteorShower(player, target, level);
            
            // Notify the player
            player.sendActionBar(net.kyori.adventure.text.Component.text(
                "§6Meteor Shower §7summoned!"
            ));
            
            // Occasionally clean up old entries
            if (random.nextInt(10) == 0) {
                cleanupOldEntries();
            }
        }
    }
    
    /**
     * Summons a meteor shower around the target.
     *
     * @param player The player who triggered the enchantment
     * @param target The target entity
     * @param level The enchantment level
     */
    private void summonMeteorShower(Player player, LivingEntity target, int level) {
        World world = target.getWorld();
        Location targetLoc = target.getLocation();
        
        // Determine number of meteors based on level
        int baseCount = 3 + (level * 2); // Level 1: 5, Level 2: 7, Level 3: 9
        int meteorCount = baseCount + random.nextInt(4); // Add 0-3 random meteors
        
        // Play initial warning sound
        world.playSound(targetLoc, Sound.ENTITY_WITHER_SPAWN, 0.8f, 1.5f);
        
        // Create central warning effect
        createCentralWarningEffect(targetLoc);
        
        // Summon meteors with a delay between them
        new BukkitRunnable() {
            int meteorsSummoned = 0;
            int tick = 0;
            
            @Override
            public void run() {
                if (meteorsSummoned >= meteorCount) {
                    this.cancel();
                    return;
                }
                
                // Summon meteors at random intervals
                if (tick % (random.nextInt(5) + 2) == 0) {
                    // Determine random position within scatter radius
                    double angle = random.nextDouble() * Math.PI * 2;
                    double distance = random.nextDouble() * SCATTER_RADIUS;
                    double x = Math.cos(angle) * distance;
                    double z = Math.sin(angle) * distance;
                    
                    // Calculate impact position
                    Location impactPos = targetLoc.clone().add(0, 0, 0);
                    
                    // Create individual warning effect
                    createWarningEffect(impactPos);
                    
                    // Summon meteor with random properties
                    summonSingleMeteor(player, impactPos, level, meteorsSummoned);
                    
                    meteorsSummoned++;
                    
                    // Sound effect for each meteor
                    world.playSound(targetLoc, Sound.ENTITY_GENERIC_EXPLODE, 0.3f, 2.0f);
                }
                
                tick++;
            }
        }.runTaskTimer(plugin, 10L, 1L);
    }
    
    /**
     * Summons a single meteor at the specified location.
     *
     * @param player The player who triggered the enchantment
     * @param targetPos The position where the meteor should impact
     * @param level The enchantment level
     * @param meteorIndex The index of this meteor in the shower
     */
    private void summonSingleMeteor(Player player, Location targetPos, int level, int meteorIndex) {
        World world = targetPos.getWorld();
        
        // Calculate spawn position (at an angle)
        double angle = random.nextDouble() * Math.PI * 2;
        double horizontalDistance = random.nextDouble() * 3;
        double height = 10 * random.nextDouble();
        
        // Calculate spawn location for angled approach
        Location spawnLoc = targetPos.clone().add(
            Math.cos(angle) * horizontalDistance,
            height,
            Math.sin(angle) * horizontalDistance
        );
        
        // Randomize meteor size (smaller = faster)
        float baseScale = 0.8f + (random.nextFloat() * 0.4f) + (level * 0.2f);
        float meteorScale = Math.min(3.0f, baseScale);
        
        // Randomize meteor speed
        int fallDuration = Math.max(10, 5 - meteorIndex - (level)); // Faster with higher levels

        // Calculate meteor velocity vector (from spawn to target)
        Vector velocity = targetPos.clone().subtract(spawnLoc).toVector().normalize();
        
        // Create the meteor block display
        BlockDisplay meteor = (BlockDisplay) world.spawnEntity(spawnLoc, EntityType.BLOCK_DISPLAY);
        
        // Set random meteor material
        Material meteorMaterial = METEOR_MATERIALS[random.nextInt(METEOR_MATERIALS.length)];
        meteor.setBlock(meteorMaterial.createBlockData());
        
        // Set initial transformation
        Transformation initialTransform = new Transformation(
            new Vector3f(0, 0, 0), // translation
            new AxisAngle4f(random.nextFloat() * 2.0f * (float) Math.PI, random.nextFloat(), random.nextFloat(), random.nextFloat()), // random rotation
            new Vector3f(meteorScale, meteorScale, meteorScale), // scale
            new AxisAngle4f(0, 0, 0, 0) // right rotation
        );
        meteor.setTransformation(initialTransform);
        
        // Set display properties
        meteor.setBrightness(new Display.Brightness(15, 15)); // Full brightness
        meteor.setGlowing(true);
        
        // Fall animation
        BukkitTask bukkitTask = new BukkitRunnable() {
            int tick = 0;
            final Vector path = velocity.clone().multiply(2.0 / fallDuration); // Move per tick
            boolean hasImpacted = false;

            @Override
            public void run() {
                if (!meteor.isValid() || hasImpacted || tick > fallDuration * 1.5) {
                    meteor.remove();
                    this.cancel();
                    return;
                }

                // Move the meteor
                Location newLoc = meteor.getLocation().add(path);
                meteor.teleport(newLoc);

                // Rotate the meteor
                Transformation currentTransform = meteor.getTransformation();
                Transformation newTransform = new Transformation(
                        currentTransform.getTranslation(),
                        new AxisAngle4f((float) (tick * 0.1), 1.0f, 0.5f, 0.0f), // Continuous rotation
                        currentTransform.getScale(),
                        new AxisAngle4f(0, 0, 0, 0)
                );
                meteor.setTransformation(newTransform);

                // Create trailing particles
                createMeteorTrail(meteor.getLocation(), meteorScale, level);

                // Check if close to ground or reached the target
                if (tick >= fallDuration || isNearGround(meteor.getLocation()) ||
                        meteor.getLocation().distanceSquared(targetPos) < 0) {

                    // Create explosion effect
                    createMeteorImpactEffect(meteor.getLocation(), meteorScale, level);

                    // Apply damage to nearby entities
                    applyMeteorDamage(player, meteor.getLocation(), meteorScale, level);

                    // Set as impacted
                    hasImpacted = true;
                }

                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
    
    /**
     * Checks if a location is near the ground.
     *
     * @param location The location to check
     * @return true if the location is near or below ground level
     */
    private boolean isNearGround(Location location) {
        // Check a few blocks down for solid ground
        for (int i = 0; i < 2; i++) {
            Location check = location.clone().subtract(0, i, 0);
            if (check.getBlock().getType().isSolid()) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Creates trailing particles behind a meteor.
     *
     * @param location The meteor's current location
     * @param scale The meteor's scale
     * @param level The enchantment level
     */
    private void createMeteorTrail(Location location, float scale, int level) {
        World world = location.getWorld();
        
        // Number of particles based on size and level
        int particleCount = (int)(5 * scale) + (level * 2);
        
        // Flame trail
        world.spawnParticle(
            Particle.FLAME,
            location,
            particleCount,
            0.2 * scale, 0.2 * scale, 0.2 * scale,
            0.01
        );
        
        // Smoke trail
        world.spawnParticle(
            Particle.CAMPFIRE_COSY_SMOKE,
            location,
            particleCount / 2,
            0.1 * scale, 0.1 * scale, 0.1 * scale,
            0.01
        );
        
        // Add sparks for higher levels
        if (level >= 2) {
            world.spawnParticle(
                Particle.LAVA,
                location,
                1 + level,
                0.1 * scale, 0.1 * scale, 0.1 * scale,
                0
            );
        }
    }
    
    /**
     * Creates a central warning effect for the meteor shower.
     *
     * @param location The center of the meteor shower
     */
    private void createCentralWarningEffect(Location location) {
        World world = location.getWorld();
        
        // Create a pulsing circular warning effect
        new BukkitRunnable() {
            int tick = 0;
            final int duration = 40; // 2 seconds warning
            
            @Override
            public void run() {
                if (tick >= duration) {
                    this.cancel();
                    return;
                }
                
                // Calculate pulsing radius
                double baseRadius = SCATTER_RADIUS / 2;
                double radiusOffset = Math.sin(tick * 0.2) * 2;
                double radius = baseRadius + radiusOffset;
                
                // Fewer particles for performance (just outline)
                int particles = 20;
                double angleIncrement = 2 * Math.PI / particles;
                
                for (int i = 0; i < particles; i++) {
                    double angle = i * angleIncrement;
                    double x = Math.cos(angle) * radius;
                    double z = Math.sin(angle) * radius;
                    
                    Location particleLoc = location.clone().add(x, 0.1, z);
                    
                    // Warning particles
                    world.spawnParticle(
                        Particle.FLAME,
                        particleLoc,
                        1,
                        0, 0, 0,
                        0
                    );
                    
                    // Add some ash particles near ground
                    if (tick % 5 == 0 && random.nextInt(3) == 0) {
                        Location ashLoc = location.clone().add(
                            random.nextDouble() * SCATTER_RADIUS - (SCATTER_RADIUS / 2),
                            0.1,
                            random.nextDouble() * SCATTER_RADIUS - (SCATTER_RADIUS / 2)
                        );
                        
                        world.spawnParticle(
                            Particle.CAMPFIRE_COSY_SMOKE,
                            ashLoc,
                            1,
                            0, 0, 0,
                            0.02
                        );
                    }
                }
                
                // Ambient sounds
                if (tick % 10 == 0) {
                    world.playSound(location, Sound.BLOCK_FIRE_AMBIENT, 0.4f, 0.5f);
                }
                
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
    
    /**
     * Creates a warning effect for a single meteor impact.
     *
     * @param location The impact location
     */
    private void createWarningEffect(Location location) {
        World world = location.getWorld();
        
        // Create a brief circular warning effect
        new BukkitRunnable() {
            int tick = 0;
            final int duration = 10; // Half-second warning
            
            @Override
            public void run() {
                if (tick >= duration) {
                    this.cancel();
                    return;
                }
                
                // Create small circle
                double radius = 1.5;
                int particles = 12;
                double angleIncrement = 2 * Math.PI / particles;
                
                for (int i = 0; i < particles; i++) {
                    double angle = i * angleIncrement;
                    double x = Math.cos(angle) * radius;
                    double z = Math.sin(angle) * radius;
                    
                    Location particleLoc = location.clone().add(x, 0.1, z);
                    
                    // Warning particles
                    world.spawnParticle(
                        Particle.SOUL_FIRE_FLAME,
                        particleLoc,
                        1,
                        0, 0, 0,
                        0
                    );
                }
                
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
    
    /**
     * Creates a meteor impact explosion effect.
     *
     * @param location The location of the impact
     * @param meteorScale The scale of the meteor
     * @param level The enchantment level
     */
    private void createMeteorImpactEffect(Location location, float meteorScale, int level) {
        World world = location.getWorld();
        
        // Explosion scale based on meteor size and level
        float explosionScale = meteorScale * (1.0f + (level * 0.2f));
        
        // Play impact sound
        world.playSound(location, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 0.5f + (random.nextFloat() * 0.5f));
        
        // Create explosion particles
        world.spawnParticle(
            Particle.EXPLOSION_EMITTER,
            location,
            1 + (int)(explosionScale * 2),
            0.3 * explosionScale, 0.3 * explosionScale, 0.3 * explosionScale,
            0.1
        );
        
        // Create fire particles
        int fireParticles = (int)(15 * explosionScale);
        for (int i = 0; i < fireParticles; i++) {
            double offsetX = random.nextDouble() * explosionScale * 2 - explosionScale;
            double offsetY = random.nextDouble() * explosionScale;
            double offsetZ = random.nextDouble() * explosionScale * 2 - explosionScale;
            
            world.spawnParticle(
                Particle.FLAME,
                location.clone().add(offsetX, offsetY, offsetZ),
                1,
                0.1, 0.1, 0.1,
                0.1
            );
        }
        
        // Create lava particles for larger meteors
        if (meteorScale > 1.5f) {
            int lavaParticles = (int)(10 * explosionScale);
            for (int i = 0; i < lavaParticles; i++) {
                double offsetX = random.nextDouble() * explosionScale - (explosionScale / 2);
                double offsetY = random.nextDouble() * (explosionScale / 2);
                double offsetZ = random.nextDouble() * explosionScale - (explosionScale / 2);
                
                world.spawnParticle(
                    Particle.LAVA,
                    location.clone().add(offsetX, offsetY, offsetZ),
                    1,
                    0, 0, 0,
                    0
                );
            }
        }
        
        // Create smoke plume
        new BukkitRunnable() {
            int tick = 0;
            final int duration = (int)(15 * explosionScale); // Duration based on explosion size
            
            @Override
            public void run() {
                if (tick >= duration) {
                    this.cancel();
                    return;
                }
                
                // Smoke particles rising up
                world.spawnParticle(
                    Particle.CAMPFIRE_COSY_SMOKE,
                    location.clone().add(
                        random.nextDouble() * explosionScale - (explosionScale / 2),
                        0.5 + (tick * 0.2),
                        random.nextDouble() * explosionScale - (explosionScale / 2)
                    ),
                    1,
                    0.2, 0.1, 0.2,
                    0.01
                );
                
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
        
        // Create ground scorching effect
        createGroundScorchEffect(location, explosionScale);
    }
    
    /**
     * Creates a ground scorch effect after meteor impact.
     *
     * @param location The impact location
     * @param radius The scorch radius
     */
    private void createGroundScorchEffect(Location location, float radius) {
        World world = location.getWorld();
        
        // Create expanding ring of particles along the ground
        new BukkitRunnable() {
            double currentRadius = 0.5;
            final double maxRadius = radius * 1.5;
            final double expansionRate = 0.2;
            
            @Override
            public void run() {
                if (currentRadius >= maxRadius) {
                    this.cancel();
                    return;
                }
                
                int particles = (int)(currentRadius * 8);
                double angleIncrement = 2 * Math.PI / particles;
                
                for (int i = 0; i < particles; i++) {
                    double angle = i * angleIncrement;
                    double x = Math.cos(angle) * currentRadius;
                    double z = Math.sin(angle) * currentRadius;
                    Location particleLoc = location.clone().add(x, 0.1, z);
                    
                    world.spawnParticle(
                        Particle.SMALL_FLAME,
                        particleLoc,
                        1,
                        0.05, 0.05, 0.05,
                        0.01
                    );
                }
                
                currentRadius += expansionRate;
            }
        }.runTaskTimer(plugin, 0L, 1L);
        
        // Create cinder particles
        new BukkitRunnable() {
            int tick = 0;
            final int duration = 20;
            
            @Override
            public void run() {
                if (tick >= duration) {
                    this.cancel();
                    return;
                }
                
                for (int i = 0; i < 3; i++) {
                    double angle = random.nextDouble() * Math.PI * 2;
                    double distance = random.nextDouble() * radius;
                    double x = Math.cos(angle) * distance;
                    double z = Math.sin(angle) * distance;
                    
                    Location cinderLoc = location.clone().add(x, 0.1, z);
                    
                    // Create floating cinders
                    world.spawnParticle(
                        Particle.FALLING_LAVA,
                        cinderLoc,
                        1,
                        0.1, 0.1, 0.1,
                        0.01
                    );
                }
                
                tick++;
            }
        }.runTaskTimer(plugin, 5L, 2L);
    }
    
    /**
     * Applies damage and knockback from a meteor impact to nearby entities.
     *
     * @param player The player who triggered the enchantment
     * @param location The location of the impact
     * @param meteorScale The scale of the meteor
     * @param level The enchantment level
     */
    private void applyMeteorDamage(Player player, Location location, float meteorScale, int level) {
        World world = location.getWorld();
        
        // Calculate effect radius based on meteor size and level
        double radius = 2.0 * meteorScale + (level * 0.5); // Scale with meteor size and enchant level
        double damage = BASE_DAMAGE * meteorScale + (level * 1.5); // 4-12 damage based on size and level
        float knockbackMultiplier = KNOCKBACK_STRENGTH * meteorScale; // Scales with meteor size
        
        // Find entities in explosion radius
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
            
            // Calculate knockback direction (away from explosion and upward)
            Vector knockbackDir = livingEntity.getLocation().toVector()
                .subtract(location.toVector())
                .normalize()
                .setY(0.6) // Add upward component
                .normalize()
                .multiply(knockbackMultiplier * distanceFactor);
            
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
    }
    
    /**
     * Cleans up old cooldown entries.
     */
    private void cleanupOldEntries() {
        long currentTime = System.currentTimeMillis();
        lastProcTime.entrySet().removeIf(entry -> 
            currentTime - entry.getValue() > COOLDOWN_MILLIS * 2);
    }
    
    @Override
    public boolean conflictsWith(CustomEnchant other) {
        return other.getName().equalsIgnoreCase("FireStorm") ||
               other.getName().equalsIgnoreCase("InfernoBlast") ||
               other.getName().equalsIgnoreCase("MeteorStrike");
    }
} 