package canyonuhc;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityListener;
import org.bukkit.event.entity.EntityTargetEvent;

public class UHCEntityListener extends EntityListener {
    private final UHCPlugin plugin;

    public UHCEntityListener(UHCPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onEntityDamage(EntityDamageEvent event) {
        if (event instanceof EntityDamageByEntityEvent) {
            EntityDamageByEntityEvent event2 = (EntityDamageByEntityEvent)event;
            if (
                event2.getDamager() instanceof Player &&
                plugin.spectatingPlayers.contains(((Player)event2.getDamager()).getName())
            ) {
                event.setCancelled(true);
                return;
            }
        }
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player)event.getEntity();
        plugin.lastDamageCauses.put(player.getName(), event.getCause());
        if (event instanceof EntityDamageByEntityEvent) {
            plugin.lastAttackers.put(player.getName(), ((EntityDamageByEntityEvent)event).getDamager());
        } else {
            plugin.lastAttackers.remove(player.getName());
        }
        if (plugin.spectatingPlayers.contains(player.getName())) {
            event.setCancelled(true);
            return;
        }
    }

    @Override
    public void onEntityDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player)event.getEntity();
        UHCPlugin.broadcastMessage(plugin.getDeathMessage(player));
        if (plugin.uhcStarted) {
            plugin.killPlayer(player);
            if (plugin.currentUhc.teamGame) {
                // TODO: implement team games
            } else {
                Player theAlive = null;
                for (Player player2 : Bukkit.getOnlinePlayers()) {
                    if (!plugin.spectatingPlayers.contains(player2.getName())) {
                        if (theAlive == null) {
                            theAlive = player2;
                        } else {
                            theAlive = null;
                            break;
                        }
                    }
                }
                if (theAlive != null) {
                    Location spawnLocation = Bukkit.getWorld("world").getSpawnLocation();
                    for (Player player2 : Bukkit.getOnlinePlayers()) {
                        player2.sendMessage(ChatColor.GOLD.toString() +
                            theAlive.getName() + " won the UHC!"
                        );
                        player2.playEffect(spawnLocation, Effect.RECORD_PLAY, 0); // 0 = win sound
                    }
                    plugin.endUhc();
                }
            }
        }
    }

    @Override
    public void onEntityTarget(EntityTargetEvent event) {
        if (
            event.getTarget() instanceof Player &&
            plugin.spectatingPlayers.contains(((Player)event.getTarget()).getName())
        ) {
            event.setCancelled(true);
        }
    }
}
