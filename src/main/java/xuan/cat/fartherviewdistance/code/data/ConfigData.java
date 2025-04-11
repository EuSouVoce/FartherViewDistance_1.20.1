package xuan.cat.fartherviewdistance.code.data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import xuan.cat.fartherviewdistance.code.data.viewmap.ViewMapMode;

/**
 * The `ConfigData` class in Java represents configuration settings for a
 * plugin, including server
 * settings, permissions, and world configurations.
 */
public final class ConfigData {
    private FileConfiguration fileConfiguration;
    private final JavaPlugin plugin;
    public ViewMapMode viewDistanceMode;
    public int serverViewDistance;
    public boolean autoAdaptPlayerNetworkSpeed;
    public double playerNetworkSpeedUseDegree;
    public int asyncThreadAmount;
    private int serverSendSecondMaxBytes;
    public int serverTickMaxGenerateAmount;
    private World worldDefault;
    private Map<String, World> worlds;
    public boolean calculateMissingHeightMap;
    public boolean disableFastProcess;
    public List<Map.Entry<String, Integer>> permissionsNodeList;
    public long permissionsPeriodicMillisecondCheck;

    public ConfigData(final JavaPlugin plugin, final FileConfiguration fileConfiguration) {
        this.plugin = plugin;
        this.fileConfiguration = fileConfiguration;
        this.load();
    }

    public void reload() {
        this.plugin.reloadConfig();
        this.fileConfiguration = this.plugin.getConfig();
        this.load();
    }

    public int getServerSendTickMaxBytes() {
        return this.serverSendSecondMaxBytes / 20;
    }

    public World getWorld(final String worldName) {
        return this.worlds.getOrDefault(worldName, this.worldDefault);
    }

    /**
     * The `World` class in Java represents a world in a game with various
     * properties such as name,
     * view distance, data sending settings, and prevention of X-ray vision.
     */
    public class World {
        public final String worldName;
        public final boolean enable;
        public final int maxViewDistance;
        public final int worldTickMaxGenerateAmount;
        public final boolean sendTitleData;
        private final int worldSendSecondMaxBytes;
        private final int playerSendSecondMaxBytes;
        public final boolean readServerLoadedChunk;
        public final int delayBeforeSend;
        public final Map<BlockData, BlockData[]> preventXray;
        public final double speedingNotSend;

        public World(final ViewMapMode viewDistanceMode, final String worldName, final boolean enable,
                final int maxViewDistance,
                final int worldTickMaxGenerateAmount, final boolean sendTitleData, final int worldSendSecondMaxBytes,
                final int playerSendSecondMaxBytes, final boolean readServerLoadedChunk, final int delayBeforeSend,
                final Map<BlockData, BlockData[]> preventXray, final double speedingNotSend) {
            this.worldName = worldName;
            this.enable = enable;
            this.maxViewDistance = maxViewDistance;
            if (maxViewDistance > viewDistanceMode.getExtend()) {
                ConfigData.this.plugin.getLogger()
                        .warning("`max-view-distance: " + maxViewDistance
                                + "` exceeded the maximum distance allowed by `view-distance-mode: "
                                + viewDistanceMode.name() + "`");
            }
            this.worldTickMaxGenerateAmount = worldTickMaxGenerateAmount;
            this.sendTitleData = sendTitleData;
            this.worldSendSecondMaxBytes = worldSendSecondMaxBytes;
            this.playerSendSecondMaxBytes = playerSendSecondMaxBytes;
            this.readServerLoadedChunk = readServerLoadedChunk;
            this.delayBeforeSend = delayBeforeSend;
            this.preventXray = preventXray;
            this.speedingNotSend = speedingNotSend;
        }

        public int getPlayerSendTickMaxBytes() {
            return this.playerSendSecondMaxBytes / 20;
        }

        public int getWorldSendTickMaxBytes() {
            return this.worldSendSecondMaxBytes / 20;
        }

        public String getName() {
            // TODO Auto-generated method stub
            throw new UnsupportedOperationException("Unimplemented method 'getName'");
        }
    }

