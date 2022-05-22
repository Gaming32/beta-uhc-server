package canyonuhc;

import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
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
        if (event.getCause() == DamageCause.SUICIDE && !player.isOp()) {
            player.sendRawMessage("Cannot /kill yourself in a UHC!");
            event.setCancelled(true);
            return;
        }
        if (plugin.spectatingPlayers.contains(player.getName())) {
            event.setCancelled(true);
        }
    }

    @Override
    public void onEntityDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player)event.getEntity();
        UHCPlugin.broadcastMessage(plugin.getDeathMessage(player));
        if (plugin.uhcStarted) {
            plugin.killPlayer(player);
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
