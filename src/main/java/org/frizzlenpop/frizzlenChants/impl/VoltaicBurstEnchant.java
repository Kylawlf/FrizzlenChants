package org.frizzlenpop.frizzlenChants.impl;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.frizzlenpop.frizzlenChants.FrizzlenChants;
import org.frizzlenpop.frizzlenChants.enchant.CustomEnchant;
import org.frizzlenpop.frizzlenChants.enchant.EnchantmentUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;

/**
 * Voltaic Burst enchantment for weapons that creates electrical discharges
 * when hitting entities, with a chance to chain to nearby entities.
 */
public class VoltaicBurstEnchant extends CustomEnchant {

    private static final String METADATA_KEY = "voltaic_burst_level";
    private final FrizzlenChants plugin;
    private final Random random = new Random();
    
    public VoltaicBurstEnchant() {
        super("VoltaicBurst", 3, 
              Material.WOODEN_SWORD, Material.STONE_SWORD, Material.IRON_SWORD, 
              Material.GOLDEN_SWORD, Material.DIAMOND_SWORD, Material.NETHERITE_SWORD,
              Material.BOW, Material.CROSSBOW, Material.TRIDENT);
        this.plugin = FrizzlenChants.getPlugin(FrizzlenChants.class);
    }

    @Override
    public void applyEffect(Player player, ItemStack item, int level) {
        // This is implemented in the event handlers for specific weapon types
    }
    
    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        // Handle melee weapons (swords, tridents)
        if (!(event.getDamager() instanceof Player)) {
            return;
        }
        
        Player player = (Player) event.getDamager();
        ItemStack item = player.getInventory().getItemInMainHand();
        
        if (item == null || !item.hasItemMeta()) {
            return;
        }
        
        // Check if the weapon has this enchantment
        int level = EnchantmentUtils.getEnchantmentLevel(item, this, plugin.getEnchantmentRegistry());
        
