package org.frizzlenpop.frizzlenChants.impl;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.frizzlenpop.frizzlenChants.FrizzlenChants;
import org.frizzlenpop.frizzlenChants.enchant.CustomEnchant;
import org.frizzlenpop.frizzlenChants.enchant.EnchantmentUtils;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Voidwalker enchantment that gives the player a chance to teleport
 * away when hit, granting brief invulnerability.
 */
public class VoidwalkerEnchant extends CustomEnchant {

    private final FrizzlenChants plugin;
    private final Random random = new Random();
    private final Set<UUID> recentlyTeleported = new HashSet<>();
    
    // Constants
    private static final int COOLDOWN_TICKS = 300; // 15 seconds cooldown
    private static final int INVULNERABILITY_TICKS = 20; // 1 second of invulnerability
    
    public VoidwalkerEnchant() {
        super("Voidwalker", 3, 
              Material.LEATHER_CHESTPLATE, Material.CHAINMAIL_CHESTPLATE, 
              Material.IRON_CHESTPLATE, Material.GOLDEN_CHESTPLATE, 
              Material.DIAMOND_CHESTPLATE, Material.NETHERITE_CHESTPLATE);
        this.plugin = FrizzlenChants.getPlugin(FrizzlenChants.class);
    }

    @Override
    public void applyEffect(Player player, ItemStack item, int level) {
        // Implementation handled in the event handler below
    }
    
    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        // Only process if a player is being damaged
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        
        Player player = (Player) event.getEntity();
        
