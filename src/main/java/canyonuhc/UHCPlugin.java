package canyonuhc;

import java.io.File;
import java.io.FileReader;
import java.io.IOError;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Difficulty;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.inventory.ItemStack;
import org.bukkit.map.MapView;
import org.bukkit.plugin.java.JavaPlugin;

import canyonuhc.packets.CustomPacketManager;
import canyonuhc.uhc.WorldBorderStage;
import canyonuhc.util.MutableDouble;

public class UHCPlugin extends JavaPlugin implements Listener {
    public static final boolean TEST_MODE = Boolean.getBoolean("canyonuhc.testMode");
    public static final Logger LOGGER = Logger.getLogger("Canyon-UHC");
    public static final Map<DamageCause, String> DEATH_MESSAGES;

    static {
        Map<DamageCause, String> deathMessages = new EnumMap<>(DamageCause.class);
        Properties deathMessageTranslations = new Properties();
        try (InputStream is = UHCPlugin.class.getResourceAsStream("/deathMessages.properties")) {
            deathMessageTranslations.load(is);
        } catch (IOException e) {
            throw new IOError(e);
        }
        for (DamageCause damageCause : DamageCause.values()) {
            deathMessages.put(
                damageCause,
                deathMessageTranslations.getProperty(damageCause.toString(), damageCause.toString())
            );
        }
        DEATH_MESSAGES = Collections.unmodifiableMap(deathMessages);
    }

    public CustomPacketManager packetManager;
    public UHCBlockListener blockListener;
    public UHCEntityListener entityListener;
    public UHCPlayerListener playerListener;
    public boolean uhcStarted = false;
    public Set<String> spectatingPlayers;
    public final Map<String, MapView> playerFaceMaps = new HashMap<>();
    public final Map<Short, String> faceMapIdToPlayer = new HashMap<>();
    public final Map<String, DamageCause> lastDamageCauses = new HashMap<>();
    public final Map<String, Entity> lastAttackers = new HashMap<>();
    private final Map<String, Set<String>> teamNameToMembers = new HashMap<>();
    private final Map<String, String> memberToTeamName = new HashMap<>();
    public final Map<String, Integer> globalGlowingEffects = new HashMap<>();
    private final Map<String, Location> lastPlayerPos = new HashMap<>();
    private final Map<String, MutableDouble> accumulatedBorderDamage = new HashMap<>();
    private double worldBorderPos = 200;
    private double worldBorderInterpDest = worldBorderPos;
    private long worldBorderInterpRemaining = 0;
    private int worldBorderTask = -1;
    public UHCRunner currentUhc;

