package xuan.cat.fartherviewdistance.code.branch;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.bukkit.block.data.BlockData;
import org.bukkit.craftbukkit.CraftChunk;
import org.bukkit.craftbukkit.block.CraftBiome;
import org.bukkit.craftbukkit.block.data.CraftBlockData;
import org.bukkit.util.Vector;

import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import xuan.cat.fartherviewdistance.api.branch.BranchChunk;
import xuan.cat.fartherviewdistance.api.branch.BranchChunkLight;
import xuan.cat.fartherviewdistance.api.branch.BranchNBT;

public final class ChunkCode implements BranchChunk {
    private final LevelChunk levelChunk;
    private final ServerLevel worldServer;

    public ChunkCode(ServerLevel worldServer, LevelChunk levelChunk) {
        this.levelChunk = levelChunk;
        this.worldServer = worldServer;
    }

    public BranchNBT toNBT(BranchChunkLight light, List<Runnable> asyncRunnable) {
        return new ChunkNBT(
                ChunkRegionLoader.saveChunk(worldServer, levelChunk, (ChunkLightCode) light, asyncRunnable));
    }

    LevelChunk getLevelChunk() {
        return levelChunk;
    }

    public org.bukkit.Chunk getChunk() {
        return new CraftChunk(levelChunk);
    }

    public org.bukkit.World getWorld() {
        return worldServer.getWorld();
    }

    public BlockState getIBlockData(final int x, final int y, final int z) {
        final int indexY = (y >> 4) - this.levelChunk.getMinSectionY();
        final LevelChunkSection[] chunkSections = this.levelChunk.getSections();
        if (indexY >= 0 && indexY < chunkSections.length) {
            final LevelChunkSection chunkSection = chunkSections[indexY];
            if (chunkSection != null && !chunkSection.hasOnlyAir())
                return chunkSection.getBlockState(x & 15, y & 15, z & 15);
        }
        return Blocks.AIR.defaultBlockState();
    }

    public void setIBlockData(final int x, final int y, final int z, final BlockState iBlockData) {
        final int indexY = (y >> 4) - this.levelChunk.getMinSectionY();
        final LevelChunkSection[] chunkSections = this.levelChunk.getSections();

        if (indexY >= 0 && indexY < chunkSections.length) {
            LevelChunkSection chunkSection = chunkSections[indexY];

            if (chunkSection == null) {
                chunkSection = chunkSections[indexY] = new LevelChunkSection(
                        this.worldServer.registryAccess().lookupOrThrow(Registries.BIOME), this.levelChunk.getLevel(),
                        new ChunkPos(this.levelChunk.locX, this.levelChunk.locZ), indexY);
            }
            chunkSection.setBlockState(x & 15, y & 15, z & 15, iBlockData, false);
        }
    }

    @Override
    public boolean equalsBlockData(final int x, final int y, final int z, final BlockData blockData) {
        return this.equalsBlockData(x, y, z, ((CraftBlockData) blockData).getState());
    }

    public boolean equalsBlockData(final int x, final int y, final int z, final BlockState other) {
        final BlockState state = this.getIBlockData(x, y, z);
        return state != null && state.equals(other);
    }

    @Override
    public BlockData getBlockData(final int x, final int y, final int z) {
        final BlockState blockData = this.getIBlockData(x, y, z);
        return blockData != null ? CraftBlockData.fromData(blockData)
                : CraftBlockData.fromData(Blocks.AIR.defaultBlockState());
    }

    @Override
    public void setBlockData(final int x, final int y, final int z, final BlockData blockData) {
        final BlockState iBlockData = ((CraftBlockData) blockData).getState();
        if (iBlockData != null)
            this.setIBlockData(x, y, z, iBlockData);
    }

    public Map<Vector, BlockData> getBlockDataMap() {
        Map<Vector, BlockData> vectorBlockDataMap = new HashMap<>();
        int maxHeight = worldServer.getMaxY();
        int minHeight = worldServer.getMinY();
        for (int x = 0; x < 16; x++) {
            for (int y = minHeight; y < maxHeight; y++) {
                for (int z = 0; z < 16; z++) {
                    BlockData blockData = this.getBlockData(x, y, z);
                    org.bukkit.Material material = blockData.getMaterial();
                    if (material != org.bukkit.Material.AIR && material != org.bukkit.Material.VOID_AIR
                            && material != org.bukkit.Material.CAVE_AIR) {
                        vectorBlockDataMap.put(new Vector(x, y, z), blockData);
                    }
                }
            }
        }

        return vectorBlockDataMap;
    }

    public int getX() {
        return levelChunk.getPos().x;
    }

    public int getZ() {
        return levelChunk.getPos().z;
    }

