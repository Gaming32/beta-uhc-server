package canyonuhc.packets;

import org.bukkit.entity.Player;

@FunctionalInterface
public interface CustomPacketListener {
    void handle(Player player, String packetType, String data);
}
