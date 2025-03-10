package org.frizzlenpop.frizzlenChants.impl;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.frizzlenpop.frizzlenChants.FrizzlenChants;
import org.frizzlenpop.frizzlenChants.enchant.CustomEnchant;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Sword Summoner Enchantment
 *
 * This powerful enchantment summons a mysterious sword boss entity that targets
 * nearby enemies and performs a sequence of devastating attacks.
 *
 * Features:
 * - Summons multiple swords that collect and explode
 * - Creates a boss entity from the swords and nearby blocks
 * - The boss performs 3 sequential attacks:
 *   1. Summons NPC copies of the player to attack enemies
 *   2. Creates a black hole that consumes enemy souls
 *   3. Fires eye lasers at remaining enemies
 * - Includes dialogue and visual effects
 *
 * Usage:
 * Apply with: /customenchant SwordSummoner 1-3
 */
public class SwordSummonerEnchant extends CustomEnchant {

    private final FrizzlenChants plugin;
    private final Random random = new Random();

    // Constants
    private static final int MAX_LEVEL = 3;
    private static final int PROC_CHANCE_BASE = 15;
    private static final int PROC_CHANCE_LEVEL_BONUS = 5;
    private static final int COOLDOWN_SECONDS = 300; // 5 minutes cooldown
    private static final int SWORD_COUNT_BASE = 5;
    private static final int SWORD_COUNT_LEVEL_BONUS = 3;
    private static final int BOSS_DURATION = 60; // 60 seconds of boss activity
    private static final double BOSS_RANGE = 20.0; // Range the boss can target entities
    private static final double ACTIVATION_RANGE = 3.0; // Range for initial activation

    // Boss attributes
    private static final int BOSS_HEIGHT = 3;
    private static final int BOSS_ATTACK_DELAY = 10; // Seconds between attacks
    private static final int BOSS_DIALOGUE_DURATION = 4; // Seconds for dialogue display

    // Track player cooldowns
    private final Map<UUID, Long> lastProcTime = new HashMap<>();

    /**
     * Constructor for Sword Summoner enchantment.
     */
    public SwordSummonerEnchant() {
        super("SwordSummoner", MAX_LEVEL, Material.DIAMOND_SWORD, Material.NETHERITE_SWORD);
        this.plugin = FrizzlenChants.getPlugin(FrizzlenChants.class);
    }

    @Override
    public void applyEffect(Player player, ItemStack item, int level) {
        // This enchant is triggered through the damage event
    }

