package org.frizzlenpop.frizzlenChants.impl;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
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
 * Echo of the Ancients enchantment that emits an ancient, resonant sound
 * that confuses nearby hostile mobs when the player swings their weapon.
 */
public class EchoOfTheAncientsEnchant extends CustomEnchant {

    private final FrizzlenChants plugin;
    private final Random random = new Random();
    private final Map<UUID, Long> lastSwingTime = new HashMap<>();
    
    // Constants
    private static final int COOLDOWN_MILLIS = 1500; // 1.5 seconds between activations
    private static final String[] RUNE_SYMBOLS = {"ᚠ", "ᚢ", "ᚦ", "ᚨ", "ᚱ", "ᚲ", "ᚷ", "ᚹ", "ᚺ", "ᚾ", "ᛁ", "ᛃ", "ᛇ", "ᛈ", "ᛉ", "ᛊ", "ᛏ", "ᛒ", "ᛖ", "ᛗ", "ᛚ", "ᛜ", "ᛞ", "ᛟ"};
    
    public EchoOfTheAncientsEnchant() {
        super("EchoOfTheAncients", 3, 
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
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        
        // Check if player is swinging a weapon (left/right click air)
        if (event.getAction() != Action.LEFT_CLICK_AIR && 
            event.getAction() != Action.RIGHT_CLICK_AIR) {
            return;
        }
        
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
        
        // Check cooldown
        UUID playerId = player.getUniqueId();
        long currentTime = System.currentTimeMillis();
        
        if (lastSwingTime.containsKey(playerId)) {
            long lastSwing = lastSwingTime.get(playerId);
            if (currentTime - lastSwing < COOLDOWN_MILLIS) {
                return; // Still on cooldown
            }
        }
        
        // Update last swing time
        lastSwingTime.put(playerId, currentTime);
        
        // Create the ancient echo effect
        createAncientEcho(player, level);
        
        // Clean up old entries periodically to prevent memory leaks
        if (random.nextInt(20) == 0) {
            cleanupOldEntries();
        }
    }
    
    /**
     * Creates the ancient echo effect around the player.
     *
     * @param player The player who triggered the effect
     * @param level The enchantment level
     */
    private void createAncientEcho(Player player, int level) {
        World world = player.getWorld();
        Location center = player.getLocation();
        
        // Calculate effect parameters based on level
        double radius = 4.0 + (level * 1.0); // 5-7 block radius
        int confusionDuration = 40 + (level * 20); // 2-4 seconds
        int confusionAmplifier = level - 1; // Level 1: 0, Level 2: 1, Level 3: 2
        
        // Play sound effects
        world.playSound(center, Sound.BLOCK_CONDUIT_AMBIENT, 1.0f, 0.5f);
        world.playSound(center, Sound.ENTITY_ELDER_GUARDIAN_AMBIENT, 0.5f, 0.7f);
        
        // Show message to player
        player.sendActionBar(net.kyori.adventure.text.Component.text(
            "§5Echo of the Ancients §7resonates!"
        ));
        
        // Apply confusion to nearby hostile mobs
        for (LivingEntity entity : world.getLivingEntities()) {
            // Skip if not a hostile mob or too far away
            if (!(entity instanceof Monster) || 
                entity.getLocation().distance(center) > radius) {
                continue;
            }
            
            // Apply confusion effect
            entity.addPotionEffect(new PotionEffect(
                PotionEffectType.getByName("CONFUSION"),
                confusionDuration,
                confusionAmplifier,
                false,
                true,
                true
            ));
            
            // Higher levels also apply slowness
            if (level >= 2) {
                entity.addPotionEffect(new PotionEffect(
                    PotionEffectType.getByName("SLOW"),
                    confusionDuration / 2,
                    0,
                    false,
                    true,
                    true
                ));
            }
            
            // Make the mob look at the player (as if confused about the source)
            makeEntityLookRandom(entity);
            
            // Create particle effect on the affected mob
            createAffectedMobEffect(entity, level);
        }
        
        // Create visual ripple effect
        createEchoRippleEffect(center, radius, level);
        
        // Create runic symbols
        createRunicSymbols(center, radius, level);
    }
    
    /**
     * Makes the entity look in random directions to simulate confusion.
     *
     * @param entity The entity to affect
     */
    private void makeEntityLookRandom(LivingEntity entity) {
        // Schedule a series of random direction changes
        for (int i = 0; i < 5; i++) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (!entity.isValid() || entity.isDead()) {
                        this.cancel();
                        return;
                    }
                    
                    // Generate random yaw and pitch
                    float yaw = random.nextFloat() * 360.0f;
                    float pitch = random.nextFloat() * 60.0f - 30.0f;
                    
                    // Set the entity's look direction
                    Location lookLoc = entity.getLocation().clone();
                    lookLoc.setYaw(yaw);
                    lookLoc.setPitch(pitch);
                    entity.teleport(lookLoc);
                }
            }.runTaskLater(plugin, 5L + (5L * i));
        }
    }
    
    /**
     * Creates a visual effect on the affected mob.
     *
     * @param entity The affected entity
     * @param level The enchantment level
     */
    private void createAffectedMobEffect(LivingEntity entity, int level) {
        Location loc = entity.getLocation().clone().add(0, 1.0, 0);
        World world = entity.getWorld();
        
        // Spawn spiral particles around the entity's head
        new BukkitRunnable() {
            int ticks = 0;
            final int maxTicks = 20; // 1 second of effect
            
            @Override
            public void run() {
                if (!entity.isValid() || entity.isDead() || ticks >= maxTicks) {
                    this.cancel();
                    return;
                }
                
                // Create a spiral of particles
                double angle = ticks * 0.5;
                double radius = 0.5;
                double x = Math.cos(angle) * radius;
                double z = Math.sin(angle) * radius;
                
                Location particleLoc = loc.clone().add(x, 0, z);
                
                // Spawn confusion particles
                world.spawnParticle(
                    Particle.DRAGON_BREATH,
                    particleLoc,
                    3,
                    0.1, 0.1, 0.1,
                    0.01
                );
                
                // Add note particles for higher levels
                if (level >= 2 && ticks % 2 == 0) {
                    world.spawnParticle(
                        Particle.NOTE,
                        particleLoc.clone().add(0, 0.2, 0),
                        1,
                        0.1, 0.1, 0.1,
                        0
                    );
                }
                
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
    
    /**
     * Creates a ripple effect that expands outward from the player.
     *
     * @param center The center location
     * @param radius The maximum radius of the ripple
     * @param level The enchantment level
     */
    private void createEchoRippleEffect(Location center, double radius, int level) {
        World world = center.getWorld();
        
        // Color based on level
        final Color rippleColor;
        switch (level) {
            case 1:
                rippleColor = Color.fromRGB(150, 100, 180); // Light purple
                break;
            case 2:
                rippleColor = Color.fromRGB(120, 60, 170); // Medium purple
                break;
            case 3:
                rippleColor = Color.fromRGB(90, 30, 160);  // Deep purple
                break;
            default:
                rippleColor = Color.fromRGB(120, 60, 170);
        }
        
        // Create expanding rings
        new BukkitRunnable() {
            private double currentRadius = 0.5;
            private final double maxRadius = radius;
            private final double expandPerTick = radius / 10.0; // Complete expansion in 10 ticks
            
            @Override
            public void run() {
                if (currentRadius >= maxRadius) {
                    this.cancel();
                    return;
                }
                
                // Create a ring at the current radius
                int particleCount = (int) (currentRadius * 8); // Particles scale with radius
                double angleIncrement = 2 * Math.PI / particleCount;
                
                for (int i = 0; i < particleCount; i++) {
                    double angle = i * angleIncrement;
                    double x = Math.cos(angle) * currentRadius;
                    double z = Math.sin(angle) * currentRadius;
                    
                    // Position at player's feet level
                    Location particleLoc = center.clone().add(x, 0.1, z);
                    
                    // Send colored dust particle
                    plugin.getVisualEffectManager().spawnColoredParticles(
                        particleLoc,
                        Particle.DUST,
                        rippleColor,
                        1.0f,
                        1
                    );
                    
                    // Add some additional particles for higher levels
                    if (level >= 3 && i % 4 == 0) {
                        world.spawnParticle(
                            Particle.REVERSE_PORTAL,
                            particleLoc,
                            1,
                            0, 0, 0,
                            0.02
                        );
                    }
                }
                
                // Increment radius for next tick
                currentRadius += expandPerTick;
            }
        }.runTaskTimer(plugin, 0L, 1L);
        
        // For level 3, add a second delayed ripple
        if (level == 3) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    createEchoRippleEffect(center, radius * 0.7, 2);
                }
            }.runTaskLater(plugin, 10L);
        }
    }
    
    /**
     * Creates floating runic symbols around the player.
     *
     * @param center The center location
     * @param radius The radius around the player
     * @param level The enchantment level
     */
    private void createRunicSymbols(Location center, double radius, int level) {
        World world = center.getWorld();
        
        // Number of runes to display
        int runeCount = 3 + level * 2; // 5-9 runes
        
        for (int i = 0; i < runeCount; i++) {
            // Select a random rune symbol
            String rune = RUNE_SYMBOLS[random.nextInt(RUNE_SYMBOLS.length)];
            
            // Calculate random position within radius
            double angle = random.nextDouble() * 2 * Math.PI;
            double distance = random.nextDouble() * radius * 0.7; // Keep within 70% of total radius
            
            double x = Math.cos(angle) * distance;
            double z = Math.sin(angle) * distance;
            double y = 1.0 + random.nextDouble() * 1.5; // Between 1-2.5 blocks high
            
            final Location runeLoc = center.clone().add(x, y, z);
            
            // Create floating rune effect
            new BukkitRunnable() {
                int ticks = 0;
                final int maxTicks = 40 + random.nextInt(20); // 2-3 seconds duration, randomized per rune
                final double floatRange = 0.2;
                double initialY = runeLoc.getY();
                
                @Override
                public void run() {
                    if (ticks >= maxTicks) {
                        this.cancel();
                        return;
                    }
                    
                    // Calculate floating motion
                    double yOffset = Math.sin((double) ticks / 10) * floatRange;
                    Location currentLoc = runeLoc.clone();
                    currentLoc.setY(initialY + yOffset);
                    
                    // Display rune (in the real implementation, this would use ProtocolLib for displaying text)
                    // Instead, we'll use particles to simulate a rune
                    world.spawnParticle(
                        Particle.END_ROD,
                        currentLoc,
                        5,
                        0.1, 0.1, 0.1,
                        0.02
                    );
                    
                    // Add some ambient particles
                    if (ticks % 5 == 0) {
                        world.spawnParticle(
                            Particle.REVERSE_PORTAL,
                            currentLoc,
                            1,
                            0.1, 0.1, 0.1,
                            0.01
                        );
                    }
                    
                    ticks++;
                }
            }.runTaskTimer(plugin, random.nextInt(10), 1L);
        }
    }
    
    /**
     * Cleans up old swing time entries to prevent memory leaks.
     */
    private void cleanupOldEntries() {
        long currentTime = System.currentTimeMillis();
        lastSwingTime.entrySet().removeIf(entry -> 
            currentTime - entry.getValue() > COOLDOWN_MILLIS * 5);
    }
    
    @Override
    public boolean conflictsWith(CustomEnchant other) {
        // Conflicts with other sound/confusion enchantments
        return other.getName().toLowerCase().contains("echo") ||
               other.getName().toLowerCase().contains("sound") ||
               other.getName().toLowerCase().contains("confusion") ||
               other.getName().toLowerCase().contains("ancient");
    }
} 