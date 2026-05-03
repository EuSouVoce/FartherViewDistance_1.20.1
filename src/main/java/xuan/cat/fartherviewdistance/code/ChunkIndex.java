package xuan.cat.fartherviewdistance.code;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import com.mojang.brigadier.tree.LiteralCommandNode;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.minecraft.commands.arguments.EntityArgument;

import java.nio.file.Path;

import xuan.cat.fartherviewdistance.api.branch.BranchMinecraft;
import xuan.cat.fartherviewdistance.api.branch.BranchPacket;
import xuan.cat.fartherviewdistance.code.branch.BranchResolver;
import xuan.cat.fartherviewdistance.code.command.Command;
import xuan.cat.fartherviewdistance.code.data.ConfigData;
import xuan.cat.fartherviewdistance.code.data.viewmap.ViewShape;
import xuan.cat.fartherviewdistance.code.metrics.MetricsCollector;

public final class ChunkIndex extends JavaPlugin {
    // private static ProtocolManager protocolManager;
    private static Plugin plugin;
    private static ChunkServer chunkServer;
    private static ConfigData configData;
    private static BranchPacket branchPacket;
    private static BranchMinecraft branchMinecraft;

    @Override
    public void onEnable() {
        // protocolManager = ProtocolLibrary.getProtocolManager();
        ChunkIndex.plugin = this;

        this.saveDefaultConfig();
        ChunkIndex.configData = new ConfigData(this, this.getConfig());

        final String bukkitVersion = Bukkit.getBukkitVersion();
        final String minecraftVersion = Bukkit.getMinecraftVersion();
        final String branchLibraryDirectory = this.getConfig().getString("branch-autoload.library-directory",
                "plugins/FartherViewDistance/branch-libraries");

        final BranchResolver.SelectedBranch selectedBranch = BranchResolver
                .resolve(minecraftVersion, this.getLogger(), this.getClass().getClassLoader(),
                        Path.of(branchLibraryDirectory))
                .orElse(null);

        if (selectedBranch != null) {
            ChunkIndex.branchPacket = selectedBranch.packet();
            ChunkIndex.branchMinecraft = selectedBranch.minecraft();
            ChunkIndex.chunkServer = new ChunkServer(ChunkIndex.configData, this, ViewShape.ROUND,
                    ChunkIndex.branchMinecraft, ChunkIndex.branchPacket);
            this.getLogger().info("Loaded branch provider " + selectedBranch.provider().id() + " for Minecraft "
                    + minecraftVersion + " (branch " + selectedBranch.provider().branchVersion() + ")");
        } else {
            this.getLogger().warning(
                    "No compatible branch provider found for Minecraft " + minecraftVersion
                            + ". Install a branch extension or update the plugin core.");
            this.getServer().getPluginManager().disablePlugin(this);
            throw new IllegalArgumentException(
                    "Unsupported MC version: " + minecraftVersion + " Bukkit Version:" + bukkitVersion);
        }

        // Initialize for players.
        for (final Player player : Bukkit.getOnlinePlayers())
            ChunkIndex.chunkServer.initView(player);
        for (final World world : Bukkit.getWorlds())
            ChunkIndex.chunkServer.initWorld(world);

        Bukkit.getPluginManager()
                .registerEvents(
                        new ChunkEvent(ChunkIndex.chunkServer, ChunkIndex.branchPacket, ChunkIndex.branchMinecraft),
                        this);
        // protocolManager.addPacketListener(new ChunkPacketEvent(plugin, chunkServer));
        try {
            if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
                Class.forName("me.clip.placeholderapi.expansion.PlaceholderExpansion");
                ChunkPlaceholder.registerPlaceholder();
                this.getLogger().info("PlaceholderAPI detected. Placeholders working!");
            }
        } catch (NoClassDefFoundError | ClassNotFoundException e) {
            this.getLogger().warning("PlaceholderAPI not found. Placeholders will not be available!");
        }

        // Cria a árvore do comando "viewdistance" com os subcomandos e argumentos
        final LiteralCommandNode<CommandSourceStack> command = Commands
                .literal("viewdistance")
                .requires(source -> source.getSender().hasPermission("command.viewdistance"))

                // Subcomando "reload"
                .then(Commands.literal("reload")
                        .executes(Command::reload))

                // Subcomando "report" com as opções "server", "thread", "world" e "player"
                .then(Commands.literal("report")
                        .then(Commands.literal("server")
                                .executes(Command::reportServer))
                        .then(Commands.literal("thread")
                                .executes(Command::reportThread))
                        .then(Commands.literal("world")
                                .executes(Command::reportWorld))
                        .then(Commands.literal("player")
                                .executes(Command::reportPlayer)))

