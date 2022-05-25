package canyonuhc;

import java.util.HashSet;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerListener;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

public class UHCPlayerListener extends PlayerListener {
    private final Set<String> awaitingPingResponse = new HashSet<>();
    private final UHCPlugin plugin;

    public UHCPlayerListener(UHCPlugin plugin) {
        this.plugin = plugin;
        plugin.packetManager.register("pong", (player, packetType, data) ->
            awaitingPingResponse.remove(player.getName())
        );
    }

    @Override
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!plugin.spectatingPlayers.contains(event.getPlayer().getName())) return;
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK || event.getAction() == Action.RIGHT_CLICK_AIR) {
            String toTeleportTo = plugin.faceMapIdToPlayer.get(event.getPlayer().getItemInHand().getDurability());
            if (toTeleportTo != null) {
                Player playerToTeleportTo = Bukkit.getPlayerExact(toTeleportTo);
                if (playerToTeleportTo != null) {
                    event.getPlayer().teleport(playerToTeleportTo);
                }
            }
        }
    }

    @Override
    public void onPlayerPickupItem(PlayerPickupItemEvent event) {
        if (plugin.spectatingPlayers.contains(event.getPlayer().getName())) {
            event.setCancelled(true);
        }
    }

    @Override
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!plugin.playerFaceMaps.containsKey(event.getPlayer().getName())) {
            plugin.generateFaceMap(event.getPlayer());
        }
        awaitingPingResponse.add(event.getPlayer().getName());
        if (plugin.uhcStarted) {
            Bukkit.getScheduler().cancelTask(
                plugin.currentUhc.playerDeathTimeouts.remove(event.getPlayer().getName())
            );
        }
        Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> {
            plugin.packetManager.sendPacket(event.getPlayer(), "ping");
            Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> {
                if (awaitingPingResponse.contains(event.getPlayer().getName())) {
                    event.getPlayer().sendMessage(
                        ChatColor.RED + "WARNING: You do not have the UHC client mod installed. You may run into issues."
                    );
                    awaitingPingResponse.remove(event.getPlayer().getName());
                    // plugin.packetManager.getDoesntHaveMod().add(event.getPlayer().getName());
                }
            }, 60);
            // Sync data
            plugin.worldBorderInit(event.getPlayer());
            plugin.packetManager.sendPacket(event.getPlayer(), "reset-spectators");
            for (String spectator : plugin.spectatingPlayers) {
                plugin.packetManager.sendPacket(event.getPlayer(), "spectator", spectator);
            }
            if (plugin.spectatingPlayers.contains(event.getPlayer().getName())) {
                plugin.initPlayerDead(event.getPlayer());
            }
            plugin.packetManager.sendPacket(event.getPlayer(), "noglowing");
            for (var glowingEffect : plugin.globalGlowingEffects.entrySet()) {
                plugin.packetManager.sendPacket(
                    event.getPlayer(),
                    "glowing",
                    Integer.toHexString(glowingEffect.getValue()) + ' ' + glowingEffect.getKey()
                );
            }
            plugin.packetManager.sendPacket(event.getPlayer(), "cleardisplaynames");
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!player.getDisplayName().equals(player.getName())) {
                    plugin.packetManager.sendPacket(
                        event.getPlayer(),
                        "displayname",
                        player.getName() + ' ' + player.getDisplayName()
                    );
                }
            }
        }, 20);
    }

    @Override
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (plugin.uhcStarted) {
            plugin.currentUhc.playerDeathTimeouts.put(
                event.getPlayer().getName(),
                Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> {
                    UHCPlugin.broadcastMessage(
                        ChatColor.YELLOW + event.getPlayer().getName() +
                        " was considered dead because they logged out for more than 5 minutes."
                    );
                    plugin.lastDamageCauses.put(event.getPlayer().getName(), DamageCause.CUSTOM);
                    event.getPlayer().setHealth(0);
                    if (plugin.currentUhc != null) { // May be null if the game ended because of this
                        plugin.currentUhc.playerDeathTimeouts.remove(event.getPlayer().getName());
                    }
                }, 5 * 60 * 20)
            );
            plugin.currentUhc.checkUhcEnd();
        }
        plugin.packetManager.getDoesntHaveMod().remove(event.getPlayer().getName());
    }

    @Override
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        if (plugin.uhcStarted) {
            plugin.handOutMaps(event.getPlayer());
        }
    }

    @Override
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        if (plugin.spectatingPlayers.contains(event.getPlayer().getName())) {
            event.setCancelled(true);
        }
    }
}
