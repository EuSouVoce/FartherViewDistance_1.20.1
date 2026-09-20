package xuan.cat.fartherviewdistance.code.branch;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.VarInt;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacketData;

public final class PacketHandleLightUpdateCode {

    private PacketHandleLightUpdateCode() {
    }

    private static void saveBitSet(final byte[][] nibbleArrays, final int index, final BitSet notEmpty, final BitSet isEmpty, final List<byte[]> list, final Consumer<Integer> consumeTraffic) {
        final byte[] nibbleArray = nibbleArrays[index];
        if (nibbleArray != ChunkLightCode.EMPTY) {
            if (nibbleArray == null) {
                isEmpty.set(index);
            }
            else {
                notEmpty.set(index);
                list.add(nibbleArray);
                consumeTraffic.accept(
                        nibbleArray.length + VarInt.getByteSize(nibbleArray.length) // byte[] is written as length (VarInt) and all the bytes
                );
            }
        }

        // BitSet#size() returns "number of bits of space actually in use by this BitSet to represent bit values", so we multiply it by 8 (a byte is guaranteed to be equal to 8 bits in any JVM)
        consumeTraffic.accept(notEmpty.size() * 8);
        consumeTraffic.accept(isEmpty.size() * 8);
    }

    public static <E> void writeCollection(FriendlyByteBuf buf, Collection<E> collection, BiConsumer<FriendlyByteBuf, E> writer) {
        int size = collection.size();
        buf.writeVarInt(size);

        for (E element : collection) {
            writer.accept(buf, element);
        }
    }

    public static ClientboundLightUpdatePacketData createLightData(final ChunkLightCode light, final Consumer<Integer> consumeTraffic) {
        final List<byte[]> dataSky = new ArrayList<>();
        final List<byte[]> dataBlock = new ArrayList<>();
        final BitSet notSkyEmpty = new BitSet();
        final BitSet notBlockEmpty = new BitSet();
        final BitSet isSkyEmpty = new BitSet();
        final BitSet isBlockEmpty = new BitSet();

        for (int index = 0; index < light.getArrayLength(); ++index) {
            saveBitSet(light.getSkyLights(), index, notSkyEmpty, isSkyEmpty, dataSky, consumeTraffic);
            saveBitSet(light.getBlockLights(), index, notBlockEmpty, isBlockEmpty, dataBlock, consumeTraffic);
        }

        return new ClientboundLightUpdatePacketData(notSkyEmpty, notBlockEmpty, isSkyEmpty, isBlockEmpty, dataSky, dataBlock);
    }
}