    @Override
    public void onEnable() {
        if (TEST_MODE) {
            LOGGER.info("UHC test mode active. All times will be quartered.");
        }

        spectatingPlayers = new HashSet<>();

        packetManager = new CustomPacketManager();
        Bukkit.getPluginManager().registerEvent(Event.Type.PLAYER_CHAT, packetManager, Event.Priority.Normal, this);

        packetManager.register("teleport", (player, packetType, data) -> {
            if (!player.isOp() && !spectatingPlayers.contains(player.getName())) {
                player.sendMessage(ChatColor.RED + "You do not have the permissions to do that.");
                return;
            }
            if (data == null) {
                player.sendMessage(ChatColor.RED + "ERROR: null player name");
                return;
            }
            Player teleportTo = Bukkit.getPlayerExact(data);
            if (teleportTo == null) {
                player.sendMessage(ChatColor.RED + "ERROR: Couldn't find that player");
                return;
            }
            player.teleport(teleportTo);
        });

        blockListener = new UHCBlockListener(this);
        entityListener = new UHCEntityListener(this);
        playerListener = new UHCPlayerListener(this);
        Bukkit.getPluginManager().registerEvent(Event.Type.BLOCK_BREAK, blockListener, Event.Priority.Normal, this);
        Bukkit.getPluginManager().registerEvent(Event.Type.BLOCK_PLACE, blockListener, Event.Priority.Normal, this);
        Bukkit.getPluginManager().registerEvent(Event.Type.ENTITY_DAMAGE, entityListener, Event.Priority.Normal, this);
        Bukkit.getPluginManager().registerEvent(Event.Type.ENTITY_DEATH, entityListener, Event.Priority.Normal, this);
        Bukkit.getPluginManager().registerEvent(Event.Type.ENTITY_TARGET, entityListener, Event.Priority.Normal, this);
        Bukkit.getPluginManager().registerEvent(Event.Type.PLAYER_INTERACT, playerListener, Event.Priority.Normal, this);
        Bukkit.getPluginManager().registerEvent(Event.Type.PLAYER_PICKUP_ITEM, playerListener, Event.Priority.Normal, this);
        Bukkit.getPluginManager().registerEvent(Event.Type.PLAYER_JOIN, playerListener, Event.Priority.Normal, this);
        Bukkit.getPluginManager().registerEvent(Event.Type.PLAYER_QUIT, playerListener, Event.Priority.Normal, this);
        Bukkit.getPluginManager().registerEvent(Event.Type.PLAYER_RESPAWN, playerListener, Event.Priority.Normal, this);
        Bukkit.getPluginManager().registerEvent(Event.Type.PLAYER_DROP_ITEM, playerListener, Event.Priority.Normal, this);
        Bukkit.getPluginManager().registerEvent(Event.Type.PLAYER_TELEPORT, playerListener, Event.Priority.Normal, this);

        getCommand("reset-uhc").setExecutor((sender, command, label, args) -> {
            if (!sender.isOp()) {
                sender.sendMessage(ChatColor.RED + "Only ops may run this command");
                return true;
            }
            endUhc();
            return true;
        });

        getCommand("start-uhc").setExecutor((sender, command, label, args) -> {
            if (!sender.isOp()) {
                sender.sendMessage(ChatColor.RED + "Only ops may run this command");
                return true;
            }
            if (uhcStarted) {
                sender.sendMessage("A UHC is already running! Run /reset-uhc to reset it.");
                return true;
            }
            endUhc(); // Ensure everything's cleaned up
            boolean isTeamGame = args.length > 0 && Boolean.parseBoolean(args[0]);
            Map<String, Location> teamOrigins = null;
            if (isTeamGame) {
                try {
                    loadTeams();
                } catch (IOException e) {
                    sender.sendMessage(ChatColor.RED + "Failed to load teams: " + e.getLocalizedMessage());
                    e.printStackTrace();
                    return true;
                }
                teamOrigins = new HashMap<>();
            }
            setWorldBorder(WorldBorderStage.FIRST.getStartSize());
            setPvp(false);
            ThreadLocalRandom rand = ThreadLocalRandom.current();
            World world = Bukkit.getWorld("world");
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.setNoDamageTicks(400);
                String teamName;
                if (isTeamGame && (teamName = getTeamName(player)) != null) {
                    setDisplayName(player, player.getName() + " " + ChatColor.GOLD + '[' + teamName + "]\u00a7r");
                    Location teamOrigin = teamOrigins.get(teamName);
                    if (teamOrigin == null) {
                        teamOrigins.put(teamName, teamOrigin = getRandomTeamPlayerLocation(world, rand));
                    }
                    int x = teamOrigin.getBlockX() + rand.nextInt(-10, 11);
                    int z = teamOrigin.getBlockZ() + rand.nextInt(-10, 11);
                    world.loadChunk(x >> 4, z >> 4, true);
                    player.teleportAsync(new Location(world, x, world.getHighestBlockYAt(x, z), z));
                } else {
                    player.teleportAsync(getRandomTeamPlayerLocation(world, rand));
                }
            }
            for (World serverWorld : Bukkit.getWorlds()) {
                for (Entity entity : serverWorld.getEntities()) {
                    if (entity instanceof org.bukkit.entity.Item) {
                        entity.remove();
                    }
                }
                serverWorld.setDifficulty(Difficulty.HARD);
            }
            uhcStarted = true;
            (currentUhc = new UHCRunner(this, isTeamGame)).beginUhc();
            return true;
        });

