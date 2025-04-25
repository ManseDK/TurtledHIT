package net.manse.turtledhit;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;

public class TurtledHIT extends JavaPlugin implements Listener {

    private final Set<UUID> recentlyAttacked = ConcurrentHashMap.newKeySet();
    private boolean debug = false;
    private boolean enabled = true;
    private List<String> enabledWorlds = new ArrayList<>();
    private PacketEventsPacketListener packetListener;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        CompletableFuture.runAsync(this::loadConfig).thenRun(() -> {
            getServer().getPluginManager().registerEvents(this, this);
            packetListener = new PacketEventsPacketListener();
            if (enabled) {
                PacketEvents.getAPI().getEventManager().registerListener(packetListener);
            }
            getLogger().info("TurtledHIT loaded! Status: " + (enabled ? "ENABLED" : "DISABLED") + ", Debug mode: " + debug);
            getLogger().info("Enabled worlds: " + String.join(", ", enabledWorlds));
        });
    }

    private void loadConfig() {
        FileConfiguration config = getConfig();
        debug = config.getBoolean("debug", false);
        enabled = config.getBoolean("enabled", true);
        List<String> defaultWorlds = new ArrayList<>();
        defaultWorlds.add("world");
        enabledWorlds = config.getStringList("enabled-worlds");
        if (enabledWorlds.isEmpty()) {
            enabledWorlds = defaultWorlds;
            CompletableFuture.runAsync(() -> {
                config.set("enabled-worlds", enabledWorlds);
                saveConfig();
            });
        }
    }

    @Override
    public void onDisable() {
        if (packetListener != null) {
            PacketEvents.getAPI().getEventManager().unregisterListener(packetListener);
        }
        recentlyAttacked.clear();
        getLogger().info("TurtledHIT disabled!");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("turtledhit")) return false;

        if (!sender.hasPermission("turtledhit.toggle")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }

        if (args.length == 0) {
            sendCommandHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "toggle":
                handleToggleCommand(sender);
                break;
            case "debug":
                handleDebugCommand(sender);
                break;
            case "status":
                handleStatusCommand(sender);
                break;
            case "world":
                handleWorldCommand(sender, args);
                break;
            default:
                sender.sendMessage(ChatColor.RED + "Unknown subcommand. Use /turtledhit for help.");
        }
        return true;
    }

    private void sendCommandHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "TurtledHIT v" + getDescription().getVersion());
        sender.sendMessage(ChatColor.YELLOW + "/turtledhit toggle - Toggle the plugin on/off");
        sender.sendMessage(ChatColor.YELLOW + "/turtledhit status - Check plugin status");
        sender.sendMessage(ChatColor.YELLOW + "/turtledhit debug - Toggle debug mode");
        sender.sendMessage(ChatColor.YELLOW + "/turtledhit world add <world> - Add a world to enabled list");
        sender.sendMessage(ChatColor.YELLOW + "/turtledhit world remove <world> - Remove a world from enabled list");
        sender.sendMessage(ChatColor.YELLOW + "/turtledhit world list - List enabled worlds");
    }

    private void handleToggleCommand(CommandSender sender) {
        enabled = !enabled;
        if (packetListener != null) {
            if (enabled) {
                PacketEvents.getAPI().getEventManager().registerListener(packetListener);
                sender.sendMessage(ChatColor.GREEN + "TurtledHIT packet listener has been ENABLED.");
            } else {
                PacketEvents.getAPI().getEventManager().unregisterListener(packetListener);
                sender.sendMessage(ChatColor.RED + "TurtledHIT packet listener has been DISABLED.");
            }
        }
        CompletableFuture.runAsync(() -> {
            getConfig().set("enabled", enabled);
            saveConfig();
        });
    }

    private void handleDebugCommand(CommandSender sender) {
        debug = !debug;
        sender.sendMessage(ChatColor.YELLOW + "TurtledHIT debug mode " +
                (debug ? ChatColor.GREEN + "ENABLED" : ChatColor.RED + "DISABLED"));
        CompletableFuture.runAsync(() -> {
            getConfig().set("debug", debug);
            saveConfig();
        });
    }

    private void handleStatusCommand(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "TurtledHIT Status:");
        sender.sendMessage(ChatColor.YELLOW + "Packet listening: " +
                (enabled ? ChatColor.GREEN + "ENABLED" : ChatColor.RED + "DISABLED"));
        sender.sendMessage(ChatColor.YELLOW + "Debug mode: " +
                (debug ? ChatColor.GREEN + "ENABLED" : ChatColor.RED + "DISABLED"));
        sender.sendMessage(ChatColor.YELLOW + "Enabled worlds: " + String.join(", ", enabledWorlds));
        sender.sendMessage(ChatColor.YELLOW + "Tracked recent attacks: " + recentlyAttacked.size());
    }

    private void handleWorldCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /turtledhit world <list|add|remove>");
            return;
        }

        switch (args[1].toLowerCase()) {
            case "list":
                handleWorldList(sender);
                break;
            case "add":
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.RED + "Usage: /turtledhit world add <world>");
                    return;
                }
                handleWorldAdd(sender, args[2]);
                break;
            case "remove":
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.RED + "Usage: /turtledhit world remove <world>");
                    return;
                }
                handleWorldRemove(sender, args[2]);
                break;
            default:
                sender.sendMessage(ChatColor.RED + "Unknown subcommand. Use /turtledhit for help.");
        }
    }

    private void handleWorldList(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "TurtledHIT Enabled Worlds:");
        for (String world : enabledWorlds) {
            boolean exists = (Bukkit.getWorld(world) != null);
            sender.sendMessage(ChatColor.YELLOW + "- " + world +
                    (exists ? ChatColor.GREEN + " (Loaded)" : ChatColor.RED + " (Not loaded)"));
        }
    }

    private void handleWorldAdd(CommandSender sender, String worldName) {
        if (enabledWorlds.contains(worldName)) {
            sender.sendMessage(ChatColor.YELLOW + "World '" + worldName + "' is already enabled.");
            return;
        }

        enabledWorlds.add(worldName);
        World world = Bukkit.getWorld(worldName);
        sender.sendMessage(ChatColor.GREEN + "Added world '" + worldName + "' to enabled worlds list." +
                (world == null ? ChatColor.YELLOW + " Warning: This world isn't currently loaded." : ""));

        CompletableFuture.runAsync(() -> {
            getConfig().set("enabled-worlds", enabledWorlds);
            saveConfig();
        });
    }

    private void handleWorldRemove(CommandSender sender, String worldName) {
        if (!enabledWorlds.contains(worldName)) {
            sender.sendMessage(ChatColor.YELLOW + "World '" + worldName + "' is not in the enabled list.");
            return;
        }

        enabledWorlds.remove(worldName);
        sender.sendMessage(ChatColor.GREEN + "Removed world '" + worldName + "' from enabled worlds list.");

        CompletableFuture.runAsync(() -> {
            getConfig().set("enabled-worlds", enabledWorlds);
            saveConfig();
        });
    }

    private boolean isWorldEnabled(String worldName) {
        return enabledWorlds.contains(worldName);
    }

    private class PacketEventsPacketListener extends PacketListenerAbstract {
        public PacketEventsPacketListener() {
            super(PacketListenerPriority.HIGH);
        }

        @Override
        public void onPacketReceive(PacketReceiveEvent event) {
            if (!enabled) return;

            if (event.getPacketType() == PacketType.Play.Client.INTERACT_ENTITY) {
                final Player player = (Player) event.getPlayer();

                if (!isWorldEnabled(player.getWorld().getName())) {
                    return;
                }

                WrapperPlayClientInteractEntity packet = new WrapperPlayClientInteractEntity(event);
                if (packet.getAction() == WrapperPlayClientInteractEntity.InteractAction.ATTACK) {
                    final int entityId = packet.getEntityId();

                    Bukkit.getScheduler().runTaskAsynchronously(TurtledHIT.this, () -> {
                        Entity target = null;
                        for (Entity entity : player.getWorld().getEntities()) {
                            if (entity.getEntityId() == entityId) {
                                target = entity;
                                break;
                            }
                        }

                        if (target != null) {
                            recentlyAttacked.add(target.getUniqueId());
                        }
                    });
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player)) return;

        Player attacker = (Player) event.getDamager();
        Entity target = event.getEntity();

        if (!enabled || !isWorldEnabled(target.getWorld().getName())) return;

        UUID targetUUID = target.getUniqueId();

        if (recentlyAttacked.remove(targetUUID)) {
            if (attacker.hasPermission("turtledhit.notify") && debug) {
                attacker.sendMessage(ChatColor.DARK_AQUA + "[TurtledHIT] " +
                        ChatColor.GREEN + "Hit registered via packet!");
            }
        } else {
            if (attacker.hasPermission("turtledhit.notify") && debug) {
                attacker.sendMessage(ChatColor.DARK_AQUA + "[TurtledHIT] " +
                        ChatColor.YELLOW + "Normal hit registered.");
            }
        }
    }
}