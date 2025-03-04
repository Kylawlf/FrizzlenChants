package org.frizzlenpop.frizzlenChants.enchant;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;

/**
 * Registry for all custom enchantments in the FrizzlenChants system.
 * This class manages the registration and retrieval of enchantments.
 */
public class EnchantmentRegistry {

    private final Map<String, CustomEnchant> enchantments;
    private final Plugin plugin;
    
    /**
     * Creates a new enchantment registry for the specified plugin.
     *
     * @param plugin The plugin that owns this registry
     */
    public EnchantmentRegistry(Plugin plugin) {
        this.enchantments = new HashMap<>();
        this.plugin = plugin;
    }
    
    /**
     * Registers a custom enchantment in the registry.
     * Also registers the enchantment as a Bukkit event listener.
     *
     * @param enchant The enchantment to register
     * @return true if registration was successful, false if an enchantment with the same name already exists
     */
    public boolean register(CustomEnchant enchant) {
        if (enchant == null) {
            plugin.getLogger().warning("Attempted to register a null enchantment");
            return false;
        }
        
        String name = enchant.getName().toLowerCase();
        if (enchantments.containsKey(name)) {
            plugin.getLogger().warning("Attempted to register duplicate enchantment: " + enchant.getName());
            return false;
        }
        
        enchantments.put(name, enchant);
        Bukkit.getPluginManager().registerEvents(enchant, plugin);
        plugin.getLogger().log(Level.INFO, "Registered enchantment: " + enchant.getName());
        return true;
    }
    
    /**
     * Unregisters an enchantment from the registry.
     *
     * @param name The name of the enchantment to unregister
     * @return The unregistered enchantment, or null if none was found
     */
    public CustomEnchant unregister(String name) {
        return enchantments.remove(name.toLowerCase());
    }
    
    /**
     * Gets an enchantment by its name.
     *
     * @param name The name of the enchantment to retrieve
     * @return Optional containing the enchantment if found, or empty if not found
     */
    public Optional<CustomEnchant> getEnchantment(String name) {
        return Optional.ofNullable(enchantments.get(name.toLowerCase()));
    }
    
    /**
     * Returns all registered enchantments.
     *
     * @return Collection of all enchantments
     */
    public Collection<CustomEnchant> getAllEnchantments() {
        return enchantments.values();
    }
    
    /**
     * Checks if an enchantment with the given name is registered.
     *
     * @param name The name to check
     * @return true if the enchantment exists, false otherwise
     */
    public boolean isRegistered(String name) {
        return enchantments.containsKey(name.toLowerCase());
    }
    
    /**
     * Returns the number of registered enchantments.
     *
     * @return The number of enchantments
     */
    public int size() {
        return enchantments.size();
    }
} 