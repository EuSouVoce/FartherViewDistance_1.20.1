package xuan.cat.fartherviewdistance.code.branch;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;

import it.unimi.dsi.fastutil.shorts.ShortList;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.ShortTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.status.ChunkType;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
import net.minecraft.world.level.levelgen.BelowZeroRetrogen;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.blending.BlendingData;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.ticks.LevelChunkTicks;
import net.minecraft.world.ticks.ProtoChunkTicks;
import net.minecraft.world.ticks.SavedTick;
import xuan.cat.fartherviewdistance.api.branch.BranchChunk;
import xuan.cat.fartherviewdistance.api.branch.BranchChunkLight;

/**
 * @see SerializableChunkData
 */
public final class ChunkRegionLoader {
    private static final int CURRENT_DATA_VERSION = SharedConstants.getCurrentVersion().getDataVersion().getVersion();
    private static final boolean JUST_CORRUPT_IT = Boolean.getBoolean("Paper.ignoreWorldDataVersion");

    public static BranchChunk.Status loadStatus(final CompoundTag nbt) {
        return ChunkCode.ofStatus(ChunkStatus.byName(nbt.getString("Status")));
    }

    private static Codec<PalettedContainerRO<Holder<Biome>>> makeBiomeCodec(final Registry<Biome> biomeRegistry) {
        return PalettedContainer.codecRO(biomeRegistry.asHolderIdMap(), biomeRegistry.holderByNameCodec(),
                PalettedContainer.Strategy.SECTION_BIOMES, biomeRegistry.getOrThrow(Biomes.PLAINS));
    }

