package xuan.cat.fartherviewdistance.code;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

import xuan.cat.fartherviewdistance.api.branch.BranchChunk;
import xuan.cat.fartherviewdistance.api.branch.BranchChunkLight;
import xuan.cat.fartherviewdistance.api.branch.BranchMinecraft;
import xuan.cat.fartherviewdistance.api.branch.BranchNBT;
import xuan.cat.fartherviewdistance.api.branch.BranchPacket;
import xuan.cat.fartherviewdistance.api.branch.packet.PacketEvent;
import xuan.cat.fartherviewdistance.api.branch.packet.PacketMapChunkEvent;
import xuan.cat.fartherviewdistance.api.event.PlayerSendExtendChunkEvent;
import xuan.cat.fartherviewdistance.code.data.ConfigData;
import xuan.cat.fartherviewdistance.code.data.CumulativeReport;
import xuan.cat.fartherviewdistance.code.data.LangFiles;
import xuan.cat.fartherviewdistance.code.data.NetworkTraffic;
import xuan.cat.fartherviewdistance.code.data.PlayerChunkView;
import xuan.cat.fartherviewdistance.code.data.viewmap.ViewMap;
import xuan.cat.fartherviewdistance.code.data.viewmap.ViewShape;

public final class ChunkServer {
    private final ConfigData configData;
    private final Plugin plugin;
    private boolean running = true;
    public final BranchMinecraft branchMinecraft;
    public final BranchPacket branchPacket;
    private final Set<ScheduledTask> bukkitTasks = ConcurrentHashMap.newKeySet();

    public static final Random random = new Random();
    private ScheduledExecutorService multithreadedService;
    private AtomicBoolean multithreadedCanRun;

    public final Map<Player, PlayerChunkView> playersViewMap = new ConcurrentHashMap<>();

    private final NetworkTraffic serverNetworkTraffic = new NetworkTraffic();
    private final Map<World, NetworkTraffic> worldsNetworkTraffic = new ConcurrentHashMap<>();
    private List<World> lastWorldList = new ArrayList<>();

    private final AtomicInteger serverGeneratedChunk = new AtomicInteger(0);
    private final Map<World, AtomicInteger> worldsGeneratedChunk = new ConcurrentHashMap<>();

    public final CumulativeReport serverCumulativeReport = new CumulativeReport();
    public final Map<World, CumulativeReport> worldsCumulativeReport = new ConcurrentHashMap<>();
    public final Map<Integer, CumulativeReport> threadsCumulativeReport = new ConcurrentHashMap<>();

    private final Set<Runnable> waitMoveSyncQueue = ConcurrentHashMap.newKeySet();
    public final Set<Thread> threadsSet = ConcurrentHashMap.newKeySet();
    public volatile boolean globalPause = false;
    public final LangFiles lang = new LangFiles();
    private final ViewShape viewShape;

    public ChunkServer(final ConfigData configData, final Plugin plugin, final ViewShape viewShape,
                       final BranchMinecraft branchMinecraft, final BranchPacket branchPacket) {
        this.configData = configData;
        this.plugin = plugin;
        this.branchMinecraft = branchMinecraft;
        this.branchPacket = branchPacket;
        this.viewShape = viewShape;

        this.bukkitTasks.add(Bukkit.getGlobalRegionScheduler()
                .runAtFixedRate(plugin, this::tickSync, 1, 1));

        this.bukkitTasks.add(Bukkit.getAsyncScheduler()
                .runAtFixedRate(plugin, this::tickAsync, 50, 50, TimeUnit.MILLISECONDS));

        this.reloadMultithreaded();
    }

    private void tickAsync(ScheduledTask scheduledTask) {
        this.serverNetworkTraffic.next();
        this.worldsNetworkTraffic.values().forEach(NetworkTraffic::next);
        this.playersViewMap.values().forEach(view -> {
            view.networkTraffic.next();
            view.networkSpeed.next();
        });
        this.serverGeneratedChunk.set(0);
        this.worldsGeneratedChunk.values().forEach(generatedChunk -> generatedChunk.set(0));
    }

    private void tickSync(ScheduledTask scheduledTask) {
        final List<World> worldList = Bukkit.getWorlds();
        Collections.shuffle(worldList);
        this.lastWorldList = worldList;
        this.waitMoveSyncQueue.removeIf(runnable -> {
            try {
                runnable.run();
            } catch (final Exception exception) {
                exception.printStackTrace();
            }
            return true;
        });
    }


    public PlayerChunkView initView(final Player player) {
        final PlayerChunkView view = new PlayerChunkView(player, this.configData, this.viewShape, this.branchPacket);
        this.playersViewMap.put(player, view);
        return view;
    }

