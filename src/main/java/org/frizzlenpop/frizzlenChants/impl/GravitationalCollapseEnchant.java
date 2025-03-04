package org.frizzlenpop.frizzlenChants.impl;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
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
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Gravitational Collapse Enchantment
 * 
 * Triggers a powerful gravitational singularity that collapses nearby blocks into a black hole,
 * then launches them as projectiles toward nearby enemies.
 */
public class GravitationalCollapseEnchant extends CustomEnchant {

    private final FrizzlenChants plugin;
    private final Random random = new Random();
    private final Map<UUID, Long> lastProcTime = new HashMap<>();
    
    // Configuration constants
    private static final int MAX_LEVEL = 3;
    private static final int MIN_ENTITY_DISTANCE = 3;
    private static final int BLOCK_RADIUS = 3; 
    private static final int MAX_BLOCKS = 12;
    private static final double BLACK_HOLE_OFFSET_Y = 1.5;
    private static final double PROC_CHANCE_BASE = 0.15;
    private static final double PROC_CHANCE_PER_LEVEL = 0.05;
    private static final double BASE_DAMAGE = 8.0;
    private static final double DAMAGE_PER_LEVEL = 4.0;
    private static final int COOLDOWN_SECONDS = 20;
    
    /**
     * Creates a new Gravitational Collapse enchantment.
     */
    public GravitationalCollapseEnchant() {
        super("GravitationalCollapse", 3, 
              Material.WOODEN_SWORD, Material.STONE_SWORD, Material.IRON_SWORD, 
              Material.GOLDEN_SWORD, Material.DIAMOND_SWORD, Material.NETHERITE_SWORD);
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
            if (currentTime - lastProc < COOLDOWN_SECONDS * 1000) {
                return; // Still on cooldown
            }
        }
        
        // Calculate proc chance (20% + 10% per level)
        double procChance = PROC_CHANCE_BASE + (level * PROC_CHANCE_PER_LEVEL);
        
