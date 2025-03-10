package org.frizzlenpop.frizzlenChants.impl;

import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.frizzlenpop.frizzlenChants.enchant.CustomEnchant;

/**
 * Custom Fire Aspect enchantment that sets entities on fire and
 * adds a visual fire effect when attacking.
 */
public class FireAspectEnchant extends CustomEnchant {

    public FireAspectEnchant() {
        super("Inferno", 3, 
              Material.WOODEN_SWORD, Material.STONE_SWORD, Material.IRON_SWORD, 
              Material.GOLDEN_SWORD, Material.DIAMOND_SWORD, Material.NETHERITE_SWORD);
    }

    @Override
    public void applyEffect(Player player, ItemStack item, int level) {
        // This method is called from the global listener
        // We don't need to implement it here since we use the event handler below
    }
    
    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player)) {
            return;
        }
        
        Player player = (Player) event.getDamager();
        ItemStack item = player.getInventory().getItemInMainHand();
        
        // Check if the player has this enchantment on their weapon
        if (!isEnchanted(player, item)) {
            return;
        }
        
        int level = getEnchantmentLevel(player, item);
        Entity target = event.getEntity();
        
        // Apply fire effect to the target
        if (target instanceof LivingEntity) {
            // Set target on fire (2 seconds per level)
            target.setFireTicks(level * 20);
            
            // Add visual effects
            target.getWorld().spawnParticle(Particle.FLAME, target.getLocation().add(0, 1, 0), 
                    2 * level, 0.5, 0.5, 0.5, 0.1);
            target.getWorld().playSound(target.getLocation(), Sound.ENTITY_GENERIC_BURN, 1.0f, 1.0f);
            
        }
    }
    
    /**
     * Helper method to check if the player's item has this enchantment.
     * In a real implementation, this would use the EnchantmentUtils class,
     * but for simplicity we're checking the item lore directly.
     */
    private boolean isEnchanted(Player player, ItemStack item) {
        if (item == null || !item.hasItemMeta() || !item.getItemMeta().hasLore()) {
            return false;
        }
        
        for (String lore : item.getItemMeta().getLore()) {
            if (lore.contains(getName())) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Helper method to get the level of this enchantment on the item.
     * In a real implementation, this would use the EnchantmentUtils class,
     * but for simplicity we're parsing the item lore directly.
     */
    private int getEnchantmentLevel(Player player, ItemStack item) {
        if (item == null || !item.hasItemMeta() || !item.getItemMeta().hasLore()) {
            return 0;
        }
        
        for (String lore : item.getItemMeta().getLore()) {
            if (lore.contains(getName())) {
                if (lore.contains("I")) return 1;
                if (lore.contains("II")) return 2;
                if (lore.contains("III")) return 3;
                return 1;
            }
        }
        
        return 0;
    }
    
    @Override
    public boolean conflictsWith(CustomEnchant other) {
        // This enchantment conflicts with any other fire-based enchantment
        return other.getName().toLowerCase().contains("fire") || 
               other.getName().toLowerCase().contains("flame") ||
               other.getName().toLowerCase().contains("burn");
    }
} 