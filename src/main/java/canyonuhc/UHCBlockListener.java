package canyonuhc;

import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockListener;
import org.bukkit.event.block.BlockPlaceEvent;

public class UHCBlockListener extends BlockListener {
    private final UHCPlugin plugin;

    public UHCBlockListener(UHCPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onBlockBreak(BlockBreakEvent event) {
        if (
            plugin.spectatingPlayers.contains(event.getPlayer().getName()) ||
            !plugin.isInsideWorldBorder(event.getBlock().getLocation())
        ) {
            event.setCancelled(true);
        }
    }

    @Override
    public void onBlockPlace(BlockPlaceEvent event) {
        if (
            plugin.spectatingPlayers.contains(event.getPlayer().getName()) ||
            !plugin.isInsideWorldBorder(event.getBlock().getLocation())
        ) {
            event.setCancelled(true);
        }
    }
}
