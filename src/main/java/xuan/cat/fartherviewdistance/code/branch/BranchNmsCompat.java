package xuan.cat.fartherviewdistance.code.branch;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import net.minecraft.core.Holder;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.LevelChunk;

public final class BranchNmsCompat {
    private static final Method CHUNK_POS_X_METHOD = BranchNmsCompat.method(ChunkPos.class, "x");
    private static final Method CHUNK_POS_Z_METHOD = BranchNmsCompat.method(ChunkPos.class, "z");
    private static final Field CHUNK_POS_X_FIELD = BranchNmsCompat.field(ChunkPos.class, "x");
    private static final Field CHUNK_POS_Z_FIELD = BranchNmsCompat.field(ChunkPos.class, "z");

    private static final Method LEVEL_CHUNK_SET_NOISE_BIOME = BranchNmsCompat.method(LevelChunk.class,
            "setNoiseBiome", int.class, int.class, int.class, Holder.class);
    private static final Method LEVEL_CHUNK_SET_BIOME = BranchNmsCompat.method(LevelChunk.class, "setBiome",
            int.class, int.class, int.class, Holder.class);

    private BranchNmsCompat() {
    }

    public static int chunkX(final ChunkPos chunkPos) {
        return BranchNmsCompat.chunkCoordinate(chunkPos, BranchNmsCompat.CHUNK_POS_X_METHOD,
                BranchNmsCompat.CHUNK_POS_X_FIELD);
    }

    public static int chunkZ(final ChunkPos chunkPos) {
        return BranchNmsCompat.chunkCoordinate(chunkPos, BranchNmsCompat.CHUNK_POS_Z_METHOD,
                BranchNmsCompat.CHUNK_POS_Z_FIELD);
    }

    public static void setBiome(final LevelChunk chunk, final int x, final int y, final int z,
            final Holder<Biome> biome) {
        final Method method = BranchNmsCompat.LEVEL_CHUNK_SET_NOISE_BIOME != null
                ? BranchNmsCompat.LEVEL_CHUNK_SET_NOISE_BIOME
                : BranchNmsCompat.LEVEL_CHUNK_SET_BIOME;
        if (method == null) {
            throw new IllegalStateException("No compatible LevelChunk biome setter found");
        }

        try {
            method.invoke(chunk, x, y, z, biome);
        } catch (final ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not set chunk biome", exception);
        }
    }

    private static int chunkCoordinate(final ChunkPos chunkPos, final Method method, final Field field) {
        try {
            if (method != null) {
                return ((Integer) method.invoke(chunkPos)).intValue();
            }
            if (field != null) {
                return field.getInt(chunkPos);
            }
        } catch (final ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not read chunk coordinate", exception);
        }
        throw new IllegalStateException("No compatible ChunkPos coordinate accessor found");
    }

    private static Method method(final Class<?> type, final String name, final Class<?>... parameterTypes) {
        try {
            final Method method = type.getDeclaredMethod(name, parameterTypes);
            method.setAccessible(true);
            return method;
        } catch (final NoSuchMethodException exception) {
            return null;
        }
    }

    private static Field field(final Class<?> type, final String name) {
        try {
            final Field field = type.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (final NoSuchFieldException exception) {
            return null;
        }
    }
}
