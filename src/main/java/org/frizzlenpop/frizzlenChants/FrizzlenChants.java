package org.frizzlenpop.frizzlenChants;

import org.bukkit.plugin.java.JavaPlugin;
import org.frizzlenpop.frizzlenChants.commands.EnchantCommand;
import org.frizzlenpop.frizzlenChants.enchant.EnchantmentLoader;
import org.frizzlenpop.frizzlenChants.enchant.EnchantmentRegistry;
import org.frizzlenpop.frizzlenChants.listeners.EnchantmentListener;
import org.frizzlenpop.frizzlenChants.visual.VisualEffectManager;

import java.util.logging.Level;

/**
 * Main plugin class for FrizzlenChants.
 * Manages the initialization and shutdown of the plugin components.
 */
public final class FrizzlenChants extends JavaPlugin {

    private EnchantmentRegistry enchantmentRegistry;
    private VisualEffectManager visualEffectManager;
    
    @Override
    public void onEnable() {
        // Create the enchantment registry
        enchantmentRegistry = new EnchantmentRegistry(this);
        
        // Create the visual effect manager
        visualEffectManager = new VisualEffectManager(this);
        
        // Load enchantments using reflection
        EnchantmentLoader loader = new EnchantmentLoader(this, enchantmentRegistry, "org.frizzlenpop.frizzlenChants.impl");
        int loaded = loader.loadEnchantments();
        getLogger().log(Level.INFO, "Loaded " + loaded + " custom enchantments");
        
        // Register the enchantment listener
        EnchantmentListener listener = new EnchantmentListener(enchantmentRegistry);
        getServer().getPluginManager().registerEvents(listener, this);
        
        // Register the enchant command
        EnchantCommand enchantCommand = new EnchantCommand(enchantmentRegistry);
        getCommand("customenchant").setExecutor(enchantCommand);
        getCommand("customenchant").setTabCompleter(enchantCommand);
        
        getLogger().log(Level.INFO, "FrizzlenChants has been enabled!");
    }
    
    @Override
    public void onDisable() {
        // Cancel any active visual effects
        if (visualEffectManager != null) {
            visualEffectManager.cancelAllEffects();
        }
        
        getLogger().log(Level.INFO, "FrizzlenChants has been disabled!");
    }
    
    /**
     * Gets the enchantment registry.
     *
     * @return The enchantment registry
     */
    public EnchantmentRegistry getEnchantmentRegistry() {
        return enchantmentRegistry;
    }
    
    /**
     * Gets the visual effect manager.
     *
     * @return The visual effect manager
     */
    public VisualEffectManager getVisualEffectManager() {
        return visualEffectManager;
    }
}
