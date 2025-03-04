package org.frizzlenpop.frizzlenChants.listeners;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.frizzlenpop.frizzlenChants.enchant.CustomEnchant;
import org.frizzlenpop.frizzlenChants.enchant.EnchantmentRegistry;
import org.frizzlenpop.frizzlenChants.enchant.EnchantmentUtils;

import java.util.Map;

/**
 * Listener for detecting and applying enchantment effects.
 * This class handles the various events that can trigger enchantment effects.
 */
public class EnchantmentListener implements Listener {

    private final EnchantmentRegistry registry;
    
    /**
     * Creates a new enchantment listener.
     *
     * @param registry The enchantment registry
     */
    public EnchantmentListener(EnchantmentRegistry registry) {
        this.registry = registry;
    }
    
    /**
     * Handles entity damage events to apply weapon enchantments.
     *
     * @param event The entity damage event
     */
    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player)) {
            return;
        }
        
        Player player = (Player) event.getDamager();
        ItemStack item = player.getInventory().getItemInMainHand();
        
        if (item == null || !item.hasItemMeta()) {
            return;
        }
        
        // Get enchantments on the weapon
        Map<CustomEnchant, Integer> enchantments = EnchantmentUtils.getEnchantments(item, registry);
        for (Map.Entry<CustomEnchant, Integer> entry : enchantments.entrySet()) {
            CustomEnchant enchant = entry.getKey();
            int level = entry.getValue();
            
            // Apply the enchantment effect
            enchant.applyEffect(player, item, level);
        }
    }
    
    /**
     * Handles bow shooting events to apply bow enchantments.
     *
     * @param event The bow shooting event
     */
    @EventHandler
    public void onBowShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        
        Player player = (Player) event.getEntity();
        ItemStack bow = event.getBow();
        
        if (bow == null || !bow.hasItemMeta()) {
            return;
        }
        
        // Get enchantments on the bow
        Map<CustomEnchant, Integer> enchantments = EnchantmentUtils.getEnchantments(bow, registry);
        for (Map.Entry<CustomEnchant, Integer> entry : enchantments.entrySet()) {
            CustomEnchant enchant = entry.getKey();
            int level = entry.getValue();
            
            // Apply the enchantment effect
            enchant.applyEffect(player, bow, level);
        }
    }
    
    /**
     * Handles block breaking events to apply tool enchantments.
     *
     * @param event The block break event
     */
    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        
        if (item == null || !item.hasItemMeta()) {
            return;
        }
        
        // Get enchantments on the tool
        Map<CustomEnchant, Integer> enchantments = EnchantmentUtils.getEnchantments(item, registry);
        for (Map.Entry<CustomEnchant, Integer> entry : enchantments.entrySet()) {
            CustomEnchant enchant = entry.getKey();
            int level = entry.getValue();
            
            // Apply the enchantment effect
            enchant.applyEffect(player, item, level);
        }
    }
    
    /**
     * Handles player interact events to apply item enchantments.
     *
     * @param event The player interact event
     */
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        
        if (item == null || !item.hasItemMeta()) {
            return;
        }
        
        // Get enchantments on the item
        Map<CustomEnchant, Integer> enchantments = EnchantmentUtils.getEnchantments(item, registry);
        for (Map.Entry<CustomEnchant, Integer> entry : enchantments.entrySet()) {
            CustomEnchant enchant = entry.getKey();
            int level = entry.getValue();
            
            // Apply the enchantment effect
            enchant.applyEffect(player, item, level);
        }
    }
    
    /**
     * Handles player movement events to apply armor enchantments.
     *
     * @param event The player move event
     */
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        // Only process every few ticks to avoid lag
        if (event.getFrom().getBlockX() == event.getTo().getBlockX() &&
            event.getFrom().getBlockY() == event.getTo().getBlockY() &&
            event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }
        
        Player player = event.getPlayer();
        
        // Check all armor pieces
        for (ItemStack armorPiece : player.getInventory().getArmorContents()) {
            if (armorPiece == null || !armorPiece.hasItemMeta()) {
                continue;
            }
            
            // Get enchantments on the armor
            Map<CustomEnchant, Integer> enchantments = EnchantmentUtils.getEnchantments(armorPiece, registry);
            for (Map.Entry<CustomEnchant, Integer> entry : enchantments.entrySet()) {
                CustomEnchant enchant = entry.getKey();
                int level = entry.getValue();
                
                // Apply the enchantment effect
                enchant.applyEffect(player, armorPiece, level);
            }
        }
    }
} 