    @SuppressWarnings("unchecked")
    public static BranchChunk loadChunk(final ServerLevel world, final int chunkX, final int chunkZ,
            final CompoundTag nbt,
            final boolean integralHeightmap) {
        if (nbt.contains("DataVersion", 99)) {
            final int dataVersion = nbt.getInt("DataVersion");
            if (!ChunkRegionLoader.JUST_CORRUPT_IT && dataVersion > ChunkRegionLoader.CURRENT_DATA_VERSION) {
                (new RuntimeException("Server attempted to load chunk saved with newer version of minecraft! "
                        + dataVersion + " > " + ChunkRegionLoader.CURRENT_DATA_VERSION)).printStackTrace();
                System.exit(1);
            }
        }

        final ChunkPos chunkPos = new ChunkPos(chunkX, chunkZ);
        final UpgradeData upgradeData = nbt.contains("UpgradeData", 10)
                ? new UpgradeData(nbt.getCompound("UpgradeData"), world)
                : UpgradeData.EMPTY;
        final boolean isLightOn = Objects.requireNonNullElse(ChunkStatus.byName(nbt.getString("Status")),
                ChunkStatus.EMPTY).isOrAfter(ChunkStatus.LIGHT)
                && (nbt.get("isLightOn") != null || nbt.getInt("starlight.light_version") == 6); // Latest version is 9,
                                                                                                 // but we need to force
                                                                                                 // the light to loaded
        final ListTag sectionArrayNBT = nbt.getList("sections", 10);
        final int sectionsCount = world.getSectionsCount();
        final LevelChunkSection[] sections = new LevelChunkSection[sectionsCount];
        final ServerChunkCache chunkSource = world.getChunkSource();
        final LevelLightEngine lightEngine = chunkSource.getLightEngine();
        final Registry<Biome> biomeRegistry = world.registryAccess().lookupOrThrow(Registries.BIOME);
        final Codec<PalettedContainer<Holder<Biome>>> paletteCodec = PalettedContainer.codecRW(
                biomeRegistry.asHolderIdMap(),
                biomeRegistry.holderByNameCodec(), PalettedContainer.Strategy.SECTION_BIOMES,
                biomeRegistry.getOrThrow(Biomes.PLAINS), null);
        for (int sectionIndex = 0; sectionIndex < sectionArrayNBT.size(); ++sectionIndex) {
            final CompoundTag sectionNBT = sectionArrayNBT.getCompound(sectionIndex);
            final byte locationY = sectionNBT.getByte("Y");
            final int sectionY = world.getSectionIndexFromSectionY(locationY);
            if (sectionY >= 0 && sectionY < sections.length) {
                // Block converter
                PalettedContainer<BlockState> paletteBlock;
                if (sectionNBT.contains("block_states", 10)) {
                    paletteBlock = SerializableChunkData.BLOCK_STATE_CODEC
                            .parse(NbtOps.INSTANCE, sectionNBT.getCompound("block_states")).promotePartial(sx -> {
                            }).getOrThrow();
                } else {
                    paletteBlock = new PalettedContainer<>(Block.BLOCK_STATE_REGISTRY, Blocks.AIR.defaultBlockState(),
                            PalettedContainer.Strategy.SECTION_STATES);
                }

                // Biome converter
                PalettedContainer<Holder<Biome>> paletteBiome;
                if (sectionNBT.contains("biomes", 10)) {
                    paletteBiome = paletteCodec.parse(NbtOps.INSTANCE, sectionNBT.getCompound("biomes"))
                            .promotePartial(sx -> {
                            }).getOrThrow();
                } else {
                    paletteBiome = new PalettedContainer<>(biomeRegistry.asHolderIdMap(),
                            biomeRegistry.getOrThrow(Biomes.PLAINS), PalettedContainer.Strategy.SECTION_BIOMES, null);
                }

                final LevelChunkSection chunkSection = new LevelChunkSection(paletteBlock, paletteBiome);
                sections[sectionY] = chunkSection;
            }
        }

        final long inhabitedTime = nbt.getLong("InhabitedTime");
        final ChunkType chunkType = SerializableChunkData.getChunkTypeFromTag(nbt);
        BlendingData blendingData;
        if (nbt.contains("blending_data", 10)) {
            blendingData = BlendingData.unpack(BlendingData.Packed.CODEC
                    .parse(new Dynamic<>(NbtOps.INSTANCE, nbt.getCompound("blending_data"))).resultOrPartial(sx -> {
                    }).orElse(null));
        } else {
            blendingData = null;
        }

        ChunkAccess chunk;
        if (chunkType == ChunkType.LEVELCHUNK) {
            final LevelChunkTicks<Block> ticksBlock = LevelChunkTicks.load(nbt.getList("block_ticks", 10),
                    sx -> BuiltInRegistries.BLOCK.getOptional(ResourceLocation.tryParse(sx)), chunkPos);
            final LevelChunkTicks<Fluid> ticksFluid = LevelChunkTicks.load(nbt.getList("fluid_ticks", 10),
                    sx -> BuiltInRegistries.FLUID.getOptional(ResourceLocation.tryParse(sx)), chunkPos);
            final LevelChunk levelChunk = new LevelChunk(world.getLevel(), chunkPos, upgradeData, ticksBlock,
                    ticksFluid,
                    inhabitedTime, sections, null, blendingData);
            chunk = levelChunk;

            // Block entities
            final ListTag blockEntities = nbt.getList("block_entities", 10);
            for (int entityIndex = 0; entityIndex < blockEntities.size(); ++entityIndex) {
                final CompoundTag entityNBT = blockEntities.getCompound(entityIndex);
                final boolean keepPacked = entityNBT.getBoolean("keepPacked");
                if (keepPacked) {
                    chunk.setBlockEntityNbt(entityNBT);
                } else {
                    final BlockPos blockposition = BlockEntity.getPosFromTag(nbt);
                    if (blockposition.getX() >> 4 == chunkPos.x && blockposition.getZ() >> 4 == chunkPos.z) {
                        final BlockEntity tileentity = BlockEntity.loadStatic(blockposition,
                                chunk.getBlockState(blockposition), nbt, world.registryAccess());
                        if (tileentity != null) {
                            chunk.setBlockEntity(tileentity);
                        }
                    }
                }
            }
        } else {
            final List<SavedTick<Block>> ticksBlock = SavedTick.loadTickList(nbt.getList("block_ticks", 10),
                    s1 -> BuiltInRegistries.BLOCK.getOptional(ResourceLocation.tryParse(s1)), chunkPos);
            final List<SavedTick<Fluid>> ticksFluid = SavedTick.loadTickList(nbt.getList("fluid_ticks", 10),
                    s1 -> BuiltInRegistries.FLUID.getOptional(ResourceLocation.tryParse(s1)), chunkPos);
            final ProtoChunk protochunk = new ProtoChunk(chunkPos, upgradeData, sections,
                    (ProtoChunkTicks<Block>) ticksBlock,
                    (ProtoChunkTicks<Fluid>) ticksFluid, world, biomeRegistry, blendingData);
            chunk = protochunk;
            protochunk.setInhabitedTime(inhabitedTime);
            if (nbt.contains("below_zero_retrogen", 10)) {
                BelowZeroRetrogen.CODEC.parse(new Dynamic<>(NbtOps.INSTANCE, nbt.getCompound("below_zero_retrogen")))
                        .resultOrPartial(sx -> {
                        }).ifPresent(protochunk::setBelowZeroRetrogen);
            }

            final ChunkStatus chunkStatus = ChunkStatus.byName(nbt.getString("Status"));
            protochunk.setPersistedStatus(chunkStatus);
            if (chunkStatus.isOrAfter(ChunkStatus.FEATURES)) {
                protochunk.setLightEngine(lightEngine);
            }
        }
        chunk.setLightCorrect(isLightOn);

        // Heightmaps
        final CompoundTag heightmapsNBT = nbt.getCompound("Heightmaps");
        final EnumSet<Heightmap.Types> enumHeightmapType = EnumSet.noneOf(Heightmap.Types.class);
        for (final Heightmap.Types heightmapTypes : chunk.getPersistedStatus().heightmapsAfter()) {
            final String serializationKey = heightmapTypes.getSerializationKey();
            if (heightmapsNBT.contains(serializationKey, 12)) {
                chunk.setHeightmap(heightmapTypes, heightmapsNBT.getLongArray(serializationKey));
            } else {
                enumHeightmapType.add(heightmapTypes);
            }
        }
        if (integralHeightmap) {
            Heightmap.primeHeightmaps(chunk, enumHeightmapType);
        }

        final ListTag processListNBT = nbt.getList("PostProcessing", 9);
        for (int indexList = 0; indexList < processListNBT.size(); ++indexList) {
            final ListTag processNBT = processListNBT.getList(indexList);
            for (int index = 0; index < processNBT.size(); ++index) {
                chunk.addPackedPostProcess(ShortList.of(processNBT.getShort(index)), indexList);
            }
        }

        if (chunkType == ChunkType.LEVELCHUNK) {
            return new ChunkCode(world, (LevelChunk) chunk);
        } else {
            final ProtoChunk protoChunk = (ProtoChunk) chunk;
            return new ChunkCode(world, new LevelChunk(world, protoChunk, v -> {
            }));
        }
    }

