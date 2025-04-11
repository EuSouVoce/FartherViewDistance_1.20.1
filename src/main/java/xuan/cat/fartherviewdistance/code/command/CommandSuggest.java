package xuan.cat.fartherviewdistance.code.command;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import xuan.cat.fartherviewdistance.code.ChunkServer;
import xuan.cat.fartherviewdistance.code.data.ConfigData;

@SuppressWarnings("unused")
public final class CommandSuggest implements TabCompleter {

    private final ChunkServer chunkServer;
    private final ConfigData configData;

    public CommandSuggest(final ChunkServer chunkServer, final ConfigData configData) {
        this.chunkServer = chunkServer;
        this.configData = configData;
    }

    public List<String> onTabComplete(final CommandSender sender, final Command command, final String s,
            final String[] args) {
        if (!sender.hasPermission("command.viewdistance")) {
            return new ArrayList<>();
        }

        final List<String> list = new ArrayList<>();

        if (args.length == 1) {
            list.add("start");
            list.add("stop");
            list.add("reload");
            list.add("report");
            list.add("permissionCheck");
            list.add("debug");
        } else if (args.length == 2) {
            switch (args[0]) {
                case "report":
                    list.add("server");
                    list.add("thread");
                    list.add("world");
                    list.add("player");
                    break;
                case "permissionCheck":
                    Bukkit.getOnlinePlayers().forEach(player -> list.add(player.getName()));
                    break;
                case "debug":
                    list.add("view");
                    break;
            }
        } else if (args.length == 3) {
            switch (args[0]) {
                case "report":
                    break;
                case "permissionCheck":
                    break;
                case "debug":
                    switch (args[1]) {
                        case "view":
                            Bukkit.getOnlinePlayers().forEach(player -> list.add(player.getName()));
                            break;
                    }
                    break;
            }
        }

        return list;
    }
}