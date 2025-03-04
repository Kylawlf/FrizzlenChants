package org.frizzlenpop.frizzlenChants.enchant;

import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.logging.Level;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Loader for dynamically discovering and loading custom enchantments.
 * Uses reflection to find all classes that extend CustomEnchant in the
 * specified package and registers them in the EnchantmentRegistry.
 */
public class EnchantmentLoader {

    private final Plugin plugin;
    private final EnchantmentRegistry registry;
    private final String packageName;
    
    /**
     * Creates a new enchantment loader.
     *
     * @param plugin The plugin instance
     * @param registry The enchantment registry to register enchantments in
     * @param packageName The package to scan for enchantment classes
     */
    public EnchantmentLoader(Plugin plugin, EnchantmentRegistry registry, String packageName) {
        this.plugin = plugin;
        this.registry = registry;
        this.packageName = packageName;
    }
    
    /**
     * Loads all enchantments from the specified package.
     *
     * @return The number of enchantments loaded
     */
    public int loadEnchantments() {
        List<Class<?>> classes = findClasses();
        int count = 0;
        
        for (Class<?> clazz : classes) {
            try {
                if (CustomEnchant.class.isAssignableFrom(clazz) && !clazz.equals(CustomEnchant.class)) {
                    CustomEnchant enchant = (CustomEnchant) clazz.getDeclaredConstructor().newInstance();
                    if (registry.register(enchant)) {
                        count++;
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load enchantment class: " + clazz.getName(), e);
            }
        }
        
        plugin.getLogger().log(Level.INFO, "Loaded " + count + " enchantments");
        return count;
    }
    
    /**
     * Finds all classes in the specified package.
     *
     * @return List of classes found in the package
     */
    private List<Class<?>> findClasses() {
        List<Class<?>> classes = new ArrayList<>();
        String path = packageName.replace('.', '/');
        
        try {
            // Get the plugin JAR file
            File jarFile = new File(plugin.getClass().getProtectionDomain().getCodeSource().getLocation().toURI());
            
            if (jarFile.isFile()) { // It's a JAR file
                try (ZipFile zip = new ZipFile(jarFile)) {
                    // Iterate through all entries in the JAR
                    Enumeration<? extends ZipEntry> entries = zip.entries();
                    while (entries.hasMoreElements()) {
                        ZipEntry entry = entries.nextElement();
                        String name = entry.getName();
                        
                        // Check if the entry is a class file in the target package
                        if (name.startsWith(path) && name.endsWith(".class")) {
                            // Convert file path to class name
                            String className = name.replace('/', '.').substring(0, name.length() - 6);
                            try {
                                Class<?> clazz = Class.forName(className, true, plugin.getClass().getClassLoader());
                                classes.add(clazz);
                            } catch (ClassNotFoundException | NoClassDefFoundError e) {
                                plugin.getLogger().log(Level.WARNING, "Could not load class: " + className, e);
                            }
                        }
                    }
                }
            } else { // It's a directory (development environment)
                ClassLoader classLoader = plugin.getClass().getClassLoader();
                Enumeration<URL> resources = classLoader.getResources(path);
                
                while (resources.hasMoreElements()) {
                    URL resource = resources.nextElement();
                    File directory = new File(resource.getFile());
                    
                    if (directory.exists()) {
                        findClassesInDirectory(directory, packageName, classes);
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error loading classes from package: " + packageName, e);
        }
        
        return classes;
    }
    
    /**
     * Recursively finds classes in a directory.
     *
     * @param directory The directory to scan
     * @param packageName The current package name
     * @param classes The list to add found classes to
     */
    private void findClassesInDirectory(File directory, String packageName, List<Class<?>> classes) {
        if (!directory.exists()) {
            return;
        }
        
        File[] files = directory.listFiles();
        if (files == null) {
            return;
        }
        
        for (File file : files) {
            if (file.isDirectory()) {
                findClassesInDirectory(file, packageName + "." + file.getName(), classes);
            } else if (file.getName().endsWith(".class")) {
                String className = packageName + "." + file.getName().substring(0, file.getName().length() - 6);
                try {
                    Class<?> clazz = Class.forName(className, true, plugin.getClass().getClassLoader());
                    classes.add(clazz);
                } catch (ClassNotFoundException | NoClassDefFoundError e) {
                    plugin.getLogger().log(Level.WARNING, "Could not load class: " + className, e);
                }
            }
        }
    }
} 