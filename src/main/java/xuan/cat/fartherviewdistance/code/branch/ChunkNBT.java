package xuan.cat.fartherviewdistance.code.branch;

import net.minecraft.nbt.CompoundTag;
import xuan.cat.fartherviewdistance.api.branch.BranchNBT;

public final class ChunkNBT implements BranchNBT {

    protected CompoundTag tag;

    public ChunkNBT() {
        this.tag = new CompoundTag();
    }

    public ChunkNBT(final CompoundTag tag) {
        this.tag = tag;
    }

    public CompoundTag getNMSTag() {
        return this.tag;
    }

    @Override
    public String toString() {
        return this.tag.toString();
    }
}
