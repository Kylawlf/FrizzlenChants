package org.frizzlenpop.frizzlenChants.impl;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.frizzlenpop.frizzlenChants.FrizzlenChants;
import org.frizzlenpop.frizzlenChants.enchant.CustomEnchant;
import org.frizzlenpop.frizzlenChants.enchant.EnchantmentUtils;

/**
 * Speed enchantment that increases the player's movement speed
 * and creates particle trails when moving.
 */
public class SpeedEnchant extends CustomEnchant {

    private final FrizzlenChants plugin;
    
    public SpeedEnchant() {
        super("Swift", 3, Material.LEATHER_BOOTS, Material.CHAINMAIL_BOOTS, 
              Material.IRON_BOOTS, Material.GOLDEN_BOOTS, Material.DIAMOND_BOOTS, 
              Material.NETHERITE_BOOTS);
        
        // Get the plugin instance for visual effects
        this.plugin = FrizzlenChants.getPlugin(FrizzlenChants.class);
    }

    @Override
    public void applyEffect(Player player, ItemStack item, int level) {
        // This is called from the global listener
        // For passive effects like speed, we'll use the event handler below
    }
    
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        // Only process when the player actually moves to a new block
        if (event.getFrom().getBlockX() == event.getTo().getBlockX() &&
            event.getFrom().getBlockY() == event.getTo().getBlockY() &&
            event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }
        
        Player player = event.getPlayer();
        ItemStack boots = player.getInventory().getBoots();
        
        if (boots == null || !boots.hasItemMeta()) {
            return;
        }
        
        // Check if boots have this enchantment
        int level = EnchantmentUtils.getEnchantmentLevel(boots, this, plugin.getEnchantmentRegistry());
        
        if (level <= 0) {
            return;
        }
        
        // Apply speed effect
        PotionEffect speedEffect = new PotionEffect(
            PotionEffectType.SPEED, 
            40, // Duration: 2 seconds
            level - 1, // Amplifier (0 = Speed I)
            false, // No ambient particles
            false, // No particles
            true // Show icon
        );
        
        player.addPotionEffect(speedEffect);
        
        // Only create particles when player is sprinting
        if (player.isSprinting()) {
            // Create particle trail
            Color trailColor;
            switch (level) {
                case 1: 
                    trailColor = Color.AQUA;
                    break;
                case 2:
                    trailColor = Color.BLUE;
                    break;
                case 3:
                    trailColor = Color.PURPLE;
                    break;
                default:
                    trailColor = Color.WHITE;
            }
            
            // Use the visual effect manager to create a particle trail
            plugin.getVisualEffectManager().spawnColoredParticles(
                player.getLocation().add(0, 0.1, 0),
                Particle.DUST,
                trailColor,
                1.0f,
                5
            );
        }
    }
    
    @Override
    public boolean conflictsWith(CustomEnchant other) {
        // Conflicts with other movement-related enchantments
        return other.getName().toLowerCase().contains("speed") ||
               other.getName().toLowerCase().contains("swift") ||
               other.getName().toLowerCase().contains("quick");
    }
} 