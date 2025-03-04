package org.frizzlenpop.frizzlenChants.visual;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manager for handling visual effects using ProtocolLib.
 * Provides methods for creating various visual effects like particles,
 * glowing entities, and custom item models.
 */
public class VisualEffectManager {

    private final Plugin plugin;
    private final ProtocolManager protocolManager;
    private final Map<UUID, BukkitRunnable> activeEffects;
    
    /**
     * Creates a new visual effect manager.
     *
     * @param plugin The plugin instance
     */
    public VisualEffectManager(Plugin plugin) {
        this.plugin = plugin;
        this.protocolManager = ProtocolLibrary.getProtocolManager();
        this.activeEffects = new HashMap<>();
    }
    
    /**
     * Creates a particle effect at the specified location.
     *
     * @param location The location to spawn particles
     * @param particle The particle type
     * @param count The number of particles
     * @param offsetX The X offset
     * @param offsetY The Y offset
     * @param offsetZ The Z offset
     * @param speed The particle speed
     * @param data Optional data for certain particles
     */
    public void spawnParticles(Location location, Particle particle, int count, double offsetX, double offsetY, double offsetZ, double speed, Object data) {
        location.getWorld().spawnParticle(particle, location, count, offsetX, offsetY, offsetZ, speed, data);
    }
    
    /**
     * Creates a colored particle effect at the specified location.
     *
     * @param location The location to spawn particles
     * @param particle The particle type (must support dust options)
     * @param color The color of the particles
     * @param size The size of the particles
     * @param count The number of particles
     */
    public void spawnColoredParticles(Location location, Particle particle, Color color, float size, int count) {
        if (particle == Particle.DUST || particle == Particle.DUST_COLOR_TRANSITION) {
            Particle.DustOptions dustOptions = new Particle.DustOptions(color, size);
            location.getWorld().spawnParticle(particle, location, count, 0, 0, 0, 0, dustOptions);
        }
    }
    
    /**
     * Creates a particle trail behind a moving entity.
     *
     * @param entity The entity to create a trail for
     * @param particle The particle type
     * @param color The color of the particles (if applicable)
     * @param duration The duration of the trail in ticks
     * @return The ID of the created effect
     */
    public UUID createParticleTrail(Entity entity, Particle particle, Color color, int duration) {
        UUID effectId = UUID.randomUUID();
        
        BukkitRunnable task = new BukkitRunnable() {
            private int ticks = 0;
            
            @Override
            public void run() {
                if (ticks >= duration || entity.isDead()) {
                    this.cancel();
                    activeEffects.remove(effectId);
                    return;
                }
                
                Location location = entity.getLocation().add(0, 0.5, 0);
                
                if (particle == Particle.DUST || particle == Particle.DUST_COLOR_TRANSITION) {
                    Particle.DustOptions dustOptions = new Particle.DustOptions(color != null ? color : Color.WHITE, 1.0f);
                    location.getWorld().spawnParticle(particle, location, 1, 0, 0, 0, 0, dustOptions);
                } else {
                    location.getWorld().spawnParticle(particle, location, 1, 0, 0, 0, 0);
                }
                
                ticks++;
            }
        };
        
        task.runTaskTimer(plugin, 0, 1);
        activeEffects.put(effectId, task);
        
        return effectId;
    }
    
    /**
     * Makes an entity glow with a specific color for a player.
     *
     * @param player The player to send the packet to
     * @param entity The entity to make glow
     * @param glowing Whether the entity should glow
     */
    public void setGlowing(Player player, Entity entity, boolean glowing) {
        // Create a metadata packet
        PacketContainer packet = protocolManager.createPacket(PacketType.Play.Server.ENTITY_METADATA);
        packet.getIntegers().write(0, entity.getEntityId());
        
        WrappedDataWatcher watcher = new WrappedDataWatcher();
        WrappedDataWatcher.Serializer byteSerializer = WrappedDataWatcher.Registry.get(Byte.class);
        
        // Get the current entity metadata
        byte bitMask = 0;
        if (glowing) {
            bitMask |= 0x40; // Set the glowing bit
        }
        
        watcher.setObject(0, byteSerializer, bitMask);
        packet.getWatchableCollectionModifier().write(0, watcher.getWatchableObjects());
        
        // Send the packet to the player
        try {
            protocolManager.sendServerPacket(player, packet);
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to set entity glowing: " + e.getMessage());
        }
    }
    
