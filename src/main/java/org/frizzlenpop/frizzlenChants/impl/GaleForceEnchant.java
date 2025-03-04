package org.frizzlenpop.frizzlenChants.impl;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.util.Vector;
import org.frizzlenpop.frizzlenChants.FrizzlenChants;
import org.frizzlenpop.frizzlenChants.enchant.CustomEnchant;
import org.frizzlenpop.frizzlenChants.enchant.EnchantmentUtils;

import java.util.Collection;

/**
 * Gale Force enchantment for bows that creates a gust of wind
 * when arrows hit, pushing back nearby entities and projectiles.
 */
public class GaleForceEnchant extends CustomEnchant {

    private static final String METADATA_KEY = "gale_force_level";
    private final FrizzlenChants plugin;
    
    public GaleForceEnchant() {
        super("Gale Force", 3, Material.BOW, Material.CROSSBOW);
        this.plugin = FrizzlenChants.getPlugin(FrizzlenChants.class);
    }

    @Override
    public void applyEffect(Player player, ItemStack item, int level) {
        // This method is called from the global listener
        // For bow enchantments, we'll use the event handlers below
    }
    
    @EventHandler
    public void onBowShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player) || !(event.getProjectile() instanceof Arrow)) {
            return;
        }
        
        Player player = (Player) event.getEntity();
        Arrow arrow = (Arrow) event.getProjectile();
        ItemStack bow = event.getBow();
        
        if (bow == null) {
            return;
        }
        
        // Check if the bow has this enchantment
        int level = EnchantmentUtils.getEnchantmentLevel(bow, this, plugin.getEnchantmentRegistry());
        
        if (level > 0) {
            // Mark the arrow with this enchantment's level
            arrow.setMetadata(METADATA_KEY, new FixedMetadataValue(plugin, level));
            
            // Apply a subtle visual effect to the arrow to indicate it has Gale Force
            arrow.setGlowing(true);
        }
    }
    
    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Arrow)) {
            return;
        }
        
        Arrow arrow = (Arrow) event.getEntity();
        
        // Check if this arrow was shot with the Gale Force enchantment
        if (!arrow.hasMetadata(METADATA_KEY)) {
            return;
        }
        
        // Get the level of the enchantment
        int level = arrow.getMetadata(METADATA_KEY).get(0).asInt();
        Location impactLocation = arrow.getLocation();
        
        // Calculate effect parameters based on enchantment level
        double radius = 3.0 + (level * 1.5); // 4.5 to 7.5 block radius
        double knockbackStrength = 0.8 + (level * 0.4); // 1.2 to 2.0 strength
        
        // Create wind effect at impact location
        createWindEffect(impactLocation, radius, level);
        
        // Play wind sound
        impactLocation.getWorld().playSound(impactLocation, Sound.ENTITY_PHANTOM_FLAP, 1.0f, 1.0f);
        
        // Get all entities in the radius
        Collection<Entity> nearbyEntities = impactLocation.getWorld().getNearbyEntities(impactLocation, radius, radius, radius);
        
        for (Entity entity : nearbyEntities) {
            // Skip the shooter to prevent self-knockback
            if (arrow.getShooter() instanceof Entity && entity.equals(arrow.getShooter())) {
                continue;
            }
            
            // Calculate direction vector from impact point to entity
            Vector direction = entity.getLocation().toVector().subtract(impactLocation.toVector());
            
            // Skip if entity is behind the impact point (based on arrow direction)
            if (arrow.getVelocity().normalize().dot(direction.normalize()) > 0) {
                continue;
            }
            
            // Normalize and scale the vector for knockback
            if (direction.lengthSquared() > 0) { // Avoid division by zero
                direction.normalize().multiply(knockbackStrength);
                direction.setY(Math.max(0.2, direction.getY())); // Ensure some upward boost
                
                // Apply knockback to entity
                entity.setVelocity(entity.getVelocity().add(direction));
            }
            
            // If it's a projectile, give it a stronger boost
            if (entity instanceof Projectile) {
                entity.setVelocity(entity.getVelocity().multiply(1.5));
            }
        }
    }
    
    /**
     * Creates a wind particle effect at the specified location.
     *
     * @param location The center location of the effect
     * @param radius The radius of the effect
     * @param level The enchantment level
     */
    private void createWindEffect(Location location, double radius, int level) {
        // Create color based on level
        Color windColor;
        switch (level) {
            case 1:
                windColor = Color.WHITE;
                break;
            case 2:
                windColor = Color.fromRGB(200, 255, 255); // Light cyan
                break;
            case 3:
                windColor = Color.fromRGB(150, 255, 255); // Cyan
                break;
            default:
                windColor = Color.WHITE;
        }
        
        // Create expanding rings of particles
        for (int ring = 0; ring < 3; ring++) {
            double ringRadius = radius * (ring + 1) / 3;
            int particleCount = (int) (20 * ringRadius);
            
            for (int i = 0; i < particleCount; i++) {
                double angle = (2 * Math.PI * i) / particleCount;
                double x = ringRadius * Math.cos(angle);
                double z = ringRadius * Math.sin(angle);
                
                Location particleLocation = location.clone().add(x, 0.1, z);
                
                // Use dust particles for the wind effect
                plugin.getVisualEffectManager().spawnColoredParticles(
                    particleLocation,
                    Particle.DUST,
                    windColor,
                    1.0f,
                    1
                );
                
                // Add some cloud particles for effect
                if (i % 5 == 0) {
                    location.getWorld().spawnParticle(
                        Particle.CLOUD,
                        particleLocation,
                        1,
                        0.1, 0.1, 0.1,
                        0.05
                    );
                }
            }
        }
    }
    
    @Override
    public boolean conflictsWith(CustomEnchant other) {
        // Conflicts with other wind/air/push enchantments
        return other.getName().toLowerCase().contains("wind") ||
               other.getName().toLowerCase().contains("gale") ||
               other.getName().toLowerCase().contains("push") ||
               other.getName().toLowerCase().contains("knockback");
    }
} 