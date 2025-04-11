package xuan.cat.fartherviewdistance.api.data;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import xuan.cat.fartherviewdistance.code.data.PlayerChunkView;

public final class PlayerView {
    private final PlayerChunkView chunkView;

    public PlayerView(PlayerChunkView chunkView) {
        this.chunkView = chunkView;
    }

    public boolean isChunkSend(Location location) {
        return isChunkSend(location.getBlockX() >> 4, location.getBlockZ() >> 4);
    }

    public boolean isChunkSend(int chunkX, int chunkZ) {
        return chunkView.getMap().isSendPosition(chunkX, chunkZ);
    }

    public boolean isChunkWait(Location location) {
        return isChunkWait(location.getBlockX() >> 4, location.getBlockZ() >> 4);
    }

    public boolean isChunkWait(int chunkX, int chunkZ) {
        return chunkView.getMap().isWaitPosition(chunkX, chunkZ);
    }

    public boolean inChunk(Location location) {
        return inChunk(location.getBlockX() >> 4, location.getBlockZ() >> 4);
    }

    public boolean inChunk(int chunkX, int chunkZ) {
        return chunkView.getMap().inPosition(chunkX, chunkZ);
    }

    public void setChunkSend(Location location) {
        setChunkSend(location.getBlockX() >> 4, location.getBlockZ() >> 4);
    }

    public void setChunkSend(int chunkX, int chunkZ) {
        chunkView.getMap().markSendPosition(chunkX, chunkZ);
    }

    public void setChunkWait(Location location) {
        setChunkWait(location.getBlockX() >> 4, location.getBlockZ() >> 4);
    }

    public void setChunkWait(int chunkX, int chunkZ) {
        chunkView.getMap().markWaitPosition(chunkX, chunkZ);
    }

    public int getNowExtendViewDistance() {
        return chunkView.getMap().extendDistance;
    }

    public int getNowServerViewDistance() {
        return chunkView.getMap().serverDistance;
    }

    public int getMaxExtendViewDistance() {
        return chunkView.max();
    }

    public void setCenter(Location location) {
        setCenter(location.getBlockX() >> 4, location.getBlockZ() >> 4);
    }

    public void setCenter(int chunkX, int chunkZ) {
        chunkView.getMap().setCenter(chunkX, chunkZ);
    }

    public Location getCenter() {
        return new Location((org.bukkit.World) chunkView.getLastWorld(), chunkView.getMap().getCenterX() << 4, 0,
                chunkView.getMap().getCenterZ() << 4);
    }

    public void clear() {
        chunkView.getMap().clear();
    }

    public void unload() {
        chunkView.unload();
    }

    public void install() {
        chunkView.install();
    }

    public void recalculate() {
        chunkView.recalculate();
    }

    public void setDelay(int delayTime) {
        chunkView.delay(delayTime);
    }

    public long getDelay() {
        return chunkView.getDelayTime();
    }

    public Player getPlayer() {
        return chunkView.getPlayer();
    }

    public World getWorld() {
        return chunkView.getLastWorld();
    }

    public void setForciblySendSecondMaxBytes(Integer value) {
        chunkView.forciblySendSecondMaxBytes = value;
    }

    public Integer getForciblySendSecondMaxBytes() {
        return chunkView.forciblySendSecondMaxBytes;
    }

    public void setForciblyMaxDistance(Integer value) {
        chunkView.forciblyMaxDistance = value;
    }

    public Integer getForciblyMaxDistance() {
        return chunkView.forciblyMaxDistance;
    }

    public int getNetworkSpeedAVG() {
        return chunkView.networkSpeed.avg();
    }

    public int getNetworkReportLoadFast5s() {
        return chunkView.cumulativeReport.reportLoadFast5s();
    }

    public int getNetworkReportLoadFast1m() {
        return chunkView.cumulativeReport.reportLoadFast1m();
    }

    public int getNetworkReportLoadFast5m() {
        return chunkView.cumulativeReport.reportLoadFast5m();
    }

    public int getNetworkReportLoadSlow5s() {
        return chunkView.cumulativeReport.reportLoadSlow5s();
    }

    public int getNetworkReportLoadSlow1m() {
        return chunkView.cumulativeReport.reportLoadSlow1m();
    }

    public int getNetworkReportLoadSlow5m() {
        return chunkView.cumulativeReport.reportLoadSlow5m();
    }

    public long getNetworkReportConsume5s() {
        return chunkView.cumulativeReport.reportConsume5s();
    }

    public long getNetworkReportConsume1m() {
        return chunkView.cumulativeReport.reportConsume1m();
    }

    public long getNetworkReportConsume5m() {
        return chunkView.cumulativeReport.reportConsume5m();
    }
}
