package xuan.cat.fartherviewdistance.code.branch;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;

public final class PacketHandleLightUpdateCode {
    public PacketHandleLightUpdateCode() {
    }

    private static void saveBitSet(final byte[][] nibbleArrays, final int index, final BitSet notEmpty,
            final BitSet isEmpty,
            final List<byte[]> list) {
        final byte[] nibbleArray = nibbleArrays[index];
        if (nibbleArray != ChunkLightCode.EMPTY) {
            if (nibbleArray == null) {
                isEmpty.set(index);
            } else {
                notEmpty.set(index);
                list.add(nibbleArray);
            }
        }
    }

    public void write(final FriendlyByteBuf serializer, final ChunkLightCode light) {
        final List<byte[]> dataSky = new ArrayList<>();
        final List<byte[]> dataBlock = new ArrayList<>();
        final BitSet notSkyEmpty = new BitSet();
        final BitSet notBlockEmpty = new BitSet();
        final BitSet isSkyEmpty = new BitSet();
        final BitSet isBlockEmpty = new BitSet();

        for (int index = 0; index < light.getArrayLength(); ++index) {
            PacketHandleLightUpdateCode.saveBitSet(light.getSkyLights(), index, notSkyEmpty, isSkyEmpty, dataSky);
            PacketHandleLightUpdateCode.saveBitSet(light.getBlockLights(), index, notBlockEmpty, isBlockEmpty,
                    dataBlock);
        }

        serializer.writeBitSet(notSkyEmpty);
        serializer.writeBitSet(notBlockEmpty);
        serializer.writeBitSet(isSkyEmpty);
        serializer.writeBitSet(isBlockEmpty);
        serializer.writeCollection(dataSky, RegistryFriendlyByteBuf::writeByteArray);
        serializer.writeCollection(dataBlock, RegistryFriendlyByteBuf::writeByteArray);
    }
}