package canyonuhc;

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
        if (event instanceof EntityDamageByEntityEvent event2) {
            if (
                event2.getDamager() instanceof Player damager &&
                plugin.spectatingPlayers.contains(damager.getName())
            ) {
                event.setCancelled(true);
                return;
            }
        }
        if (!(event.getEntity() instanceof Player player)) return;
        plugin.lastDamageCauses.put(player.getName(), event.getCause());
        if (event instanceof EntityDamageByEntityEvent event2) {
            if (
                event2.getDamager() instanceof Player damager &&
                plugin.currentUhc != null && plugin.currentUhc.teamGame
            ) {
                if (plugin.getTeamName(player).equals(plugin.getTeamName(damager))) {
                    event.setCancelled(true);
                    return;
                }
            }
            plugin.lastAttackers.put(player.getName(), event2.getDamager());
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
        if (!(event.getEntity() instanceof Player player)) return;
        UHCPlugin.broadcastMessage(plugin.getDeathMessage(player));
        if (plugin.uhcStarted) {
            plugin.killPlayer(player);
        }
    }

    @Override
    public void onEntityTarget(EntityTargetEvent event) {
        if (
            event.getTarget() instanceof Player player &&
            plugin.spectatingPlayers.contains(player.getName())
        ) {
            event.setCancelled(true);
        }
    }
}
