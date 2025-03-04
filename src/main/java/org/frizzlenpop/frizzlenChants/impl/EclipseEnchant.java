package org.frizzlenpop.frizzlenChants.impl;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import org.frizzlenpop.frizzlenChants.FrizzlenChants;
import org.frizzlenpop.frizzlenChants.enchant.CustomEnchant;
import org.frizzlenpop.frizzlenChants.enchant.EnchantmentUtils;

import java.util.Collection;
import java.util.Random;

/**
 * Eclipse enchantment that blinds and slows nearby enemies when the
 * player lands a critical hit.
 */
public class EclipseEnchant extends CustomEnchant {

    private final FrizzlenChants plugin;
    private final Random random = new Random();
    
    public EclipseEnchant() {
        super("Eclipse", 3, 
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
        // Only process if a player is attacking
        if (!(event.getDamager() instanceof Player) || !(event.getEntity() instanceof LivingEntity)) {
            return;
        }
        
        Player player = (Player) event.getDamager();
        LivingEntity target = (LivingEntity) event.getEntity();
        ItemStack item = player.getInventory().getItemInMainHand();
        
        if (item == null || !item.hasItemMeta()) {
            return;
        }
        
        // Check if the weapon has this enchantment
        int level = EnchantmentUtils.getEnchantmentLevel(item, this, plugin.getEnchantmentRegistry());
        
        if (level <= 0) {
            return;
        }
        
        // Check if this is a critical hit
        // In Minecraft, a critical hit happens when the player is falling and not on the ground
        if (isCriticalHit(player)) {
            // Trigger the Eclipse effect
            triggerEclipseEffect(player, target, level);
        }
    }
    
    /**
     * Checks if the attack is a critical hit.
     * In Minecraft, critical hits occur when a player is falling and not on ground.
     *
     * @param player The attacking player
     * @return true if the attack is a critical hit, false otherwise
     */
    private boolean isCriticalHit(Player player) {
        return player.getFallDistance() > 0.0F && !player.isOnGround() && 
               !player.isInWater() && !player.isInsideVehicle();
    }
    
    /**
     * Triggers the Eclipse effect on a critical hit.
     *
     * @param player The player who landed the critical hit
     * @param target The target that was hit
     * @param level The enchantment level
     */
    private void triggerEclipseEffect(Player player, LivingEntity target, int level) {
        Location location = target.getLocation();
        
        // Calculate effect parameters based on level
        double radius = 3.0 + (level * 1.0); // 4-6 block radius
        int blindnessDuration = 40 + (level * 20); // 3-5 seconds (60-100 ticks)
        int slownessDuration = 60 + (level * 40); // 5-9 seconds (100-180 ticks)
        int blindnessAmplifier = 0; // Blindness doesn't have amplifiers
        int slownessAmplifier = level - 1; // 0-2 (I-III)
        
        // Create dark burst effect
        createDarkBurstEffect(location, radius, level);
        
        // Play sound
        location.getWorld().playSound(location, Sound.ENTITY_WITHER_AMBIENT, 1.0f, 0.5f);
        
        // Send message to player
        player.sendActionBar(net.kyori.adventure.text.Component.text("§8Eclipse §7triggered!"));
        
        // Apply effects to nearby entities
        Collection<Entity> nearbyEntities = location.getWorld().getNearbyEntities(location, radius, radius, radius);
        
        for (Entity entity : nearbyEntities) {
            // Skip the player who caused the effect
            if (entity.equals(player)) {
                continue;
            }
            
            // Only affect living entities
            if (entity instanceof LivingEntity) {
                LivingEntity livingEntity = (LivingEntity) entity;
                
                // Apply blindness
                livingEntity.addPotionEffect(new PotionEffect(
                    PotionEffectType.BLINDNESS,
                    blindnessDuration,
                    blindnessAmplifier,
                    false, // Not ambient
                    true, // Show particles
                    true // Show icon
                ));
                
                // Apply slowness
                livingEntity.addPotionEffect(new PotionEffect(
                    PotionEffectType.SLOWNESS,
                    slownessDuration,
                    slownessAmplifier,
                    false, // Not ambient
                    true, // Show particles
                    true // Show icon
                ));
            }
        }
    }
    
    /**
     * Creates a dark burst particle effect.
     *
     * @param location The center location of the effect
     * @param radius The radius of the effect
     * @param level The enchantment level
     */
    private void createDarkBurstEffect(Location location, double radius, int level) {
        // Base dark color for the effect, darker with higher levels
        Color darkColor;
        switch (level) {
            case 1:
                darkColor = Color.fromRGB(50, 0, 70); // Dark purple
                break;
            case 2:
                darkColor = Color.fromRGB(30, 0, 50); // Deeper purple
                break;
            case 3:
                darkColor = Color.fromRGB(10, 0, 30); // Very dark purple, almost black
                break;
            default:
                darkColor = Color.fromRGB(50, 0, 70);
        }
        
        // Create expanding rings
        for (int ring = 0; ring < 5; ring++) {
            double ringRadius = (radius * ring) / 5;
            createParticleRing(location, ringRadius, darkColor, level);
            
            // Add small delay
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                // Ignore
            }
        }
        
        // Create cloud effect at the center
        for (int i = 0; i < 50 * level; i++) {
            double x = (random.nextDouble() * 2 - 1) * (radius / 2);
            double y = random.nextDouble() * 2;
            double z = (random.nextDouble() * 2 - 1) * (radius / 2);
            
            Location particleLocation = location.clone().add(x, y, z);
            
            // Spawn dark smoke particles
            location.getWorld().spawnParticle(
                Particle.CAMPFIRE_COSY_SMOKE,
                particleLocation,
                1,
                0, 0, 0,
                0.01
            );
            
            // Add some dust particles for color
            if (i % 5 == 0) {
                plugin.getVisualEffectManager().spawnColoredParticles(
                    particleLocation,
                    Particle.DUST,
                    darkColor,
                    1.5f,
                    1
                );
            }
        }
        
        // Add some special particles based on level
        if (level >= 2) {
            // Add some end rod particles (white) for contrast
            for (int i = 0; i < 20 * level; i++) {
                double angle = random.nextDouble() * Math.PI * 2;
                double distance = random.nextDouble() * radius;
                double x = Math.cos(angle) * distance;
                double z = Math.sin(angle) * distance;
                double y = random.nextDouble() * 2;
                
                Location particleLocation = location.clone().add(x, y, z);
                
                location.getWorld().spawnParticle(
                    Particle.END_ROD,
                    particleLocation,
                    1,
                    0, 0, 0,
                    0.02
                );
            }
        }
        
        // Level 3 adds a "dark pulse" effect
        if (level == 3) {
            location.getWorld().spawnParticle(
                Particle.EXPLOSION_EMITTER,
                location.clone().add(0, 1, 0),
                3,
                0, 0, 0,
                0
            );
        }
    }
    
