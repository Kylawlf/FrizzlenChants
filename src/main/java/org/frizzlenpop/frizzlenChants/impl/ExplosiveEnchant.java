package org.frizzlenpop.frizzlenChants.impl;

import org.bukkit.Material;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.frizzlenpop.frizzlenChants.FrizzlenChants;
import org.frizzlenpop.frizzlenChants.enchant.CustomEnchant;
import org.frizzlenpop.frizzlenChants.enchant.EnchantmentUtils;

/**
 * Explosive enchantment for bows that creates explosions when arrows hit.
 */
public class ExplosiveEnchant extends CustomEnchant {

    private static final String METADATA_KEY = "explosive_level";
    private final FrizzlenChants plugin;
    
    public ExplosiveEnchant() {
        super("Explosive", 3, Material.BOW, Material.CROSSBOW);
        this.plugin = FrizzlenChants.getPlugin(FrizzlenChants.class);
    }

    @Override
    public void applyEffect(Player player, ItemStack item, int level) {
        // This is called from the global listener
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
            
            // Apply visual effects to the arrow
            arrow.setGlowing(true);
            arrow.setFireTicks(Integer.MAX_VALUE); // Make the arrow appear on fire
        }
    }
    
    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Arrow)) {
            return;
        }
        
        Arrow arrow = (Arrow) event.getEntity();
        
        // Check if this arrow was shot with the explosive enchantment
        if (!arrow.hasMetadata(METADATA_KEY)) {
            return;
        }
        
        // Get the level of the enchantment
        int level = arrow.getMetadata(METADATA_KEY).get(0).asInt();
        
        // Create an explosion when the arrow lands
        float power = 0.5f + (level * 0.5f); // Scale from 1.0F to 2.0F based on level
        boolean setFire = level >= 3; // Only set fire at max level
        boolean breakBlocks = false; // Don't break blocks to avoid griefing
        
        // Create the explosion
        arrow.getWorld().createExplosion(
            arrow.getLocation(),
            power,
            setFire,
            breakBlocks,
            arrow.getShooter() instanceof Player ? (Player) arrow.getShooter() : null
        );
        
        // Remove the arrow to prevent it from being picked up
        arrow.remove();
    }
    
    @Override
    public boolean conflictsWith(CustomEnchant other) {
        // Conflicts with other explosion-related enchantments
        return other.getName().toLowerCase().contains("explo") ||
               other.getName().toLowerCase().contains("blast") ||
               other.getName().toLowerCase().contains("bomb");
    }
} 