    /**
     * The `load()` function reads and processes configuration settings from a file
     * in Java, including
     * view distance mode, server settings, permissions, and world configurations.
     */
    private void load() {
        final String viewDistanceModeString = this.fileConfiguration.getString("view-distance-mode", "X31");
        ViewMapMode viewDistanceMode;
        try {
            viewDistanceMode = ViewMapMode.valueOf(viewDistanceModeString.toUpperCase(Locale.ROOT));
        } catch (final Exception exception) {
            throw new NullPointerException("config.yml>view-distance-mode Non-existent option: "
                    + viewDistanceModeString + " , allowed options: " + Arrays.toString(ViewMapMode.values()));
        }
        final int serverViewDistance = this.fileConfiguration.getInt("server-view-distance", -1);
        final boolean autoAdaptPlayerNetworkSpeed = this.fileConfiguration.getBoolean("auto-adapt-player-network-speed",
                true);
        final double playerNetworkSpeedUseDegree = this.fileConfiguration.getDouble("player-network-speed-use-degree",
                0.6);
        final int asyncThreadAmount = this.fileConfiguration.getInt("async-thread-amount", 2);
        final int serverSendSecondMaxBytes = this.fileConfiguration.getInt("server-send-second-max-bytes", 20971520);
        final int serverTickMaxGenerateAmount = this.fileConfiguration.getInt("server-tick-max-generate-amount", 2);
        final boolean calculateMissingHeightMap = this.fileConfiguration.getBoolean("calculate-missing-height-map",
                false);
        final boolean disableFastProcess = this.fileConfiguration.getBoolean("disable-fast-process", false);

        // Permissions
        final ConfigurationSection permissionsConfiguration = this.fileConfiguration
                .getConfigurationSection("permissions");
        if (permissionsConfiguration == null)
            throw new NullPointerException("config.yml>permissions");
        final Map<String, Integer> permissionsNodeMap = new HashMap<>();
        for (final String line : permissionsConfiguration.getStringList("node-list")) {
            final String[] lineSplit = line.split(";", 2);
            if (lineSplit.length != 2)
                throw new NullPointerException(
                        "config.yml>permissions->node-list Can't find the separator \";\": " + line);
            permissionsNodeMap.put(lineSplit[1], Integer.parseInt(lineSplit[0]));
        }
        final long permissionsPeriodicMillisecondCheck = permissionsConfiguration.getLong("periodic-millisecond-check",
                60000L);

        // World
        final ConfigurationSection worldsConfiguration = this.fileConfiguration.getConfigurationSection("worlds");
        final Map<String, World> worlds = new HashMap<>();
        if (worldsConfiguration == null)
            throw new NullPointerException("config.yml>worlds");
        ConfigurationSection worldDefaultConfiguration = worldsConfiguration.getConfigurationSection("default");
        if (worldDefaultConfiguration == null)
            worldDefaultConfiguration = new YamlConfiguration();
        final World worldDefault = new World(
                viewDistanceMode,
                "",
                worldDefaultConfiguration.getBoolean("enable", true),
                worldDefaultConfiguration.getInt("max-view-distance", 31),
                worldDefaultConfiguration.getInt("world-tick-max-generate-amount", 2),
                worldDefaultConfiguration.getBoolean("send-title-data", true),
                worldDefaultConfiguration.getInt("world-send-second-max-bytes", 10485760),
                worldDefaultConfiguration.getInt("player-send-second-max-bytes", 2097152),
                worldDefaultConfiguration.getBoolean("read-server-loaded-chunk", true),
                worldDefaultConfiguration.getInt("delay-before-send", 5000),
                this.parsePreventXray(worldDefaultConfiguration.getConfigurationSection("prevent-xray"), "default",
                        null),
                worldDefaultConfiguration.getDouble("speeding-not-send", 1.2));
        for (final String worldName : worldsConfiguration.getKeys(false)) {
            if (worldName.equals("default"))
                continue;
            final ConfigurationSection worldConfiguration = worldsConfiguration.getConfigurationSection(worldName);
            if (worldConfiguration == null)
                continue;
            worlds.put(worldName, new World(
                    viewDistanceMode,
                    worldName,
                    worldConfiguration.getBoolean("enable", worldDefault.enable),
                    worldConfiguration.getInt("max-view-distance", worldDefault.maxViewDistance),
                    worldConfiguration.getInt("world-tick-max-generate-amount",
                            worldDefault.worldTickMaxGenerateAmount),
                    worldConfiguration.getBoolean("send-title-data", worldDefault.sendTitleData),
                    worldConfiguration.getInt("world-send-second-max-bytes", worldDefault.worldSendSecondMaxBytes),
                    worldConfiguration.getInt("player-send-second-max-bytes", worldDefault.playerSendSecondMaxBytes),
                    worldConfiguration.getBoolean("read-server-loaded-chunk", worldDefault.readServerLoadedChunk),
                    worldConfiguration.getInt("delay-before-send", worldDefault.delayBeforeSend),
                    this.parsePreventXray(worldConfiguration.getConfigurationSection("prevent-xray"), worldName,
                            worldDefault.preventXray),
                    worldConfiguration.getDouble("speeding-not-send", worldDefault.speedingNotSend)));
        }

        // Replacement
        this.viewDistanceMode = viewDistanceMode;
        this.serverViewDistance = serverViewDistance;
        this.autoAdaptPlayerNetworkSpeed = autoAdaptPlayerNetworkSpeed;
        this.playerNetworkSpeedUseDegree = playerNetworkSpeedUseDegree;
        this.asyncThreadAmount = asyncThreadAmount;
        this.serverSendSecondMaxBytes = serverSendSecondMaxBytes;
        this.serverTickMaxGenerateAmount = serverTickMaxGenerateAmount;
        this.calculateMissingHeightMap = calculateMissingHeightMap;
        this.disableFastProcess = disableFastProcess;
        this.permissionsNodeList = new ArrayList<>(permissionsNodeMap.entrySet());
        this.permissionsPeriodicMillisecondCheck = permissionsPeriodicMillisecondCheck;
        this.worldDefault = worldDefault;
        this.worlds = worlds;
    }