    public static BranchChunkLight loadLight(final ServerLevel world, final CompoundTag nbt) {
        // Data version checker
        if (nbt.contains("DataVersion", 99)) {
            final int dataVersion = nbt.getInt("DataVersion");
            if (!ChunkRegionLoader.JUST_CORRUPT_IT && dataVersion > ChunkRegionLoader.CURRENT_DATA_VERSION) {
                (new RuntimeException("Server attempted to load chunk saved with newer version of minecraft! "
                        + dataVersion + " > " + ChunkRegionLoader.CURRENT_DATA_VERSION)).printStackTrace();
                System.exit(1);
            }
        }

        final boolean isLightOn = Objects
                .requireNonNullElse(ChunkStatus.byName(nbt.getString("Status")), ChunkStatus.EMPTY)
                .isOrAfter(ChunkStatus.LIGHT)
                && (nbt.get("isLightOn") != null || nbt.getInt("starlight.light_version") == 6);
        final boolean hasSkyLight = world.dimensionType().hasSkyLight();
        final ListTag sectionArrayNBT = nbt.getList("sections", 10);
        final ChunkLightCode chunkLight = new ChunkLightCode(world);
        for (int sectionIndex = 0; sectionIndex < sectionArrayNBT.size(); ++sectionIndex) {
            final CompoundTag sectionNBT = sectionArrayNBT.getCompound(sectionIndex);
            final byte locationY = sectionNBT.getByte("Y");
            if (isLightOn) {
                if (sectionNBT.contains("BlockLight", 7)) {
                    chunkLight.setBlockLight(locationY, sectionNBT.getByteArray("BlockLight"));
                }
                if (hasSkyLight) {
                    if (sectionNBT.contains("SkyLight", 7)) {
                        chunkLight.setSkyLight(locationY, sectionNBT.getByteArray("SkyLight"));
                    }
                }
            }
        }

        return chunkLight;
    }

