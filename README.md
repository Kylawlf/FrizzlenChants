# FrizzlenChants

A fully modular custom enchantment system for Minecraft 1.21 using Paper and ProtocolLib.

## Features

- **Modular Design**: Add new enchantments simply by creating a new Java class
- **Automatic Registration**: Enchantments are discovered and registered at runtime using reflection
- **Lore-Based Storage**: Enchantments are stored in item lore for maximum compatibility
- **Visual Effects**: Custom visual effects using ProtocolLib
- **Conflict Management**: System for defining conflicts between enchantments
- **Permission System**: Configurable permission system for applying enchantments

## Installation

1. Download the latest release JAR file
2. Place it in your server's `plugins` directory
3. Install ProtocolLib (required dependency)
4. Restart your server

## Usage

### Commands

- `/customenchant <enchantment> [level]` (aliases: `/cenchant`, `/ce`): Apply a custom enchantment to the item in your hand

### Permissions

- `frizzlenchants.enchant`: Allows players to use the `/customenchant` command (default: op)

## Creating Custom Enchantments

Creating a new enchantment is as simple as creating a new Java class that extends `CustomEnchant`. Place your class in the `org.frizzlenpop.frizzlenChants.impl` package, and it will be automatically discovered and registered when the plugin starts.

### Example Enchantment

```java
package org.frizzlenpop.frizzlenChants.impl;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.frizzlenpop.frizzlenChants.enchant.CustomEnchant;

public class ExampleEnchant extends CustomEnchant {

    public ExampleEnchant() {
        super("Example", 3, Material.DIAMOND_SWORD, Material.NETHERITE_SWORD);
    }

    @Override
    public void applyEffect(Player player, ItemStack item, int level) {
        // Implement your enchantment effect here
    }
    
    @Override
    public boolean conflictsWith(CustomEnchant other) {
        // Define conflicts with other enchantments
        return false;
    }
}
```

### Key Methods to Implement

1. **Constructor**: Define the name, maximum level, and applicable items
2. **applyEffect**: Implement the enchantment's effect
3. **conflictsWith** (optional): Define conflicts with other enchantments

### Event Handling

Your enchantment class can also implement Bukkit event listeners to respond to specific events. Since the class implements `Listener` and is registered automatically, you can simply add event handlers:

```java
@EventHandler
public void onEntityDamage(EntityDamageByEntityEvent event) {
    // Your event handling code here
}
```

## System Architecture

The plugin consists of several key components:

1. **CustomEnchant**: Base class for all enchantments
2. **EnchantmentRegistry**: Central registry for all enchantments
3. **EnchantmentLoader**: Uses reflection to discover and load enchantments
4. **EnchantmentUtils**: Utility methods for working with enchantments
5. **VisualEffectManager**: Handles visual effects using ProtocolLib
6. **EnchantmentListener**: Global listener for enchantment triggers
7. **EnchantCommand**: Command for applying enchantments

## Built-in Enchantments

The plugin comes with several example enchantments:

1. **Inferno**: Sets entities on fire with visual effects (Levels 1-3)
2. **Swift**: Increases movement speed and creates particle trails (Levels 1-3)
3. **Explosive**: Creates explosions when arrows hit targets (Levels 1-3)

## Dependencies

- Paper 1.21+
- ProtocolLib 5.1.0+

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Contributing

Contributions are welcome! Feel free to submit pull requests or create issues for bugs and feature requests. 