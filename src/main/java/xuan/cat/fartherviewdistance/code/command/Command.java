package xuan.cat.fartherviewdistance.code.command;

import org.bukkit.entity.Player;

import com.mojang.brigadier.context.CommandContext;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import xuan.cat.fartherviewdistance.code.ChunkIndex;
import xuan.cat.fartherviewdistance.code.data.ConfigData;
import xuan.cat.fartherviewdistance.code.data.CumulativeReport;

public final class Command {

    public static int reload(final CommandContext<CommandSourceStack> context) {
        final CommandSourceStack source = context.getSource();
        try {
            final ConfigData configData = ChunkIndex.getConfigData();
            configData.reload();
            ChunkIndex.getChunkServer().reloadMultithreaded();
            source.getSender().sendMessage(Component.text(
                    ChunkIndex.getChunkServer().lang.get(source.getSender(),
                            "command.reread_configuration_successfully")));
        } catch (final Exception e) {
            e.printStackTrace();
            source.getSender().sendMessage(Component.text(
                    ChunkIndex.getChunkServer().lang.get(source.getSender(),
                            "command.reread_configuration_error")));
        }
        return 1;
    }

    public static int reportServer(final CommandContext<CommandSourceStack> context) {
        final CommandSourceStack source = context.getSource();
        Command.sendReportHead(source);
        Command.sendReportCumulative(source, "*SERVER", ChunkIndex.getChunkServer().serverCumulativeReport);
        return 1;
    }

    public static int reportThread(final CommandContext<CommandSourceStack> context) {
        final CommandSourceStack source = context.getSource();
        Command.sendReportHead(source);
        ChunkIndex.getChunkServer().threadsCumulativeReport.forEach((threadNumber,
                cumulativeReport) -> Command.sendReportCumulative(source, "*THREAD#" + threadNumber, cumulativeReport));
        return 1;
    }

    public static int reportWorld(final CommandContext<CommandSourceStack> context) {
        final CommandSourceStack source = context.getSource();
        Command.sendReportHead(source);
        ChunkIndex.getChunkServer().worldsCumulativeReport
                .forEach((world, cumulativeReport) -> Command.sendReportCumulative(source, world.getName(), cumulativeReport));
        return 1;
    }

    public static int reportPlayer(final CommandContext<CommandSourceStack> context) {
        final CommandSourceStack source = context.getSource();
        Command.sendReportHead(source);
        ChunkIndex.getChunkServer().playersViewMap
                .forEach((player, view) -> Command.sendReportCumulative(source, player.getName(), view.cumulativeReport));
        return 1;
    }

    public static int start(final CommandContext<CommandSourceStack> context) {
        final CommandSourceStack source = context.getSource();
        ChunkIndex.getChunkServer().globalPause = false;
        source.getSender().sendMessage(Component.text(
                ChunkIndex.getChunkServer().lang.get(source.getSender(), "command.continue_execution")));
        return 1;
    }

    public static int stop(final CommandContext<CommandSourceStack> context) {
        final CommandSourceStack source = context.getSource();
        ChunkIndex.getChunkServer().globalPause = true;
        source.getSender().sendMessage(Component.text(
                ChunkIndex.getChunkServer().lang.get(source.getSender(), "command.suspension_execution")));
        return 1;
    }

    public static int permissionCheck(final CommandContext<CommandSourceStack> context, final Player target) {
        final CommandSourceStack source = context.getSource();
        if (target == null) {
            source.getSender().sendMessage(Component.text(
                    ChunkIndex.getChunkServer().lang.get(source.getSender(), "command.players_do_not_exist")));
        } else {
            ChunkIndex.getChunkServer().getView(target).permissionsNeed = true;
            source.getSender().sendMessage(Component.text(
                    ChunkIndex.getChunkServer().lang.get(source.getSender(),
                            "command.rechecked_player_permissions")));
        }
        return 1;
    }

    public static int debugView(final CommandContext<CommandSourceStack> context, final Player target) {
        final CommandSourceStack source = context.getSource();
        if (target == null) {
            source.getSender().sendMessage(Component.text(
                    ChunkIndex.getChunkServer().lang.get(source.getSender(), "command.players_do_not_exist")));
        } else {
            // Executa o método debug na view do jogador
            ChunkIndex.getChunkServer().getView(target).getMap().debug(source.getSender());
        }
        return 1;
    }

    public static void sendReportHead(final CommandSourceStack source) {
        final String timeSegment = ChunkIndex.getChunkServer().lang.get(source.getSender(), "command.report.5s")
                + "/" + ChunkIndex.getChunkServer().lang.get(source.getSender(), "command.report.1m")
                + "/" + ChunkIndex.getChunkServer().lang.get(source.getSender(), "command.report.5m");
        source.getSender().sendMessage(Component.text(
                ChunkIndex.getChunkServer().lang.get(source.getSender(), "command.report.source")
                        + " | " +
                        ChunkIndex.getChunkServer().lang.get(source.getSender(), "command.report.fast") + " "
                        + timeSegment
                        + " | " +
                        ChunkIndex.getChunkServer().lang.get(source.getSender(), "command.report.slow") + " "
                        + timeSegment
                        + " | " +
                        ChunkIndex.getChunkServer().lang.get(source.getSender(), "command.report.flow") + " "
                        + timeSegment));
    }

    public static void sendReportCumulative(final CommandSourceStack source, final String sourceName,
            final CumulativeReport cumulativeReport) {
        final String message = sourceName + " | " +
                cumulativeReport.reportLoadFast5s() + "/" +
                cumulativeReport.reportLoadFast1m() + "/" +
                cumulativeReport.reportLoadFast5m() + " | " +
                cumulativeReport.reportLoadSlow5s() + "/" +
                cumulativeReport.reportLoadSlow1m() + "/" +
                cumulativeReport.reportLoadSlow5m() + " | " +
                cumulativeReport.reportConsume5s() + "/" +
                cumulativeReport.reportConsume1m() + "/" +
                cumulativeReport.reportConsume5m();
        source.getSender().sendMessage(Component.text(message));
    }
}