                // Subcomando "start"
                .then(Commands.literal("start")
                        .executes(Command::start))

                // Subcomando "stop"
                .then(Commands.literal("stop")
                        .executes(Command::stop))

                // Subcomando "permissionCheck" com um argumento do tipo jogador
                .then(Commands.literal("permissionCheck")
                        .then(Commands.argument("target", ArgumentTypes.player())
                                .executes(context -> Command.permissionCheck(context,
                                        (Player) context.getArgument("target", Player.class)))))

                // Subcomando "debug" com a opção "view" e argumento jogador
                .then(Commands.literal("debug")
                        .then(Commands.literal("view")
                                .then(Commands.argument("target", EntityArgument.player())
                                        .executes(context -> Command.debugView(context,
                                                (Player) context.getArgument("target", Player.class))))))
                .build();

        this.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, commands -> {
            commands.registrar().register(command);
        });
        // bStats
        final int pluginId = 26238;
        final Metrics metrics = new Metrics(this, pluginId);
        try {
            // Access the private 'metricsBase' field on Metrics
            final java.lang.reflect.Field baseField = metrics.getClass().getDeclaredField("metricsBase");
            baseField.setAccessible(true);
            final Object metricsBase = baseField.get(metrics);
            // Access the private 'enabled' field on MetricsBase subclass
            final java.lang.reflect.Field enabledField = metricsBase.getClass().getDeclaredField("enabled");
            enabledField.setAccessible(true);
            final boolean enabled = enabledField.getBoolean(metricsBase);
            if (enabled) {
                // Initialize the metrics collector and set up custom charts only if metrics are
                // enabled
                MetricsCollector.initialize(this);
                this.setupCustomMetrics(metrics);
            }
        } catch (final Exception e) {
            /* Do nothing if it fails */
        }
    }

    /**
     * Set up custom metrics charts for bStats
     * 
     * @param metrics The bStats metrics instance
     */
    private void setupCustomMetrics(final Metrics metrics) {
        // View distance metrics
        metrics.addCustomChart(new Metrics.SingleLineChart("average_view_distance",
                MetricsCollector::getAverageViewDistance));

        // Network speed metrics
        metrics.addCustomChart(new Metrics.SingleLineChart("average_network_speed_kbps",
                () -> MetricsCollector.getAverageNetworkSpeed() / 1024));

        // Load metrics - fast
        metrics.addCustomChart(new Metrics.SingleLineChart("load_fast_5s",
                MetricsCollector::getLoadFast5s));

        metrics.addCustomChart(new Metrics.SingleLineChart("load_fast_1m",
                MetricsCollector::getLoadFast1m));

        metrics.addCustomChart(new Metrics.SingleLineChart("load_fast_5m",
                MetricsCollector::getLoadFast5m));

        // Load metrics - slow
        metrics.addCustomChart(new Metrics.SingleLineChart("load_slow_5s",
                MetricsCollector::getLoadSlow5s));

        metrics.addCustomChart(new Metrics.SingleLineChart("load_slow_1m",
                MetricsCollector::getLoadSlow1m));

        metrics.addCustomChart(new Metrics.SingleLineChart("load_slow_5m",
                MetricsCollector::getLoadSlow5m));

        // Consumption metrics
        metrics.addCustomChart(new Metrics.SingleLineChart("consumption_5s",
                MetricsCollector::getConsume5s));

        metrics.addCustomChart(new Metrics.SingleLineChart("consumption_1m",
                MetricsCollector::getConsume1m));

        metrics.addCustomChart(new Metrics.SingleLineChart("consumption_5m",
                MetricsCollector::getConsume5m));

        // Distribution charts
        metrics.addCustomChart(new Metrics.DrilldownPie("network_speed_distribution",
                MetricsCollector::getNetworkSpeedDistribution));

        metrics.addCustomChart(new Metrics.DrilldownPie("view_distance_distribution",
                MetricsCollector::getViewDistanceDistribution));
    }

    @Override
    public void onDisable() {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            ChunkPlaceholder.unregisterPlaceholder();
        }
        if (ChunkIndex.chunkServer != null)
            ChunkIndex.chunkServer.close();
    }

    public ChunkIndex() {
        super();
    }

    public static ChunkServer getChunkServer() {
        return ChunkIndex.chunkServer;
    }

    public static ConfigData getConfigData() {
        return ChunkIndex.configData;
    }

    public static Plugin getPlugin() {
        return ChunkIndex.plugin;
    }

}
