package xuan.cat.fartherviewdistance.code.branch;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.block.data.BlockData;
import org.bukkit.craftbukkit.CraftChunk;
import org.bukkit.craftbukkit.block.CraftBiome;
import org.bukkit.craftbukkit.block.data.CraftBlockData;
import org.bukkit.util.Vector;

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
import xuan.cat.fartherviewdistance.api.branch.BranchChunk;
import xuan.cat.fartherviewdistance.api.branch.BranchChunkLight;
import xuan.cat.fartherviewdistance.api.branch.BranchNBT;

public final class ChunkCode implements BranchChunk {
    private final LevelChunk levelChunk;
    private final ServerLevel worldServer;

    public ChunkCode(final ServerLevel worldServer, final LevelChunk levelChunk) {
        this.levelChunk = levelChunk;
        this.worldServer = worldServer;
    }

    @Override
    public BranchNBT toNBT(final BranchChunkLight light, final List<Runnable> asyncRunnable) {
        return new ChunkNBT(
                ChunkRegionLoader.saveChunk(this.worldServer, this.levelChunk, (ChunkLightCode) light, asyncRunnable));
    }

    LevelChunk getLevelChunk() {
        return this.levelChunk;
    }

    @Override
    public org.bukkit.Chunk getChunk() {
        return new CraftChunk(this.levelChunk);
    }

    @Override
    public org.bukkit.World getWorld() {
        return this.worldServer.getWorld();
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
                        this.levelChunk.level.palettedContainerFactory(), this.levelChunk.getLevel(),
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

    @Override
    public Map<Vector, BlockData> getBlockDataMap() {
        final Map<Vector, BlockData> vectorBlockDataMap = new HashMap<>();
        final int maxHeight = this.worldServer.getMaxY();
        final int minHeight = this.worldServer.getMinY();
        for (int x = 0; x < 16; x++) {
            for (int y = minHeight; y < maxHeight; y++) {
                for (int z = 0; z < 16; z++) {
                    final BlockData blockData = this.getBlockData(x, y, z);
                    final org.bukkit.Material material = blockData.getMaterial();
                    if (material != org.bukkit.Material.AIR && material != org.bukkit.Material.VOID_AIR
                            && material != org.bukkit.Material.CAVE_AIR) {
                        vectorBlockDataMap.put(new Vector(x, y, z), blockData);
                    }
                }
            }
        }

        return vectorBlockDataMap;
    }

    @Override
    public int getX() {
        return this.levelChunk.getPos().x;
    }

    @Override
    public int getZ() {
        return this.levelChunk.getPos().z;
    }

    private static Field field_LevelChunkSection_nonEmptyBlockCount;
    static {
        try {
            ChunkCode.field_LevelChunkSection_nonEmptyBlockCount = LevelChunkSection.class.getDeclaredField("nonEmptyBlockCount");
            ChunkCode.field_LevelChunkSection_nonEmptyBlockCount.setAccessible(true);
        } catch (final NoSuchFieldException exception) {
            exception.printStackTrace();
        }
    }

    @Override
    public void replaceAllMaterial(final BlockData[] target, final BlockData to) {
        final Set<Block> targetBlocks = new HashSet<>();
        for (final BlockData targetData : target) {
            final BlockState targetState = ((CraftBlockData) targetData).getState();
            targetBlocks.add(targetState.getBlock());
        }
        final BlockState toI = ((CraftBlockData) to).getState();
        for (final LevelChunkSection section : this.levelChunk.getSections()) {
            if (section != null) {
                final PalettedContainer<BlockState> blocks = section.getStates();

                // Fast path: if this section doesn't contain any target blocks in its palette,
                // avoid scanning all 4096 positions.
                final boolean[] hasTarget = new boolean[] { false };
                blocks.count((state, count) -> {
                    if (!hasTarget[0] && state != null && count > 0 && targetBlocks.contains(state.getBlock())) {
                        hasTarget[0] = true;
                    }
                });
                if (!hasTarget[0]) {
                    continue;
                }

                // NOTE: PalettedContainer#count provides (state, count) pairs, not per-block locations.
                // We must iterate all 4096 positions in the section to actually replace blocks.
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        for (int x = 0; x < 16; x++) {
                            BlockState state = blocks.get(x, y, z);
                            if (state == null) {
                                state = Blocks.AIR.defaultBlockState();
                            }

                            if (targetBlocks.contains(state.getBlock())) {
                                state = toI;
                                blocks.getAndSetUnchecked(x, y, z, toI);
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    public org.bukkit.Material getMaterial(final int x, final int y, final int z) {
        return this.getBlockData(x, y, z).getMaterial();
    }

    @Override
    public void setMaterial(final int x, final int y, final int z, final org.bukkit.Material material) {
        this.setBlockData(x, y, z, material.createBlockData());
    }

    @Override
    @Deprecated
    public org.bukkit.block.Biome getBiome(final int x, final int z) {
        return this.getBiome(x, 0, z);
    }

    @Override
    public org.bukkit.block.Biome getBiome(final int x, final int y, final int z) {
        return CraftBiome.minecraftHolderToBukkit(this.levelChunk.getNoiseBiome(x, y, z));
    }

    @Override
    @Deprecated
    public void setBiome(final int x, final int z, final org.bukkit.block.Biome biome) {
        this.setBiome(x, 0, z, biome);
    }

    @Override
    public void setBiome(final int x, final int y, final int z, final org.bukkit.block.Biome biome) {
        this.levelChunk.setBiome(x, y, z, CraftBiome.bukkitToMinecraftHolder(biome));
    }

    @Override
    public boolean hasFluid(final int x, final int y, final int z) {
        return !this.getIBlockData(x, y, z).getFluidState().isEmpty();
    }

    @Override
    public boolean isAir(final int x, final int y, final int z) {
        return this.getIBlockData(x, y, z).isAir();
    }

    @Override
    public int getHighestY(final int x, final int z) {
        return this.levelChunk.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
    }

    public static Status ofStatus(final ChunkStatus chunkStatus) {
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

    @Override
    public Status getStatus() {
        return ChunkCode.ofStatus(this.levelChunk.getPersistedStatus());
    }
}