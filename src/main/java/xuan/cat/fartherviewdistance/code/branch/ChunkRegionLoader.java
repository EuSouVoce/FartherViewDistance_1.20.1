package xuan.cat.fartherviewdistance.code.branch;

import java.lang.reflect.Field;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.mojang.serialization.Codec;

import it.unimi.dsi.fastutil.shorts.ShortList;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.ShortTag;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
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
import net.minecraft.world.level.levelgen.blending.BlendingData.Packed;
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
        return ChunkCode.ofStatus(ChunkStatus.byName(nbt.getString("Status").get()));
    }

    private static Codec<PalettedContainerRO<Holder<Biome>>> makeBiomeCodec(final Registry<Biome> biomeRegistry) {
        return PalettedContainer.codecRO(biomeRegistry.asHolderIdMap(), biomeRegistry.holderByNameCodec(),
                PalettedContainer.Strategy.SECTION_BIOMES, biomeRegistry.getOrThrow(Biomes.PLAINS));
    }

    public static BranchChunk loadChunk(final ServerLevel world, final int chunkX, final int chunkZ,
            final CompoundTag nbt,
            final boolean integralHeightmap) {
        if (nbt.contains("DataVersion")) {
            final int dataVersion = nbt.getInt("DataVersion").get();
            if (!ChunkRegionLoader.JUST_CORRUPT_IT && dataVersion > ChunkRegionLoader.CURRENT_DATA_VERSION) {
                (new RuntimeException("Server attempted to load chunk saved with newer version of minecraft! "
                        + dataVersion + " > " + ChunkRegionLoader.CURRENT_DATA_VERSION)).printStackTrace();
                System.exit(1);
            }
        }

        final ChunkPos chunkPos = new ChunkPos(chunkX, chunkZ);
        final ChunkStatus chunkStatus = (ChunkStatus) nbt.read("Status", ChunkStatus.CODEC).orElse(ChunkStatus.EMPTY);
        final UpgradeData upgradeData = (UpgradeData) nbt.getCompound("UpgradeData")
                .map(nbtItem -> new UpgradeData(nbtItem, world)).orElse(UpgradeData.EMPTY);
        final boolean isLightOn = ((ChunkStatus) nbt.read("Status", ChunkStatus.CODEC).orElse(ChunkStatus.EMPTY))
                .isOrAfter(ChunkStatus.LIGHT)
                && (nbt.get("isLightOn") != null
                        || (nbt.getIntOr("starlight.light_version", -1) == 6)); // Latest version is 9, but we need to
                                                                                // force the light to be loaded by
                                                                                // setting a lower light version.
        final ListTag sectionArrayNBT = nbt.getListOrEmpty("sections");
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
            final CompoundTag sectionNBT = sectionArrayNBT.getCompoundOrEmpty(sectionIndex);
            final byte locationY = sectionNBT.getByteOr("Y", (byte) 0);
            final int sectionY = world.getSectionIndexFromSectionY(locationY);
            if (sectionY >= 0 && sectionY < sections.length) {

                final BlockState[] presetBlockStates = world.chunkPacketBlockController.getPresetBlockStates(world,
                        chunkPos,
                        sectionY);
                final Codec<PalettedContainer<BlockState>> blockStateCodec = presetBlockStates == null
                        ? ChunkRegionLoader.getBlockStateCodec()
                        : PalettedContainer.codecRW(Block.BLOCK_STATE_REGISTRY, BlockState.CODEC,
                                PalettedContainer.Strategy.SECTION_STATES,
                                Blocks.AIR.defaultBlockState(), presetBlockStates);
                final PalettedContainer<BlockState> paletteBlock = (PalettedContainer<BlockState>) sectionNBT
                        .getCompound("block_states")
                        .map(compoundTag1 -> ((PalettedContainer<BlockState>) blockStateCodec
                                .parse(NbtOps.INSTANCE, compoundTag1)
                                .promotePartial(string -> {
                                }).result().orElse(null)))
                        .orElseGet(
                                () -> new PalettedContainer<>(Block.BLOCK_STATE_REGISTRY,
                                        Blocks.AIR.defaultBlockState(),
                                        PalettedContainer.Strategy.SECTION_STATES, presetBlockStates));
                // Biome converter
                final PalettedContainer<Holder<Biome>> paletteBiome = (PalettedContainer<Holder<Biome>>) sectionNBT
                        .getCompound("biomes")
                        .map(compoundTag1 -> ((PalettedContainer<Holder<Biome>>) paletteCodec
                                .parse(NbtOps.INSTANCE, compoundTag1)
                                .promotePartial(string -> {

                                }).result().orElse(null)))
                        .orElseGet(() -> new PalettedContainer<Holder<Biome>>(biomeRegistry.asHolderIdMap(),
                                biomeRegistry.getOrThrow(Biomes.PLAINS), PalettedContainer.Strategy.SECTION_BIOMES,
                                (Holder<Biome> @Nullable []) null));
                final LevelChunkSection chunkSection = new LevelChunkSection(paletteBlock, paletteBiome);
                sections[sectionY] = chunkSection;
            }
        }

        final long inhabitedTime = nbt.getLong("InhabitedTime").get();
        final ChunkType chunkType = chunkStatus.getChunkType();
        final BlendingData.Packed packed = (BlendingData.Packed) nbt.read("blending_data", BlendingData.Packed.CODEC)
                .orElse((Packed) null);
        final BlendingData blendingData = BlendingData.unpack(packed);

        ChunkAccess chunk;
        if (chunkType == ChunkType.LEVELCHUNK) {
            final List<SavedTick<Block>> ListTicksBlock = SavedTick.filterTickListForChunk(
                    (List<SavedTick<Block>>) nbt.read("block_ticks", ChunkRegionLoader.getBlockTicksCodec())
                            .orElse(List.of()),
                    chunkPos);
            final List<SavedTick<Fluid>> ListTicksFluid = SavedTick.filterTickListForChunk(
                    (List<SavedTick<Fluid>>) nbt.read("fluid_ticks", ChunkRegionLoader.getFluidTicksCodec())
                            .orElse(List.of()),
                    chunkPos);

            final LevelChunkTicks<Block> ticksBlock = new LevelChunkTicks<Block>(ListTicksBlock);
            final LevelChunkTicks<Fluid> ticksFluid = new LevelChunkTicks<Fluid>(ListTicksFluid);
            final LevelChunk levelChunk = new LevelChunk(world.getLevel(), chunkPos, upgradeData, ticksBlock,
                    ticksFluid,
                    inhabitedTime, sections, null, blendingData);
            chunk = levelChunk;

            // Block entities
            final List<CompoundTag> blockEntities = nbt.getList("block_entities").stream()
                    .flatMap(ListTag::compoundStream)
                    .toList();
            for (int entityIndex = 0; entityIndex < blockEntities.size(); ++entityIndex) {

                final CompoundTag entityNBT = blockEntities.get(entityIndex);
                final boolean keepPacked = entityNBT.getBoolean("keepPacked").orElse(false);
                if (keepPacked) {
                    chunk.setBlockEntityNbt(entityNBT);
                } else {
                    final BlockPos blockposition = ChunkRegionLoader.getPosFromTag(chunkPos, nbt);
                    if (blockposition.getX() >> 4 == chunkPos.x && blockposition.getZ() >> 4 == chunkPos.z) {
                        final BlockEntity tileentity = ChunkRegionLoader.loadStatic(blockposition,
                                chunk.getBlockState(blockposition), nbt, world.registryAccess());
                        if (tileentity != null) {
                            chunk.setBlockEntity(tileentity);
                        }
                    }
                }

            }
        } else {
            final List<SavedTick<Block>> ListTicksBlock = SavedTick.filterTickListForChunk(
                    (List<SavedTick<Block>>) nbt.read("block_ticks", ChunkRegionLoader.getBlockTicksCodec())
                            .orElse(List.of()),
                    chunkPos);
            final List<SavedTick<Fluid>> ListTicksFluid = SavedTick.filterTickListForChunk(
                    (List<SavedTick<Fluid>>) nbt.read("fluid_ticks", ChunkRegionLoader.getFluidTicksCodec())
                            .orElse(List.of()),
                    chunkPos);
            final ProtoChunk protochunk = new ProtoChunk(chunkPos, upgradeData, sections,
                    (ProtoChunkTicks<Block>) ProtoChunkTicks.load(ListTicksBlock),
                    (ProtoChunkTicks<Fluid>) ProtoChunkTicks.load(ListTicksFluid), world, biomeRegistry, blendingData);
            chunk = protochunk;
            protochunk.setInhabitedTime(inhabitedTime);
            if (nbt.contains("below_zero_retrogen")) {
                final BelowZeroRetrogen tmp = (BelowZeroRetrogen) nbt
                        .read("below_zero_retrogen", BelowZeroRetrogen.CODEC)
                        .orElse((BelowZeroRetrogen) null);
                if (tmp != null)
                    protochunk.setBelowZeroRetrogen(tmp);
            }
            protochunk.setPersistedStatus(chunkStatus);
            if (chunkStatus.isOrAfter(ChunkStatus.FEATURES)) {
                protochunk.setLightEngine(lightEngine);
            }
        }
        chunk.setLightCorrect(isLightOn);

        // Heightmaps
        final CompoundTag heightmapsNBT = nbt.getCompoundOrEmpty("Heightmaps");
        final EnumSet<Heightmap.Types> enumHeightmapType = EnumSet.noneOf(Heightmap.Types.class);
        for (final Heightmap.Types heightmapTypes : chunk.getPersistedStatus().heightmapsAfter()) {
            final String serializationKey = heightmapTypes.getSerializationKey();
            if (heightmapsNBT.contains(serializationKey)) {
                chunk.setHeightmap(heightmapTypes, heightmapsNBT.getLongArray(serializationKey).get());
            } else {
                enumHeightmapType.add(heightmapTypes);
            }
        }
        if (integralHeightmap) {
            Heightmap.primeHeightmaps(chunk, enumHeightmapType);
        }

        final ListTag processListNBT = nbt.getListOrEmpty("PostProcessing");
        for (int indexList = 0; indexList < processListNBT.size(); ++indexList) {
            final ListTag processNBT = processListNBT.getListOrEmpty(indexList);
            for (int index = 0; index < processNBT.size(); ++index) {
                chunk.addPackedPostProcess(ShortList.of(processNBT.getShort(index).get()), indexList);
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

    @Nullable
    public static BlockEntity loadStatic(final BlockPos pos, final BlockState state, final CompoundTag tag,
            final HolderLookup.Provider registries) {
        final BlockEntityType<?> blockEntityType = tag.read("id", ChunkRegionLoader.getBlockEntityTypeCodec())
                .orElse(null);
        if (blockEntityType == null) {
            return null;
        }
        BlockEntity blockEntity;
        try {
            blockEntity = (BlockEntity) blockEntityType.create(pos, state);
        } catch (final Throwable var8) {
            return null;
        }
        try {
            blockEntity.loadWithComponents(tag, registries);
            return blockEntity;
        } catch (final Throwable var9) {
            return null;
        }
    }

    public static BlockPos getPosFromTag(final ChunkPos chunkPos, final CompoundTag tag) {
        int intOr = tag.getIntOr("x", 0);
        final int intOr1 = tag.getIntOr("y", 0);
        int intOr2 = tag.getIntOr("z", 0);
        if (chunkPos != null) {
            final int sectionPosCoord = SectionPos.blockToSectionCoord(intOr);
            final int sectionPosCoord1 = SectionPos.blockToSectionCoord(intOr2);
            if (sectionPosCoord != chunkPos.x || sectionPosCoord1 != chunkPos.z) {
                intOr = chunkPos.getBlockX(SectionPos.sectionRelative(intOr));
                intOr2 = chunkPos.getBlockZ(SectionPos.sectionRelative(intOr2));
            }
        }

        return new BlockPos(intOr, intOr1, intOr2);
    }

    private static Codec<BlockEntityType<?>> getBlockEntityTypeCodec() {
        Field field = null;
        try {
            field = BlockEntity.class.getDeclaredField("TYPE_CODEC");
            field.setAccessible(true);
            return (Codec<BlockEntityType<?>>) field.get(null);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Codec<List<SavedTick<Fluid>>> getFluidTicksCodec() {
        Field field = null;
        try {
            field = SerializableChunkData.class.getDeclaredField("FLUID_TICKS_CODEC");
            field.setAccessible(true);
            return (Codec<List<SavedTick<Fluid>>>) field.get(null);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Codec<List<SavedTick<Block>>> getBlockTicksCodec() {
        Field field = null;
        try {
            field = SerializableChunkData.class.getDeclaredField("BLOCK_TICKS_CODEC");
            field.setAccessible(true);
            return (Codec<List<SavedTick<Block>>>) field.get(null);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Codec<PalettedContainer<BlockState>> getBlockStateCodec() {
        Field field = null;
        try {
            field = SerializableChunkData.class.getDeclaredField("BLOCK_STATE_CODEC");
            field.setAccessible(true);
            return (Codec<PalettedContainer<BlockState>>) field.get(null);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static BranchChunkLight loadLight(final ServerLevel world, final CompoundTag nbt) {
        // Data version checker
        if (nbt.contains("DataVersion")) {
            final int dataVersion = nbt.getInt("DataVersion").get();
            if (!ChunkRegionLoader.JUST_CORRUPT_IT && dataVersion > ChunkRegionLoader.CURRENT_DATA_VERSION) {
                (new RuntimeException("Server attempted to load chunk saved with newer version of minecraft! "
                        + dataVersion + " > " + ChunkRegionLoader.CURRENT_DATA_VERSION)).printStackTrace();
                System.exit(1);
            }
        }

        final boolean isLightOn = ((ChunkStatus) nbt.read("Status", ChunkStatus.CODEC).orElse(ChunkStatus.EMPTY))
                .isOrAfter(ChunkStatus.LIGHT)
                && (nbt.get("isLightOn") != null || nbt.getIntOr("starlight.light_version", -1) == 6);
        final boolean hasSkyLight = world.dimensionType().hasSkyLight();
        final ListTag sectionArrayNBT = nbt.getList("sections").get();
        final ChunkLightCode chunkLight = new ChunkLightCode(world);
        for (int sectionIndex = 0; sectionIndex < sectionArrayNBT.size(); ++sectionIndex) {
            final CompoundTag sectionNBT = sectionArrayNBT.getCompoundOrEmpty(sectionIndex);
            final byte locationY = sectionNBT.getByteOr("Y", (byte) 0);
            if (isLightOn) {
                if (sectionNBT.contains("BlockLight")) {
                    chunkLight.setBlockLight(locationY, sectionNBT.getByteArray("BlockLight").get());
                }
                if (hasSkyLight) {
                    if (sectionNBT.contains("SkyLight")) {
                        chunkLight.setSkyLight(locationY, sectionNBT.getByteArray("SkyLight").get());
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
