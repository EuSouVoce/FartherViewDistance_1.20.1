package xuan.cat.fartherviewdistance.code.branch;

import java.util.Arrays;

import org.bukkit.World;
import org.bukkit.craftbukkit.CraftWorld;

import net.minecraft.server.level.ServerLevel;
import xuan.cat.fartherviewdistance.api.branch.BranchChunkLight;

public final class ChunkLightCode implements BranchChunkLight {
    public static final byte[] EMPTY = new byte[0];

    private final ServerLevel worldServer;
    private final byte[][] blockLights;
    private final byte[][] skyLights;

    public ChunkLightCode(final World world) {
        this(((CraftWorld) world).getHandle());
    }

    public ChunkLightCode(final ServerLevel worldServer) {
        this(worldServer, new byte[worldServer.getSectionsCount() + 2][],
                new byte[worldServer.getSectionsCount() + 2][]);
    }

    public ChunkLightCode(final ServerLevel worldServer, final byte[][] blockLights, final byte[][] skyLights) {
        this.worldServer = worldServer;
        this.blockLights = blockLights;
        this.skyLights = skyLights;
        Arrays.fill(blockLights, ChunkLightCode.EMPTY);
        Arrays.fill(skyLights, ChunkLightCode.EMPTY);
    }

    public static int indexFromSectionY(final ServerLevel worldServer, final int sectionY) {
        return sectionY - worldServer.getMinSectionY() + 1;
    }

    public ServerLevel getWorldServer() {
        return this.worldServer;
    }

    public int getArrayLength() {
        return this.blockLights.length;
    }

    public void setBlockLight(final int sectionY, final byte[] blockLight) {
        this.blockLights[ChunkLightCode.indexFromSectionY(this.worldServer, sectionY)] = blockLight;
    }

    public void setSkyLight(final int sectionY, final byte[] skyLight) {
        this.skyLights[ChunkLightCode.indexFromSectionY(this.worldServer, sectionY)] = skyLight;
    }

    public byte[] getBlockLight(final int sectionY) {
        return this.blockLights[ChunkLightCode.indexFromSectionY(this.worldServer, sectionY)];
    }

    public byte[] getSkyLight(final int sectionY) {
        return this.skyLights[ChunkLightCode.indexFromSectionY(this.worldServer, sectionY)];
    }

    public byte[][] getBlockLights() {
        return this.blockLights;
    }

    public byte[][] getSkyLights() {
        return this.skyLights;
    }
}