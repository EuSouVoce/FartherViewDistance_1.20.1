package xuan.cat.fartherviewdistance.code.branch.v120_4;

import java.util.function.Consumer;
import org.bukkit.entity.Player;
import xuan.cat.fartherviewdistance.api.branch.BranchChunk;
import xuan.cat.fartherviewdistance.api.branch.BranchChunkLight;
import xuan.cat.fartherviewdistance.api.branch.BranchPacket;

public final class Branch_120_4_Packet implements BranchPacket {

  @Override
  public void sendViewDistance(Player player, int viewDistance) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void sendUnloadChunk(Player player, int chunkX, int chunkZ) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Consumer<Player> sendChunkAndLight(
    BranchChunk chunk,
    BranchChunkLight light,
    boolean needTile,
    Consumer<Integer> consumeTraffic
  ) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void sendKeepAlive(Player player, long id) {
    throw new UnsupportedOperationException();
  }
}
