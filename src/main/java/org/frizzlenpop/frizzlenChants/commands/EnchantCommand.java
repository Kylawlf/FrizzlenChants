package org.frizzlenpop.frizzlenChants.commands;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.frizzlenpop.frizzlenChants.enchant.CustomEnchant;
import org.frizzlenpop.frizzlenChants.enchant.EnchantmentRegistry;
import org.frizzlenpop.frizzlenChants.enchant.EnchantmentUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Command for applying custom enchantments to items.
 * Usage: /enchant <enchantment> [level]
 */
public class EnchantCommand implements CommandExecutor, TabCompleter {

    private final EnchantmentRegistry registry;
    
    /**
     * Creates a new enchant command.
     *
     * @param registry The enchantment registry
     */
    public EnchantCommand(EnchantmentRegistry registry) {
        this.registry = registry;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return true;
        }
        
        Player player = (Player) sender;
        
        if (!player.hasPermission("frizzlenchants.enchant")) {
            player.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }
        
        if (args.length < 1) {
            player.sendMessage(ChatColor.RED + "Usage: /" + label + " <enchantment> [level]");
            return true;
        }
        
        String enchantName = args[0];
        int level = 1;
        
        if (args.length > 1) {
            try {
                level = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                player.sendMessage(ChatColor.RED + "Invalid level. Please specify a number.");
                return true;
            }
        }
        
        ItemStack item = player.getInventory().getItemInMainHand();
        
        if (item == null || item.getType().isAir()) {
            player.sendMessage(ChatColor.RED + "You must hold an item to enchant it.");
            return true;
        }
        
        Optional<CustomEnchant> enchantOpt = registry.getEnchantment(enchantName);
        
        if (!enchantOpt.isPresent()) {
            player.sendMessage(ChatColor.RED + "Unknown enchantment: " + enchantName);
            return true;
        }
        
        CustomEnchant enchant = enchantOpt.get();
        
        if (level <= 0 || level > enchant.getMaxLevel()) {
            player.sendMessage(ChatColor.RED + "Invalid level. The maximum level for " + 
                    enchant.getName() + " is " + enchant.getMaxLevel() + ".");
            return true;
        }
        
        if (!enchant.canApplyTo(item)) {
            player.sendMessage(ChatColor.RED + "This enchantment cannot be applied to this item.");
            return true;
        }
        
        // Check for conflicting enchantments
        for (CustomEnchant existingEnchant : EnchantmentUtils.getEnchantments(item, registry).keySet()) {
            if (existingEnchant.conflictsWith(enchant) || enchant.conflictsWith(existingEnchant)) {
                player.sendMessage(ChatColor.RED + "This enchantment conflicts with " + 
                        existingEnchant.getName() + ".");
                return true;
            }
        }
        
        // Apply the enchantment
        ItemStack result = EnchantmentUtils.applyEnchantment(item, enchant, level, registry);
        player.getInventory().setItemInMainHand(result);
        
        player.sendMessage(ChatColor.GREEN + "Applied " + enchant.getName() + " " + 
                toRoman(level) + " to your item.");
        
        return true;
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            
            // Return enchantment names that start with the partial input
            return registry.getAllEnchantments().stream()
                    .map(CustomEnchant::getName)
                    .filter(name -> name.toLowerCase().startsWith(partial))
                    .collect(Collectors.toList());
        } else if (args.length == 2) {
            Optional<CustomEnchant> enchantOpt = registry.getEnchantment(args[0]);
            
            if (enchantOpt.isPresent()) {
                CustomEnchant enchant = enchantOpt.get();
                List<String> levels = new ArrayList<>();
                
                // Return possible levels for the enchantment
                for (int i = 1; i <= enchant.getMaxLevel(); i++) {
                    levels.add(String.valueOf(i));
                }
                
                return levels;
            }
        }
        
        return new ArrayList<>();
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