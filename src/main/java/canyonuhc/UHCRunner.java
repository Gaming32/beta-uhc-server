package canyonuhc;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;

import canyonuhc.uhc.WorldBorderStage;

public final class UHCRunner {
    private final UHCPlugin plugin;
    private final Set<Integer> activeTasks = new HashSet<>(1);
    public final boolean teamGame;

    private WorldBorderStage currentStage = WorldBorderStage.FIRST;

    public UHCRunner(UHCPlugin plugin, boolean teamGame) {
        this.plugin = plugin;
        this.teamGame = teamGame;
    }

    public void worldBorderFinishedShrinking() {
        currentStage = currentStage.getNextStage();
        if (currentStage == null) {
            return;
        }
        if (currentStage == WorldBorderStage.END) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!player.getWorld().getName().equals("world")) {
                    player.sendMessage(ChatColor.GOLD +
                        "The nether will close in 5 minutes."
                    );
                }
            }
            activeTasks.add(Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (!player.getWorld().getName().equals("world")) {
                        plugin.lastDamageCauses.put(player.getName(), DamageCause.FIRE);
                        player.setHealth(0);
                    }
                }
            }, 5 * 60 * 20, 100));
            return;
        } else if (currentStage == WorldBorderStage.SIX) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!player.getWorld().getName().equals("world")) {
                    player.sendMessage(ChatColor.GOLD +
                        "The nether will close in 16 minutes."
                    );
                }
            }
        }
        double size = plugin.getWorldBorder();
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(ChatColor.GOLD +
                "The world border currently extends from -" + size + " to " + size
            );
            player.playEffect(
                player.getLocation(),
                Effect.CLICK1,
                0 // data is unused for CLICK1, according to wiki.vg
            );
        }
        moveWorldBorders(currentStage.getEndSize(), currentStage.getTime(size));
    }

    public void checkUhcEnd() {
        List<Player> alive = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!plugin.spectatingPlayers.contains(player.getName())) {
                alive.add(player);
            }
        }
        String winner = null;
        boolean ended = false;
        if (plugin.currentUhc.teamGame) {
            // Keep track of the teams with at least one player alive
            Set<String> aliveTeams = new HashSet<>();
            for (Player player : alive) {
                String teamName = plugin.getTeamName(player.getName());
                aliveTeams.add(teamName != null ? teamName : player.getName());
            }
            if (aliveTeams.size() < 2) {
                ended = true;
                if (aliveTeams.size() == 1) {
                    winner = aliveTeams.iterator().next();
                }
            }
        } else {
            if (alive.size() < 2) {
                ended = true;
                if (alive.size() == 1) {
                    winner = alive.get(0).getName();
                }
            }
        }
        if (ended) {
            if (winner != null) {
                Location spawnLocation = Bukkit.getWorld("world").getSpawnLocation();
                for (Player player : Bukkit.getOnlinePlayers()) {
                    player.sendMessage(ChatColor.GOLD.toString() +
                        winner + " won the UHC!"
                    );
                    player.playEffect(spawnLocation, Effect.RECORD_PLAY, 0); // 0 = win sound
                }
            } else {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    player.sendMessage(ChatColor.GOLD +
                        "The UHC ended because all living players logged off."
                    );
                    player.playEffect(
                        player.getLocation(),
                        Effect.EXTINGUISH,
                        0 // data is unused for EXTINGUISH, according to wiki.vg
                    );
                }
            }
            plugin.endUhc();
        }
    }

    public void beginUhc() {
        startGracePeriod(() -> {
            UHCPlugin.setPvp(true);
            startWorldBorders();
        });
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
                0 // data is unused for CLICK2, according to wiki.vg
            );
        }

        int[] remainingGracePeriodAndTaskId = {10, -1};
        activeTasks.add(
            remainingGracePeriodAndTaskId[1] = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
                remainingGracePeriodAndTaskId[0] -= 2;
                if (remainingGracePeriodAndTaskId[0] == 0) {
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
                    Bukkit.getScheduler().cancelTask(remainingGracePeriodAndTaskId[1]);
                    activeTasks.remove(remainingGracePeriodAndTaskId[1]);
                    endGracePeriod.run();
                } else {
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        player.sendMessage(ChatColor.GOLD +
                            "Grace Period will end in " + remainingGracePeriodAndTaskId[0] + " minutes"
                        );
                        player.playEffect(
                            player.getLocation(),
                            Effect.CLICK2,
                            0 // data is unused for CLICK1, according to wiki.vg
                        );
                    }
                }
            }, 2 * 60 * 20, 2 * 60 * 20)
        );
    }

    private void startWorldBorders() {
        double size = plugin.getWorldBorder();
        currentStage = WorldBorderStage.getStage(size);
        if (currentStage == null) {
            currentStage = WorldBorderStage.FIRST;
            moveWorldBorders(WorldBorderStage.FIRST.getStartSize(), 0);
        }
        if (currentStage == WorldBorderStage.END) {
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

    void cancelTasks() {
        for (int task : activeTasks) {
            Bukkit.getScheduler().cancelTask(task);
        }
        activeTasks.clear();
    }
}