    private static Field field_LevelChunkSection_nonEmptyBlockCount;
    static {
        try {
            field_LevelChunkSection_nonEmptyBlockCount = LevelChunkSection.class.getDeclaredField("nonEmptyBlockCount");
            field_LevelChunkSection_nonEmptyBlockCount.setAccessible(true);
        } catch (NoSuchFieldException exception) {
            exception.printStackTrace();
        }
    }

    @Override
    public void replaceAllMaterial(final BlockData[] target, final BlockData to) {
        final Map<Block, BlockState> targetMap = new HashMap<>();
        for (final BlockData targetData : target) {
            final BlockState targetState = ((CraftBlockData) targetData).getState();
            targetMap.put(targetState.getBlock(), targetState);
        }
        final BlockState toI = ((CraftBlockData) to).getState();
        for (final LevelChunkSection section : this.levelChunk.getSections()) {
            if (section != null) {
                final AtomicInteger counts = new AtomicInteger();
                final PalettedContainer<BlockState> blocks = section.getStates();
                final List<Integer> conversionLocationList = new ArrayList<>();
                final PalettedContainer.CountConsumer<BlockState> forEachLocation = (state, location) -> {
                    if (state == null)
                        return;
                    final BlockState targetState = targetMap.get(state.getBlock());
                    if (targetState != null) {
                        conversionLocationList.add(location);
                        state = toI;
                    }
                    if (!state.isAir())
                        counts.incrementAndGet();
                    final FluidState fluid = state.getFluidState();
                    if (!fluid.isEmpty())
                        counts.incrementAndGet();
                };

                blocks.count(forEachLocation);
                conversionLocationList.forEach(location -> {
                    blocks.getAndSetUnchecked(location & 15, location >> 8 & 15, location >> 4 & 15, toI);
                });
                try {
                    field_LevelChunkSection_nonEmptyBlockCount.set(section, counts.shortValue());
                } catch (final IllegalAccessException exception) {
                    exception.printStackTrace();
                }
            }
        }
    }

    public org.bukkit.Material getMaterial(int x, int y, int z) {
        return getBlockData(x, y, z).getMaterial();
    }

    public void setMaterial(int x, int y, int z, org.bukkit.Material material) {
        setBlockData(x, y, z, material.createBlockData());
    }

    @Deprecated
    public org.bukkit.block.Biome getBiome(int x, int z) {
        return this.getBiome(x, 0, z);
    }

    public org.bukkit.block.Biome getBiome(int x, int y, int z) {
        return CraftBiome.minecraftHolderToBukkit(levelChunk.getNoiseBiome(x, y, z));
    }

    @Deprecated
    public void setBiome(int x, int z, org.bukkit.block.Biome biome) {
        setBiome(x, 0, z, biome);
    }

    public void setBiome(int x, int y, int z, org.bukkit.block.Biome biome) {
        levelChunk.setBiome(x, y, z, CraftBiome.bukkitToMinecraftHolder(biome));
    }

    public boolean hasFluid(int x, int y, int z) {
        return !getIBlockData(x, y, z).getFluidState().isEmpty();
    }

    public boolean isAir(int x, int y, int z) {
        return getIBlockData(x, y, z).isAir();
    }

    public int getHighestY(int x, int z) {
        return levelChunk.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
    }

    public static Status ofStatus(ChunkStatus chunkStatus) {
        if (chunkStatus == ChunkStatus.EMPTY) {
            return Status.EMPTY;
        } else if (chunkStatus == ChunkStatus.STRUCTURE_STARTS) {
            return Status.STRUCTURE_STARTS;
        } else if (chunkStatus == ChunkStatus.STRUCTURE_REFERENCES) {
            return Status.STRUCTURE_REFERENCES;
        } else if (chunkStatus == ChunkStatus.BIOMES) {
            return Status.BIOMES;
        } else if (chunkStatus == ChunkStatus.NOISE) {
            return Status.NOISE;
        } else if (chunkStatus == ChunkStatus.SURFACE) {
            return Status.SURFACE;
        } else if (chunkStatus == ChunkStatus.CARVERS) {
            return Status.CARVERS;
        } else if (chunkStatus == ChunkStatus.FEATURES) {
            return Status.FEATURES;
        } else if (chunkStatus == ChunkStatus.LIGHT) {
            return Status.LIGHT;
        } else if (chunkStatus == ChunkStatus.INITIALIZE_LIGHT) {
            return Status.INITIALIZE_LIGHT;
        } else if (chunkStatus == ChunkStatus.SPAWN) {
            return Status.SPAWN;
        } else if (chunkStatus == ChunkStatus.FULL) {
            return Status.FULL;
        }
        return Status.EMPTY;
    }

    public Status getStatus() {
        return ofStatus(levelChunk.getPersistedStatus());
    }
}