    public void clearView(final Player player) {
        this.playersViewMap.remove(player);
    }

    public PlayerChunkView getView(final Player player) {
        return this.playersViewMap.get(player);
    }

    public synchronized void reloadMultithreaded() {
        if (this.multithreadedCanRun != null)
            this.multithreadedCanRun.set(false);
        if (this.multithreadedService != null) {
            this.multithreadedService.shutdown();
        }
        this.threadsCumulativeReport.clear();
        this.threadsSet.clear();
        this.playersViewMap.values().forEach(view -> view.waitSend = false);

        final AtomicBoolean canRun = new AtomicBoolean(true);
        this.multithreadedCanRun = canRun;
        this.multithreadedService = Executors.newScheduledThreadPool(this.configData.asyncThreadAmount + 1);

        this.multithreadedService.schedule(() -> {
            final Thread thread = Thread.currentThread();
            thread.setName("FartherViewDistance View thread");
            thread.setPriority(3);
            this.threadsSet.add(thread);
            this.runView(canRun);
        }, 0, TimeUnit.MILLISECONDS);

        for (int index = 0; index < this.configData.asyncThreadAmount; index++) {
            final int threadNumber = index;
            final CumulativeReport threadCumulativeReport = new CumulativeReport();
            this.threadsCumulativeReport.put(index, threadCumulativeReport);
            this.multithreadedService.schedule(() -> {
                final Thread thread = Thread.currentThread();
                thread.setName("FartherViewDistance AsyncTick thread #" + threadNumber);
                thread.setPriority(2);
                this.threadsSet.add(thread);
                this.runThread(canRun, threadCumulativeReport);
            }, 0, TimeUnit.MILLISECONDS);
        }
    }

    public void initWorld(final World world) {
        this.worldsNetworkTraffic.put(world, new NetworkTraffic());
        this.worldsCumulativeReport.put(world, new CumulativeReport());
        this.worldsGeneratedChunk.put(world, new AtomicInteger(0));
    }

    public void clearWorld(final World world) {
        this.worldsNetworkTraffic.remove(world);
        this.worldsCumulativeReport.remove(world);
        this.worldsGeneratedChunk.remove(world);
    }

    private void tickSync() {
        final List<World> worldList = Bukkit.getWorlds();
        Collections.shuffle(worldList);
        this.lastWorldList = worldList;
        this.waitMoveSyncQueue.removeIf(runnable -> {
            try {
                runnable.run();
            } catch (final Exception exception) {
                exception.printStackTrace();
            }
            return true;
        });
    }

    private void tickAsync() {
        this.serverNetworkTraffic.next();
        this.worldsNetworkTraffic.values().forEach(NetworkTraffic::next);
        this.playersViewMap.values().forEach(view -> {
            view.networkTraffic.next();
            view.networkSpeed.next();
        });
        this.serverGeneratedChunk.set(0);
        this.worldsGeneratedChunk.values().forEach(generatedChunk -> generatedChunk.set(0));
    }

    private void tickReport() {
        this.serverCumulativeReport.next();
        this.worldsCumulativeReport.values().forEach(CumulativeReport::next);
        this.playersViewMap.values().forEach(view -> view.cumulativeReport.next());
        this.threadsCumulativeReport.values().forEach(CumulativeReport::next);
    }

    private void runView(final AtomicBoolean canRun) {
        while (canRun.get()) {
            final long startTime = System.currentTimeMillis();
            try {
                this.playersViewMap.forEach((player, view) -> {
                    if (!view.install()) view.updateDistance();
                    view.moveTooFast = view.overSpeed();
                });
            } catch (final Exception exception) {
                exception.printStackTrace();
            }
            final long endTime = System.currentTimeMillis();
            final long needSleep = 50 - (endTime - startTime);
            if (needSleep > 0) {
                try {
                    Thread.sleep(needSleep);
                } catch (final InterruptedException ignored) {
                }
            }
        }
    }

