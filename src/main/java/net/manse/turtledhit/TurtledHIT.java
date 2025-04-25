package net.manse.turtledhit;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TurtledHIT extends JavaPlugin implements Listener {

    private final Set<UUID> recentlyAttacked = ConcurrentHashMap.newKeySet();
    private boolean debug = false;
    private boolean enabled = true;
    private PacketEventsPacketListener packetListener;

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);

        packetListener = new PacketEventsPacketListener();

        PacketEvents.getAPI().getEventManager().registerListener(packetListener);

        saveDefaultConfig();
        debug = getConfig().getBoolean("debug", false);
        enabled = getConfig().getBoolean("enabled", true);

        getLogger().info("TurtledHIT loaded! Status: " + (enabled ? "ENABLED" : "DISABLED") + ", Debug mode: " + debug);
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
        if (command.getName().equalsIgnoreCase("turtledhit")) {
            if (!sender.hasPermission("turtledhit.toggle")) {
                sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
                return true;
            }

            if (args.length == 0) {
                sender.sendMessage(ChatColor.GOLD + "TurtledHIT v" + getDescription().getVersion());
                sender.sendMessage(ChatColor.YELLOW + "/turtledhit toggle - Toggle the plugin on/off");
                sender.sendMessage(ChatColor.YELLOW + "/turtledhit status - Check plugin status");
                sender.sendMessage(ChatColor.YELLOW + "/turtledhit debug - Toggle debug mode");
                return true;
            }

            if (args[0].equalsIgnoreCase("toggle")) {
                if (enabled) {
                    enabled = false;
                    if (packetListener != null) {
                        PacketEvents.getAPI().getEventManager().unregisterListener(packetListener);
                    }
                    sender.sendMessage(ChatColor.RED + "TurtledHIT packet listener has been DISABLED.");
                } else {
                    enabled = true;
                    if (packetListener != null) {
                        PacketEvents.getAPI().getEventManager().registerListener(packetListener);
                    }
                    sender.sendMessage(ChatColor.GREEN + "TurtledHIT packet listener has been ENABLED.");
                }
                getConfig().set("enabled", enabled);
                saveConfig();
                return true;
            }

            if (args[0].equalsIgnoreCase("debug")) {
                debug = !debug;
                sender.sendMessage(ChatColor.YELLOW + "TurtledHIT debug mode " +
                        (debug ? ChatColor.GREEN + "ENABLED" : ChatColor.RED + "DISABLED"));
                getConfig().set("debug", debug);
                saveConfig();
                return true;
            }

            if (args[0].equalsIgnoreCase("status")) {
                sender.sendMessage(ChatColor.GOLD + "TurtledHIT Status:");
                sender.sendMessage(ChatColor.YELLOW + "Packet listening: " +
                        (enabled ? ChatColor.GREEN + "ENABLED" : ChatColor.RED + "DISABLED"));
                sender.sendMessage(ChatColor.YELLOW + "Debug mode: " +
                        (debug ? ChatColor.GREEN + "ENABLED" : ChatColor.RED + "DISABLED"));
                sender.sendMessage(ChatColor.YELLOW + "Tracked recent attacks: " + recentlyAttacked.size());
                return true;
            }

            sender.sendMessage(ChatColor.RED + "Unknown subcommand. Use /turtledhit for help.");
            return true;
        }
        return false;
    }

    private void log(String message) {
        if (debug) {
            getLogger().info("[DEBUG] " + message);
        }
    }

    private class PacketEventsPacketListener extends PacketListenerAbstract {
        public PacketEventsPacketListener() {
            super(PacketListenerPriority.HIGH);
        }

        @Override
        public void onPacketReceive(PacketReceiveEvent event) {
            if (!enabled) return;

            if (event.getPacketType() == PacketType.Play.Client.INTERACT_ENTITY) {
                WrapperPlayClientInteractEntity packet = new WrapperPlayClientInteractEntity(event);
                if (packet.getAction() == WrapperPlayClientInteractEntity.InteractAction.ATTACK) {
                    final Player player = (Player) event.getPlayer();
                    final int entityId = packet.getEntityId();

                    log("Attack packet received from " + player.getName() + " targeting entity ID: " + entityId);

                    Bukkit.getScheduler().runTask(TurtledHIT.this, () -> {
                        Entity target = null;
                        for (Entity entity : player.getWorld().getEntities()) {
                            if (entity.getEntityId() == entityId) {
                                target = entity;
                                break;
                            }
                        }

                        if (target != null) {
                            log("Found entity: " + target.getType() + " with UUID: " + target.getUniqueId());
                            recentlyAttacked.add(target.getUniqueId());
                        } else {
                            log("Could not find entity with ID: " + entityId);
                        }
                    });
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player)) {
            return;
        }

        Player attacker = (Player) event.getDamager();
        Entity target = event.getEntity();
        UUID targetUUID = target.getUniqueId();

        if (enabled) {
            if (recentlyAttacked.remove(targetUUID)) {
                log("Hit registered via packet from " + attacker.getName() + " to " + target.getType());

                if (attacker.hasPermission("turtledhit.notify") && debug) {
                    attacker.sendMessage(ChatColor.DARK_AQUA + "[TurtledHIT] " +
                            ChatColor.GREEN + "Hit registered via packet!");
                }
            } else {
                log("Normal hit from " + attacker.getName() + " to " + target.getType());

                if (attacker.hasPermission("turtledhit.notify") && debug) {
                    attacker.sendMessage(ChatColor.DARK_AQUA + "[TurtledHIT] " +
                            ChatColor.YELLOW + "Normal hit registered.");
                }
            }
        } else {
            log("Plugin disabled, not tracking hit from " + attacker.getName() + " to " + target.getType());
        }
    }
}