    /**
     * Creates a spiral particle effect around an entity.
     *
     * @param entity The entity to create the spiral around
     * @param particle The particle type
     * @param color The color of the particles (if applicable)
     * @param radius The radius of the spiral
     * @param duration The duration of the effect in ticks
     * @return The ID of the created effect
     */
    public UUID createSpiralEffect(Entity entity, Particle particle, Color color, double radius, int duration) {
        UUID effectId = UUID.randomUUID();
        
        BukkitRunnable task = new BukkitRunnable() {
            private int ticks = 0;
            private double angle = 0;
            
            @Override
            public void run() {
                if (ticks >= duration || entity.isDead()) {
                    this.cancel();
                    activeEffects.remove(effectId);
                    return;
                }
                
                Location location = entity.getLocation();
                World world = location.getWorld();
                
                // Calculate the spiral points
                for (int i = 0; i < 2; i++) {
                    double currentAngle = angle + (Math.PI * i);
                    double x = radius * Math.cos(currentAngle);
                    double z = radius * Math.sin(currentAngle);
                    double y = (ticks % 10) / 10.0;
                    
                    Location particleLocation = location.clone().add(x, y, z);
                    
                    if (particle == Particle.DUST || particle == Particle.DUST_COLOR_TRANSITION) {
                        Particle.DustOptions dustOptions = new Particle.DustOptions(color != null ? color : Color.WHITE, 1.0f);
                        world.spawnParticle(particle, particleLocation, 1, 0, 0, 0, 0, dustOptions);
                    } else {
                        world.spawnParticle(particle, particleLocation, 1, 0, 0, 0, 0);
                    }
                }
                
                angle += Math.PI / 8; // Increment the angle for the next iteration
                ticks++;
            }
        };
        
        task.runTaskTimer(plugin, 0, 1);
        activeEffects.put(effectId, task);
        
        return effectId;
    }
    
    /**
     * Creates a directional beam effect from an entity.
     *
     * @param entity The entity to create the beam from
     * @param particle The particle type
     * @param color The color of the particles (if applicable)
     * @param range The range of the beam
     * @param duration The duration of the effect in ticks
     * @return The ID of the created effect
     */
    public UUID createBeamEffect(Entity entity, Particle particle, Color color, double range, int duration) {
        UUID effectId = UUID.randomUUID();
        
        BukkitRunnable task = new BukkitRunnable() {
            private int ticks = 0;
            
            @Override
            public void run() {
                if (ticks >= duration || entity.isDead()) {
                    this.cancel();
                    activeEffects.remove(effectId);
                    return;
                }
                
                Location location = entity.getLocation().add(0, 1, 0); // Start at entity eye location
                Vector direction = location.getDirection();
                World world = location.getWorld();
                
                // Create beam particles along the direction vector
                for (double d = 0; d <= range; d += 0.5) {
                    Location particleLocation = location.clone().add(direction.clone().multiply(d));
                    
                    if (particle == Particle.DUST || particle == Particle.DUST_COLOR_TRANSITION) {
                        Particle.DustOptions dustOptions = new Particle.DustOptions(color != null ? color : Color.WHITE, 1.0f);
                        world.spawnParticle(particle, particleLocation, 1, 0, 0, 0, 0, dustOptions);
                    } else {
                        world.spawnParticle(particle, particleLocation, 1, 0, 0, 0, 0);
                    }
                }
                
                ticks++;
            }
        };
        
        task.runTaskTimer(plugin, 0, 1);
        activeEffects.put(effectId, task);
        
        return effectId;
    }
    
    /**
     * Cancels a visual effect.
     *
     * @param effectId The ID of the effect to cancel
     * @return true if the effect was found and canceled, false otherwise
     */
    public boolean cancelEffect(UUID effectId) {
        BukkitRunnable task = activeEffects.remove(effectId);
        if (task != null) {
            task.cancel();
            return true;
        }
        return false;
    }
    
    /**
     * Cancels all active visual effects.
     */
    public void cancelAllEffects() {
        for (BukkitRunnable task : activeEffects.values()) {
            task.cancel();
        }
        activeEffects.clear();
    }
} 