        getCommand("worldborder").setExecutor((sender, command, label, args) -> {
            // if (sender instanceof Player) {
            //     Player player = (Player)sender;
            //     player.playEffect(player.getLocation(), Effect.RECORD_PLAY, 1); // 1 = win sound
            // }
            if (args.length == 0) {
                sender.sendMessage("The current world border is from -" + worldBorderPos + " to " + worldBorderPos);
            } else if (!sender.isOp()) {
                sender.sendMessage(ChatColor.RED + "Only ops may run this command");
            } else {
                double distance;
                try {
                    distance = Double.parseDouble(args[0]);
                } catch (NumberFormatException e) {
                    sender.sendMessage(ChatColor.RED + "Invalid decimal: " + args[0]);
                    return true;
                }
                if (args.length == 1) {
                    sender.sendMessage("Setting the world border to be from -" + distance + " to " + distance);
                    setWorldBorder(distance);
                } else {
                    int ticks;
                    try {
                        ticks = Integer.parseInt(args[1]);
                    } catch (NumberFormatException e) {
                        sender.sendMessage(ChatColor.RED + "Invalid integer: " + args[1]);
                        return true;
                    }
                    sender.sendMessage(
                        "Setting the world border to be from -" + distance + " to " + distance +
                        " over a time of " + (ticks / 20.0) + " seconds"
                    );
                    setWorldBorder(distance, ticks);
                }
            }
            return true;
        });

        getCommand("glow").setExecutor((sender, command, label, args) -> {
            if (args.length < 1 && !(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "player argument must be specified if you aren't a player");
            } else if (!sender.isOp()) {
                sender.sendMessage(ChatColor.RED + "Only ops may run this command");
            } else {
                Player player;
                if (args.length > 0) {
                    player = Bukkit.getPlayer(args[0]);
                } else {
                    player = (Player)sender;
                }
                int color = 0xffffff;
                if (args.length > 1) {
                    if (args[1].equals("-")) {
                        if (args.length > 2) {
                            Player forPlayer = Bukkit.getPlayer(args[2]);
                            removeGlowing(player, forPlayer);
                            sender.sendMessage("Stopped glowing " + player.getName() + " for " + forPlayer.getName());
                        } else {
                            removeGlowing(player);
                            sender.sendMessage("Stopped glowing " + player.getName());
                        }
                        return true;
                    }
                    color = Integer.parseUnsignedInt(args[1], 16);
                }
                if (args.length > 2) {
                    Player forPlayer = Bukkit.getPlayer(args[2]);
                    glow(player, forPlayer, color);
                    sender.sendMessage("Now glowing " + player.getName() + " for " + forPlayer);
                } else {
                    glow(player, color);
                    sender.sendMessage("Now glowing " + player.getName());
                }
            }
            return true;
        });

