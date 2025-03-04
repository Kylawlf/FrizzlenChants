package org.frizzlenpop.frizzlenChants.enchant;

import org.bukkit.ChatColor;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for handling enchantment-related functions.
 * Provides methods for applying, retrieving, and removing enchantments from items.
 */
public class EnchantmentUtils {

    private static final Pattern ENCHANT_PATTERN = Pattern.compile("§7([\\w\\s]+) ([IVXLCDM]+)");
    
    /**
     * Private constructor to prevent instantiation of utility class.
     */
    private EnchantmentUtils() {
    }
    
    /**
     * Applies a custom enchantment to an item.
     *
     * @param item The item to enchant
     * @param enchant The enchantment to apply
     * @param level The level of the enchantment
     * @param registry The enchantment registry
     * @return The enchanted item, or null if the enchantment could not be applied
     */
    public static ItemStack applyEnchantment(ItemStack item, CustomEnchant enchant, int level, EnchantmentRegistry registry) {
        if (item == null || enchant == null || level <= 0 || level > enchant.getMaxLevel()) {
            return item;
        }
        
        if (!enchant.canApplyTo(item)) {
            return item;
        }
        
        // Check for conflicting enchantments
        Map<CustomEnchant, Integer> existingEnchants = getEnchantments(item, registry);
        for (CustomEnchant existing : existingEnchants.keySet()) {
            if (existing.conflictsWith(enchant) || enchant.conflictsWith(existing)) {
                return item;
            }
        }
        
        // Clone the item to avoid modifying the original
        ItemStack result = item.clone();
        ItemMeta meta = result.getItemMeta();
        
        if (meta == null) {
            return item;
        }
        
        // Get the current lore or create a new list
        List<String> lore = meta.hasLore() ? meta.getLore() : new ArrayList<>();
        if (lore == null) {
            lore = new ArrayList<>();
        }
        
        // Remove any existing instances of this enchantment
        removeEnchantmentFromLore(lore, enchant);
        
        // Add the enchantment to the lore
        lore.add(enchant.getDisplayName(level));
        
        // Sort enchantments to keep them in a consistent order
        sortEnchantmentLore(lore);
        
        meta.setLore(lore);
        result.setItemMeta(meta);
        
        return result;
    }
    
    /**
     * Retrieves all custom enchantments applied to an item.
     *
     * @param item The item to check
     * @param registry The enchantment registry
     * @return Map of enchantments and their levels
     */
    public static Map<CustomEnchant, Integer> getEnchantments(ItemStack item, EnchantmentRegistry registry) {
        Map<CustomEnchant, Integer> result = new HashMap<>();
        
        if (item == null || !item.hasItemMeta()) {
            return result;
        }
        
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasLore()) {
            return result;
        }
        
        List<String> lore = meta.getLore();
        if (lore == null) {
            return result;
        }
        
        for (String line : lore) {
            Matcher matcher = ENCHANT_PATTERN.matcher(line);
            if (matcher.matches()) {
                String enchantName = matcher.group(1).trim();
                String levelStr = matcher.group(2);
                
                Optional<CustomEnchant> enchant = registry.getEnchantment(enchantName);
                if (enchant.isPresent()) {
                    int level = romanToInt(levelStr);
                    result.put(enchant.get(), level);
                }
            }
        }
        
        return result;
    }
    
    /**
     * Checks if an item has a specific custom enchantment.
     *
     * @param item The item to check
     * @param enchant The enchantment to look for
     * @param registry The enchantment registry
     * @return The level of the enchantment, or 0 if the item doesn't have the enchantment
     */
    public static int getEnchantmentLevel(ItemStack item, CustomEnchant enchant, EnchantmentRegistry registry) {
        Map<CustomEnchant, Integer> enchants = getEnchantments(item, registry);
        return enchants.getOrDefault(enchant, 0);
    }
    
    /**
     * Removes a custom enchantment from an item.
     *
     * @param item The item to modify
     * @param enchant The enchantment to remove
     * @return The modified item, or null if the item could not be modified
     */
    public static ItemStack removeEnchantment(ItemStack item, CustomEnchant enchant) {
        if (item == null || enchant == null || !item.hasItemMeta()) {
            return item;
        }
        
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasLore()) {
            return item;
        }
        
        List<String> lore = meta.getLore();
        if (lore == null) {
            return item;
        }
        
        // Clone the item to avoid modifying the original
        ItemStack result = item.clone();
        meta = result.getItemMeta();
        lore = new ArrayList<>(lore);
        
        if (removeEnchantmentFromLore(lore, enchant)) {
            meta.setLore(lore);
            result.setItemMeta(meta);
            return result;
        }
        
        return item;
    }
    
    /**
     * Sorts the lore to keep enchantments at the top and in a consistent order.
     *
     * @param lore The lore list to sort
     */
    private static void sortEnchantmentLore(List<String> lore) {
        List<String> enchantments = new ArrayList<>();
        List<String> otherLore = new ArrayList<>();
        
        // Separate enchantments from other lore
        for (String line : lore) {
            if (ENCHANT_PATTERN.matcher(line).matches()) {
                enchantments.add(line);
            } else {
                otherLore.add(line);
            }
        }
        
        // Sort enchantments alphabetically
        Collections.sort(enchantments);
        
        // Clear and rebuild the lore with enchantments at the top
        lore.clear();
        lore.addAll(enchantments);
        lore.addAll(otherLore);
    }
    
    /**
     * Removes a specific enchantment from the lore.
     *
     * @param lore The lore to modify
     * @param enchant The enchantment to remove
     * @return true if the enchantment was found and removed, false otherwise
     */
    private static boolean removeEnchantmentFromLore(List<String> lore, CustomEnchant enchant) {
        String enchantName = ChatColor.GRAY + enchant.getName();
        Iterator<String> iterator = lore.iterator();
        boolean removed = false;
        
        while (iterator.hasNext()) {
            String line = iterator.next();
            if (line.startsWith(enchantName + " ")) {
                iterator.remove();
                removed = true;
            }
        }
        
        return removed;
    }
    
    /**
     * Converts a Roman numeral to an integer.
     *
     * @param roman The Roman numeral to convert
     * @return The integer value
     */
    private static int romanToInt(String roman) {
        Map<Character, Integer> romanMap = new HashMap<>();
        romanMap.put('I', 1);
        romanMap.put('V', 5);
        romanMap.put('X', 10);
        romanMap.put('L', 50);
        romanMap.put('C', 100);
        romanMap.put('D', 500);
        romanMap.put('M', 1000);
        
        int result = 0;
        int prevValue = 0;
        
        for (int i = roman.length() - 1; i >= 0; i--) {
            int currentValue = romanMap.getOrDefault(roman.charAt(i), 0);
            
            if (currentValue >= prevValue) {
                result += currentValue;
            } else {
                result -= currentValue;
            }
            
            prevValue = currentValue;
        }
        
        return result;
    }
} 