    /**
     * The function `parsePreventXray` reads and parses a configuration section to
     * create a mapping of
     * block data conversions for preventing x-ray vision in a specific world.
     * 
     * @param preventXrayConfiguration The `preventXrayConfiguration` parameter is a
     *                                 configuration
     *                                 section that contains settings related to
     *                                 preventing X-ray vision in a specific world.
     *                                 It
     *                                 includes information such as whether the
     *                                 prevention is enabled, a list of block
     *                                 conversions to
     *                                 apply, and the materials involved in those
     *                                 conversions. The method `parsePreventX
     * @param worldName                The `worldName` parameter in the
     *                                 `parsePreventXray` method is used to specify
     *                                 the name of the world for which the prevent
     *                                 x-ray configuration is being parsed. This
     *                                 helps in
     *                                 identifying the specific world configuration
     *                                 settings and applying them accordingly.
     * @param defaultValue             The `defaultValue` parameter in the
     *                                 `parsePreventXray` method is a map that
     *                                 stores a mapping between a `BlockData` object
     *                                 and an array of `BlockData` objects. This map
     *                                 serves as the default value to be returned if
     *                                 the `preventXrayConfiguration` is null.
     * @return The method `parsePreventXray` is returning a `Map<BlockData,
     *         BlockData[]>` containing
     *         the conversion mappings for preventing x-ray vision in a specific
     *         world. If the
     *         `preventXrayConfiguration` is null, it will return the `defaultValue`
     *         provided. Otherwise, it
     *         will parse the configuration and populate the
     *         `preventXrayConversionMap` with the conversion
     *         mappings based on the configuration
     */
    private Map<BlockData, BlockData[]> parsePreventXray(final ConfigurationSection preventXrayConfiguration,
            final String worldName, final Map<BlockData, BlockData[]> defaultValue) {
        if (preventXrayConfiguration == null) {
            return defaultValue;
        } else {
            final Map<BlockData, BlockData[]> preventXrayConversionMap = new HashMap<>();
            if (preventXrayConfiguration.getBoolean("enable", true)) {
                // Read conversion list
                final ConfigurationSection conversionConfiguration = preventXrayConfiguration
                        .getConfigurationSection("conversion-list");
                if (conversionConfiguration != null) {
                    for (final String toString : conversionConfiguration.getKeys(false)) {
                        final Material toMaterial = Material.getMaterial(toString.toUpperCase());

                        if (toMaterial == null) {
                            this.plugin.getLogger().warning("worlds->" + worldName
                                    + "->prevent-xray->conversion-list Can't find this material: " + toString);
                            continue;
                        }

                        final List<Material> hitMaterials = new ArrayList<>();
                        for (final String hitString : conversionConfiguration.getStringList(toString)) {
                            final Material targetMaterial = Material.getMaterial(hitString.toUpperCase());
                            if (targetMaterial == null) {
                                // Can't find this material
                                this.plugin.getLogger().warning("worlds->" + worldName
                                        + "->prevent-xray->conversion-list Can't find this material: " + hitString);
                                continue;
                            }
                            hitMaterials.add(targetMaterial);
                        }

                        final BlockData[] materials = new BlockData[hitMaterials.size()];
                        for (int i = 0; i < materials.length; ++i)
                            materials[i] = hitMaterials.get(i).createBlockData();

                        preventXrayConversionMap.put(toMaterial.createBlockData(), materials);
                    }
                }
            }
            return preventXrayConversionMap;
        }
    }
}