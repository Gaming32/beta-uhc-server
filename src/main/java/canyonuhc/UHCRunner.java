package canyonuhc;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Effect;
import org.bukkit.entity.Player;

import canyonuhc.uhc.WorldBorderStage;

public final class UHCRunner {
    private final UHCPlugin plugin;
    private WorldBorderStage currentStage = WorldBorderStage.FIRST;

    public UHCRunner(UHCPlugin plugin) {
        this.plugin = plugin;
    }

    public void worldBorderFinishedShrinking() {
        currentStage = currentStage.getNextStage();
        if (currentStage == null) {
            return;
        }
        if (currentStage == WorldBorderStage.END) {
            worldBorderComplete();
            return;
        }
        double size = plugin.getWorldBorder();
        moveWorldBorders(currentStage.getEndSize(), currentStage.getTime(size));
    }

    public void beginUhc() {
        startGracePeriod(() -> {
            UHCPlugin.setPvp(true);
            startWorldBorders();
        });
    }

    private void worldBorderComplete() {
        Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> {
        }, 5 * 60 * 20);
    }

    private void startGracePeriod(Runnable endGracePeriod) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(ChatColor.GOLD +
                "Grace Period will end in 10 minutes, " +
                "once the grace period is over PvP will be enabled and world border will start moving"
            );
            player.playEffect(
                player.getLocation(),
                Effect.CLICK2,
                0 // data is unused for CLICK1, according to wiki.vg
            );
        }

        int[] remainingGracePeriod = {10};
        plugin.currentUhcTask = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            remainingGracePeriod[0] -= 2;
            if (remainingGracePeriod[0] == 0) {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    player.sendMessage(ChatColor.GOLD +
                        "Grace Period will end in " + remainingGracePeriod[0] + " minutes"
                    );
                    player.playEffect(
                        player.getLocation(),
                        Effect.CLICK2,
                        0 // data is unused for CLICK1, according to wiki.vg
                    );
                }
            } else {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    player.sendMessage(ChatColor.GOLD +
                        "Grace Period is now over, PVP is enabled and world border has started! Good Luck!"
                    );
                    player.playEffect(
                        player.getLocation(),
                        Effect.BOW_FIRE,
                        0 // data is unused for BOW_FIRE, according to wiki.vg
                    );
                }
                Bukkit.getScheduler().cancelTask(plugin.currentUhcTask);
                endGracePeriod.run();
            }
        }, 2 * 60 * 20, 2 * 60 * 60);
    }

    private void startWorldBorders() {
        double size = plugin.getWorldBorder();
        currentStage = WorldBorderStage.getStage(size);
        if (currentStage == null) {
            currentStage = WorldBorderStage.FIRST;
            moveWorldBorders(WorldBorderStage.FIRST.getStartSize(), 0);
        }
        if (currentStage == WorldBorderStage.END) {
            // trigger WORLD_BORDER_COMPLETE
            return;
        }
        moveWorldBorders(currentStage.getEndSize(), currentStage.getTime(size));
    }

    private void moveWorldBorders(double newSize, long time) {
        if (time == 0) {
            plugin.setWorldBorder(newSize);
        } else {
            plugin.setWorldBorder(newSize, time * 20);
        }
    }
}