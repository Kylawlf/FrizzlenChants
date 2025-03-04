package org.frizzlenpop.frizzlenChants.enchant;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Base class for all custom enchantments in the FrizzlenChants system.
 * All enchantments must extend this class and implement its abstract methods.
 */
public abstract class CustomEnchant implements Listener {

    private final String name;
    private final int maxLevel;
    private final Set<Material> applicableItems;
    private final UUID uuid;
    
    /**
     * Constructor for creating a new custom enchantment.
     *
     * @param name The name of the enchantment
     * @param maxLevel The maximum level this enchantment can reach
     * @param applicableItems Set of items this enchantment can be applied to
     */
    public CustomEnchant(String name, int maxLevel, Set<Material> applicableItems) {
        this.name = name;
        this.maxLevel = maxLevel;
        this.applicableItems = applicableItems;
        this.uuid = UUID.randomUUID();
    }
    
    /**
     * Convenience constructor that takes varargs for applicable items.
     *
     * @param name The name of the enchantment
     * @param maxLevel The maximum level this enchantment can reach
     * @param applicableItems Array of materials this enchantment can be applied to
     */
    public CustomEnchant(String name, int maxLevel, Material... applicableItems) {
        this(name, maxLevel, new HashSet<>(Arrays.asList(applicableItems)));
    }
    
    /**
     * Returns the name of this enchantment.
     *
     * @return The enchantment name
     */
    public String getName() {
        return name;
    }
    
    /**
     * Returns the maximum level this enchantment can reach.
     *
     * @return The maximum level
     */
    public int getMaxLevel() {
        return maxLevel;
    }
    
    /**
     * Returns the set of materials this enchantment can be applied to.
     *
     * @return Set of applicable materials
     */
    public Set<Material> getApplicableItems() {
        return applicableItems;
    }
    
    /**
     * Returns the UUID of this enchantment instance.
     *
     * @return The UUID
     */
    public UUID getUUID() {
        return uuid;
    }
    
    /**
     * Checks if this enchantment can be applied to the given item.
     *
     * @param item The item to check
     * @return true if the enchantment can be applied, false otherwise
     */
    public boolean canApplyTo(ItemStack item) {
        return item != null && applicableItems.contains(item.getType());
    }
    
    /**
     * Checks if this enchantment conflicts with another enchantment.
     * Override this method to define custom conflict logic.
     *
     * @param other The other enchantment to check against
     * @return true if there is a conflict, false otherwise
     */
    public boolean conflictsWith(CustomEnchant other) {
        return false; // Default: no conflicts
    }
    
    /**
     * Abstract method that defines the logic for applying the enchantment's effect.
     * This method should be implemented by all subclasses.
     *
     * @param player The player using the enchanted item
     * @param item The enchanted item
     * @param level The level of the enchantment
     */
    public abstract void applyEffect(Player player, ItemStack item, int level);
    
    /**
     * Returns the display name of the enchantment with its level in Roman numerals.
     *
     * @param level The level of the enchantment
     * @return The formatted display name
     */
    public String getDisplayName(int level) {
        return "§7" + getName() + " " + toRoman(level);
    }
    
    /**
     * Converts an integer to its Roman numeral representation.
     *
     * @param number The number to convert
     * @return The Roman numeral representation
     */
    private String toRoman(int number) {
        if (number <= 0) {
            return "I";
        }
        
        String[] thousands = {"", "M", "MM", "MMM"};
        String[] hundreds = {"", "C", "CC", "CCC", "CD", "D", "DC", "DCC", "DCCC", "CM"};
        String[] tens = {"", "X", "XX", "XXX", "XL", "L", "LX", "LXX", "LXXX", "XC"};
        String[] ones = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX"};
        
        return thousands[number / 1000] + 
               hundreds[(number % 1000) / 100] + 
               tens[(number % 100) / 10] + 
               ones[number % 10];
    }
} 