    public static CompoundTag saveChunk(final ServerLevel world, final ChunkAccess chunk, final ChunkLightCode light,
            final List<Runnable> asyncRunnable) {
        final int minSection = world.getMinSectionY() - 1;// WorldUtil.getMinLightSection();
        final ChunkPos chunkPos = chunk.getPos();
        final CompoundTag nbt = NbtUtils.addCurrentDataVersion(new CompoundTag());
        nbt.putInt("xPos", chunkPos.x);
        nbt.putInt("yPos", chunk.getMinSectionY());
        nbt.putInt("zPos", chunkPos.z);
        nbt.putLong("LastUpdate", world.getGameTime());
        nbt.putLong("InhabitedTime", chunk.getInhabitedTime());
        nbt.putString("Status", chunk.getPersistedStatus().getName());
        final BlendingData blendingData = chunk.getBlendingData();
        if (blendingData != null) {
            BlendingData.Packed.CODEC.encodeStart(NbtOps.INSTANCE, blendingData.pack()).resultOrPartial(sx -> {
            }).ifPresent(nbtData -> nbt.put("blending_data", nbtData));
        }

        final BelowZeroRetrogen belowZeroRetrogen = chunk.getBelowZeroRetrogen();
        if (belowZeroRetrogen != null) {
            BelowZeroRetrogen.CODEC.encodeStart(NbtOps.INSTANCE, belowZeroRetrogen).resultOrPartial(sx -> {
            }).ifPresent(nbtData -> nbt.put("below_zero_retrogen", nbtData));
        }

        final LevelChunkSection[] chunkSections = chunk.getSections();
        final ListTag sectionArrayNBT = new ListTag();
        final ThreadedLevelLightEngine lightEngine = world.getChunkSource().getLightEngine();

        // Biome parser
        final Registry<Biome> biomeRegistry = world.registryAccess().lookupOrThrow(Registries.BIOME);
        final Codec<PalettedContainerRO<Holder<Biome>>> paletteCodec = ChunkRegionLoader
                .makeBiomeCodec(biomeRegistry);
        boolean lightCorrect = false;

        for (int locationY = lightEngine.getMinLightSection(); locationY < lightEngine
                .getMaxLightSection(); ++locationY) {
            final int sectionY = chunk.getSectionIndexFromSectionY(locationY);
            final boolean inSections = sectionY >= 0 && sectionY < chunkSections.length;
            DataLayer blockNibble;
            DataLayer skyNibble;

            blockNibble = chunk.starlight$getBlockNibbles()[locationY - minSection].toVanillaNibble();
            skyNibble = chunk.starlight$getSkyNibbles()[locationY - minSection].toVanillaNibble();

            if (inSections || blockNibble != null || skyNibble != null) {
                final CompoundTag sectionNBT = new CompoundTag();
                if (inSections) {
                    final LevelChunkSection chunkSection = chunkSections[sectionY];
                    asyncRunnable.add(() -> {
                        sectionNBT.put("block_states", SerializableChunkData.BLOCK_STATE_CODEC
                                .encodeStart(NbtOps.INSTANCE, chunkSection.getStates()).getOrThrow());
                        sectionNBT.put("biomes",
                                paletteCodec.encodeStart(NbtOps.INSTANCE, chunkSection.getBiomes()).getOrThrow());
                    });
                }

                if (blockNibble != null) {
                    if (!blockNibble.isEmpty()) {
                        if (light != null) {
                            light.setBlockLight(locationY, blockNibble.getData());
                        } else {
                            sectionNBT.putByteArray("BlockLight", blockNibble.getData());
                            lightCorrect = true;
                        }
                    }
                }

                if (skyNibble != null) {
                    if (!skyNibble.isEmpty()) {
                        if (light != null) {
                            light.setSkyLight(locationY, skyNibble.getData());
                        } else {
                            sectionNBT.putByteArray("SkyLight", skyNibble.getData());
                            lightCorrect = true;
                        }
                    }
                }

                // Add inSections to ensure asyncRunnable wont produce errors
                if (!sectionNBT.isEmpty() || inSections) {
                    sectionNBT.putByte("Y", (byte) locationY);
                    sectionArrayNBT.add(sectionNBT);
                }
            }
        }
        nbt.put("sections", sectionArrayNBT);

        if (lightCorrect) {
            nbt.putInt("starlight.light_version", 6); // Older version to force the paper to render the light.
            nbt.putBoolean("isLightOn", true);
        }

        // Block entities
        final ListTag blockEntitiesNBT = new ListTag();
        for (final BlockPos blockPos : chunk.getBlockEntitiesPos()) {
            final CompoundTag blockEntity = chunk.getBlockEntityNbtForSaving(blockPos, world.registryAccess());
            if (blockEntity != null) {
                blockEntitiesNBT.add(blockEntity);
            }
        }
        nbt.put("block_entities", blockEntitiesNBT);

        if (chunk.getPersistedStatus().getChunkType() == ChunkType.PROTOCHUNK) { // Not Generated yet, ignore it
        }

        /*
         * final long gameTime = world.getLevelData().getGameTime();
         * ChunkAccess.PackedTicks tickSchedulers =
         * chunk.getTicksForSerialization(gameTime);
         * //nbt.put("block_ticks", tickSchedulers.blocks().save(gameTime, (block) ->
         * BuiltInRegistries.BLOCK.getKey(block).toString()));
         * //nbt.put("fluid_ticks", tickSchedulers.fluids().save(gameTime, (fluid) ->
         * BuiltInRegistries.FLUID.getKey(fluid).toString()));
         */
        final ShortList[] packOffsetList = chunk.getPostProcessing();
        final ListTag packOffsetsNBT = new ListTag();
        for (final ShortList shortlist : packOffsetList) {
            final ListTag packsNBT = new ListTag();
            if (shortlist != null) {
                for (final Short shortData : shortlist) {
                    packsNBT.add(ShortTag.valueOf(shortData));
                }
            }
            packOffsetsNBT.add(packsNBT);
        }
        nbt.put("PostProcessing", packOffsetsNBT);

        // Heightmaps
        final CompoundTag heightmapsNBT = new CompoundTag();
        for (final Map.Entry<Heightmap.Types, Heightmap> entry : chunk.getHeightmaps()) {
            if (chunk.getPersistedStatus().heightmapsAfter().contains(entry.getKey())) {
                heightmapsNBT.put(entry.getKey().getSerializationKey(),
                        new LongArrayTag(entry.getValue().getRawData()));
            }
        }
        nbt.put("Heightmaps", heightmapsNBT);

        return nbt;
    }
}
