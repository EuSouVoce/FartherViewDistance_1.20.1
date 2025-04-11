package xuan.cat.fartherviewdistance.code.command;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import xuan.cat.fartherviewdistance.code.ChunkIndex;
import xuan.cat.fartherviewdistance.code.ChunkServer;
import xuan.cat.fartherviewdistance.code.data.ConfigData;
import xuan.cat.fartherviewdistance.code.data.CumulativeReport;

public final class Command implements CommandExecutor {

    private final ChunkServer chunkServer;
    private final ConfigData configData;

    public Command(final ChunkServer chunkServer, final ConfigData configData) {
        this.chunkServer = chunkServer;
        this.configData = configData;
    }

    @Override
    public boolean onCommand(final CommandSender sender, final org.bukkit.command.Command command, final String message,
            final String[] parameters) {
        if (!sender.hasPermission("command.viewdistance")) {
            sender.sendMessage(ChatColor.RED + this.chunkServer.lang.get(sender, "command.no_permission"));
        } else if (parameters.length < 1) {
            sender.sendMessage(ChatColor.RED + this.chunkServer.lang.get(sender, "command.missing_parameters"));
        } else {
            final String var5 = parameters[0];
            switch (var5) {
                case "reload":
                    try {
                        this.configData.reload();
                        ChunkIndex.getChunkServer().reloadMultithreaded();
                        sender.sendMessage(ChatColor.YELLOW
                                + this.chunkServer.lang.get(sender, "command.reread_configuration_successfully"));
                    } catch (final Exception var10) {
                        var10.printStackTrace();
                        sender.sendMessage(ChatColor.RED
                                + this.chunkServer.lang.get(sender, "command.reread_configuration_error"));
                    }
                    break;
                case "report":
                    if (parameters.length < 2) {
                        sender.sendMessage(
                                ChatColor.RED + this.chunkServer.lang.get(sender, "command.missing_parameters"));
                    } else {
                        final String var12 = parameters[1];
                        switch (var12) {
                            case "server":
                                this.sendReportHead(sender);
                                this.sendReportCumulative(sender, "*SERVER", this.chunkServer.serverCumulativeReport);
                                return true;
                            case "thread":
                                this.sendReportHead(sender);
                                this.chunkServer.threadsCumulativeReport
                                        .forEach((threadNumber, cumulativeReport) -> this
                                                .sendReportCumulative(sender, "*THREAD#" + threadNumber,
                                                        cumulativeReport));
                                return true;
                            case "world":
                                this.sendReportHead(sender);
                                this.chunkServer.worldsCumulativeReport
                                        .forEach((world, cumulativeReport) -> this.sendReportCumulative(sender,
                                                world.getName(), cumulativeReport));
                                return true;
                            case "player":
                                this.sendReportHead(sender);
                                this.chunkServer.playersViewMap
                                        .forEach((playerx, view) -> this.sendReportCumulative(sender, playerx.getName(),
                                                view.cumulativeReport));
                                return true;
                            default:
                                sender.sendMessage(
                                        ChatColor.RED
                                                + this.chunkServer.lang.get(sender, "command.unknown_parameter_type")
                                                + " " + parameters[0]);
                        }
                    }
                    break;
                case "start":
                    this.chunkServer.globalPause = false;
                    sender.sendMessage(
                            ChatColor.YELLOW + this.chunkServer.lang.get(sender, "command.continue_execution"));
                    break;
                case "stop":
                    this.chunkServer.globalPause = true;
                    sender.sendMessage(
                            ChatColor.YELLOW + this.chunkServer.lang.get(sender, "command.suspension_execution"));
                    break;
                case "permissionCheck":
                    if (parameters.length < 2) {
                        sender.sendMessage(
                                ChatColor.RED + this.chunkServer.lang.get(sender, "command.missing_parameters"));
                    } else {
                        final Player player = Bukkit.getPlayer(parameters[1]);
                        if (player == null) {
                            sender.sendMessage(
                                    ChatColor.RED + this.chunkServer.lang.get(sender, "command.players_do_not_exist"));
                        } else {
                            this.chunkServer.getView(player).permissionsNeed = true;
                            sender.sendMessage(ChatColor.YELLOW
                                    + this.chunkServer.lang.get(sender, "command.rechecked_player_permissions"));
                        }
                    }
                    break;
                case "debug":
                    if (parameters.length < 2) {
                        sender.sendMessage(
                                ChatColor.RED + this.chunkServer.lang.get(sender, "command.missing_parameters"));
                    } else {
                        final String player = parameters[1];
                        byte var8 = -1;
                        switch (player.hashCode()) {
                            case 3619493:
                                if (player.equals("view")) {
                                    var8 = 0;
                                }
                            default:
                                switch (var8) {
                                    case 0:
                                        if (parameters.length < 3) {
                                            sender.sendMessage(ChatColor.RED
                                                    + this.chunkServer.lang.get(sender, "command.missing_parameters"));
                                        } else {
                                            final Player target = Bukkit.getPlayer(parameters[2]);
                                            if (target == null) {
                                                sender.sendMessage(ChatColor.RED + this.chunkServer.lang.get(sender,
                                                        "command.players_do_not_exist"));
                                            } else {
                                                this.chunkServer.getView(target).getMap().debug(sender);
                                            }
                                        }
                                }
                        }
                    }
                    break;
                default:
                    sender.sendMessage(
                            ChatColor.RED + this.chunkServer.lang.get(sender, "command.unknown_parameter_type") + " "
                                    + parameters[0]);
            }
        }

        return true;
    }

    private void sendReportHead(final CommandSender sender) {
        final String timeSegment = this.chunkServer.lang.get(sender, "command.report.5s") + "/"
                + this.chunkServer.lang.get(sender, "command.report.1m") + "/"
                + this.chunkServer.lang.get(sender, "command.report.5m");
        sender.sendMessage(ChatColor.YELLOW + this.chunkServer.lang.get(sender, "command.report.source")
                + ChatColor.WHITE + " | "
                + ChatColor.GREEN + this.chunkServer.lang.get(sender, "command.report.fast") + " " + timeSegment
                + ChatColor.WHITE + " | "
                + ChatColor.RED + this.chunkServer.lang.get(sender, "command.report.slow") + " " + timeSegment
                + ChatColor.WHITE + " | "
                + ChatColor.GOLD + this.chunkServer.lang.get(sender, "command.report.flow") + " " + timeSegment);
    }

    private void sendReportCumulative(final CommandSender sender, final String source,
            final CumulativeReport cumulativeReport) {
        sender.sendMessage(ChatColor.YELLOW + source + ChatColor.WHITE + " | " + ChatColor.GREEN
                + cumulativeReport.reportLoadFast5s() + "/"
                + cumulativeReport.reportLoadFast1m() + "/" + cumulativeReport.reportLoadFast5m() + ChatColor.WHITE
                + " | " + ChatColor.RED
                + cumulativeReport.reportLoadSlow5s() + "/" + cumulativeReport.reportLoadSlow1m() + "/"
                + cumulativeReport.reportLoadSlow5m() + ChatColor.WHITE + " | " + ChatColor.GOLD
                + cumulativeReport.reportConsume5s() + "/"
                + cumulativeReport.reportConsume1m() + "/" + cumulativeReport.reportConsume5m());
    }
}