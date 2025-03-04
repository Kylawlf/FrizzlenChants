package org.frizzlenpop.frizzlenChants.impl;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
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
 * Spectral Chains enchantment that binds enemies with ghostly chains,
 * slowing their movement and briefly freezing them.
 */
public class SpectralChainsEnchant extends CustomEnchant {

    private final FrizzlenChants plugin;
    private final Random random = new Random();
    private final Map<UUID, Long> entityCooldowns = new HashMap<>();
    
    // Constants
    private static final int COOLDOWN_MILLIS = 5000; // 5 seconds between applications to same entity
    private static final int FREEZE_TICKS = 5; // Number of ticks to freeze entity (5 ticks = 0.25s)
    
    public SpectralChainsEnchant() {
        super("SpectralChains", 3, 
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
        UUID targetId = target.getUniqueId();
        
        // Check cooldown
        if (entityCooldowns.containsKey(targetId)) {
            long lastApplied = entityCooldowns.get(targetId);
            if (System.currentTimeMillis() - lastApplied < COOLDOWN_MILLIS) {
                return;
            }
        }
        
        // Calculate proc chance (30% + 10% per level)
        int procChance = 20 + (level * 10);
        
        // Roll for effect application
        if (random.nextInt(100) < procChance) {
            // Apply the chains effect
            applySpectralChains(player, target, level);
            
            // Update cooldown
            entityCooldowns.put(targetId, System.currentTimeMillis());
            
            // Clean up old cooldowns periodically
            if (random.nextInt(10) == 0) {
                cleanupCooldowns();
            }
        }
    }
    
    /**
     * Applies the spectral chains effect to the target.
     *
     * @param player The player who triggered the effect
     * @param target The entity being affected
     * @param level The enchantment level
     */
    private void applySpectralChains(Player player, LivingEntity target, int level) {
        // Calculate effect duration based on level (2-4 seconds)
        int durationTicks = 40 + (level * 20);
        int amplifier = level - 1; // Level 1: 0, Level 2: 1, Level 3: 2
        
        // Apply slowness effect
        target.addPotionEffect(new PotionEffect(
            PotionEffectType.getByName("SLOW"), 
            durationTicks,
            amplifier,
            false, // ambient
            true,  // particles
            true   // icon
        ));
        
        // For higher levels, also apply mining fatigue
        if (level >= 2) {
            target.addPotionEffect(new PotionEffect(
                PotionEffectType.getByName("SLOW_DIGGING"),
                durationTicks,
                level - 2,
                false,
                true,
                true
            ));
        }
        
        // Apply brief freeze (immobilization)
        freezeEntity(target, level);
        
        // Create visual effects
        createChainEffects(player, target, level, durationTicks);
        
        // Play sound effects
        player.getWorld().playSound(
            target.getLocation(),
            Sound.BLOCK_CHAIN_PLACE,
            1.0f,
            0.5f
        );
        
        // Show message to player
        player.sendActionBar(net.kyori.adventure.text.Component.text(
            "§3Spectral Chains §7bound your enemy!"
        ));
        
        // If the target is a player, show them a message too
        if (target instanceof Player) {
            ((Player) target).sendActionBar(net.kyori.adventure.text.Component.text(
                "§3Spectral Chains §7are slowing you down!"
            ));
        }
    }
    
    /**
     * Freezes the entity by repeatedly setting its velocity to zero.
     *
     * @param entity The entity to freeze
     * @param level The enchantment level
     */
    private void freezeEntity(LivingEntity entity, int level) {
        Vector originalVelocity = entity.getVelocity().clone();
        
        // Store the entity's pre-freeze velocity to restore a portion later
        final Vector storedVelocity = originalVelocity.clone();
        
        // Initial freeze
        entity.setVelocity(new Vector(0, 0, 0));
        
        // Prevent knockback/movement for a brief period
        new BukkitRunnable() {
            int ticks = 0;
            final int maxTicks = FREEZE_TICKS + (level * 2); // More freeze ticks at higher levels
            
            @Override
            public void run() {
                if (!entity.isValid() || entity.isDead()) {
                    this.cancel();
                    return;
                }
                
                if (ticks < maxTicks) {
                    // Continue to keep entity in place
                    entity.setVelocity(new Vector(0, -0.05, 0)); // Slight downward force to keep on ground
                } else {
                    // Restore a small portion of original velocity
                    double reductionFactor = 0.2; // Only restore 20% of original velocity
                    Vector reducedVelocity = storedVelocity.multiply(reductionFactor);
                    entity.setVelocity(reducedVelocity);
                    this.cancel();
                }
                
                ticks++;
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }
    
    /**
     * Creates the visual chain effects between the player and target.
     *
     * @param player The player who triggered the effect
     * @param target The entity being affected
     * @param level The enchantment level
     * @param durationTicks How long the effect lasts
     */
    private void createChainEffects(Player player, LivingEntity target, int level, int durationTicks) {
        // Create duration in server ticks (20 ticks = 1 second)
        long effectDuration = durationTicks / 2; // Visual effect lasts half as long as the slowness
        
        // Chain color based on level
        final Color chainColor;
        switch (level) {
            case 1:
                chainColor = Color.fromRGB(180, 180, 235); // Light blue-gray
                break;
            case 2:
                chainColor = Color.fromRGB(120, 140, 210); // Medium blue-gray
                break;
            case 3:
                chainColor = Color.fromRGB(80, 100, 200);  // Deep blue with hint of purple
                break;
            default:
                chainColor = Color.fromRGB(180, 180, 235);
        }
        
        // Schedule chain effect task
        new BukkitRunnable() {
            private int tick = 0;
            private final int maxTicks = (int) effectDuration;
            
            @Override
            public void run() {
                if (!target.isValid() || target.isDead() || tick >= maxTicks) {
                    this.cancel();
                    return;
                }
                
                // Create chains connecting player to target
                createChainParticles(player.getLocation(), target.getLocation(), chainColor, level);
                
                // Create circling chains around the target
                createCirclingChains(target.getLocation(), chainColor, level, tick);
                
                // Occasional chain sound
                if (tick % 10 == 0) {
                    target.getWorld().playSound(
                        target.getLocation(),
                        Sound.BLOCK_CHAIN_STEP,
                        0.5f,
                        0.5f + (random.nextFloat() * 0.3f)
                    );
                }
                
                tick += 2; // Increment by 2 to reduce particle spam
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }
    
    /**
     * Creates chain-like particles between two locations.
     *
     * @param start The starting location
     * @param end The ending location
     * @param color The color of the chain
     * @param level The enchantment level
     */
    private void createChainParticles(Location start, Location end, Color color, int level) {
        World world = start.getWorld();
        
        // Get vector from start to end
        Vector direction = end.toVector().subtract(start.toVector());
        
        // Calculate length and number of particles
        double length = direction.length();
        int particles = (int) (length * 2) + 1;
        
        // Normalize the direction vector
        direction.normalize();
        
        // Create the chain segments
        for (int i = 0; i < particles; i++) {
            // Calculate position along the line
            double t = i / (double) particles;
            Vector pos = start.toVector().add(direction.clone().multiply(length * t));
            
            // Add some noise to create chain-like pattern
            double offset = 0.1 * Math.sin(t * Math.PI * 10);
            double yOffset = 0.1 * Math.cos(t * Math.PI * 10);
            
            // Create a perpendicular vector for the offset
            Vector perpendicular = new Vector(-direction.getZ(), yOffset, direction.getX()).normalize().multiply(offset);
            
            // Apply the offset
            pos.add(perpendicular);
            
            // Create particle at position
            Location particleLoc = pos.toLocation(world);
            
            // Send colored dust particle
            plugin.getVisualEffectManager().spawnColoredParticles(
                particleLoc,
                Particle.DUST,
                color,
                0.8f,
                1
            );
            
            // Add some end rod particles for "spectral" effect on higher levels
            if (level >= 2 && i % 3 == 0) {
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
    
    /**
     * Creates circling chain particles around the target.
     *
     * @param center The center location
     * @param color The color of the chains
     * @param level The enchantment level
     * @param tick The current tick counter
     */
    private void createCirclingChains(Location center, Color color, int level, int tick) {
        World world = center.getWorld();
        
        // Parameters for the spiral
        double radius = 1.0;
        double height = 2.0;
        int points = 10 + (level * 5);
        
        // Create helix/spiral pattern
        for (int i = 0; i < points; i++) {
            // Calculate position along the spiral
            double angle = ((double) tick / 10 + (double) i / points) * 2 * Math.PI;
            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;
            double y = (double) i / points * height;
            
            // Adjust the center to the entity's feet
            Location particleLoc = center.clone().add(x, y, z);
            
            // Send colored dust particle
            plugin.getVisualEffectManager().spawnColoredParticles(
                particleLoc, 
                Particle.DUST,
                color,
                0.7f,
                1
            );
            
            // Add extra effects for higher levels
            if (level >= 3 && i % 4 == 0) {
                world.spawnParticle(
                    Particle.SCULK_SOUL,
                    particleLoc,
                    1,
                    0, 0, 0,
                    0.02
                );
            }
        }
    }
    
    /**
     * Cleans up old cooldowns to prevent memory leaks.
     */
    private void cleanupCooldowns() {
        long currentTime = System.currentTimeMillis();
        entityCooldowns.entrySet().removeIf(entry -> 
            currentTime - entry.getValue() > COOLDOWN_MILLIS * 2);
    }
    
    @Override
    public boolean conflictsWith(CustomEnchant other) {
        // Conflicts with other movement-limiting enchantments
        return other.getName().toLowerCase().contains("slow") ||
               other.getName().toLowerCase().contains("freeze") ||
               other.getName().toLowerCase().contains("chain") ||
               other.getName().toLowerCase().contains("bind");
    }
} 