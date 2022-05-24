package canyonuhc.packets;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerChatEvent;
import org.bukkit.event.player.PlayerListener;

import canyonuhc.UHCPlugin;

public final class CustomPacketManager extends PlayerListener {
    private final Map<String, List<CustomPacketListener>> handlers = new HashMap<>();
    private final Set<String> doesntHaveMod = new HashSet<>();

    public CustomPacketManager() {
    }

    public void register(String packetType, CustomPacketListener handler) {
        handlers.computeIfAbsent(packetType, k -> new ArrayList<>()).add(handler);
    }

    public void sendPacket(Player player, String packetType) {
        if (doesntHaveMod.contains(player.getName())) return;
        player.sendMessage("canyonuhc:" + packetType);
    }

    public void sendPacket(Player player, String packetType, String data) {
        if (doesntHaveMod.contains(player.getName())) return;
        player.sendMessage("canyonuhc:" + packetType + ' ' + data);
    }

    public void broadcastPacket(String packetType) {
        String message = "canyonuhc:" + packetType;
        UHCPlugin.LOGGER.info(message);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (doesntHaveMod.contains(player.getName())) continue;
            player.sendMessage(message);
        }
    }

    public void broadcastPacket(String packetType, String data) {
        String message = "canyonuhc:" + packetType + ' ' + data;
        UHCPlugin.LOGGER.info(message);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (doesntHaveMod.contains(player.getName())) continue;
            player.sendMessage(message);
        }
    }

    @Override
    public void onPlayerChat(PlayerChatEvent event) {
        if (doesntHaveMod.contains(event.getPlayer().getName())) return;
        String message = event.getMessage();
        if (message.startsWith("canyonuhc:")) {
            event.setCancelled(true);
            int endIndex = message.indexOf(' ', 10);
            String data;
            if (endIndex == -1) {
                endIndex = message.length();
                data = null;
            } else {
                data = message.substring(endIndex + 1);
            }
            String packetType = message.substring(10, endIndex);
            List<CustomPacketListener> handlers;
            if ((handlers = this.handlers.get(packetType)) != null) {
                for (CustomPacketListener handler : handlers) {
                    try {
                        handler.handle(event.getPlayer(), packetType, data);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    public Set<String> getDoesntHaveMod() {
        return doesntHaveMod;
    }

    public static String doubleToString(double d) {
        return Long.toHexString(Double.doubleToRawLongBits(d));
    }

    public static double stringToDouble(String s) {
        return Double.longBitsToDouble(Long.parseUnsignedLong(s, 16));
    }
}
