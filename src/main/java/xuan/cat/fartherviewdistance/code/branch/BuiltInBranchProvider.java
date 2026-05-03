package xuan.cat.fartherviewdistance.code.branch;

import xuan.cat.fartherviewdistance.api.branch.BranchMinecraft;
import xuan.cat.fartherviewdistance.api.branch.BranchPacket;

public final class BuiltInBranchProvider implements BranchProvider {
    private static final String BRANCH_VERSION = "1.21.11";
    private static final BranchResolver.ComparableVersion MIN_VERSION = BranchResolver.ComparableVersion.parse("1.21.10");
    private static final BranchResolver.ComparableVersion MAX_VERSION = BranchResolver.ComparableVersion.parse("1.21.11");

    @Override
    public String id() {
        return "builtin-1.21.11";
    }

    @Override
    public String branchVersion() {
        return BuiltInBranchProvider.BRANCH_VERSION;
    }

    @Override
    public boolean supportsMinecraftVersion(final String minecraftVersion) {
        final BranchResolver.ComparableVersion current = BranchResolver.ComparableVersion.parse(minecraftVersion);
        return current.compareTo(BuiltInBranchProvider.MIN_VERSION) >= 0 && current.compareTo(BuiltInBranchProvider.MAX_VERSION) <= 0;
    }

    @Override
    public BranchMinecraft createMinecraft() {
        return new MinecraftCode();
    }

    @Override
    public BranchPacket createPacket() {
        return new PacketCode();
    }
}
