package canyonuhc;

import java.util.HashSet;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
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
        if (plugin.uhcStarted) {
            plugin.initPlayerDead(event.getPlayer());
            plugin.spectatingPlayers.add(event.getPlayer().getName());
        }
        awaitingPingResponse.add(event.getPlayer().getName());
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
            // Sync spectators
            plugin.packetManager.sendPacket(event.getPlayer(), "reset-spectators");
            plugin.worldBorderInit(event.getPlayer());
            for (String spectator : plugin.spectatingPlayers) {
                plugin.packetManager.sendPacket(event.getPlayer(), "spectator", spectator);
            }
        }, 20);
    }

    @Override
    public void onPlayerKick(PlayerKickEvent event) {
        if (event.getReason().equals("Flying is not enabled on this server")) {
            // Total hack, but w/e
            event.getPlayer().setFallDistance(0);
            event.setCancelled(true);
        }
    }

    @Override
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (plugin.uhcStarted) {
            plugin.killPlayer(event.getPlayer());
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