        getCommand("spectator").setExecutor((sender, command, label, args) -> {
            if (!sender.isOp()) {
                sender.sendMessage(ChatColor.RED + "Only ops may run this command");
                return true;
            }
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ChatColor.RED + "Only players may run this command");
                return true;
            }
            spectatingPlayers.add(player.getName());
            packetManager.broadcastPacket("spectator", player.getName());
            return true;
        });

        getCommand("display-name").setExecutor((sender, command, label, args) -> {
            if (!sender.isOp()) {
                sender.sendMessage(ChatColor.RED + "Only ops may run this command");
                return true;
            }
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ChatColor.RED + "Only players may run this command");
                return true;
            }
            setDisplayName(player, args.length > 0 ? args[0] : null);
            return true;
        });

        getCommand("tpcoords").setExecutor((sender, command, label, args) -> {
            if (!sender.isOp()) {
                sender.sendMessage(ChatColor.RED + "Only ops may run this command");
                return true;
            }
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ChatColor.RED + "Only players may run this command");
                return true;
            }
            if (args.length < 3) {
                return false;
            }
            World world = player.getWorld();
            int argsOffset = 0;
            if (args.length > 3) {
                world = Bukkit.getWorld(args[0]);
                if (world == null) {
                    sender.sendMessage(ChatColor.RED + "Could not find the world '" + args[0] + "'");
                    return true;
                }
                argsOffset = 1;
            }
            double[] coords = new double[3];
            for (int i = 0; i < 3; i++) {
                try {
                    coords[i] = Double.parseDouble(args[i + argsOffset]);
                } catch (NumberFormatException e) {
                    sender.sendMessage(ChatColor.RED + "Not a valid number: " + args[i + argsOffset]);
                    return true;
                }
            }
            player.teleportAsync(new Location(world, coords[0], coords[1], coords[2]));
            return true;
        });

        getCommand("lightning").setExecutor((sender, command, label, args) -> {
            if (!sender.isOp()) {
                sender.sendMessage(ChatColor.RED + "Only ops may run this command");
                return true;
            }
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ChatColor.RED + "Only players may run this command");
                return true;
            }
            player.getWorld().strikeLightningEffect(player.getLocation());
            packetManager.broadcastPacket("lightning");
            return true;
        });

        Bukkit.getScheduler().scheduleSyncRepeatingTask(this, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (spectatingPlayers.contains(player.getName())) {
                    continue; // Spectators aren't affected by the border
                }
                Location oldPosition = lastPlayerPos.get(player.getName());
                Location newPosition = player.getLocation();
                if (oldPosition != null) {
                    double distanceFromBorder = Math.max(
                        Math.max(
                            newPosition.getX() - worldBorderPos,
                            -worldBorderPos - newPosition.getX()
                        ),
                        Math.max(
                            newPosition.getZ() - worldBorderPos,
                            -worldBorderPos - newPosition.getZ()
                        )
                    );
                    if (isInsideWorldBorder(oldPosition)) {
                        if (distanceFromBorder > 0 && distanceFromBorder < 6) {
                            player.teleportAsync(newPosition = oldPosition);
                        }
                    } else {
                        if (!isInsideWorldBorder(newPosition)) {
                            if (distanceFromBorder > 5) {
                                MutableDouble accumulatedDamage = accumulatedBorderDamage.computeIfAbsent(
                                    player.getName(),
                                    k -> new MutableDouble()
                                );
                                accumulatedDamage.addValue((distanceFromBorder - 5) * 0.2);
                                if (accumulatedDamage.getValue() > 1) {
                                    int intDamage = (int)accumulatedDamage.getValue();
                                    lastDamageCauses.put(player.getName(), DamageCause.SUFFOCATION);
                                    player.damage(intDamage);
                                    accumulatedDamage.addValue(-intDamage);
                                }
                            }
                        }
                    }
                }
                if (newPosition != oldPosition) { // Optimization
                    lastPlayerPos.put(player.getName(), newPosition);
                }
            }
        }, 0, 20);

        LOGGER.info("Enabled Canyon-UHC");
    }

    public void endUhc() {
        uhcStarted = false;
        if (currentUhc != null) {
            currentUhc.cancelTasks();
            currentUhc = null;
        }
        setPvp(true);
        for (Player player : Bukkit.getOnlinePlayers()) {
            setDisplayName(player, null);
            player.setSleepingIgnored(false);
            player.resetPlayerTime();
            player.setHealth(20);
            player.getInventory().clear();
            player.getInventory().setArmorContents(new ItemStack[4]);
            player.teleportAsync(Bukkit.getWorld("world").getSpawnLocation());
        }
        for (World world : Bukkit.getWorlds()) {
            world.setTime(0);
            world.setStorm(false);
            world.setThundering(false);
            world.setDifficulty(Difficulty.PEACEFUL);
        }
        spectatingPlayers.clear();
        packetManager.broadcastPacket("reset-spectators");
        clearGlowing();
        setWorldBorder(200);
    }

    public boolean isInsideWorldBorder(Location pos) {
        return
            pos.getX() < worldBorderPos &&
            pos.getX() > -worldBorderPos &&
            pos.getZ() < worldBorderPos &&
            pos.getZ() > -worldBorderPos;
    }

    public double getWorldBorder() {
        return worldBorderPos;
    }

    public void worldBorderInit(Player player) {
        packetManager.sendPacket(
            player,
            "worldborder",
            CustomPacketManager.doubleToString(worldBorderPos)
        );
        if (worldBorderTask != -1) {
            packetManager.sendPacket(
                player,
                "worldborderinterp",
                CustomPacketManager.doubleToString(worldBorderInterpDest) + ' ' +
                Long.toHexString(worldBorderInterpRemaining)
            );
        }
    }

    private void worldBorderInterpSync() {
        packetManager.broadcastPacket(
            "worldborder",
            CustomPacketManager.doubleToString(worldBorderPos)
        );
        packetManager.broadcastPacket(
            "worldborderinterp",
            CustomPacketManager.doubleToString(worldBorderInterpDest) + ' ' +
            Long.toHexString(worldBorderInterpRemaining)
        );
    }

    public void setWorldBorder(double distance) {
        Bukkit.getScheduler().cancelTask(worldBorderTask);
        worldBorderTask = -1;
        worldBorderPos = distance;
        packetManager.broadcastPacket("worldborder", CustomPacketManager.doubleToString(distance));
    }

    public void setWorldBorder(double distance, long ticks) {
        if (worldBorderTask != -1) {
            Bukkit.getScheduler().cancelTask(worldBorderTask);
            worldBorderTask = -1;
        }
        worldBorderInterpDest = distance;
        worldBorderInterpRemaining = ticks;
        final double amountPerTick = (distance - worldBorderPos) / (double)ticks;
        worldBorderInterpSync();
        worldBorderTask = Bukkit.getScheduler().scheduleSyncRepeatingTask(this, () -> {
            if (worldBorderInterpRemaining > 0) {
                worldBorderPos += amountPerTick;
                worldBorderInterpRemaining--;
                if (worldBorderInterpRemaining % 100 == 0) {
                    // Sync occasionally to prevent desyncs due to server lag
                    worldBorderInterpSync();
                }
            } else {
                setWorldBorder(distance);
                if (currentUhc != null) {
                    currentUhc.worldBorderFinishedShrinking();
                }
            }
        }, 0, 1);
    }

    private void loadTeams() throws IOException {
        teamNameToMembers.clear();
        memberToTeamName.clear();
        try (Reader reader = new FileReader(new File(getDataFolder(), "teams.json"))) {
            teamNameToMembers.putAll(
                new Gson().fromJson(reader, new TypeToken<Map<String, Set<String>>>() {}.getType())
            );
        }
        for (var team : teamNameToMembers.entrySet()) {
            for (String member : team.getValue()) {
                memberToTeamName.put(member, team.getKey());
            }
        }
    }

    public void glow(Player player, Player forPlayer, int color) {
        packetManager.sendPacket(forPlayer, "glowing", Integer.toHexString(color) + ' ' + player.getName());
    }

    public void glow(Player player, int color) {
        globalGlowingEffects.put(player.getName(), color);
        packetManager.broadcastPacket("glowing", Integer.toHexString(color) + ' ' + player.getName());
    }

    public void glow(Player player, Player forPlayer) {
        glow(player, forPlayer, 0xffffff);
    }

    public void glow(Player player) {
        glow(player, 0xffffff);
    }

    public void removeGlowing(Player player, Player forPlayer) {
        packetManager.sendPacket(forPlayer, "noglowing", player.getName());
    }

    public void removeGlowing(Player player) {
        globalGlowingEffects.remove(player.getName());
        packetManager.broadcastPacket("noglowing", player.getName());
    }

    public void clearGlowing(Player forPlayer) {
        packetManager.sendPacket(forPlayer, "noglowing");
    }

    public void clearGlowing() {
        globalGlowingEffects.clear();
        packetManager.broadcastPacket("noglowing");
    }

    public Set<String> getTeamMembers(String teamName) {
        return teamNameToMembers.get(teamName);
    }

    public String getTeamName(Player member) {
        return memberToTeamName.get(member.getName());
    }

    public String getTeamName(String memberName) {
        return memberToTeamName.get(memberName);
    }

    private Location getRandomTeamPlayerLocation(World world, Random rand) {
        int x = rand.nextInt(-2000, 2001);
        int z = rand.nextInt(-2000, 2001);
        if (TEST_MODE) {
            x >>= 2;
            z >>= 2;
        }
        world.loadChunk(x >> 4, z >> 4, true);
        return new Location(world, x, world.getHighestBlockYAt(x, z), z);
    }

    public String getDeathMessage(Player player) {
        DamageCause cause = lastDamageCauses.get(player.getName());
        if (cause == null) {
            cause = DamageCause.CUSTOM; // I wish I could just do ?? or ?:
        }
        return String.format(
            DEATH_MESSAGES.get(cause),
            player.getDisplayName(),
            getEntityName(lastAttackers.get(player.getName()))
        );
    }

    public void killPlayer(Player player) {
        if (spectatingPlayers.add(player.getName())) {
            player.getWorld().strikeLightningEffect(player.getLocation());
            packetManager.broadcastPacket("lightning");
        }
        initPlayerDead(player);
        currentUhc.checkUhcEnd();
    }

    public void initPlayerDead(Player player) {
        if (!uhcStarted) return;
        packetManager.broadcastPacket("spectator", player.getName());
        setDisplayName(player, player.getName() + " " + ChatColor.RED + "[DEAD]\u00a7r");
        player.setSleepingIgnored(true);
        player.setPlayerTime(0, false);
        handOutMaps(player);
    }

    public void handOutMaps(Player player) {
        player.getInventory().clear();
        for (var playerMap : playerFaceMaps.entrySet()) {
            String playerName = playerMap.getKey();
            MapView map = playerMap.getValue();
            packetManager.sendPacket(
                player, "mapplayer",
                Integer.toHexString(map.getId()) + ' ' + playerName
            );
            player.getInventory().addItem(new ItemStack(Material.MAP, 1, map.getId()));
        }
    }

    public MapView generateFaceMap(Player player) {
        MapView map = Bukkit.createMap(player.getWorld());
        map.setScale(MapView.Scale.CLOSEST);
        map.setCenterX(1000000);
        map.setCenterZ(1000000);
        map.addRenderer(new PlayerFaceMapRenderer(player));
        playerFaceMaps.put(player.getName(), map);
        faceMapIdToPlayer.put(map.getId(), player.getName());
        return map;
    }

    public void setDisplayName(Player player, String displayName) {
        if (displayName == null || player.getName().equals(displayName)) {
            player.setDisplayName(player.getName());
            packetManager.broadcastPacket("displayname", player.getName());
            return;
        }
        player.setDisplayName(displayName);
        packetManager.broadcastPacket("displayname", player.getName() + ' ' + displayName);
    }

    @Override
    public void onDisable() {
    }

    public static String getEntityName(Entity entity) {
        if (entity == null) {
            return null;
        }
        if (entity instanceof Player player) {
            return player.getName();
        }
        String name = entity.getClass().getSimpleName();
        if (name.startsWith("Craft")) {
            return name.substring(5);
        }
        return name;
    }

    public static void broadcastMessage(String message) {
        LOGGER.info(message);
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(message);
        }
    }

    /**
     * Set the PvP toggle for all worlds
     */
    public static void setPvp(boolean pvp) {
        for (World world : Bukkit.getWorlds()) {
            world.setPVP(pvp);
        }
    }
}