        // Check if player recently teleported (still has invulnerability)
        if (recentlyTeleported.contains(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        
        // Check if the player is wearing a chestplate with this enchantment
        ItemStack chestplate = player.getInventory().getChestplate();
        
        if (chestplate == null || !chestplate.hasItemMeta()) {
            return;
        }
        
        // Check if the chestplate has this enchantment
        int level = EnchantmentUtils.getEnchantmentLevel(chestplate, this, plugin.getEnchantmentRegistry());
        
        if (level <= 0) {
            return;
        }
        
        // Calculate chance to trigger based on level (15%, 20%, 25%)
        int triggerChance = 10 + (level * 5);
        
        // Roll for teleport
        if (random.nextInt(100) < triggerChance) {
            // Cancel the current damage event
            event.setCancelled(true);
            
            // Find a safe location to teleport to
            Location safeLocation = findSafeLocation(player, level);
            
            if (safeLocation != null) {
                // Teleport the player
                teleportWithEffects(player, safeLocation, level);
            } else {
                // If no safe location found, just apply damage reduction
                event.setDamage(event.getDamage() * 0.5);
                player.sendActionBar(net.kyori.adventure.text.Component.text("§8Voidwalker §7failed to find safe location"));
            }
        }
    }
    
    /**
     * Finds a safe location to teleport the player to.
     *
     * @param player The player to teleport
     * @param level The enchantment level
     * @return A safe location, or null if none found
     */
    private Location findSafeLocation(Player player, int level) {
        Location origin = player.getLocation();
        World world = origin.getWorld();
        
        // Calculate teleport distance based on level (4-8 blocks)
        int teleportDistance = 4 + (level * 2);
        
        // Try multiple angles to find a safe spot
        for (int attempt = 0; attempt < 12; attempt++) {
            // Get a random angle around the player
            double angle = random.nextDouble() * 2 * Math.PI;
            
            // Calculate target position
            double x = origin.getX() + Math.cos(angle) * teleportDistance;
            double z = origin.getZ() + Math.sin(angle) * teleportDistance;
            
            // Find a safe Y value
            Location targetLoc = new Location(world, x, origin.getY(), z);
            Location safeLoc = findSafeYValue(targetLoc, world);
            
            if (safeLoc != null) {
                // Preserve the player's original yaw and pitch
                safeLoc.setYaw(origin.getYaw());
                safeLoc.setPitch(origin.getPitch());
                return safeLoc;
            }
        }
        
        return null; // No safe location found
    }
    
    /**
     * Finds a safe Y value at the given XZ coordinates.
     *
     * @param location The XZ coordinates to check
     * @param world The world
     * @return A safe location, or null if none found
     */
    private Location findSafeYValue(Location location, World world) {
        int x = location.getBlockX();
        int startY = location.getBlockY();
        int z = location.getBlockZ();
        
        // Check 10 blocks up and down from the original Y value
        for (int yOffset = 0; yOffset <= 10; yOffset++) {
            // Check above first, then below
            for (int sign : new int[]{1, -1}) {
                int y = startY + (yOffset * sign);
                
                if (y < 0 || y > world.getMaxHeight() - 2) {
                    continue;
                }
                
                Block block = world.getBlockAt(x, y, z);
                Block above1 = world.getBlockAt(x, y + 1, z);
                Block above2 = world.getBlockAt(x, y + 2, z);
                Block below = world.getBlockAt(x, y - 1, z);
                
                // Check if there are 2 empty blocks for the player and a solid block below
                if (isSafe(block) && isSafe(above1) && isSafe(above2) && isSolid(below)) {
                    return new Location(world, x + 0.5, y, z + 0.5);
                }
            }
        }
        
        return null; // No safe Y value found
    }
    
    /**
     * Checks if a block is safe for a player to teleport to.
     *
     * @param block The block to check
     * @return true if the block is safe, false otherwise
     */
    private boolean isSafe(Block block) {
        return block.isPassable() && 
               !block.isLiquid() && 
               block.getType() != Material.LAVA && 
               block.getType() != Material.FIRE;
    }
    
    /**
     * Checks if a block is solid and can be stood on.
     *
     * @param block The block to check
     * @return true if the block is solid, false otherwise
     */
    private boolean isSolid(Block block) {
        return block.getType().isSolid() && 
               block.getType() != Material.LAVA && 
               block.getType() != Material.FIRE;
    }
    
    /**
     * Teleports the player with visual and sound effects.
     *
     * @param player The player to teleport
     * @param destination The location to teleport to
     * @param level The enchantment level
     */
    private void teleportWithEffects(Player player, Location destination, int level) {
        Location origin = player.getLocation();
        
        // Apply pre-teleport effects
        createVoidEffect(origin, level, false);
        player.getWorld().playSound(origin, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.5f);
        
        // Teleport player
        player.teleport(destination);
        
        // Apply post-teleport effects
        createVoidEffect(destination, level, true);
        player.getWorld().playSound(destination, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.7f);
        
        // Apply brief invulnerability
        UUID playerId = player.getUniqueId();
        recentlyTeleported.add(playerId);
        
        // Show message to player
        player.sendActionBar(net.kyori.adventure.text.Component.text("§8Voidwalker §7teleport activated!"));
        
        // Remove invulnerability after a delay
        new BukkitRunnable() {
            @Override
            public void run() {
                recentlyTeleported.remove(playerId);
            }
        }.runTaskLater(plugin, INVULNERABILITY_TICKS);
    }
    
    /**
     * Creates a void-like particle effect.
     *
     * @param location The location to create the effect at
     * @param level The enchantment level
     * @param isDestination Whether this is the destination effect
     */
    private void createVoidEffect(Location location, int level, boolean isDestination) {
        // Create base effect color
        Color voidColor;
        switch (level) {
            case 1:
                voidColor = Color.fromRGB(100, 0, 100); // Purple
                break;
            case 2:
                voidColor = Color.fromRGB(50, 0, 80); // Dark purple
                break;
            case 3:
                voidColor = Color.fromRGB(20, 0, 50); // Very dark purple
                break;
            default:
                voidColor = Color.fromRGB(100, 0, 100);
        }
        
        // Create void particle effect (more particles at destination)
        int particleCount = isDestination ? 50 + (level * 15) : 30 + (level * 10);
        
        // Spiral particles
        for (int i = 0; i < particleCount; i++) {
            double angle = (double) i / particleCount * Math.PI * 4; // Two full circles
            double radius = isDestination ? 0.2 + (i * 0.05) : 1.0 - (i * 0.02);
            radius = Math.max(0.1, Math.min(radius, 1.5));
            
            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;
            double y = isDestination ? (i * 0.05) : (1.0 - (i * 0.03));
            
            Location particleLoc = location.clone().add(x, y, z);
            
            // Use dust particles for void effect
            plugin.getVisualEffectManager().spawnColoredParticles(
                particleLoc,
                Particle.DUST,
                voidColor,
                1.0f,
                1
            );
            
            // Add end rod particles for contrast
            if (i % 5 == 0) {
                location.getWorld().spawnParticle(
                    Particle.END_ROD,
                    particleLoc,
                    1,
                    0, 0, 0,
                    0.01
                );
            }
        }
        
        // Add some extra particles for higher levels
        if (level >= 2) {
            location.getWorld().spawnParticle(
                Particle.PORTAL,
                location.clone().add(0, 1, 0),
                20 * level,
                0.5, 1.0, 0.5,
                0.1
            );
        }
    }
    
    @Override
    public boolean conflictsWith(CustomEnchant other) {
        // Conflicts with other teleportation/evasion enchantments
        return other.getName().toLowerCase().contains("void") ||
               other.getName().toLowerCase().contains("teleport") ||
               other.getName().toLowerCase().contains("blink") ||
               other.getName().toLowerCase().contains("phase");
    }
} 