    private void runThread(final AtomicBoolean canRun, final CumulativeReport threadCumulativeReport) {
        while (canRun.get()) {
            final long startTime = System.currentTimeMillis();
            final long effectiveTime = startTime + 50;

            if (!this.globalPause) {
                try {
                    final List<World> worldList = this.lastWorldList;
                    final List<PlayerChunkView> viewList = Arrays
                            .asList(this.playersViewMap.values().toArray(new PlayerChunkView[0]));
                    Collections.shuffle(viewList);
                    for (final PlayerChunkView view : viewList) view.move();
                    final Map<World, List<PlayerChunkView>> worldsViews = new HashMap<>();
                    for (final PlayerChunkView view : viewList)
                        worldsViews.computeIfAbsent(view.getLastWorld(), key -> new ArrayList<>()).add(view);

                    for (final World world : worldList) {
                        final ConfigData.World configWorld = this.configData.getWorld(world.getName());
                        if (!configWorld.enable) continue;
                        final CumulativeReport worldCumulativeReport = this.worldsCumulativeReport.get(world);
                        if (worldCumulativeReport == null) continue;
                        final NetworkTraffic worldNetworkTraffic = this.worldsNetworkTraffic.get(world);
                        if (worldNetworkTraffic == null) continue;
                        if (this.serverNetworkTraffic.exceed(this.configData.getServerSendTickMaxBytes())) break;
                        if (worldNetworkTraffic.exceed(configWorld.getWorldSendTickMaxBytes())) continue;

                        final AtomicInteger worldGeneratedChunk = this.worldsGeneratedChunk.getOrDefault(world,
                                new AtomicInteger(Integer.MAX_VALUE));

                        boolean playersFull = false;
                        while (!playersFull && effectiveTime >= System.currentTimeMillis()) {
                            playersFull = true;
                            for (final PlayerChunkView view : worldsViews.getOrDefault(world, new ArrayList<>(0))) {
                                if (this.serverNetworkTraffic.exceed(this.configData.getServerSendTickMaxBytes())) break;
                                if (worldNetworkTraffic.exceed(configWorld.getWorldSendTickMaxBytes())) break;

                                synchronized (view.networkTraffic) {
                                    final Integer forciblySendSecondMaxBytes = view.forciblySendSecondMaxBytes;
                                    if (view.networkTraffic.exceed(forciblySendSecondMaxBytes != null
                                            ? (int) (forciblySendSecondMaxBytes
                                            * this.configData.playerNetworkSpeedUseDegree) / 20
                                            : configWorld.getPlayerSendTickMaxBytes()))
                                        continue;
                                    if (this.configData.autoAdaptPlayerNetworkSpeed && view.networkTraffic
                                            .exceed(Math.max(1, view.networkSpeed.avg() * 50)))
                                        continue;
                                }
                                if (view.waitSend) {
                                    playersFull = false;
                                    continue;
                                }
                                if (view.moveTooFast) continue;
                                view.waitSend = true;
                                final long syncKey = view.syncKey;
                                final Long chunkKey = view.next();
                                if (chunkKey == null) {
                                    view.waitSend = false;
                                    continue;
                                }
                                playersFull = false;
                                final int chunkX = ViewMap.getX(chunkKey);
                                final int chunkZ = ViewMap.getZ(chunkKey);

                                final Consumer<Chunk> processChunk = chunk -> {
                                    if (chunk == null) return;
                                    try {
                                        final List<Runnable> asyncRunnable = new ArrayList<>();
                                        final BranchChunkLight chunkLight = this.branchMinecraft.fromLight(world);
                                        final BranchNBT chunkNBT = this.branchMinecraft.fromChunk(world, chunk)
                                                .toNBT(chunkLight, asyncRunnable);
                                        asyncRunnable.forEach(Runnable::run);
                                        this.sendChunk(world, configWorld, worldNetworkTraffic, view, chunkX,
                                                chunkZ, chunkNBT, chunkLight, syncKey,
                                                worldCumulativeReport, threadCumulativeReport);
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                };

                                world.getChunkAtAsync(chunkX, chunkZ, true, true).whenComplete((chunk, throwable) -> {
                                    if (throwable != null) return;
                                    Bukkit.getRegionScheduler().run(this.plugin, world, chunkX, chunkZ,
                                            task -> processChunk.accept(chunk));
                                });
                                view.waitSend = false;
                            }
                            try {
                                Thread.sleep(0L);
                            } catch (final InterruptedException ignored) {
                            }
                        }
                    }
                } catch (final Exception exception) {
                    exception.printStackTrace();
                }
            }

            final long endTime = System.currentTimeMillis();
            final long needSleep = 50 - (endTime - startTime);
            if (needSleep > 0) {
                try {
                    Thread.sleep(needSleep);
                } catch (final InterruptedException ignored) {
                }
            }
        }
    }

    private void sendChunk(final World world, final ConfigData.World configWorld, final NetworkTraffic worldNetworkTraffic,
                           final PlayerChunkView view, final int chunkX, final int chunkZ, final BranchNBT chunkNBT,
                           final BranchChunkLight chunkLight, final long syncKey,
                           final CumulativeReport worldCumulativeReport, final CumulativeReport threadCumulativeReport) {
        final BranchChunk chunk = this.branchMinecraft.fromChunk(world, chunkX, chunkZ, chunkNBT,
                this.configData.calculateMissingHeightMap);
        final PlayerSendExtendChunkEvent event = new PlayerSendExtendChunkEvent(view.viewAPI, chunk, world);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return;

        if (configWorld.preventXray != null && !configWorld.preventXray.isEmpty()) {
            Bukkit.getAsyncScheduler().runNow(plugin, (plugin) -> {
                for (final Map.Entry<BlockData, BlockData[]> conversionMap : configWorld.preventXray.entrySet())
                    chunk.replaceAllMaterial(conversionMap.getValue(), conversionMap.getKey());
            });
        }

        final AtomicInteger consumeTraffic = new AtomicInteger(0);
        final Consumer<Player> chunkAndLightPacket = this.branchPacket.sendChunkAndLight(view.getPlayer(), chunk, chunkLight,
                configWorld.sendTitleData, consumeTraffic::addAndGet);

        synchronized (view.networkSpeed) {
            final Location nowLoc = view.getPlayer().getLocation();
            final int nowChunkX = nowLoc.getBlockX() >> 4;
            final int nowChunkZ = nowLoc.getBlockZ() >> 4;
            final ViewMap viewMap = view.getMap();
            if (world != nowLoc.getWorld()) {
                view.getMap().markWaitPosition(chunkX, chunkZ);
                return;
            }
            if (view.getMap().isWaitPosition(chunkX, chunkZ)) return;
            if (this.viewShape.isInsideEdge(nowChunkX, nowChunkZ, chunkX, chunkZ, viewMap.serverDistance)) return;
            if (view.syncKey != syncKey) return;
            if (!this.running) return;

            final boolean needMeasure = this.configData.autoAdaptPlayerNetworkSpeed && ((view.networkSpeed.speedID == null
                    && view.networkSpeed.speedTimestamp + 1000 <= System.currentTimeMillis())
                    || view.networkSpeed.speedTimestamp + 30000 <= System.currentTimeMillis());
            if (needMeasure) {
                if (view.networkSpeed.speedID != null) view.networkSpeed.add(30000, 0);
                final long pingID = ChunkServer.random.nextLong();
                view.networkSpeed.pingID = pingID;
                view.networkSpeed.pingTimestamp = System.currentTimeMillis();
                this.branchPacket.sendKeepAlive(view.getPlayer(), pingID);
            }

            chunkAndLightPacket.accept(view.getPlayer());
            this.serverNetworkTraffic.use(consumeTraffic.get());
            worldNetworkTraffic.use(consumeTraffic.get());
            view.networkTraffic.use(consumeTraffic.get());
            this.serverCumulativeReport.addConsume(consumeTraffic.get());
            worldCumulativeReport.addConsume(consumeTraffic.get());
            view.cumulativeReport.addConsume(consumeTraffic.get());
            threadCumulativeReport.addConsume(consumeTraffic.get());

            if (needMeasure) {
                final long speedID = ChunkServer.random.nextLong();
                view.networkSpeed.speedID = speedID;
                view.networkSpeed.speedConsume = consumeTraffic.get();
                view.networkSpeed.speedTimestamp = System.currentTimeMillis();
                this.branchPacket.sendKeepAlive(view.getPlayer(), speedID);
            }
        }
    }

    public void packetEvent(final Player player, final PacketEvent event) {
        final PlayerChunkView view = this.getView(player);
        if (view == null) return;
        if (event instanceof final PacketMapChunkEvent chunkEvent) view.send(chunkEvent.getChunkX(), chunkEvent.getChunkZ());
    }

    public void respawnView(final Player player) {
        final PlayerChunkView view = this.getView(player);
        if (view == null) return;
        view.delay();
        this.waitMoveSyncQueue.add(() -> this.branchPacket.sendViewDistance(player, view.getMap().extendDistance));
    }

    public void unloadView(final Player player, final Location from, final Location move) {
        final PlayerChunkView view = this.getView(player);
        if (view == null) return;
        final int blockDistance = view.getMap().extendDistance << 4;
        if (from.getWorld() != move.getWorld()) view.unload();
        else if (Math.abs(from.getX() - move.getX()) >= blockDistance
                || Math.abs(from.getZ() - move.getZ()) >= blockDistance) view.unload();
    }

    void close() {
        this.running = false;
        for (final ScheduledTask task : this.bukkitTasks) task.cancel();
        this.multithreadedService.shutdown();
    }
}