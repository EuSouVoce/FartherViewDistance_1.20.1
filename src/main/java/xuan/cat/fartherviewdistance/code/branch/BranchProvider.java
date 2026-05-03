package xuan.cat.fartherviewdistance.code.branch;

import xuan.cat.fartherviewdistance.api.branch.BranchMinecraft;
import xuan.cat.fartherviewdistance.api.branch.BranchPacket;

public interface BranchProvider {
    String id();

    String branchVersion();

    boolean supportsMinecraftVersion(String minecraftVersion);

    BranchMinecraft createMinecraft();

    BranchPacket createPacket();
}
