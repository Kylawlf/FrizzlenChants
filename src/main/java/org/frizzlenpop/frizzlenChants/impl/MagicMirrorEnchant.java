package org.frizzlenpop.frizzlenChants.impl;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.frizzlenpop.frizzlenChants.FrizzlenChants;
import org.frizzlenpop.frizzlenChants.enchant.CustomEnchant;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Magic Mirror Enchantment
 * 
 * Allows players to set a home location with left-click and teleport to it with right-click.
 * Different enchantment levels allow for teleportation from greater distances and across dimensions.
 * 
 * Features:
 * - Left-click with an enchanted ender pearl to set your home location
 * - Right-click to teleport back to that location
 * - Level restrictions:
 *   - Level 1: Teleport from up to 1,000 blocks away
 *   - Level 2: Teleport from up to 5,000 blocks away
 *   - Level 3: Teleport from any distance and across dimensions
 * - 5-minute (300 second) cooldown between uses
 * - Visual and sound effects for setting home and teleporting
 * 
 * Usage:
 * Apply with: /customenchant MagicMirror 1-3
 * 
 * This enchantment can only be applied to ender pearls. The home location is stored
 * in the specific ender pearl item's persistent data container, so each enchanted pearl
 * can have its own unique home location.
 */
public class MagicMirrorEnchant extends CustomEnchant {

    private final FrizzlenChants plugin;
    
    // Constants
    private static final int MAX_LEVEL = 3;
    private static final long COOLDOWN_SECONDS = 300; // 5 minutes cooldown
    private static final int LEVEL_1_DISTANCE = 1000; // 1000 blocks
    private static final int LEVEL_2_DISTANCE = 5000; // 5000 blocks
    private static final boolean DEBUG_MODE = true; // Set to true for additional debug output
    
    // Keys for storing location data
    private final NamespacedKey homeWorldKey;
    private final NamespacedKey homeXKey;
    private final NamespacedKey homeYKey;
    private final NamespacedKey homeZKey;
    private final NamespacedKey homePitchKey;
    private final NamespacedKey homeYawKey;
    
    // Track player cooldowns
    private final Map<UUID, Long> lastUseTimes = new HashMap<>();
    
    /**
     * Constructor for Magic Mirror enchantment.
     */
    public MagicMirrorEnchant() {
        super("MagicMirror", MAX_LEVEL, Material.ENDER_PEARL);
        this.plugin = FrizzlenChants.getPlugin(FrizzlenChants.class);
        
        // Initialize keys for persistent data
        this.homeWorldKey = new NamespacedKey(plugin, "magic_mirror_world");
        this.homeXKey = new NamespacedKey(plugin, "magic_mirror_x");
        this.homeYKey = new NamespacedKey(plugin, "magic_mirror_y");
        this.homeZKey = new NamespacedKey(plugin, "magic_mirror_z");
        this.homePitchKey = new NamespacedKey(plugin, "magic_mirror_pitch");
        this.homeYawKey = new NamespacedKey(plugin, "magic_mirror_yaw");
    }
    
    @Override
    public void applyEffect(Player player, ItemStack item, int level) {
        // This enchant is applied through the PlayerInteractEvent instead
    }
    
    /**
     * Handle player interaction with an ender pearl enchanted with Magic Mirror.
     */
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        
        // Check if the player is holding an ender pearl
        if (item == null || item.getType() != Material.ENDER_PEARL) {
            return;
        }
        
        if (DEBUG_MODE) {
            plugin.getLogger().info("Player " + player.getName() + " interacted with ender pearl. Action: " + event.getAction());
        }
        
        // Check if the item has the Magic Mirror enchantment
        int level = getEnchantmentLevel(item);
        if (level <= 0) {
            if (DEBUG_MODE) {
                plugin.getLogger().info("Ender pearl does not have Magic Mirror enchantment");
            }
            return;
        }
        
        if (DEBUG_MODE) {
            plugin.getLogger().info("Found Magic Mirror enchantment level " + level + " on ender pearl");
        }
        
