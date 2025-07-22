package dev.proplayer919.doublejumpboost;

import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.BooleanFlag;
import com.sk89q.worldguard.protection.flags.registry.FlagRegistry;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.plugin.java.JavaPlugin;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldedit.bukkit.BukkitAdapter;

import java.util.HashMap;
import java.util.UUID;

public class DoubleJumpBoostPlugin extends JavaPlugin implements Listener {
    private static BooleanFlag DOUBLE_JUMP_BOOST_FLAG;
    private HashMap<UUID, Long> lastDoubleJump = new HashMap<>();
    private double jumpVelocity;
    private int cooldownTicks;
    private String particleEffect;
    private String soundEffect;

    @Override
    public void onEnable() {
        // Save default config if it doesn't exist
        saveDefaultConfig();
        loadConfig();

        // Register the custom flag with WorldGuard
        registerWorldGuardFlag();

        // Register events
        getServer().getPluginManager().registerEvents(this, this);

        getLogger().info("DoubleJumpBoostPlugin enabled!");
    }

    private void loadConfig() {
        FileConfiguration config = getConfig();
        jumpVelocity = config.getDouble("jump-velocity", 1.0);
        cooldownTicks = config.getInt("cooldown-ticks", 60);
        particleEffect = config.getString("particle-effect", "CLOUD");
        soundEffect = config.getString("sound-effect", "ENTITY_ENDER_DRAGON_FLAP");
    }

    private void registerWorldGuardFlag() {
        FlagRegistry registry = WorldGuard.getInstance().getFlagRegistry();
        try {
            DOUBLE_JUMP_BOOST_FLAG = new BooleanFlag("double-jump-boost", false);
            registry.register(DOUBLE_JUMP_BOOST_FLAG);
            getLogger().info("Registered double-jump-boost flag with WorldGuard.");
        } catch (Exception e) {
            getLogger().severe("Failed to register double-jump-boost flag: " + e.getMessage());
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("doublejumpboost")) {
            if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
                if (!sender.hasPermission("doublejumpboost.reload")) {
                    sender.sendMessage("§cYou don't have permission to reload the plugin!");
                    return true;
                }
                reloadConfig();
                loadConfig();
                sender.sendMessage("§aDoubleJumpBoost configuration reloaded!");
                return true;
            }
            sender.sendMessage("§cUsage: /doublejumpboost reload");
            return true;
        }
        return false;
    }

    @EventHandler
    public void onPlayerToggleFlight(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode().isInvulnerable()) return; // Ignore creative/spectator
        if (!player.hasPermission("doublejumpboost.use")) return; // Check permission

        Location loc = BukkitAdapter.adapt(player.getLocation());
        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        RegionQuery query = container.createQuery();
        ApplicableRegionSet regions = query.getApplicableRegions(loc);

        // Check if player is in a region with double-jump-boost flag set to true
        if (regions.testState(null, DOUBLE_JUMP_BOOST_FLAG)) {
            event.setCancelled(true); // Prevent default flying
            if (canDoubleJump(player)) {
                performDoubleJump(player);
            }
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode().isInvulnerable()) return;
        if (!player.hasPermission("doublejumpboost.use")) return; // Check permission

        Location loc = BukkitAdapter.adapt(player.getLocation());
        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        RegionQuery query = container.createQuery();
        ApplicableRegionSet regions = query.getApplicableRegions(loc);

        // Enable flight if on ground and in a double-jump region
        if (regions.testState(null, DOUBLE_JUMP_BOOST_FLAG)) {
            if (!player.isFlying() && player.isOnGround()) {
                player.setAllowFlight(true);
            }
        } else {
            // Disable flight if not in a double-jump region
            if (player.getAllowFlight()) {
                player.setAllowFlight(false);
                player.setFlying(false);
            }
        }
    }

    private boolean canDoubleJump(Player player) {
        long currentTime = System.currentTimeMillis();
        UUID playerId = player.getUniqueId();
        if (lastDoubleJump.containsKey(playerId)) {
            long lastJumpTime = lastDoubleJump.get(playerId);
            long cooldownMillis = (cooldownTicks * 1000L) / 20; // Convert ticks to milliseconds
            if (currentTime - lastJumpTime < cooldownMillis) {
                return false;
            }
        }
        return true;
    }

    private void performDoubleJump(Player player) {
        // Apply upward velocity
        player.setVelocity(player.getVelocity().setY(jumpVelocity));

        // Play effects
        try {
            player.getWorld().spawnParticle(Particle.valueOf(particleEffect), player.getLocation(), 20, 0.5, 0.5, 0.5, 0.1);
            player.getWorld().playSound(player.getLocation(), Sound.valueOf(soundEffect), 1.0f, 1.0f);
        } catch (IllegalArgumentException e) {
            getLogger().warning("Invalid particle or sound effect in config: " + e.getMessage());
        }

        // Update cooldown
        lastDoubleJump.put(player.getUniqueId(), System.currentTimeMillis());

        // Disable flight until player lands again
        player.setAllowFlight(false);
    }

    @Override
    public void onDisable() {
        getLogger().info("DoubleJumpBoostPlugin disabled!");
    }
}