        // Roll for proc
        if (random.nextDouble() < procChance) {
            // Update last proc time
            lastProcTime.put(playerId, currentTime);
            
            // Trigger the gravitational collapse
            triggerGravitationalCollapse(player, target, level);
            
            // Notify the player
            player.sendActionBar(net.kyori.adventure.text.Component.text(
                "§5Gravitational Collapse §7activated!"
            ));
            
            // Occasionally clean up old entries
            if (random.nextInt(10) == 0) {
                cleanupOldEntries();
            }
        }
    }
    
    /**
     * Triggers the gravitational collapse effect.
     *
     * @param player The player who triggered the enchantment
     * @param target The target entity
     * @param level The enchantment level
     */
    private void triggerGravitationalCollapse(Player player, LivingEntity target, int level) {
        World world = target.getWorld();
        Location targetLoc = target.getLocation();
        
        // Play initial sound
        world.playSound(targetLoc, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 0.5f);
        
        // Calculate the center of the effect (offset upward for better visibility)
        Location centerLoc = targetLoc.clone().add(0, BLACK_HOLE_OFFSET_Y, 0);
        
        // Find blocks using ray-tracing
        List<Block> affectedBlocks = scanForBlocks(centerLoc, BLOCK_RADIUS, MAX_BLOCKS, level);
        
        // Start the collapse sequence if we found enough blocks
        if (!affectedBlocks.isEmpty()) {
            // First, highlight the blocks with particle effects
            highlightBlocks(affectedBlocks, centerLoc);
            
            // Then, after a short delay, start the gravitational collapse
            new BukkitRunnable() {
                @Override
                public void run() {
                    startGravitationalCollapse(player, target, centerLoc, affectedBlocks, level);
                }
            }.runTaskLater(plugin, 20L); // 1-second delay
        }
    }
    
    /**
     * Scans for blocks in a spherical radius using ray-tracing.
     *
     * @param center The center location to scan from
     * @param radius The radius to scan
     * @param maxBlocks The maximum number of blocks to collect
     * @param level The enchantment level
     * @return A list of blocks found in the area
     */
    private List<Block> scanForBlocks(Location center, int radius, int maxBlocks, int level) {
        List<Block> result = new ArrayList<>();
        World world = center.getWorld();
        Set<Block> processedBlocks = new HashSet<>();
        
        // Calculate actual radius based on enchantment level
        int actualRadius = radius + (level - 1);
        
        // Create rays in different directions for a more thorough scan
        int rayCount = 60 + (level * 15); // More rays at higher levels
        
        for (int i = 0; i < rayCount && result.size() < maxBlocks; i++) {
            // Create random ray direction
            double theta = random.nextDouble() * Math.PI * 2; // Random angle in XZ plane
            double phi = random.nextDouble() * Math.PI; // Random angle from Y axis
            
            double x = Math.sin(phi) * Math.cos(theta);
            double y = Math.cos(phi);
            double z = Math.sin(phi) * Math.sin(theta);
            
            Vector direction = new Vector(x, y, z);
            
            // Cast ray and check blocks along it
            for (double d = 1; d <= actualRadius; d += 0.5) {
                Location checkLoc = center.clone().add(direction.clone().multiply(d));
                Block block = checkLoc.getBlock();
                
                // Skip if already processed or is air/liquid
                if (processedBlocks.contains(block) || block.isEmpty() || 
                    block.isLiquid() || !block.getType().isSolid()) {
                    continue;
                }
                
                // Add to result if it's a suitable block
                if (isSuitableBlock(block.getType())) {
                    result.add(block);
                    if (result.size() >= maxBlocks) {
                        break;
                    }
                }
                
                processedBlocks.add(block);
            }
        }
        
        return result;
    }
    
    /**
     * Checks if a material is suitable for the gravitational collapse effect.
     *
     * @param material The material to check
     * @return true if the material is suitable
     */
    private boolean isSuitableBlock(Material material) {
        // Avoid bedrock, obsidian, and other unbreakable blocks
        if (material == Material.BEDROCK || material == Material.OBSIDIAN || 
            material == Material.END_PORTAL_FRAME || material == Material.COMMAND_BLOCK) {
            return false;
        }
        
        // Include most solid blocks
        return material.isSolid() && material.isBlock() && material.isOccluding();
    }
    
    /**
     * Highlights blocks with particle effects.
     *
     * @param blocks The blocks to highlight
     * @param center The center location of the effect
     */
    private void highlightBlocks(List<Block> blocks, Location center) {
        World world = center.getWorld();
        
        // Create initial highlighting effect
        new BukkitRunnable() {
            int tick = 0;
            final int duration = 20; // 1 second of highlighting
            
            @Override
            public void run() {
                if (tick >= duration) {
                    this.cancel();
                    return;
                }
                
                // Highlight each block with particles
                for (Block block : blocks) {
                    Location blockCenter = block.getLocation().add(0.5, 0.5, 0.5);
                    
                    // Calculate a pulsing effect
                    double pulseIntensity = 0.5 + 0.5 * Math.sin(tick * 0.5);
                    
                    // Create outline with particles
                    for (int i = 0; i < 3; i++) {
                        // Random position on block surface
                        double offset = 0.5 + (pulseIntensity * 0.1);
                        double x = (random.nextDouble() * 2 - 1) * offset;
                        double y = (random.nextDouble() * 2 - 1) * offset;
                        double z = (random.nextDouble() * 2 - 1) * offset;
                        
                        // Ensure point is on the surface by setting the largest component to ±0.5
                        double max = Math.max(Math.max(Math.abs(x), Math.abs(y)), Math.abs(z));
                        if (max > 0) {
                            double scale = 0.5 / max;
                            x *= scale;
                            y *= scale;
                            z *= scale;
                        }
                        
                        Location particleLoc = blockCenter.clone().add(x, y, z);
                        
                        // Create purple highlight particles
                        world.spawnParticle(
                            Particle.DRAGON_BREATH,
                            particleLoc,
                            1,
                            0, 0, 0,
                            0
                        );
                    }
                    
                    // Show directional particles pointing toward center
                    if (tick % 5 == 0) {
                        Vector direction = center.toVector().subtract(blockCenter.toVector()).normalize().multiply(0.2);
                        Location arrowLoc = blockCenter.clone().add(direction);
                        
                        world.spawnParticle(
                            Particle.END_ROD,
                            arrowLoc,
                            1,
                            0, 0, 0,
                            0.02
                        );
                    }
                }
                
                // Play sound effects
                if (tick % 5 == 0) {
                    world.playSound(center, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.5f, 0.5f + (tick / 20.0f));
                }
                
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
    
    /**
     * Starts the gravitational collapse sequence.
     *
     * @param player The player who triggered the enchantment
     * @param target The target entity
     * @param center The center location of the effect
     * @param blocks The blocks to use in the effect
     * @param level The enchantment level
     */
    private void startGravitationalCollapse(Player player, LivingEntity target, Location center, 
                                            List<Block> blocks, int level) {
        World world = center.getWorld();
        
        // Play sound effect for collapse start
        world.playSound(center, Sound.BLOCK_END_PORTAL_SPAWN, 1.0f, 0.5f);
        
        // Create the black hole particle effect at the center
        BlackHole blackHole = createBlackHole(center, level);
        
        // Transform blocks into displays
        List<BlockData> blockDataList = new ArrayList<>();
        List<BlockDisplay> blockDisplays = new ArrayList<>();
        
        for (Block block : blocks) {
            // Store original block data for respawning later
            blockDataList.add(block.getBlockData().clone());
            
            // Create block display entity
            BlockDisplay display = createBlockDisplay(block);
            if (display != null) {
                blockDisplays.add(display);
                
                // Set block to air (temporarily)
                block.setType(Material.AIR);
            }
        }
        
        // Now start the animation sequence
        startOrbitalAnimation(player, target, center, blockDisplays, blockDataList, blocks, blackHole, level);
    }
    
    /**
     * Creates a BlockDisplay entity for a given block.
     *
     * @param block The block to create a display for
     * @return The created BlockDisplay entity
     */
    private BlockDisplay createBlockDisplay(Block block) {
        try {
            Location blockCenter = block.getLocation().add(0.5, 0.5, 0.5);
            
            // Create the block display
            BlockDisplay display = block.getWorld().spawn(blockCenter, BlockDisplay.class);
            display.setBlock(block.getBlockData());
            
            // Initial scaling (start at full size)
            Transformation transformation = new Transformation(
                new Vector3f(0, 0, 0),        // translation
                new Quaternionf(),            // left rotation (identity)
                new Vector3f(1, 1, 1),        // scale (full size)
                new Quaternionf()             // right rotation (identity)
            );
            display.setTransformation(transformation);
            
            // Set display properties
            display.setBrightness(new Display.Brightness(15, 15)); // Full brightness
            display.setShadowRadius(0); // No shadow
            display.setShadowStrength(0); // No shadow
            display.setPersistent(false); // Will despawn when chunk unloads
            
            return display;
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to create block display: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Creates the black hole particle effect.
     *
     * @param center The center location for the black hole
     * @param level The enchantment level
     * @return A BlackHole object representing the effect
     */
    private BlackHole createBlackHole(Location center, int level) {
        BlackHole blackHole = new BlackHole(center, level);
        blackHole.start();
        return blackHole;
    }
    
    /**
     * Starts the orbital animation for block displays.
     *
     * @param player The player who triggered the enchantment
     * @param target The target entity
     * @param center The center location
     * @param displays The block displays
     * @param blockDataList The original block data
     * @param originalBlocks The original blocks
     * @param blackHole The black hole effect
     * @param level The enchantment level
     */
    private void startOrbitalAnimation(Player player, LivingEntity target, Location center,
                                      List<BlockDisplay> displays, List<BlockData> blockDataList, 
                                      List<Block> originalBlocks, BlackHole blackHole, int level) {
        World world = center.getWorld();
        
        // Initial orbital parameters
        Map<BlockDisplay, OrbitalData> orbitalDataMap = new HashMap<>();
        
        // Assign random orbital parameters to each display
        for (BlockDisplay display : displays) {
            OrbitalData orbitalData = new OrbitalData();
            
            // Random initial position on a sphere
            double theta = random.nextDouble() * Math.PI * 2;
            double phi = random.nextDouble() * Math.PI;
            double radius = 3.0 + random.nextDouble() * 2.0;
            
            orbitalData.theta = theta;
            orbitalData.phi = phi;
            orbitalData.radius = radius;
            orbitalData.spinSpeed = 0.05 + (random.nextDouble() * 0.1);
            orbitalData.inwardSpeed = 0.03 + ((level - 1) * 0.01);
            
            orbitalDataMap.put(display, orbitalData);
        }
        
        // Start the orbital animation
        new BukkitRunnable() {
            int tick = 0;
            final int collapseDuration = 60; // 3 seconds of collapsing
            final int holdDuration = 20; // 1 second of holding in the black hole
            List<Entity> nearbyEntities = null;
            boolean projectilesLaunched = false;
            
            @Override
            public void run() {
                // Cancel if all displays are removed
                if (displays.isEmpty()) {
                    this.cancel();
                    return;
                }
                
                // Phase 1: Orbit and collapse into black hole
                if (tick < collapseDuration) {
                    updateOrbitalPhase(displays, orbitalDataMap, center, tick, collapseDuration);
                }
                // Phase 2: Hold in black hole
                else if (tick < collapseDuration + holdDuration) {
                    // Get nearby entities for targeting
                    if (nearbyEntities == null) {
                        nearbyEntities = getNearbyTargets(center, player, level);
                    }
                    
                    // Particles swirling intensely
                    for (int i = 0; i < 20; i++) {
                        double angle = random.nextDouble() * Math.PI * 2;
                        double radius = 0.2 + (random.nextDouble() * 0.3);
                        Location particleLoc = center.clone().add(
                            Math.cos(angle) * radius,
                            random.nextDouble() * 0.5 - 0.25,
                            Math.sin(angle) * radius
                        );
                        
                        world.spawnParticle(
                            Particle.SQUID_INK, 
                            particleLoc,
                            1, 
                            0, 0, 0, 
                            0
                        );
                    }
                }
                // Phase 3: Launch projectiles
                else if (!projectilesLaunched) {
                    launchBlockProjectiles(displays, center, nearbyEntities, level);
                    projectilesLaunched = true;
                }
                // Phase 4: Wait for projectiles to finish and clean up
                else if (tick > collapseDuration + holdDuration + 100) { // 5 seconds after launch
                    // Remove any remaining displays
                    for (BlockDisplay display : new ArrayList<>(displays)) {
                        display.remove();
                    }
                    displays.clear();
                    
                    // Stop the black hole effect
                    blackHole.stop();
                    
                    // Restore original blocks
                    for (int i = 0; i < originalBlocks.size() && i < blockDataList.size(); i++) {
                        // Only restore if the block is still air
                        if (originalBlocks.get(i).getType() == Material.AIR) {
                            originalBlocks.get(i).setBlockData(blockDataList.get(i));
                        }
                    }
                    
                    this.cancel();
                }
                
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
    
    /**
     * Updates the orbital phase of the animation.
     *
     * @param displays The block displays
     * @param orbitalDataMap The orbital data for each display
     * @param center The center location
     * @param tick The current tick
     * @param duration The total duration of this phase
     */
    private void updateOrbitalPhase(List<BlockDisplay> displays, Map<BlockDisplay, OrbitalData> orbitalDataMap,
                                   Location center, int tick, int duration) {
        World world = center.getWorld();
        
        // Phase progress from 0.0 to 1.0
        double progress = (double) tick / duration;
        
        // Process each display
        for (Iterator<BlockDisplay> iterator = displays.iterator(); iterator.hasNext();) {
            BlockDisplay display = iterator.next();
            
            // Skip if display was removed
            if (display == null || display.isDead()) {
                iterator.remove();
                continue;
            }
            
            OrbitalData data = orbitalDataMap.get(display);
            
            // Update orbital parameters
            data.theta += data.spinSpeed;
            data.radius -= data.inwardSpeed;
            
            // Calculate new position
            double x = Math.sin(data.phi) * Math.cos(data.theta) * data.radius;
            double y = Math.cos(data.phi) * data.radius;
            double z = Math.sin(data.phi) * Math.sin(data.theta) * data.radius;
            
            // Calculate rotation based on movement
            Vector3f position = new Vector3f((float) x, (float) y, (float) z);
            
            // Create transformation with decreasing scale
            float scale = 1.0f - (0.5f * (float) progress); // Scale from 1.0 to 0.5
            
            // Update display position and scale
            try {
                Transformation transformation = new Transformation(
                    position,
                    new Quaternionf().rotateY((float) data.theta).rotateX((float) data.phi), // Rotation to face center
                    new Vector3f(scale, scale, scale), // Shrinking scale
                    new Quaternionf()
                );
                
                display.setTransformation(transformation);
                
                // Update display position
                Location newLoc = center.clone().add(x, y, z);
                display.teleport(newLoc);
                
                // Add particle trail
                if (tick % 2 == 0) {
                    world.spawnParticle(
                        Particle.END_ROD,
                        newLoc,
                        1,
                        0, 0, 0,
                        0.02
                    );
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Error updating display: " + e.getMessage());
                iterator.remove();
            }
        }
    }
    
    /**
     * Gets nearby entities that can be targeted by the effect.
     *
     * @param center The center location
     * @param player The player who triggered the effect
     * @param level The enchantment level
     * @return A list of nearby entities
     */
    private List<Entity> getNearbyTargets(Location center, Player player, int level) {
        // Calculate targeting radius based on level
        double radius = 10.0 + (level * 5.0);
        
        return center.getWorld().getNearbyEntities(center, radius, radius, radius).stream()
            .filter(e -> e instanceof LivingEntity) // Only target living entities
            .filter(e -> e != player) // Don't target the player
            .filter(e -> !(e instanceof Player) || 
                          ((player.getWorld().getPVP() && !((Player) e).equals(player)))) // Only target players if PVP is enabled
            .collect(Collectors.toList());
    }
    
    /**
     * Launches blocks as projectiles toward nearby entities.
     *
     * @param displays The block displays to launch
     * @param center The center location
     * @param targets The potential targets
     * @param level The enchantment level
     */
    private void launchBlockProjectiles(List<BlockDisplay> displays, Location center, 
                                       List<Entity> targets, int level) {
        World world = center.getWorld();
        
        // Play launch sound
        world.playSound(center, Sound.ENTITY_WITHER_SHOOT, 1.0f, 0.5f);
        
        // Get displays and targets
        List<BlockDisplay> availableDisplays = new ArrayList<>(displays);
        List<Entity> availableTargets = new ArrayList<>(targets);
        
        // Randomize the order for more chaotic effect
        Collections.shuffle(availableDisplays);
        
        // Launch each block as a projectile
        for (BlockDisplay display : availableDisplays) {
            if (display == null || display.isDead()) {
                continue;
            }
            
            // Find a target or create random trajectory if no targets available
            Entity target = null;
            if (!availableTargets.isEmpty()) {
                target = availableTargets.remove(0); // Use each target once
            }
            
            // Start the projectile animation
            startProjectileAnimation(display, center, target, level);
        }
    }
    
    /**
     * Starts the projectile animation for a single block display.
     *
     * @param display The block display
     * @param center The center location
     * @param target The target entity (can be null)
     * @param level The enchantment level
     */
    private void startProjectileAnimation(BlockDisplay display, Location center, Entity target, int level) {
        World world = center.getWorld();
        
        // Initial position
        Location startLoc = display.getLocation();
        Vector direction;
        
        // Calculate direction - either toward target or random if no target
        if (target != null) {
            direction = target.getLocation().add(0, target.getHeight() / 2, 0)
                .subtract(startLoc).toVector().normalize();
        } else {
            // Random direction if no target
            double angleXZ = random.nextDouble() * Math.PI * 2;
            double angleY = random.nextDouble() * Math.PI - (Math.PI / 2); // -90 to +90 degrees
            
            direction = new Vector(
                Math.cos(angleXZ) * Math.cos(angleY),
                Math.sin(angleY),
                Math.sin(angleXZ) * Math.cos(angleY)
            ).normalize();
        }
        
        // Calculate speed based on level
        double speed = 0.3 + (level * 0.1); // 0.4 - 0.6 blocks per tick
        
        // Set additional info
        final Entity finalTarget = target;
        final double damage = BASE_DAMAGE + ((level - 1) * DAMAGE_PER_LEVEL);
        
        // Start animation
        new BukkitRunnable() {
            int tick = 0;
            final int maxTicks = 100; // 5 seconds max flight time
            boolean hitTarget = false;
            Vector currentVelocity = direction.clone().multiply(speed);
            
            @Override
            public void run() {
                // Cancel if display was removed or max time reached
                if (display == null || display.isDead() || tick >= maxTicks) {
                    this.cancel();
                    return;
                }
                
                // Update position based on velocity
                Location currentLoc = display.getLocation();
                Location nextLoc = currentLoc.clone().add(currentVelocity);
                
                // Check for collision with world
                if (!nextLoc.getBlock().isPassable()) {
                    // Hit a block - create impact
                    createImpactEffect(nextLoc, level);
                    display.remove();
                    this.cancel();
                    return;
                }
                
                // Check for target hit
                if (finalTarget != null && !hitTarget) {
                    // As projectile gets closer to target, increase its size for dramatic effect
                    double distanceToTarget = nextLoc.distance(finalTarget.getLocation());
                    
                    // Adjust trajectory slightly to home in on target
                    if (distanceToTarget > 1.0 && tick % 5 == 0) {
                        // Home in on target
                        Vector targetDir = finalTarget.getLocation().add(0, finalTarget.getHeight() / 2, 0)
                            .subtract(nextLoc).toVector().normalize().multiply(speed);
                        
                        // Blend current velocity with target direction
                        currentVelocity = currentVelocity.multiply(0.8).add(targetDir.multiply(0.2));
                        currentVelocity.normalize().multiply(speed);
                    }
                    
                    // Check if we hit the target
                    if (distanceToTarget < 1.0) {
                        hitTarget = true;
                        
                        // Create impact effect
                        createImpactEffect(finalTarget.getLocation(), level);
                        
                        // Apply effect
                        if (finalTarget instanceof LivingEntity) {
                            LivingEntity livingTarget = (LivingEntity) finalTarget;
                            
                            // Apply damage
                            livingTarget.damage(damage);
                            
                            // Apply knockback
                            livingTarget.setVelocity(direction.clone().multiply(0.5));
                            
                            // Apply visual effect
                            world.spawnParticle(
                                Particle.DRAGON_BREATH,
                                livingTarget.getLocation().add(0, livingTarget.getHeight() / 2, 0),
                                30,
                                0.5, 0.5, 0.5,
                                0.05
                            );
                            
                            // Play sound
                            world.playSound(livingTarget.getLocation(), Sound.ENTITY_WITHER_BREAK_BLOCK, 1.0f, 1.2f);
                        }
                        
                        // Remove the display
                        display.remove();
                        this.cancel();
                        return;
                    }
                }
                
                // Update display position
                display.teleport(nextLoc);
                
                // Create transformation for rotation effect
                float rotationSpeed = 0.2f;
                
                try {
                    Transformation transformation = display.getTransformation();
                    
                    // Create rotation based on direction
                    Quaternionf rotation = new Quaternionf();
                    rotation.rotateAxis(tick * rotationSpeed, 
                                        (float)direction.getX(), 
                                        (float)direction.getY(), 
                                        (float)direction.getZ());
                    
                    // Calculate scale that increases over time
                    float scale = 0.5f + (tick * 0.01f); // Starts at 0.5, grows slowly
                    if (scale > 1.5f) scale = 1.5f; // Cap at 1.5
                    
                    // Create new transformation
                    Transformation newTransformation = new Transformation(
                        transformation.getTranslation(),
                        rotation,
                        new Vector3f(scale, scale, scale),
                        transformation.getRightRotation()
                    );
                    
                    display.setTransformation(newTransformation);
                } catch (Exception e) {
                    plugin.getLogger().warning("Error updating display transformation: " + e.getMessage());
                }
                
                // Create trailing particles
                createProjectileTrail(nextLoc);
                
                tick++;
            }
            
            /**
             * Creates a trail of particles behind the projectile.
             *
             * @param location The current location of the projectile
             */
            private void createProjectileTrail(Location location) {
                // Main trail particles
                world.spawnParticle(
                    Particle.DRAGON_BREATH,
                    location,
                    3,
                    0.1, 0.1, 0.1,
                    0.01
                );
                
                // Additional ring effect
                if (tick % 3 == 0) {
                    double radius = 0.3;
                    int points = 8;
                    
                    for (int i = 0; i < points; i++) {
                        double angle = (2 * Math.PI * i) / points;
                        Vector offset = new Vector(
                            Math.cos(angle) * radius,
                            Math.sin(angle) * radius,
                            0
                        ).rotateAroundAxis(direction, tick * 0.2);
                        
                        Location particleLoc = location.clone().add(offset);
                        
                        world.spawnParticle(
                            Particle.END_ROD,
                            particleLoc,
                            1,
                            0, 0, 0,
                            0
                        );
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
    
    /**
     * Creates an impact effect at the specified location.
     *
     * @param location The impact location
     * @param level The enchantment level
     */
    private void createImpactEffect(Location location, int level) {
        World world = location.getWorld();
        
        // Explosion particle
        world.spawnParticle(
            Particle.EXPLOSION_EMITTER,
            location,
            1,
            0, 0, 0,
            0
        );
        
        // Dust particles
        world.spawnParticle(
            Particle.CLOUD,
            location,
            20,
            0.5, 0.5, 0.5,
            0.2
        );
        
        // Purple impact wave
        double radius = 1.0 + (level * 0.5);
        int points = 20;
        
        for (int i = 0; i < points; i++) {
            double angle = (2 * Math.PI * i) / points;
            
            for (double r = 0.5; r <= radius; r += 0.5) {
                double x = Math.cos(angle) * r;
                double z = Math.sin(angle) * r;
                
                Location particleLoc = location.clone().add(x, 0.1, z);
                
                world.spawnParticle(
                    Particle.DRAGON_BREATH,
                    particleLoc,
                    1,
                    0, 0, 0,
                    0
                );
            }
        }
        
        // Sound effect
        world.playSound(location, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.8f);
    }
    
    /**
     * Inner class representing orbital data for a block.
     */
    private class OrbitalData {
        double theta;      // Angle in XZ plane
        double phi;        // Angle from Y axis
        double radius;     // Distance from center
        double spinSpeed;  // How fast it orbits
        double inwardSpeed; // How fast it moves inward
    }
    
    /**
     * Inner class representing the black hole effect.
     */
    private class BlackHole {
        private final Location center;
        private final int level;
        private BukkitTask task;
        
        public BlackHole(Location center, int level) {
            this.center = center;
            this.level = level;
        }
        
        /**
         * Starts the black hole particle effect.
         */
        public void start() {
            task = new BukkitRunnable() {
                int tick = 0;
                
                @Override
                public void run() {
                    World world = center.getWorld();
                    
                    // Calculate size based on level and time
                    double baseSize = 0.5 + (level * 0.5); // 1.0 - 2.0 based on level
                    double pulseSize = baseSize + (0.2 * Math.sin(tick * 0.1));
                    
                    // Create dense black particle sphere
                    int particles = 30 + (level * 10);
                    for (int i = 0; i < particles; i++) {
                        // Random point on sphere
                        double theta = random.nextDouble() * Math.PI * 2;
                        double phi = random.nextDouble() * Math.PI;
                        
                        double radius = pulseSize * (0.8 + random.nextDouble() * 0.2);
                        
                        double x = Math.sin(phi) * Math.cos(theta) * radius;
                        double y = Math.cos(phi) * radius;
                        double z = Math.sin(phi) * Math.sin(theta) * radius;
                        
                        Location particleLoc = center.clone().add(x, y, z);
                        
                        // Black particles
                        world.spawnParticle(
                            Particle.SQUID_INK, 
                            particleLoc,
                            1, 
                            0, 0, 0, 
                            0
                        );
                    }
                    
                    // Add some purple particles for effect
                    if (tick % 5 == 0) {
                        for (int i = 0; i < 10; i++) {
                            double angle = random.nextDouble() * Math.PI * 2;
                            double radius = pulseSize * 1.2;
                            Location particleLoc = center.clone().add(
                                Math.cos(angle) * radius,
                                (random.nextDouble() * 2 - 1) * radius,
                                Math.sin(angle) * radius
                            );
                            
                            world.spawnParticle(
                                Particle.DRAGON_BREATH,
                                particleLoc,
                                1,
                                0, 0, 0,
                                0.02
                            );
                        }
                    }
                    
                    // Add ambient sound
                    if (tick % 20 == 0) {
                        world.playSound(center, Sound.BLOCK_BEACON_AMBIENT, 0.5f, 0.5f);
                    }
                    
                    tick++;
                }
            }.runTaskTimer(plugin, 0L, 1L);
        }
        
        /**
         * Stops the black hole effect.
         */
        public void stop() {
            if (task != null && !task.isCancelled()) {
                task.cancel();
            }
            
            // Create final explosion effect
            center.getWorld().spawnParticle(
                Particle.EXPLOSION_EMITTER,
                center,
                1,
                0, 0, 0,
                0
            );
            
            // Play explosion sound
            center.getWorld().playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.7f);
        }
    }
    
    /**
     * Cleans up old cooldown entries.
     */
    private void cleanupOldEntries() {
        long currentTime = System.currentTimeMillis();
        lastProcTime.entrySet().removeIf(entry -> 
            currentTime - entry.getValue() > COOLDOWN_SECONDS * 1000);
    }
    
    @Override
    public boolean conflictsWith(CustomEnchant other) {
        return other.getName().equalsIgnoreCase("SingularityVortex") ||
               other.getName().equalsIgnoreCase("DimensionalRift") ||
               other.getName().equalsIgnoreCase("GravityWell");
    }
} 