    /**
     * Handle entity damage event to trigger the enchantment.
     */
    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        // Check if the damager is a player
        if (!(event.getDamager() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getDamager();

        // Check if the player is holding a valid item
        ItemStack item = player.getInventory().getItemInMainHand();
        if (!canApplyTo(item)) {
            return;
        }

        // Check enchantment level
        int level = getEnchantmentLevel(item);
        if (level <= 0) {
            return;
        }

        // Check cooldown
        if (isOnCooldown(player)) {
            return;
        }

        // Check proc chance
        int procChance = PROC_CHANCE_BASE + ((level - 1) * PROC_CHANCE_LEVEL_BONUS);
        if (random.nextInt(100) >= procChance) {
            return;
        }

        // Set cooldown
        setLastProcTime(player);

        // Get the target entity location
        Location targetLocation = event.getEntity().getLocation();

        // Start the sword summoning sequence
        startSwordSummoningSequence(player, targetLocation, level);
    }
    /**
     * Start the sword summoning sequence.
     *
     * @param player The player who triggered the enchantment
     * @param targetLocation The target location for the boss
     * @param level The enchantment level
     */
    private void startSwordSummoningSequence(Player player, Location targetLocation, int level) {
        World world = targetLocation.getWorld();

        // Send message to player
        player.sendMessage(ChatColor.DARK_PURPLE + "You have summoned the Sword Guardian!");

        // Play initial sound effect
        world.playSound(targetLocation, Sound.ENTITY_WITHER_SPAWN, 0.8f, 0.5f);

        // Calculate number of swords based on level
        int swordCount = SWORD_COUNT_BASE + ((level - 1) * SWORD_COUNT_LEVEL_BONUS);

        // Create list to store sword armor stands
        List<ArmorStand> swords = new ArrayList<>();

        // Spawn swords in a circle around the target
        for (int i = 0; i < swordCount; i++) {
            double angle = (2 * Math.PI * i) / swordCount;
            double radius = 5.0; // Initial radius
            double x = targetLocation.getX() + (radius * Math.cos(angle));
            double y = targetLocation.getY() + 3.0; // Hover above
            double z = targetLocation.getZ() + (radius * Math.sin(angle));

            Location swordLoc = new Location(world, x, y, z);

            // Create an armor stand with a sword
            ArmorStand stand = (ArmorStand) world.spawnEntity(swordLoc, EntityType.ARMOR_STAND);
            stand.setVisible(false);
            stand.setGravity(false);
            stand.setInvulnerable(true);
            stand.setSmall(false);
            stand.getEquipment().setItemInMainHand(new ItemStack(Material.DIAMOND_SWORD));
            stand.setRightArmPose(stand.getRightArmPose().setX(Math.toRadians(270)));

            // Add to the list
            swords.add(stand);

            // Create particle effect
            world.spawnParticle(Particle.CRIT, swordLoc, 20, 0.5, 0.5, 0.5, 0.2);
        }

        // Start the swords animation
        animateSwordsAndSpawnBoss(player, targetLocation, swords, level);
    }

    /**
     * Animate the swords, make them collect, explode, and spawn the boss.
     *
     * @param player The player who triggered the enchantment
     * @param targetLocation The target location for the boss
     * @param swords The list of sword armor stands
     * @param level The enchantment level
     */
    private void animateSwordsAndSpawnBoss(Player player, Location targetLocation, List<ArmorStand> swords, int level) {
        // Add debugging
        plugin.getLogger().info("[SwordSummoner] Starting sword animation sequence with " + swords.size() + " swords");

        World world = targetLocation.getWorld();
        if (world == null) {
            plugin.getLogger().warning("[SwordSummoner] World is null in animateSwordsAndSpawnBoss");
            return;
        }

        // Create a safe copy of the sword list to prevent concurrent modification
        final List<ArmorStand> swordsCopy = new ArrayList<>();
        for (ArmorStand sword : swords) {
            if (sword != null && !sword.isDead()) {
                swordsCopy.add(sword);
            }
        }

        if (swordsCopy.isEmpty()) {
            plugin.getLogger().warning("[SwordSummoner] No valid swords to animate");
            return;
        }

        plugin.getLogger().info("[SwordSummoner] Found " + swordsCopy.size() + " valid swords for animation");

        // Show message to player
        player.sendActionBar(ChatColor.GOLD + "Summoning the Sword Guardian...");

        // Store original sword positions
        final Map<ArmorStand, Location> originalPositions = new HashMap<>();
        for (ArmorStand sword : swordsCopy) {
            originalPositions.put(sword, sword.getLocation().clone());
        }

        new BukkitRunnable() {
            int tick = 0;
            final int collectionStart = 40; // When swords start moving to center
            final int explosionTick = 80; // When explosion happens
            final int bossSpawnTick = 100; // When boss spawns

            @Override
            public void run() {
                try {
                    // Log every 20 ticks to track progress
                    if (tick % 20 == 0) {
                        plugin.getLogger().info("[SwordSummoner] Animation tick: " + tick + ", swords: " + swordsCopy.size());
                    }

                    // Check if all swords are gone (something went wrong)
                    boolean allSwordsGone = true;
                    for (ArmorStand sword : swordsCopy) {
                        if (sword != null && !sword.isDead()) {
                            allSwordsGone = false;
                            break;
                        }
                    }

                    if (allSwordsGone && tick < bossSpawnTick) {
                        plugin.getLogger().warning("[SwordSummoner] All swords disappeared before boss spawn at tick " + tick);
                        // Continue to boss spawn anyway to prevent getting stuck
                        tick = bossSpawnTick;
                    }

                    // Phase 1: Swords float and spin
                    if (tick < collectionStart) {
                        plugin.getLogger().fine("[SwordSummoner] Phase 1: Floating swords at tick " + tick);

                        // Process each sword
                        for (ArmorStand sword : swordsCopy) {
                            if (sword == null || sword.isDead()) continue;

                            // Make swords float up and spin
                            Location swordLoc = sword.getLocation();
                            double yOffset = Math.sin(tick * 0.1) * 0.1;
                            swordLoc.add(0, yOffset, 0);

                            // Rotate sword - using setRotation which is more reliable
                            float yaw = (sword.getLocation().getYaw() + 5) % 360;
                            sword.setRotation(yaw, 0);

                            // Update location
                            sword.teleport(swordLoc);

                            // Add particle effects every few ticks to reduce load
                            if (tick % 5 == 0) {
                                try {
                                    world.spawnParticle(Particle.CRIT, swordLoc, 3, 0.2, 0.2, 0.2, 0.05);
                                } catch (Exception e) {
                                    // Fallback to a more common particle type if there's an error
                                    world.spawnParticle(Particle.FLAME, swordLoc, 2, 0.1, 0.1, 0.1, 0.01);
                                }
                            }
                        }

                        // Play ambient sound occasionally
                        if (tick % 20 == 0) {
                            world.playSound(targetLocation, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.5f, 1.0f);
                        }
                    }
                    // Phase 2: Swords move to center
                    else if (tick <= explosionTick) {
                        if (tick == collectionStart) {
                            plugin.getLogger().info("[SwordSummoner] Starting Phase 2: Swords moving to center at tick " + tick);
                            player.sendActionBar(ChatColor.GOLD + "The swords are gathering power...");
                            world.playSound(targetLocation, Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.2f);
                        }

                        // Calculate progress (0.0 to 1.0) for this phase
                        double progress = (double)(tick - collectionStart) / (explosionTick - collectionStart);

                        for (ArmorStand sword : swordsCopy) {
                            if (sword == null || sword.isDead()) continue;

                            // Get original position and create vector to target
                            Location original = originalPositions.get(sword);
                            if (original == null) {
                                original = sword.getLocation();
                            }

                            // Calculate direction to center using lerp
                            Vector direction = targetLocation.clone().subtract(original).toVector();

                            // Determine new position based on progress
                            Location newLoc = original.clone().add(direction.multiply(progress));

                            // Rotate sword faster as it approaches center
                            float yaw = (sword.getLocation().getYaw() + 5 + (float)(progress * 10)) % 360;

                            // Update sword position and rotation
                            sword.teleport(newLoc);
                            sword.setRotation(yaw, 0);

                            // Add more intense particles as progress increases
                            if (tick % 3 == 0) {
                                try {
                                    world.spawnParticle(Particle.CRIT, newLoc, 5, 0.1, 0.1, 0.1, 0.05);
                                } catch (Exception e) {
                                    world.spawnParticle(Particle.FLAME, newLoc, 3, 0.1, 0.1, 0.1, 0.01);
                                }
                            }
                        }

                        // Increasingly intense sounds as we approach explosion
                        if (tick % 10 == 0) {
                            world.playSound(targetLocation, Sound.BLOCK_BEACON_AMBIENT, 0.5f, 0.5f + (float)progress);
                        }
                    }
                    // Phase 3: Explosion
                    else if (tick == explosionTick) {
                        plugin.getLogger().info("[SwordSummoner] Phase 3: Explosion at tick " + tick);
                        player.sendActionBar(ChatColor.RED + "Power gathering complete!");

                        // Remove all swords
                        for (ArmorStand sword : swordsCopy) {
                            if (sword != null && !sword.isDead()) {
                                sword.remove();
                            }
                        }

                        // Create explosion effect
                        try {
                            world.spawnParticle(Particle.EXPLOSION, targetLocation, 1, 0, 0, 0, 0);
                            world.spawnParticle(Particle.FLAME, targetLocation, 50, 2, 2, 2, 0.1);
                            world.spawnParticle(Particle.LAVA, targetLocation, 20, 1, 1, 1, 0.1);
                        } catch (Exception e) {
                            plugin.getLogger().warning("[SwordSummoner] Error spawning explosion particles: " + e.getMessage());
                            // Fallback particles
                            world.spawnParticle(Particle.FLAME, targetLocation, 30, 1, 1, 1, 0.1);
                        }

                        // Play explosion sound
                        world.playSound(targetLocation, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.8f);
                        world.playSound(targetLocation, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.6f, 0.8f);

                        // Create temporary boss outline effect
                        for (int i = 0; i < 5; i++) {
                            double angle = (2 * Math.PI * i) / 5;
                            double x = targetLocation.getX() + (2 * Math.cos(angle));
                            double z = targetLocation.getZ() + (2 * Math.sin(angle));
                            Location outlinePoint = new Location(world, x, targetLocation.getY() + 1.5, z);
                            world.spawnParticle(Particle.DRAGON_BREATH, outlinePoint, 5, 0.2, 0.2, 0.2, 0.05);
                        }
                    }
                    // Phase 4: Forming boss from energy
                    else if (tick < bossSpawnTick) {
                        if (tick == explosionTick + 1) {
                            plugin.getLogger().info("[SwordSummoner] Phase 4: Forming boss at tick " + tick);
                            player.sendActionBar(ChatColor.GOLD + "The Sword Guardian is forming...");
                        }

                        double progress = (tick - explosionTick) / (double)(bossSpawnTick - explosionTick);

                        // Create swirling particles that collapse into boss shape
                        for (int i = 0; i < 3; i++) {
                            double angle = (2 * Math.PI * i) / 3 + (tick * 0.2);
                            double radius = 3.0 * (1.0 - progress); // Shrinking radius
                            double x = targetLocation.getX() + (radius * Math.cos(angle));
                            double z = targetLocation.getZ() + (radius * Math.sin(angle));
                            double y = targetLocation.getY() + 1.5 + Math.sin(angle + tick * 0.1) * (1.0 - progress);

                            Location particleLoc = new Location(world, x, y, z);
                            try {
                                world.spawnParticle(Particle.FLAME, particleLoc, 3, 0.1, 0.1, 0.1, 0.05);
                                world.spawnParticle(Particle.DRAGON_BREATH, particleLoc, 1, 0, 0, 0, 0);
                            } catch (Exception e) {
                                // Try fallback particles if these don't work
                                world.spawnParticle(Particle.FLAME, particleLoc, 3, 0.1, 0.1, 0.1, 0.05);
                            }
                        }

                        // Play forming sound
                        if (tick % 10 == 0) {
                            world.playSound(targetLocation, Sound.BLOCK_FIRE_AMBIENT, 1.0f, 0.5f + (float)progress);
                        }
                    }
                    // Phase 5: Spawn boss
                    else if (tick == bossSpawnTick) {
                        plugin.getLogger().info("[SwordSummoner] Phase 5: Spawning boss at tick " + tick);
                        player.sendActionBar(ChatColor.RED + "The Sword Guardian has been summoned!");

                        // Final explosion effect
                        try {
                            world.spawnParticle(Particle.EXPLOSION, targetLocation, 2, 0.5, 0.5, 0.5, 0.1);
                            world.spawnParticle(Particle.FLAME, targetLocation, 50, 1, 1, 1, 0.2);
                        } catch (Exception e) {
                            plugin.getLogger().warning("[SwordSummoner] Error spawning final boss particles: " + e.getMessage());
                        }

                        // Play spawn sound
                        world.playSound(targetLocation, Sound.ENTITY_WITHER_SPAWN, 0.7f, 0.8f);

                        // Actually spawn the boss in a separate task to avoid timing issues
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                try {
                                    plugin.getLogger().info("[SwordSummoner] Spawning boss now...");
                                    spawnSwordBoss(player, targetLocation, level);
                                    plugin.getLogger().info("[SwordSummoner] Boss spawn completed successfully");
                                } catch (Exception e) {
                                    plugin.getLogger().severe("[SwordSummoner] Error spawning boss: " + e.getMessage());
                                    e.printStackTrace();
                                    // Notify player of failure
                                    player.sendActionBar(ChatColor.RED + "Summoning failed! Try again later.");
                                }
                            }
                        }.runTask(plugin);

                        // End this animation sequence
                        this.cancel();
                        return;
                    }

                    // Always increment the tick counter
                    tick++;

                } catch (Exception e) {
                    plugin.getLogger().severe("[SwordSummoner] Error in sword animation at tick " + tick + ": " + e.getMessage());
                    e.printStackTrace();

                    // If we encounter an error, try to skip to boss spawn to avoid getting stuck
                    try {
                        plugin.getLogger().info("[SwordSummoner] Attempting to recover by spawning boss directly...");
                        spawnSwordBoss(player, targetLocation, level);
                    } catch (Exception ex) {
                        plugin.getLogger().severe("[SwordSummoner] Recovery attempt failed: " + ex.getMessage());
                        player.sendActionBar(ChatColor.RED + "Summoning failed! Try again later.");
                    }

                    this.cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * Spawn the sword boss entity at the target location.
     *
     * @param player The player who triggered the enchantment
     * @param targetLocation The target location for the boss
     * @param level The enchantment level
     */
    private void spawnSwordBoss(Player player, Location targetLocation, int level) {
        World world = targetLocation.getWorld();

        // Create the main boss entity (armor stand)
        ArmorStand boss = (ArmorStand) world.spawnEntity(targetLocation.clone().add(0, 1, 0), EntityType.ARMOR_STAND);
        boss.setVisible(false);
        boss.setGravity(false);
        boss.setInvulnerable(true);
        boss.setCustomName("Sword Guardian");
        boss.setCustomNameVisible(true);

        // Create a sword in each hand
        boss.getEquipment().setItemInMainHand(new ItemStack(Material.DIAMOND_SWORD));
        boss.getEquipment().setItemInOffHand(new ItemStack(Material.DIAMOND_SWORD));

        // Add additional armor stands to create a more complex boss structure
        List<ArmorStand> bossParts = new ArrayList<>();

        // Create the boss body using blocks and swords
        createBossBody(boss, bossParts, targetLocation, level);

        // Create initial dialogue text display
        showBossDialogue(boss, ChatColor.RED + "I am the Sword Guardian!",
                ChatColor.GOLD + "Summoned to cleanse this land...", 5);

        // Play boss spawn sound
        world.playSound(targetLocation, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.5f);

        // Create initial effect
        world.spawnParticle(Particle.DRAGON_BREATH, targetLocation, 100, 2.0, 2.0, 2.0, 0.1);

        // Get nearby entities for targeting
        List<LivingEntity> nearbyEntities = getNearbyTargetableEntities(targetLocation, player);

        // Start the boss AI
        startBossAI(player, boss, bossParts, targetLocation, nearbyEntities, level);
    }

    /**
     * Create the boss body using blocks and swords.
     *
     * @param boss The main boss entity
     * @param bossParts List to store additional boss parts
     * @param targetLocation The target location
     * @param level The enchantment level
     */
    private void createBossBody(ArmorStand boss, List<ArmorStand> bossParts, Location targetLocation, int level) {
        World world = targetLocation.getWorld();

        // Materials for the boss body - more impressive materials at higher levels
        Material[] bodyMaterials;
        if (level == 1) {
            bodyMaterials = new Material[]{
                    Material.IRON_BLOCK, Material.IRON_BARS, Material.ANVIL
            };
        } else if (level == 2) {
            bodyMaterials = new Material[]{
                    Material.DIAMOND_BLOCK, Material.IRON_BARS, Material.ANVIL, Material.OBSIDIAN
            };
        } else {
            bodyMaterials = new Material[]{
                    Material.NETHERITE_BLOCK, Material.OBSIDIAN, Material.CRYING_OBSIDIAN, Material.ANCIENT_DEBRIS
            };
        }

        // Create body parts with floating blocks and swords
        for (int i = 0; i < 8; i++) {
            double angle = (2 * Math.PI * i) / 8;
            double radius = 1.0;
            double x = targetLocation.getX() + (radius * Math.cos(angle));
            double y = targetLocation.getY() + 1.5; // Middle height
            double z = targetLocation.getZ() + (radius * Math.sin(angle));

            Location partLoc = new Location(world, x, y, z);

            // Create armor stand for the body part
            ArmorStand part = (ArmorStand) world.spawnEntity(partLoc, EntityType.ARMOR_STAND);
            part.setVisible(false);
            part.setGravity(false);
            part.setInvulnerable(true);
            part.setSmall(true);

            // Randomly choose block or sword
            if (random.nextBoolean()) {
                // Block on head
                Material blockMaterial = bodyMaterials[random.nextInt(bodyMaterials.length)];
                part.getEquipment().setHelmet(new ItemStack(blockMaterial));
            } else {
                // Sword
                part.getEquipment().setItemInMainHand(new ItemStack(Material.DIAMOND_SWORD));
                part.setRightArmPose(part.getRightArmPose().setX(Math.toRadians(random.nextInt(360))));
            }

            bossParts.add(part);
        }

        // Create "eye" parts
        for (int i = 0; i < 2; i++) {
            double angle = Math.PI * i;
            double radius = 0.4;
            double x = targetLocation.getX() + (radius * Math.cos(angle));
            double y = targetLocation.getY() + 2.0; // Eye height
            double z = targetLocation.getZ() + (radius * Math.sin(angle));

            Location eyeLoc = new Location(world, x, y, z);

            // Create armor stand for the eye
            ArmorStand eye = (ArmorStand) world.spawnEntity(eyeLoc, EntityType.ARMOR_STAND);
            eye.setVisible(false);
            eye.setGravity(false);
            eye.setInvulnerable(true);
            eye.setSmall(true);

            // Redstone block or redstone lamp for eyes
            eye.getEquipment().setHelmet(new ItemStack(i == 0 ? Material.REDSTONE_BLOCK : Material.REDSTONE_LAMP));

            bossParts.add(eye);
        }
    }

    /**
     * Show dialogue above the boss using text display.
     *
     * @param boss The boss entity
     * @param line1 First line of dialogue
     * @param line2 Second line of dialogue
     * @param duration Duration in seconds
     */
    private void showBossDialogue(ArmorStand boss, String line1, String line2, int duration) {
        World world = boss.getWorld();
        Location textLoc = boss.getLocation().clone().add(0, 3, 0);

        // Create text display
        TextDisplay textDisplay = (TextDisplay) world.spawnEntity(textLoc, EntityType.TEXT_DISPLAY);
        textDisplay.setText(line1 + "\n" + line2);
        textDisplay.setAlignment(TextDisplay.TextAlignment.CENTER);
        textDisplay.setBillboard(Display.Billboard.CENTER);
        textDisplay.setBackgroundColor(org.bukkit.Color.fromARGB(100, 0, 0, 0));
        textDisplay.setSeeThrough(false);
        textDisplay.setViewRange(50);

        // Remove after duration
        new BukkitRunnable() {
            @Override
            public void run() {
                if (textDisplay != null && !textDisplay.isDead()) {
                    textDisplay.remove();
                }
            }
        }.runTaskLater(plugin, duration * 20L);
    }

    /**
     * Get nearby entities that can be targeted by the boss.
     *
     * @param location The center location
     * @param player The player who should not be targeted
     * @return List of nearby living entities
     */
    private List<LivingEntity> getNearbyTargetableEntities(Location location, Player player) {
        List<LivingEntity> result = new ArrayList<>();

        // Get all entities within range
        Collection<Entity> nearbyEntities = location.getWorld().getNearbyEntities(
                location, BOSS_RANGE, BOSS_RANGE, BOSS_RANGE);

        // Filter for living entities that aren't the player
        for (Entity entity : nearbyEntities) {
            if (entity instanceof LivingEntity && entity != player && !(entity instanceof ArmorStand)) {
                // Skip players if PVP is disabled
                if (entity instanceof Player && !location.getWorld().getPVP()) {
                    continue;
                }

                result.add((LivingEntity) entity);
            }
        }

        return result;
    }

    /**
     * Start the boss AI to perform attacks.
     *
     * @param player The player who summoned the boss
     * @param boss The main boss entity
     * @param bossParts Additional boss parts
     * @param centerLocation The center location of the boss
     * @param targets List of target entities
     * @param level The enchantment level
     */
    private void startBossAI(Player player, ArmorStand boss, List<ArmorStand> bossParts,
                             Location centerLocation, List<LivingEntity> targets, int level) {
        plugin.getLogger().info("Starting boss AI with " + targets.size() + " initial targets");

        if (boss == null || boss.isDead()) {
            plugin.getLogger().warning("Boss is null or dead at AI start");
            return;
        }

        // Store references to prevent garbage collection
        final List<ArmorStand> bossPartsCopy = new ArrayList<>(bossParts);

        // Notify player
        player.sendActionBar(ChatColor.RED + "The Sword Guardian is attacking!");

        new BukkitRunnable() {
            int tick = 0;
            int attackPhase = 0;
            boolean attackInProgress = false;

            @Override
            public void run() {
                try {
                    // Check if boss is still alive
                    if (boss == null || boss.isDead()) {
                        this.cancel();
                        plugin.getLogger().info("Boss died or was removed, ending AI sequence");
                        return;
                    }

                    // Animate boss (float and rotate)
                    animateBoss(boss, bossPartsCopy, tick);

                    // Start next attack if time and not already attacking
                    if (tick % (BOSS_ATTACK_DELAY * 20) == 0 && !attackInProgress) {
                        attackInProgress = true;

                        // Get fresh list of targets
                        List<LivingEntity> currentTargets = getNearbyTargetableEntities(centerLocation, player);

                        if (currentTargets.isEmpty()) {
                            // No more targets, end sequence
                            plugin.getLogger().info("No more targets found, ending boss sequence");
                            showBossDialogue(boss, ChatColor.GREEN + "All enemies defeated.",
                                    ChatColor.YELLOW + "My work here is done.", 5);

                            // Remove boss after dialogue
                            new BukkitRunnable() {
                                @Override
                                public void run() {
                                    removeBoss(boss, bossPartsCopy);
                                }
                            }.runTaskLater(plugin, 6 * 20L);

                            this.cancel();
                            return;
                        }

                        // Choose attack based on phase
                        if (attackPhase == 0) {
                            // First attack: NPC sword clones
                            plugin.getLogger().info("Starting first attack phase");
                            performFirstAttack(player, boss, centerLocation, currentTargets, level, () -> {
                                attackInProgress = false;
                                attackPhase++;
                                plugin.getLogger().info("First attack completed");
                            });
                        }
                        else if (attackPhase == 1) {
                            // Second attack: Black hole
                            plugin.getLogger().info("Starting second attack phase");
                            performSecondAttack(player, boss, bossPartsCopy, centerLocation, currentTargets, level, () -> {
                                attackInProgress = false;
                                attackPhase++;
                                plugin.getLogger().info("Second attack completed");
                            });
                        }
                        else if (attackPhase == 2) {
                            // Third attack: Eye lasers
                            plugin.getLogger().info("Starting third attack phase");
                            performThirdAttack(player, boss, bossPartsCopy, centerLocation, currentTargets, level, () -> {
                                attackInProgress = false;
                                attackPhase++;
                                plugin.getLogger().info("Third attack completed");
                            });
                        }
                        else {
                            // All attacks done, end sequence
                            plugin.getLogger().info("All attack phases completed");
                            showBossDialogue(boss, ChatColor.GREEN + "My duty is fulfilled.",
                                    ChatColor.YELLOW + "Until we meet again...", 5);

                            // Remove boss after dialogue
                            new BukkitRunnable() {
                                @Override
                                public void run() {
                                    removeBoss(boss, bossPartsCopy);
                                }
                            }.runTaskLater(plugin, 6 * 20L);

                            this.cancel();
                        }
                    }

                    tick++;

                    // End boss sequence after duration
                    if (tick >= BOSS_DURATION * 20) {
                        plugin.getLogger().info("Boss duration exceeded, ending sequence");
                        showBossDialogue(boss, ChatColor.YELLOW + "My time grows short.",
                                ChatColor.YELLOW + "I must return to the void.", 5);

                        // Remove boss after dialogue
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                removeBoss(boss, bossPartsCopy);
                            }
                        }.runTaskLater(plugin, 6 * 20L);

                        this.cancel();
                    }
                } catch (Exception e) {
                    plugin.getLogger().severe("Error in boss AI: " + e.getMessage());
                    e.printStackTrace();
                    this.cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * Animate the boss entity.
     *
     * @param boss The main boss entity
     * @param bossParts Additional boss parts
     * @param tick The current tick
     */
    private void animateBoss(ArmorStand boss, List<ArmorStand> bossParts, int tick) {
        // Float boss up and down
        Location bossLoc = boss.getLocation();
        double yOffset = Math.sin(tick * 0.1) * 0.1;
        bossLoc.add(0, yOffset, 0);
        boss.teleport(bossLoc);

        // Rotate and move parts
        for (int i = 0; i < bossParts.size(); i++) {
            ArmorStand part = bossParts.get(i);
            if (part == null || part.isDead()) continue;

            Location partLoc = part.getLocation();

            // Different movement patterns for different parts
            if (i < bossParts.size() - 2) { // Body parts
                // Orbit around boss
                double angle = (tick * 0.05) + ((2 * Math.PI * i) / (bossParts.size() - 2));
                double radius = 1.0 + (Math.sin(tick * 0.02) * 0.2); // Pulsing radius
                double x = bossLoc.getX() + (radius * Math.cos(angle));
                double z = bossLoc.getZ() + (radius * Math.sin(angle));

                partLoc.setX(x);
                partLoc.setZ(z);
                partLoc.setY(bossLoc.getY() + (Math.sin(angle + tick * 0.1) * 0.3)); // Wavy height

                // Rotate the part
                partLoc.setYaw(partLoc.getYaw() + 5);

                part.teleport(partLoc);
            }
            else { // Eye parts
                // Eyes stay in front, but can move slightly
                int eyeIndex = i - (bossParts.size() - 2);
                double angle = Math.PI * eyeIndex;
                double radius = 0.4;
                double x = bossLoc.getX() + (radius * Math.cos(angle));
                double z = bossLoc.getZ() + (radius * Math.sin(angle));

                partLoc.setX(x);
                partLoc.setZ(z);
                partLoc.setY(bossLoc.getY() + 1.0 + (Math.sin(tick * 0.1) * 0.05)); // Slight float

                part.teleport(partLoc);
            }
        }
    }

    /**
     * Remove the boss and all its parts.
     *
     * @param boss The main boss entity
     * @param bossParts Additional boss parts
     */
    private void removeBoss(ArmorStand boss, List<ArmorStand> bossParts) {
        // Remove main boss
        if (boss != null && !boss.isDead()) {
            boss.remove();
        }

        // Remove all parts
        for (ArmorStand part : bossParts) {
            if (part != null && !part.isDead()) {
                part.remove();
            }
        }
    }

    /**
     * Perform the first attack: Summoning NPC sword clones.
     *
     * @param player The player who summoned the boss
     * @param boss The main boss entity
     * @param centerLocation The center location
     * @param targets List of target entities
     * @param level The enchantment level
     * @param callback Callback to run when attack is complete
     */
    private void performFirstAttack(Player player, ArmorStand boss, Location centerLocation,
                                    List<LivingEntity> targets, int level, Runnable callback) {

        // Show attack dialogue
        showBossDialogue(boss, "First Attack: Shadow Blade Legion!", "My swords will find you...", BOSS_DIALOGUE_DURATION);

        World world = centerLocation.getWorld();

        // Create a copy of targets and select a random subset
        List<LivingEntity> targetsCopy = new ArrayList<>(targets);
        Collections.shuffle(targetsCopy);

        // Determine how many targets to attack based on level
        int targetCount = Math.min(level + 2, targetsCopy.size());

        // Create the FINAL list of selected targets - this cannot be reassigned later
        final List<LivingEntity> selectedTargets = new ArrayList<>(targetsCopy.subList(0, targetCount));

        // Create clone armor stands
        List<ArmorStand> clones = new ArrayList<>();

        // Spawn clones around the boss
        for (int i = 0; i < targetCount; i++) {
            double angle = (2 * Math.PI * i) / targetCount;
            double radius = 3.0;
            double x = centerLocation.getX() + (radius * Math.cos(angle));
            double z = centerLocation.getZ() + (radius * Math.sin(angle));
            Location cloneLoc = new Location(world, x, centerLocation.getY(), z);
            cloneLoc.setDirection(new Vector(0, 0, 1)); // Default direction

            // Create armor stand to represent player clone
            ArmorStand clone = (ArmorStand) world.spawnEntity(cloneLoc, EntityType.ARMOR_STAND);
            clone.setVisible(false);
            clone.setVisible(true);
            clone.setGravity(false);
            clone.setInvulnerable(true);
            clone.setSmall(false);

            // Give it player skin (for simplicity, we're using armor instead of actual player skin)
            clone.getEquipment().setHelmet(new ItemStack(Material.PLAYER_HEAD));
            clone.getEquipment().setChestplate(new ItemStack(Material.IRON_CHESTPLATE));
            clone.getEquipment().setLeggings(new ItemStack(Material.IRON_LEGGINGS));
            clone.getEquipment().setBoots(new ItemStack(Material.IRON_BOOTS));
            clone.getEquipment().setItemInMainHand(new ItemStack(Material.IRON_SWORD));

            // Add to list
            clones.add(clone);

            // Create clone spawn effect
            world.spawnParticle(Particle.PORTAL, cloneLoc, 30, 0.5, 1.0, 0.5, 0.1);
            world.spawnParticle(Particle.CLOUD, cloneLoc, 20, 0.3, 0.5, 0.3, 0.05);
            world.playSound(cloneLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.2f);
        }

        // Start clone attack animation
        new BukkitRunnable() {
            int tick = 0;
            Map<ArmorStand, LivingEntity> cloneTargets = new HashMap<>();
            Set<LivingEntity> killedTargets = new HashSet<>();

            @Override
            public void run() {
                // End if all clones are gone
                if (clones.isEmpty()) {
                    callback.run();
                    this.cancel();
                    return;
                }

                // Initial assignment of targets
                if (tick == 0) {
                    for (int i = 0; i < clones.size() && i < selectedTargets.size(); i++) {
                        cloneTargets.put(clones.get(i), selectedTargets.get(i));
                    }
                }

                // Animate clones
                Iterator<ArmorStand> it = clones.iterator();
                while (it.hasNext()) {
                    ArmorStand clone = it.next();
                    LivingEntity target = cloneTargets.get(clone);

                    // Skip if clone or target is gone
                    if (clone == null || clone.isDead() || target == null || target.isDead() || killedTargets.contains(target)) {
                        if (clone != null && !clone.isDead()) {
                            // Clone has completed its task, teleport away
                            world.spawnParticle(Particle.PORTAL, clone.getLocation(), 30, 0.5, 1.0, 0.5, 0.1);
                            world.playSound(clone.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.2f);
                            clone.remove();
                        }
                        it.remove();
                        continue;
                    }

                    // Move clone toward target
                    Location cloneLoc = clone.getLocation();
                    Location targetLoc = target.getLocation();

                    // Distance to target
                    double distance = cloneLoc.distance(targetLoc);

                    if (distance > 1.5) {
                        // Move toward target
                        Vector direction = targetLoc.clone().subtract(cloneLoc).toVector().normalize();
                        Location newLoc = cloneLoc.clone().add(direction.multiply(0.3));
                        newLoc.setDirection(direction);

                        clone.teleport(newLoc);

                        // Trail effect
                        if (tick % 3 == 0) {
                            world.spawnParticle(Particle.CLOUD, cloneLoc, 3, 0.1, 0.1, 0.1, 0.02);
                        }
                    } else {
                        // Attack animation
                        world.playSound(cloneLoc, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 1.0f);
                        world.spawnParticle(Particle.SWEEP_ATTACK, targetLoc.clone().add(0, 1.0, 0), 1, 0, 0, 0, 0);

                        // Damage target (instant kill for this attack)
                        target.damage(target.getHealth() + 10, player);

                        // Mark target as killed
                        killedTargets.add(target);

                        // Create death effect
                        world.spawnParticle(Particle.CLOUD, targetLoc, 20, 0.3, 0.5, 0.3, 0.1);
                    }
                }

                tick++;

                // End sequence after a while if targets aren't killed
                if (tick >= 100) {
                    // Remove remaining clones
                    for (ArmorStand clone : clones) {
                        if (clone != null && !clone.isDead()) {
                            world.spawnParticle(Particle.PORTAL, clone.getLocation(), 30, 0.5, 1.0, 0.5, 0.1);
                            world.playSound(clone.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.2f);
                            clone.remove();
                        }
                    }
                    clones.clear();

                    callback.run();
                    this.cancel();
                }
            }
        }.runTaskTimer(plugin, 20L, 1L);
    }

    /**
     * Perform the second attack: Black hole.
     *
     * @param player The player who summoned the boss
     * @param boss The main boss entity
     * @param bossParts List of boss parts (eyes are the last two)
     * @param centerLocation The center location
     * @param targets List of target entities
     * @param level The enchantment level
     * @param callback Callback to run when attack is complete
     */
    private void performSecondAttack(Player player, ArmorStand boss, List<ArmorStand> bossParts,
                                     Location centerLocation, List<LivingEntity> targets, int level, Runnable callback) {
        // Extract eyes from boss parts for this attack
        List<ArmorStand> eyes = bossParts.stream()
                .filter(part -> part.getCustomName() != null && part.getCustomName().contains("Eye"))
                .collect(Collectors.toList());

        // Number of targets to attack based on level
        int targetCount = Math.min(targets.size(), level + 1);

        // Create a temporary mutable copy, shuffle and select subset
        List<LivingEntity> targetsCopy = new ArrayList<>(targets);
        Collections.shuffle(targetsCopy);
        if (targetsCopy.size() > targetCount) {
            targetsCopy = targetsCopy.subList(0, targetCount);
        }

        // Now create the final variable for use in the inner class
        final List<LivingEntity> selectedTargets = new ArrayList<>(targetsCopy);

        // Start dialogue sequence followed by black hole attack
        new BukkitRunnable() {
            int dialoguePhase = 0;

            @Override
            public void run() {
                switch (dialoguePhase) {
                    case 0:
                        showBossDialogue(boss, ChatColor.RED + "Second Attack:",
                                ChatColor.GOLD + "Black Hole!", 5);
                        dialoguePhase++;
                        break;

                    case 1:
                        showBossDialogue(boss, ChatColor.DARK_RED + "Consume...",
                                ChatColor.DARK_RED + "Your fate is sealed.", 5);
                        dialoguePhase++;
                        break;

                    case 2:
                        showBossDialogue(boss, ChatColor.RED + "None shall escape...",
                                ChatColor.RED + "My consuming power!", 5);
                        dialoguePhase++;
                        break;

                    case 3:
                        // Start the actual attack - use selectedTargets directly since it's already final
                        performBlackHoleAttack(player, boss, eyes, selectedTargets, level, callback);
                        this.cancel();
                        break;
                }
            }
        }.runTaskTimer(plugin, 0L, 6 * 20L); // 6 seconds between dialogue phases
    }

    /**
     * Perform the black hole attack.
     *
     * @param player The player who summoned the boss
     * @param boss The main boss entity
     * @param eyes List of eye armor stands
     * @param targets List of target entities
     * @param level The enchantment level
     * @param callback Callback to run when attack is complete
     */
    private void performBlackHoleAttack(Player player, ArmorStand boss, List<ArmorStand> eyes,
                                        List<LivingEntity> targets, int level, Runnable callback) {

        World world = boss.getWorld();

        // Track killed entities
        Set<LivingEntity> killedEntities = new HashSet<>();

        // Begin eye glow effect
        for (ArmorStand eye : eyes) {
            world.spawnParticle(Particle.FLAME, eye.getLocation(), 10, 0.1, 0.1, 0.1, 0.02);
        }

        // Play charge sound
        world.playSound(boss.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.0f);

        // Start the black hole attack sequence
        BukkitTask bukkitTask = new BukkitRunnable() {
            int tick = 0;
            final int chargeDuration = 40; // 2 seconds to charge
            final int attackDuration = 100; // 5 seconds of attack
            Map<LivingEntity, Integer> targetAttackTimes = new HashMap<>();

            @Override
            public void run() {
                if (tick >= chargeDuration + attackDuration || targets.isEmpty() ||
                        killedEntities.size() >= targets.size()) {
                    // End attack
                    callback.run();
                    this.cancel();
                    return;
                }

                // Charging phase - eye glow intensifies
                if (tick < chargeDuration) {
                    for (ArmorStand eye : eyes) {
                        // Intensifying glow
                        double intensity = (double) tick / chargeDuration;
                        int particleCount = (int) (5 + (15 * intensity));

                        world.spawnParticle(Particle.FLAME, eye.getLocation(), particleCount,
                                0.1, 0.1, 0.1, 0.02 * intensity);
                    }

                    // Charging sound
                    if (tick % 10 == 0) {
                        world.playSound(boss.getLocation(), Sound.BLOCK_BEACON_AMBIENT,
                                0.5f, 0.5f + ((float) tick / chargeDuration));
                    }
                }
                // Attack phase - consume targets
                else {
                    int attackTick = tick - chargeDuration;

                    // Determine which target to attack next
                    for (LivingEntity target : targets) {
                        if (target == null || target.isDead() || killedEntities.contains(target)) {
                            continue;
                        }

                        // Check if this target is scheduled for attack
                        Integer attackTime = targetAttackTimes.get(target);
                        if (attackTime == null) {
                            // Schedule this target for attack
                            attackTime = Integer.valueOf(attackTick + random.nextInt(20)); // Random delay
                            targetAttackTimes.put(target, attackTime);
                        }

                        // If it's time to attack this target
                        if (attackTick >= attackTime) {
                            // Choose which eye to use
                            for (ArmorStand eye : eyes) {
                           eyes.get(random.nextInt(eyes.size()));

                            }
                            // Attack target
                            attackTarget(player, target, world, level);

                            // Mark as killed
                           killedEntities.add(target);
                        }
                    }

                    // Ambient eye effects
                    for (ArmorStand eye : eyes) {
                        world.spawnParticle(Particle.FLAME, eye.getLocation(), 5, 0.1, 0.1, 0.1, 0.02);
                    }
                }

                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * Attack a target with the black hole effect.
     *
     * @param player The player who summoned the boss
     * @param target The target entity
     * @param world The world
     * @param level The enchantment level
     */
    private void attackTarget(Player player, LivingEntity target, World world, int level) {
        Location targetLocation = target.getLocation().add(0, target.getHeight() / 2, 0);

        // Calculate distance to target
        double distance = targetLocation.distance(target.getLocation());

        // Laser beam density based on level
        double particleSpacing = 0.5 / level; // Higher level = denser beam

        // Create laser beam
        for (double d = 0; d < distance; d += particleSpacing) {
            Location particleLoc = targetLocation.clone().add(0, 0, 0);

            // Core beam
            world.spawnParticle(Particle.DUST_COLOR_TRANSITION, targetLocation, 0, 0, 0, 0, 1,
                    new org.bukkit.Particle.DustTransition(Color.RED, Color.GREEN, 1.0f));


            // Random side particles for effect
            if (random.nextInt(10) < 3) {
                double offsetX = (random.nextDouble() - 0.5) * 0.2;
                double offsetY = (random.nextDouble() - 0.5) * 0.2;
                double offsetZ = (random.nextDouble() - 0.5) * 0.2;

                Location sideParticleLoc = particleLoc.clone().add(offsetX, offsetY, offsetZ);
                world.spawnParticle(Particle.FLAME, sideParticleLoc, 0, 0, 0, 0, 1);
            }
        }

        // Impact effect at target
        world.spawnParticle(Particle.EXPLOSION, targetLocation, 1, 0, 0, 0, 0);
        world.spawnParticle(Particle.FLAME, targetLocation, 10, 0.2, 0.2, 0.2, 0.1);

        // Play laser sound
        world.playSound(targetLocation, Sound.ENTITY_BLAZE_SHOOT, 1.0f, 0.6f);
        world.playSound(targetLocation, Sound.ENTITY_GENERIC_BURN, 1.0f, 1.0f);

        // Damage target
        target.damage(target.getHealth() + 10, player);
    }

    /**
     * Perform the third attack: Eye lasers with dialogue.
     *
     * @param player The player who summoned the boss
     * @param boss The main boss entity
     * @param bossParts List of boss parts (eyes are the last two)
     * @param centerLocation The center location
     * @param targets List of target entities
     * @param level The enchantment level
     * @param callback Callback to run when attack is complete
     */
    private void performThirdAttack(Player player, ArmorStand boss, List<ArmorStand> bossParts,
                                    Location centerLocation, List<LivingEntity> targets, int level, Runnable callback) {
        // Find the eye parts of the boss
        List<ArmorStand> eyes = bossParts.stream()
                .filter(part -> part.getCustomName() != null && part.getCustomName().contains("Eye"))
                .collect(Collectors.toList());

        // Need at least two eyes for this attack
        if (eyes.size() < 2) {
            callback.run();
            return;
        }

        // Get the eye parts (limit to 2)
        ArmorStand leftEye = eyes.get(2);
        ArmorStand rightEye = eyes.size() > 1 ? eyes.get(1) : eyes.get(2);

        // Number of targets to attack based on level
        int targetCount = Math.min(targets.size(), level * 2);

        // Create a temporary mutable copy, shuffle and select subset
        List<LivingEntity> targetsCopy = new ArrayList<>(targets);
        Collections.shuffle(targetsCopy);
        if (targetsCopy.size() > targetCount) {
            targetsCopy = targetsCopy.subList(0, targetCount);
        }

        // Now create the final variable for use in the inner class
        final List<LivingEntity> selectedTargets = new ArrayList<>(targetsCopy);

        // Start dialogue sequence followed by laser attack
        new BukkitRunnable() {
            int dialoguePhase = 0;

            @Override
            public void run() {
                switch (dialoguePhase) {
                    case 0:
                        showBossDialogue(boss, ChatColor.RED + "Third Attack:",
                                ChatColor.GOLD + "Soul Searing Gaze!", 5);
                        dialoguePhase++;
                        break;

                    case 1:
                        showBossDialogue(boss, ChatColor.DARK_RED + "I see you...",
                                ChatColor.DARK_RED + "Your fate is sealed.", 5);
                        dialoguePhase++;
                        break;

                    case 2:
                        showBossDialogue(boss, ChatColor.RED + "None shall escape...",
                                ChatColor.RED + "My burning gaze!", 5);
                        dialoguePhase++;
                        break;

                    case 3:
                        // Start the actual attack - use selectedTargets directly since it's already final
                        performEyeLaserAttack(player, boss, eyes, selectedTargets, level, callback);
                        this.cancel();
                        break;
                }
            }
        }.runTaskTimer(plugin, 0L, 6 * 20L); // 6 seconds between dialogue phases
    }

    /**
     * Perform the eye laser attack.
     *
     * @param player The player who summoned the boss
     * @param boss The main boss entity
     * @param eyes List of eye armor stands
     * @param targets List of target entities
     * @param level The enchantment level
     * @param callback Callback to run when attack is complete
     */
    private void performEyeLaserAttack(Player player, ArmorStand boss, List<ArmorStand> eyes,
                                       List<LivingEntity> targets, int level, Runnable callback) {

        World world = boss.getWorld();

        // Track killed entities
        Set<LivingEntity> killedEntities = new HashSet<>();

        // Begin eye glow effect
        for (ArmorStand eye : eyes) {
            world.spawnParticle(Particle.FLAME, eye.getLocation(), 10, 0.1, 0.1, 0.1, 0.02);
        }

        // Play charge sound
        world.playSound(boss.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.0f);

        // Start the laser attack sequence
        new BukkitRunnable() {
            int tick = 0;
            final int chargeDuration = 40; // 2 seconds to charge
            final int attackDuration = 100; // 5 seconds of attack
            Map<LivingEntity, Integer> targetAttackTimes = new HashMap<>();

            @Override
            public void run() {
                if (tick >= chargeDuration + attackDuration || targets.isEmpty() ||
                        killedEntities.size() >= targets.size()) {
                    // End attack
                    callback.run();
                    this.cancel();
                    return;
                }

                // Charging phase - eye glow intensifies
                if (tick < chargeDuration) {
                    for (ArmorStand eye : eyes) {
                        // Intensifying glow
                        double intensity = (double) tick / chargeDuration;
                        int particleCount = (int) (5 + (15 * intensity));

                        world.spawnParticle(Particle.FLAME, eye.getLocation(), particleCount,
                                0.1, 0.1, 0.1, 0.02 * intensity);
                    }

                    // Charging sound
                    if (tick % 10 == 0) {
                        world.playSound(boss.getLocation(), Sound.BLOCK_BEACON_AMBIENT,
                                0.5f, 0.5f + ((float) tick / chargeDuration));
                    }
                }
                // Attack phase - fire lasers
                else {
                    int attackTick = tick - chargeDuration;

                    // Determine which target to attack next
                    for (LivingEntity target : targets) {
                        if (target == null || target.isDead() || killedEntities.contains(target)) {
                            continue;
                        }

                        // Check if this target is scheduled for attack
                        Integer attackTime = targetAttackTimes.get(target);
                        if (attackTime == null) {
                            // Schedule this target for attack
                            attackTime = Integer.valueOf(attackTick + random.nextInt(20)); // Random delay
                            targetAttackTimes.put(target, attackTime);
                        }

                        // If it's time to attack this target
                        if (attackTick >= attackTime) {
                            // Choose which eye to use
                            ArmorStand eye = eyes.get(random.nextInt(eyes.size()));

                            // Fire laser at target
                            fireLaserAtTarget(eye, target, world, level);

                            // Damage target
                            target.damage(target.getHealth() + 10, player);

                            // Create death effect
                            world.spawnParticle(Particle.LAVA, target.getLocation(), 20, 0.3, 0.5, 0.3, 0.1);
                            world.spawnParticle(Particle.FLAME, target.getLocation(), 30, 0.5, 0.5, 0.5, 0.1);

                            // Play sound
                            world.playSound(target.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.5f);

                            // Mark as killed
                            killedEntities.add(target);
                        }
                    }

                    // Ambient eye effects
                    for (ArmorStand eye : eyes) {
                        world.spawnParticle(Particle.FLAME, eye.getLocation(), 5, 0.1, 0.1, 0.1, 0.02);
                    }
                }

                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * Fire a laser from an eye to a target.
     *
     * @param eye The eye armor stand
     * @param target The target entity
     * @param world The world
     * @param level The enchantment level
     */
    private void fireLaserAtTarget(ArmorStand eye, LivingEntity target, World world, int level) {
        Location eyeLocation = eye.getLocation();
        Location targetLocation = target.getLocation().add(0, target.getHeight() / 2, 0);

        // Calculate direction
        Vector direction = targetLocation.clone().subtract(eyeLocation).toVector().normalize();

        // Determine laser length
        double distance = eyeLocation.distance(targetLocation);

        // Laser beam density based on level
        double particleSpacing = 0.5 / level; // Higher level = denser beam

        // Create laser beam
        for (double d = 0; d < distance; d += particleSpacing) {
            Location particleLoc = eyeLocation.clone().add(direction.clone().multiply(d));

            // Core beam
            world.spawnParticle(Particle.DUST_COLOR_TRANSITION, particleLoc, 0, 0, 0, 0, 1,
                    new org.bukkit.Particle.DustOptions(org.bukkit.Color.RED, 1.0f));

            // Random side particles for effect
            if (random.nextInt(10) < 3) {
                double offsetX = (random.nextDouble() - 0.5) * 0.2;
                double offsetY = (random.nextDouble() - 0.5) * 0.2;
                double offsetZ = (random.nextDouble() - 0.5) * 0.2;

                Location sideParticleLoc = particleLoc.clone().add(offsetX, offsetY, offsetZ);
                world.spawnParticle(Particle.FLAME, sideParticleLoc, 0, 0, 0, 0, 1);
            }
        }

        // Impact effect at target
        world.spawnParticle(Particle.EXPLOSION, targetLocation, 1, 0, 0, 0, 0);
        world.spawnParticle(Particle.FLAME, targetLocation, 10, 0.2, 0.2, 0.2, 0.1);

        // Play laser sound
        world.playSound(eyeLocation, Sound.ENTITY_BLAZE_SHOOT, 1.0f, 0.6f);
        world.playSound(targetLocation, Sound.ENTITY_GENERIC_BURN, 1.0f, 1.0f);
    }

    /**
     * Get the level of the Sword Summoner enchantment on an item.
     *
     * @param item The item to check
     * @return The enchantment level, or 0 if not enchanted
     */
    private int getEnchantmentLevel(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return 0;
        }

        // Try to get enchantment level from item
        try {
            org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
            if (meta == null) {
                return 0;
            }

            List<String> lore = meta.getLore();
            if (lore == null || lore.isEmpty()) {
                return 0;
            }

            // Check lore for enchantment
            for (String line : lore) {
                if (line.contains(getName())) {
                    // Extract level from Roman numerals
                    String[] parts = line.split(" ");
                    if (parts.length > 1) {
                        String roman = parts[parts.length - 1];
                        return romanToInt(ChatColor.stripColor(roman));
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Error getting enchantment level: " + e.getMessage());
        }

        return 0;
    }

    /**
     * Convert a Roman numeral to an integer.
     *
     * @param roman The Roman numeral
     * @return The integer value
     */
    private int romanToInt(String roman) {
        Map<Character, Integer> romanMap = new HashMap<>();
        romanMap.put('I', 1);
        romanMap.put('V', 5);
        romanMap.put('X', 10);
        romanMap.put('L', 50);
        romanMap.put('C', 100);
        romanMap.put('D', 500);
        romanMap.put('M', 1000);

        int result = 0;
        for (int i = 0; i < roman.length(); i++) {
            if (i > 0 && romanMap.get(roman.charAt(i)) > romanMap.get(roman.charAt(i-1))) {
                result += romanMap.get(roman.charAt(i)) - 2*romanMap.get(roman.charAt(i-1));
            } else {
                result += romanMap.get(roman.charAt(i));
            }
        }
        return result;
    }

    /**
     * Check if a player is on cooldown.
     *
     * @param player The player to check
     * @return True if the player is on cooldown
     */
    private boolean isOnCooldown(Player player) {
        UUID playerId = player.getUniqueId();
        if (lastProcTime.containsKey(playerId)) {
            long lastProc = lastProcTime.get(playerId);
            long currentTime = System.currentTimeMillis();

            if (currentTime - lastProc < COOLDOWN_SECONDS * 1000) {
                long remainingSeconds = ((COOLDOWN_SECONDS * 1000) - (currentTime - lastProc)) / 1000;
                player.sendMessage(ChatColor.RED + "Sword Summoner is on cooldown for " +
                        ChatColor.YELLOW + remainingSeconds +
                        ChatColor.RED + " more seconds!");
                return true;
            }
        }
        return false;
    }

    /**
     * Set the last proc time for a player.
     *
     * @param player The player to set the proc time for
     */
    private void setLastProcTime(Player player) {
        lastProcTime.put(player.getUniqueId(), Long.valueOf(System.currentTimeMillis()));
    }

    /**
     * Clean up old cooldown entries.
     */
    private void cleanupOldEntries() {
        long currentTime = System.currentTimeMillis();
        lastProcTime.entrySet().removeIf(entry ->
                currentTime - entry.getValue() > COOLDOWN_SECONDS * 1000 * 2);
    }

    @Override
    public boolean conflictsWith(CustomEnchant other) {
        return other.getName().equalsIgnoreCase("SummoningRitual") ||
                other.getName().equalsIgnoreCase("SoulHarvester") ||
                other.getName().equalsIgnoreCase("DimensionalRift");
    }
}