    /**
     * Creates a ring of particles at a specified radius.
     *
     * @param center The center location
     * @param radius The radius of the ring
     * @param color The color of the particles
     * @param level The enchantment level
     */
    private void createParticleRing(Location center, double radius, Color color, int level) {
        int particles = Math.max(20, (int)(radius * 10));
        
        for (int i = 0; i < particles; i++) {
            double angle = (2 * Math.PI * i) / particles;
            double x = radius * Math.cos(angle);
            double z = radius * Math.sin(angle);
            
            // Add some variation to Y based on angle
            double y = 0.5 + Math.sin(angle * 2) * 0.3;
            
            Location particleLocation = center.clone().add(x, y, z);
            
            // Spawn dust particles for the ring
            plugin.getVisualEffectManager().spawnColoredParticles(
                particleLocation,
                Particle.DUST,
                color,
                1.0f,
                1
            );
            
            // Add some variation with additional particles
            if (i % 4 == 0 && level >= 2) {
                center.getWorld().spawnParticle(
                    Particle.DRAGON_BREATH,
                    particleLocation,
                    1,
                    0, 0, 0,
                    0.01
                );
            }
        }
    }
    
    @Override
    public boolean conflictsWith(CustomEnchant other) {
        // Conflicts with other darkness/blindness enchantments
        return other.getName().toLowerCase().contains("eclipse") ||
               other.getName().toLowerCase().contains("blind") ||
               other.getName().toLowerCase().contains("dark") ||
               other.getName().toLowerCase().contains("shadow");
    }
} 