package org.frizzlenpop.frizzlenChants.impl;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.frizzlenpop.frizzlenChants.FrizzlenChants;
import org.frizzlenpop.frizzlenChants.enchant.CustomEnchant;
import org.frizzlenpop.frizzlenChants.enchant.EnchantmentUtils;

/**
 * Soul Siphon enchantment for weapons that heals the player for a percentage
 * of the damage they deal to enemies.
 */
public class SoulSiphonEnchant extends CustomEnchant {

    private final FrizzlenChants plugin;
    
    public SoulSiphonEnchant() {
        super("SoulSiphon", 3, 
              Material.WOODEN_SWORD, Material.STONE_SWORD, Material.IRON_SWORD, 
              Material.GOLDEN_SWORD, Material.DIAMOND_SWORD, Material.NETHERITE_SWORD,
              Material.TRIDENT, Material.BOW, Material.CROSSBOW, 
              Material.WOODEN_AXE, Material.STONE_AXE, Material.IRON_AXE,
              Material.GOLDEN_AXE, Material.DIAMOND_AXE, Material.NETHERITE_AXE);
        this.plugin = FrizzlenChants.getPlugin(FrizzlenChants.class);
    }

    @Override
    public void applyEffect(Player player, ItemStack item, int level) {
        // Implementation is handled in the event handler below
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        // Only process if damage wasn't cancelled and event is at MONITOR stage
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
        
        // Get the damage amount
        double damage = event.getFinalDamage();
        
        // Calculate healing percentage based on level (5% per level)
        double siphonPercentage = 0.05 + (level * 0.05); // 10% to 20%
        double healAmount = damage * siphonPercentage;
        
        // Cap healing to avoid excessive amounts
        healAmount = Math.min(healAmount, 6.0);
        
        // Apply healing to player
        applySoulSiphon(player, target, healAmount, level);
    }
    
    /**
     * Applies the soul siphon effect, healing the player and creating visuals.
     *
     * @param player The player to heal
     * @param target The entity damaged
     * @param healAmount The amount to heal
     * @param level The enchantment level
     */
    private void applySoulSiphon(Player player, LivingEntity target, double healAmount, int level) {
        // Get player's current and max health
        double currentHealth = player.getHealth();
        double maxHealth = player.getMaxHealth();
        
        // Don't heal if player is at max health
        if (currentHealth >= maxHealth) {
            return;
        }
        
        // Calculate new health, not exceeding max
        double newHealth = Math.min(currentHealth + healAmount, maxHealth);
        
        // Apply the healing (asynchronously to avoid event conflicts)
        player.setHealth(newHealth);
        
        // Show visual effects
        createSiphonEffect(player, target, level);
        
        // Play sound effect
        player.playSound(player.getLocation(), Sound.ENTITY_WITHER_HURT, 0.4f, 1.5f);
        
        // Send action bar message
        String healMessage = String.format("§4Soul Siphon §8» §c+%.1f❤", healAmount);
        player.sendActionBar(net.kyori.adventure.text.Component.text(healMessage));
    }
    
    /**
     * Creates a visual effect showing soul energy being siphoned from target to player.
     *
     * @param player The player siphoning health
     * @param target The entity being siphoned from
     * @param level The enchantment level
     */
    private void createSiphonEffect(Player player, LivingEntity target, int level) {
        // Create particle trail from target to player
        Location targetLoc = target.getLocation().add(0, 1.0, 0);
        Location playerLoc = player.getLocation().add(0, 1.0, 0);
        
        // Get direction vector
        Vector direction = playerLoc.toVector().subtract(targetLoc.toVector()).normalize();
        double distance = targetLoc.distance(playerLoc);
        
        // Calculate how many particles based on distance
        int particleCount = (int) (distance * 2) + 5;
        
        // Soul color (darker red with higher levels)
        Color soulColor;
        switch (level) {
            case 1:
                soulColor = Color.fromRGB(200, 0, 0); // Bright red
                break;
            case 2:
                soulColor = Color.fromRGB(150, 0, 20); // Dark red
                break;
            case 3:
                soulColor = Color.fromRGB(100, 0, 40); // Very dark red with purple tint
                break;
            default:
                soulColor = Color.fromRGB(200, 0, 0);
        }
        
        // Create the particle trail
        for (int i = 0; i < particleCount; i++) {
            double progress = (double) i / particleCount;
            Vector pos = targetLoc.toVector().add(direction.clone().multiply(distance * progress));
            Location particleLoc = pos.toLocation(target.getWorld());
            
            // Use colored particles
            plugin.getVisualEffectManager().spawnColoredParticles(
                particleLoc,
                Particle.DUST,
                soulColor,
                1.0f,
                1
            );
            
            // Add some soul particles for higher levels
            if (level >= 2 && i % 2 == 0) {
                target.getWorld().spawnParticle(
                    Particle.SOUL,
                    particleLoc,
                    1,
                    0.05, 0.05, 0.05,
                    0.01
                );
            }
        }
        
        // Add a burst effect at the player for visual feedback
        if (level >= 2) {
            player.getWorld().spawnParticle(
                Particle.HEART,
                player.getLocation().add(0, 1.8, 0),
                level,
                0.2, 0.1, 0.2,
                0.01
            );
        }
    }
    
    @Override
    public boolean conflictsWith(CustomEnchant other) {
        // Conflicts with other life-stealing enchantments
        return other.getName().toLowerCase().contains("siphon") ||
               other.getName().toLowerCase().contains("leech") ||
               other.getName().toLowerCase().contains("vampir") ||
               other.getName().toLowerCase().contains("drain");
    }
} 