        // Process based on the action
        if (event.getAction() == Action.LEFT_CLICK_AIR || event.getAction() == Action.LEFT_CLICK_BLOCK) {
            if (DEBUG_MODE) {
                plugin.getLogger().info("Player left-clicked with enchanted pearl");
            }
            // Set home location
            setHomeLocation(player, item);
            event.setCancelled(true);
        } else if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            if (DEBUG_MODE) {
                plugin.getLogger().info("Player right-clicked with enchanted pearl");
            }
            // Teleport to home location
            teleportToHome(player, item, level);
            event.setCancelled(true);
        }
    }
    
    /**
     * Get the level of the Magic Mirror enchantment on the item.
     * 
     * @param item The item to check
     * @return The enchantment level, or 0 if not enchanted
     */
    private int getEnchantmentLevel(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return 0;
        }
        
        // Try using PDC first (recommended method)
        try {
            PersistentDataContainer container = item.getItemMeta().getPersistentDataContainer();
            String enchantKey = "enchant_" + getName().toLowerCase();
            NamespacedKey key = new NamespacedKey(plugin, enchantKey);
            
            if (container.has(key, PersistentDataType.INTEGER)) {
                int level = container.get(key, PersistentDataType.INTEGER);
                plugin.getLogger().info("Found enchantment " + getName() + " level " + level + " on item using PDC");
                return level;
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Error checking enchantment level via PDC: " + e.getMessage());
        }
        
        // Fallback method - check lore
        if (item.hasItemMeta() && item.getItemMeta().hasLore()) {
            List<String> lore = item.getItemMeta().getLore();
            String enchantPrefix = ChatColor.GRAY + getName() + " ";
            
            for (String line : lore) {
                if (line.startsWith(enchantPrefix)) {
                    // Extract Roman numeral
                    String roman = line.substring(enchantPrefix.length());
                    int level = romanToInt(roman.trim());
                    plugin.getLogger().info("Found enchantment " + getName() + " level " + level + " on item using lore");
                    return level;
                }
            }
        }
        
        return 0;
    }
    
    /**
     * Convert a Roman numeral to an integer.
     * 
     * @param roman The Roman numeral
     * @return The integer value
     */
    private int romanToInt(String roman) {
        if (roman.isEmpty()) return 0;
        
        Map<Character, Integer> romanValues = new HashMap<>();
        romanValues.put('I', 1);
        romanValues.put('V', 5);
        romanValues.put('X', 10);
        romanValues.put('L', 50);
        romanValues.put('C', 100);
        romanValues.put('D', 500);
        romanValues.put('M', 1000);
        
        int result = 0;
        int prevValue = 0;
        
        for (int i = roman.length() - 1; i >= 0; i--) {
            char c = roman.charAt(i);
            int value = romanValues.getOrDefault(c, 0);
            
            if (value >= prevValue) {
                result += value;
            } else {
                result -= value;
            }
            
            prevValue = value;
        }
        
        return result;
    }
    
    /**
     * Set home location for a player.
     * 
     * @param player The player
     * @param item The enchanted ender pearl
     */
    private void setHomeLocation(Player player, ItemStack item) {
        Location location = player.getLocation();
        
        try {
            // Get the item's meta and store location in the persistent data container
            if (item.hasItemMeta()) {
                // Clone the item meta to avoid modifying the original
                org.bukkit.inventory.meta.ItemMeta itemMeta = item.getItemMeta().clone();
                PersistentDataContainer container = itemMeta.getPersistentDataContainer();
                
                // Store world name, coordinates, pitch, and yaw
                container.set(homeWorldKey, PersistentDataType.STRING, location.getWorld().getName());
                container.set(homeXKey, PersistentDataType.DOUBLE, location.getX());
                container.set(homeYKey, PersistentDataType.DOUBLE, location.getY());
                container.set(homeZKey, PersistentDataType.DOUBLE, location.getZ());
                container.set(homePitchKey, PersistentDataType.FLOAT, location.getPitch());
                container.set(homeYawKey, PersistentDataType.FLOAT, location.getYaw());
                
                // Update the item with the new metadata
                item.setItemMeta(itemMeta);
                
                // Add lore to indicate that a home location is set
                updateItemLore(player, item, location);
                
                // Visual and sound effects
                player.getWorld().playSound(location, Sound.BLOCK_RESPAWN_ANCHOR_SET_SPAWN, 1.0f, 1.0f);
                player.getWorld().playSound(location, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.5f);
                
                // Create particle effects
                createHomeSetEffects(location);
                
                // Send message to player
                player.sendMessage(ChatColor.LIGHT_PURPLE + "Home location set to " + 
                        ChatColor.GOLD + String.format("%.1f, %.1f, %.1f", location.getX(), location.getY(), location.getZ()) + 
                        ChatColor.LIGHT_PURPLE + " in " + 
                        ChatColor.GOLD + location.getWorld().getName() + 
                        ChatColor.LIGHT_PURPLE + "! Right-click to return here.");
                
                // Show action bar message for better visibility
                player.spigot().sendMessage(
                    net.md_5.bungee.api.ChatMessageType.ACTION_BAR, 
                    net.md_5.bungee.api.chat.TextComponent.fromLegacyText(
                        ChatColor.LIGHT_PURPLE + "✧ " + ChatColor.WHITE + "Home Point Set" + ChatColor.LIGHT_PURPLE + " ✧"
                    )
                );
                
                if (DEBUG_MODE) {
                    plugin.getLogger().info("Successfully set home location for " + player.getName() + 
                            " at " + location.getWorld().getName() + ": " + 
                            location.getX() + ", " + location.getY() + ", " + location.getZ());
                }
            } else {
                player.sendMessage(ChatColor.RED + "Failed to set home location: Item metadata is missing.");
            }
        } catch (Exception e) {
            player.sendMessage(ChatColor.RED + "Failed to set home location: " + e.getMessage());
            plugin.getLogger().warning("Error setting home location: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Creates particle effects when setting a home location.
     * 
     * @param location The home location
     */
    private void createHomeSetEffects(Location location) {
        World world = location.getWorld();
        
        // Create a spiral effect rising from the ground
        new BukkitRunnable() {
            double angle = 0;
            double y = 0;
            final double yMax = 2.5;
            
            @Override
            public void run() {
                if (y >= yMax) {
                    cancel();
                    return;
                }
                
                // Create spiral
                for (int i = 0; i < 2; i++) {
                    double offsetAngle = angle + (Math.PI * i);
                    double radius = 1.0 * (1 - (y / yMax)); // Decreasing radius as it goes up
                    double x = Math.cos(offsetAngle) * radius;
                    double z = Math.sin(offsetAngle) * radius;
                    
                    Location particleLoc = location.clone().add(x, y, z);
                    world.spawnParticle(Particle.END_ROD, particleLoc, 1, 0, 0, 0, 0);
                }
                
                angle += Math.PI / 8;
                y += 0.1;
            }
        }.runTaskTimer(plugin, 0L, 1L);
        
        // Create a burst of particles at the center
        world.spawnParticle(Particle.PORTAL, location.clone().add(0, 1, 0), 50, 0.5, 0.5, 0.5, 0.1);
    }
    
    /**
     * Updates the item's lore to show that a home location is set.
     * 
     * @param player The player
     * @param item The enchanted ender pearl
     * @param location The set home location
     */
    private void updateItemLore(Player player, ItemStack item, Location location) {
        if (item.hasItemMeta()) {
            org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
            List<String> lore = meta.hasLore() ? meta.getLore() : new ArrayList<>();
            
            // Remove any existing home location lore
            lore.removeIf(line -> line.contains("Home Location:"));
            
            // Add new home location information
            lore.add(ChatColor.GRAY + "Home Location: " + 
                    ChatColor.WHITE + String.format("%.1f, %.1f, %.1f", location.getX(), location.getY(), location.getZ()) +
                    ChatColor.GRAY + " in " + ChatColor.WHITE + location.getWorld().getName());
            
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
    }
    
    /**
     * Teleport a player to their set home location.
     * 
     * @param player The player
     * @param item The enchanted ender pearl
     * @param level The enchantment level
     */
    private void teleportToHome(Player player, ItemStack item, int level) {
        // Check cooldown
        if (!checkCooldown(player)) {
            return;
        }
        
        try {
            // Get the item's persistent data container
            if (!item.hasItemMeta()) {
                player.sendMessage(ChatColor.RED + "This pearl does not have any saved locations.");
                return;
            }
            
            PersistentDataContainer container = item.getItemMeta().getPersistentDataContainer();
            
            // Check if home location is set
            if (!container.has(homeWorldKey, PersistentDataType.STRING)) {
                player.sendMessage(ChatColor.RED + "You haven't set a home location yet! Left-click to set one.");
                return;
            }
            
            // Get the home location data
            String worldName = container.get(homeWorldKey, PersistentDataType.STRING);
            double x = container.get(homeXKey, PersistentDataType.DOUBLE);
            double y = container.get(homeYKey, PersistentDataType.DOUBLE);
            double z = container.get(homeZKey, PersistentDataType.DOUBLE);
            float pitch = container.get(homePitchKey, PersistentDataType.FLOAT);
            float yaw = container.get(homeYawKey, PersistentDataType.FLOAT);
            
            if (DEBUG_MODE) {
                plugin.getLogger().info("Attempting to teleport player " + player.getName() + 
                        " to: " + worldName + " at " + x + ", " + y + ", " + z);
            }
            
            // Get the home world
            World homeWorld = plugin.getServer().getWorld(worldName);
            if (homeWorld == null) {
                player.sendMessage(ChatColor.RED + "The destination world no longer exists!");
                return;
            }
            
            // Check if the current location is valid for teleportation based on level
            if (!isValidTeleportDistance(player, homeWorld, x, y, z, level)) {
                return;
            }
            
            // Create the home location
            Location homeLocation = new Location(homeWorld, x, y, z, yaw, pitch);
            
            // Set cooldown
            lastUseTimes.put(player.getUniqueId(), System.currentTimeMillis());
            
            // Visual and sound effects before teleport
            player.getWorld().playSound(player.getLocation(), Sound.BLOCK_PORTAL_TRIGGER, 0.5f, 1.5f);
            createTeleportEffects(player.getLocation(), true);
            
            // Teleport player
            player.teleport(homeLocation);
            
            // Visual and sound effects after teleport
            homeWorld.playSound(homeLocation, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
            homeWorld.playSound(homeLocation, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 0.5f);
            createTeleportEffects(homeLocation, false);
            
            // Display cooldown message
            player.sendMessage(ChatColor.LIGHT_PURPLE + "Teleported to home location! Cooldown: " 
                    + ChatColor.GOLD + COOLDOWN_SECONDS + ChatColor.LIGHT_PURPLE + " seconds.");
                    
            // Show action bar message for better visibility
            player.spigot().sendMessage(
                net.md_5.bungee.api.ChatMessageType.ACTION_BAR, 
                net.md_5.bungee.api.chat.TextComponent.fromLegacyText(
                    ChatColor.LIGHT_PURPLE + "✧ " + ChatColor.WHITE + "Home Teleport Successful" + ChatColor.LIGHT_PURPLE + " ✧"
                )
            );
            
            if (DEBUG_MODE) {
                plugin.getLogger().info("Successfully teleported " + player.getName() + 
                        " to " + worldName + ": " + x + ", " + y + ", " + z);
            }
            
            // Consume one ender pearl if the server has configured it that way
            if (plugin.getConfig().getBoolean("magic_mirror.consume_pearl", true)) {
                consumeEnderPearl(player, item);
            }
        } catch (Exception e) {
            player.sendMessage(ChatColor.RED + "Failed to teleport: " + e.getMessage());
            plugin.getLogger().warning("Error teleporting player: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Creates particle effects for teleportation.
     * 
     * @param location The teleport location
     * @param isDeparture Whether this is the departure location
     */
    private void createTeleportEffects(Location location, boolean isDeparture) {
        World world = location.getWorld();
        
        // Create implosion/explosion effect
        new BukkitRunnable() {
            int tick = 0;
            final int maxTicks = 20;
            
            @Override
            public void run() {
                if (tick >= maxTicks) {
                    cancel();
                    return;
                }
                
                double progress = (double) tick / maxTicks;
                double radius = isDeparture ? 
                        1.5 * (1 - progress) : // Shrinking for departure
                        1.5 * progress;         // Expanding for arrival
                
                // Create circle of particles
                for (int i = 0; i < 8; i++) {
                    double angle = (2 * Math.PI * i / 8) + (progress * Math.PI);
                    double x = Math.cos(angle) * radius;
                    double z = Math.sin(angle) * radius;
                    
                    Location particleLoc = location.clone().add(x, 0.1, z);
                    
                    // Different particle types for departure and arrival
                    if (isDeparture) {
                        world.spawnParticle(Particle.PORTAL, particleLoc, 1, 0, 0, 0, 0);
                    } else {
                        world.spawnParticle(Particle.END_ROD, particleLoc, 1, 0, 0, 0, 0);
                    }
                }
                
                // Add central column
                if (tick % 2 == 0) {
                    Location centerLoc = location.clone().add(0, 1 + (isDeparture ? (1 - progress) : progress), 0);
                    world.spawnParticle(
                        isDeparture ? Particle.REVERSE_PORTAL : Particle.PORTAL, 
                        centerLoc, 
                        5, 0.2, 0.2, 0.2, 0.05
                    );
                }
                
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
    
    /**
     * Check if teleportation is valid based on distance and dimension restrictions.
     * 
     * @param player The player
     * @param homeWorld The home world
     * @param x X coordinate
     * @param y Y coordinate
     * @param z Z coordinate
     * @param level Enchantment level
     * @return True if teleportation is valid
     */
    private boolean isValidTeleportDistance(Player player, World homeWorld, double x, double y, double z, int level) {
        Location currentLoc = player.getLocation();
        World currentWorld = currentLoc.getWorld();
        
        // Check for cross-dimension teleportation
        if (!currentWorld.equals(homeWorld)) {
            // Level 3 can teleport across dimensions
            if (level < 3) {
                player.sendMessage(ChatColor.RED + "Your Magic Mirror (Level " + level + 
                        ") cannot teleport across dimensions! You need Level 3.");
                return false;
            }
        } else {
            // Check for distance limitations within the same dimension
            double distance = Math.sqrt(
                Math.pow(currentLoc.getX() - x, 2) +
                Math.pow(currentLoc.getY() - y, 2) +
                Math.pow(currentLoc.getZ() - z, 2)
            );
            
            if (level == 1 && distance > LEVEL_1_DISTANCE) {
                player.sendMessage(ChatColor.RED + "Your Magic Mirror (Level 1) cannot teleport from distances greater than " 
                        + LEVEL_1_DISTANCE + " blocks! Current distance: " + (int)distance + " blocks.");
                return false;
            } else if (level == 2 && distance > LEVEL_2_DISTANCE) {
                player.sendMessage(ChatColor.RED + "Your Magic Mirror (Level 2) cannot teleport from distances greater than " 
                        + LEVEL_2_DISTANCE + " blocks! Current distance: " + (int)distance + " blocks.");
                return false;
            }
            // Level 3 has no distance limitation
        }
        
        return true;
    }
    
    /**
     * Check if the player is on cooldown.
     * 
     * @param player The player
     * @return True if not on cooldown
     */
    private boolean checkCooldown(Player player) {
        UUID playerId = player.getUniqueId();
        long currentTime = System.currentTimeMillis();
        
        // Check if the player has used the enchantment before
        if (lastUseTimes.containsKey(playerId)) {
            long lastUseTime = lastUseTimes.get(playerId);
            long cooldownMillis = COOLDOWN_SECONDS * 1000;
            
            if (currentTime - lastUseTime < cooldownMillis) {
                // Still on cooldown
                long remainingSeconds = (cooldownMillis - (currentTime - lastUseTime)) / 1000;
                player.sendMessage(ChatColor.RED + "Magic Mirror is on cooldown! " + 
                        ChatColor.GOLD + remainingSeconds + ChatColor.RED + " seconds remaining.");
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Consume one ender pearl from the player's stack.
     * 
     * @param player The player
     * @param item The ender pearl stack
     */
    private void consumeEnderPearl(Player player, ItemStack item) {
        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            // If it's the last pearl, remove it and update player's hand
            if (player.getInventory().getItemInMainHand().equals(item)) {
                player.getInventory().setItemInMainHand(null);
            } else if (player.getInventory().getItemInOffHand().equals(item)) {
                player.getInventory().setItemInOffHand(null);
            }
        }
    }
    
    /**
     * Clean up old cooldown entries.
     */
    private void cleanupOldEntries() {
        long currentTime = System.currentTimeMillis();
        lastUseTimes.entrySet().removeIf(entry -> 
            currentTime - entry.getValue() > COOLDOWN_SECONDS * 1000 * 2); // Remove after double the cooldown time
    }
    
    @Override
    public boolean conflictsWith(CustomEnchant other) {
        return other.getName().equalsIgnoreCase("EnderMastery") ||
               other.getName().equalsIgnoreCase("VoidWalker") ||
               other.getName().equalsIgnoreCase("SpatialDistortion");
    }
} 