        if (level > 0 && event.getEntity() instanceof LivingEntity) {
            LivingEntity target = (LivingEntity) event.getEntity();
            
            // Apply the voltaic burst effect
            applyVoltaicEffect(player, target, level);
        }
    }
    
    @EventHandler
    public void onBowShoot(EntityShootBowEvent event) {
        // Handle bow/crossbow projectiles
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
            
            // Apply visual effect to indicate voltaic enchantment
            arrow.setGlowing(true);
            
            // Use the VisualEffectManager to create a trail effect
            plugin.getVisualEffectManager().createParticleTrail(
                arrow, 
                Particle.ELECTRIC_SPARK, 
                Color.fromRGB(120, 180, 255), // Light blue color
                200 // Duration in ticks
            );
        }
    }
    
    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        // Handle arrow impacts
        if (!(event.getEntity() instanceof Arrow)) {
            return;
        }
        
        Arrow arrow = (Arrow) event.getEntity();
        
        // Check if this arrow was shot with the voltaic burst enchantment
        if (!arrow.hasMetadata(METADATA_KEY)) {
            return;
        }
        
        // Get the level of the enchantment
        int level = arrow.getMetadata(METADATA_KEY).get(0).asInt();
        
        // Check if the arrow hit an entity
        if (event.getHitEntity() instanceof LivingEntity && arrow.getShooter() instanceof Player) {
            Player shooter = (Player) arrow.getShooter();
            LivingEntity target = (LivingEntity) event.getHitEntity();
            
            // Apply the voltaic burst effect
            applyVoltaicEffect(shooter, target, level);
        } else {
            // Arrow hit a block, create a smaller electric effect at impact
            Location impactLocation = arrow.getLocation();
            createElectricEffect(impactLocation, 2.0, level, false);
            impactLocation.getWorld().playSound(impactLocation, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.5f, 1.5f);
        }
    }
    
    /**
     * Applies the voltaic effect to a target entity.
     *
     * @param attacker The player who triggered the effect
     * @param target The entity being affected
     * @param level The enchantment level
     */
    private void applyVoltaicEffect(Player attacker, LivingEntity target, int level) {
        Location targetLocation = target.getLocation();
        
        // Create the electric visual effect
        createElectricEffect(targetLocation, 3.0, level, true);
        
        // Play thunder sound at reduced volume
        targetLocation.getWorld().playSound(targetLocation, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.7f, 1.2f);
        
        // Apply base damage
        double baseDamage = 2.0 + (level * 1.5); // 3.5 to 6.5 damage
        target.damage(baseDamage, attacker);
        
        // Chain lightning effect (chance increases with level)
        int chainChance = 20 + (level * 10); // 30% to 50% chance
        
        if (random.nextInt(100) < chainChance) {
            LivingEntity chainTarget = findNearbyTarget(target, level);
            
            if (chainTarget != null) {
                // Create visual arc between entities
                createLightningArc(target.getLocation().add(0, 1, 0), 
                                 chainTarget.getLocation().add(0, 1, 0), 
                                 level);
                
                // Apply chain damage (reduced from primary damage)
                double chainDamage = baseDamage * 0.6;
                chainTarget.damage(chainDamage, attacker);
                
                // Smaller electric effect on chained target
                createElectricEffect(chainTarget.getLocation(), 2.0, level, false);
                
                // Play electric sound on chain target
                chainTarget.getWorld().playSound(chainTarget.getLocation(), 
                                            Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 
                                            0.5f, 1.5f);
            }
        }
    }
    
    /**
     * Creates a visual electric effect at the specified location.
     *
     * @param location The center location of the effect
     * @param radius The radius of the effect
     * @param level The enchantment level
     * @param isFull Whether this is a full effect or reduced effect
     */
    private void createElectricEffect(Location location, double radius, int level, boolean isFull) {
        // Base color for the electric effect - bluer with higher levels
        Color electricColor;
        switch (level) {
            case 1:
                electricColor = Color.fromRGB(150, 150, 255); // Light blue
                break;
            case 2:
                electricColor = Color.fromRGB(100, 100, 255); // Medium blue
                break;
            case 3:
                electricColor = Color.fromRGB(50, 50, 255); // Deep blue
                break;
            default:
                electricColor = Color.fromRGB(150, 150, 255);
        }
        
        // Create a cloud of electric particles
        for (int i = 0; i < (isFull ? 80 : 40); i++) {
            // Random position within the radius
            double x = (random.nextDouble() * 2 - 1) * radius;
            double y = random.nextDouble() * 2 * radius;
            double z = (random.nextDouble() * 2 - 1) * radius;
            
            Location particleLocation = location.clone().add(x, y, z);
            
            // Spawn electric spark particles
            location.getWorld().spawnParticle(
                Particle.ELECTRIC_SPARK,
                particleLocation,
                1,
                0, 0, 0,
                0.05
            );
            
            // Add some dust particles for color effect (less of these)
            if (i % 5 == 0) {
                plugin.getVisualEffectManager().spawnColoredParticles(
                    particleLocation,
                    Particle.DUST,
                    electricColor,
                    1.0f,
                    1
                );
            }
        }
        
        // Add lightning visual effect for full effect
        if (isFull && level >= 2) {
            // Spawn a visual-only lightning strike at higher levels
            location.getWorld().strikeLightningEffect(location);
        }
    }
    
    /**
     * Creates a visual arc of lightning between two locations.
     *
     * @param start The starting location
     * @param end The ending location
     * @param level The enchantment level
     */
    private void createLightningArc(Location start, Location end, int level) {
        // Calculate the direction vector
        double distance = start.distance(end);
        int points = (int) (distance * 3); // More points for longer distances
        
        // Get direction vector
        double dX = (end.getX() - start.getX()) / points;
        double dY = (end.getY() - start.getY()) / points;
        double dZ = (end.getZ() - start.getZ()) / points;
        
        // Create arc with some randomness
        for (int i = 0; i < points; i++) {
            // Add some randomness to create a jagged look
            double offsetX = (random.nextDouble() * 0.5 - 0.25);
            double offsetY = (random.nextDouble() * 0.5 - 0.25);
            double offsetZ = (random.nextDouble() * 0.5 - 0.25);
            
            // Calculate this point along the arc
            Location point = start.clone().add(
                (dX * i) + offsetX,
                (dY * i) + offsetY,
                (dZ * i) + offsetZ
            );
            
            // Spawn particle at this point
            start.getWorld().spawnParticle(
                Particle.ELECTRIC_SPARK,
                point,
                1,
                0, 0, 0,
                0
            );
        }
    }
    
    /**
     * Finds a nearby entity to chain to.
     *
     * @param source The source entity
     * @param level The enchantment level
     * @return A nearby living entity, or null if none found
     */
    private LivingEntity findNearbyTarget(LivingEntity source, int level) {
        // Calculate chain distance based on level
        double chainDistance = 3.0 + (level * 1.0); // 4-6 blocks
        
        // Get nearby entities
        Collection<Entity> nearbyEntities = source.getNearbyEntities(
            chainDistance, chainDistance, chainDistance);
        
        // Filter for living entities and sort by distance
        List<LivingEntity> validTargets = new ArrayList<>();
        
        for (Entity entity : nearbyEntities) {
            if (entity instanceof LivingEntity && entity != source && 
                !(entity instanceof Player)) { // Don't chain to players
                validTargets.add((LivingEntity) entity);
            }
        }
        
        // Return random entity from valid targets, or null if none found
        if (!validTargets.isEmpty()) {
            return validTargets.get(random.nextInt(validTargets.size()));
        }
        
        return null;
    }
    
    @Override
    public boolean conflictsWith(CustomEnchant other) {
        // Conflicts with other electrical/lightning enchantments
        return other.getName().toLowerCase().contains("volta") ||
               other.getName().toLowerCase().contains("electr") ||
               other.getName().toLowerCase().contains("lightning") ||
               other.getName().toLowerCase().contains("thunder");
    }
} 