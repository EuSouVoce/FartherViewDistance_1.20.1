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
import xuan.cat.fartherviewdistance.api.branch.BranchMinecraft;
import xuan.cat.fartherviewdistance.api.branch.BranchPacket;
import xuan.cat.fartherviewdistance.code.branch.MinecraftCode;
import xuan.cat.fartherviewdistance.code.branch.PacketCode;
import xuan.cat.fartherviewdistance.code.command.Command;
import xuan.cat.fartherviewdistance.code.data.ConfigData;
import xuan.cat.fartherviewdistance.code.data.viewmap.ViewShape;

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

        saveDefaultConfig();
        ChunkIndex.configData = new ConfigData(this, getConfig());

        final String bukkitVersion = Bukkit.getBukkitVersion();
        final String minecraftVersion = Bukkit.getMinecraftVersion();

        if (minecraftVersion.equals("1.21.4")) {
            ChunkIndex.branchPacket = new PacketCode();
            ChunkIndex.branchMinecraft = new MinecraftCode();
            ChunkIndex.chunkServer = new ChunkServer(ChunkIndex.configData, this, ViewShape.ROUND,
                    ChunkIndex.branchMinecraft, ChunkIndex.branchPacket);
        } else {
            this.getLogger().warning(
                    "Unsupported Version, for versions < 1.21.4 downgrade to 9.9.2, for versions > 1.21.4 download the corresponding version");
            this.getServer().getPluginManager().disablePlugin((Plugin) this);
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
                        (Plugin) this);
        // protocolManager.addPacketListener(new ChunkPacketEvent(plugin, chunkServer));
        // Section changed on pull request #22 by Mapacheee on branch 1.21.5.
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
        LiteralCommandNode<CommandSourceStack> command = Commands
                .literal("viewdistance")
                .requires(source -> source.getSender().hasPermission("command.viewdistance"))

                // Subcomando "reload"
                .then(Commands.literal("reload")
                        .executes(context -> Command.reload(context)))

                // Subcomando "report" com as opções "server", "thread", "world" e "player"
                .then(Commands.literal("report")
                        .then(Commands.literal("server")
                                .executes(context -> Command.reportServer(context)))
                        .then(Commands.literal("thread")
                                .executes(context -> Command.reportThread(context)))
                        .then(Commands.literal("world")
                                .executes(context -> Command.reportWorld(context)))
                        .then(Commands.literal("player")
                                .executes(context -> Command.reportPlayer(context))))

                // Subcomando "start"
                .then(Commands.literal("start")
                        .executes(context -> Command.start(context)))

                // Subcomando "stop"
                .then(Commands.literal("stop")
                        .executes(context -> Command.stop(context)))

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
