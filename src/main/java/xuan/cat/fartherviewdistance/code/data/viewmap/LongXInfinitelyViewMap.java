package xuan.cat.fartherviewdistance.code.data.viewmap;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.command.CommandSender;

/**
 * 可以無限增加的視圖計算器
 */
public final class LongXInfinitelyViewMap extends ViewMap {
    /*
    每位玩家都有一個 long 陣列
    最高 255 * 255 (因為求奇數)
        0 表示等待中
        1 表示已發送區塊
    (255 - 1) / 2 = 127 所以實際上最遠只能擴充 63 個視野距離

    每個 long 的最後一位數用於其他資料標記

    long[].length = 255
           long                                                                   long                                                                    long                                                                     long
                 0        8        16       24       32       40       48       56       64       72       80       88       96       104      112      120        128      136      144      152      160      168      176      184      192      200      208      216      224      232      240      248
                |--------|--------|--------|--------|--------|--------|--------|--------|--------|--------|--------|--------|--------|--------|--------|------- -| --------|--------|--------|--------|--------|--------|--------|--------|--------|--------|--------|--------|--------|--------|--------|------|--|
      long[  0] |                                                                       |                                                                       *|                                                                        |                                                                     |  | 縱軸中心點
                |--------|--------|--------|--------|--------|--------|--------|--------|--------|--------|--------|--------|--------|--------|--------|------- -| --------|--------|--------|--------|--------|--------|--------|--------|--------|--------|--------|--------|--------|--------|--------|------|--|
            ... |                                                                       |                                                                        |                                                                        |                                                                     |  |
      long[127] |                                                                       |                                                                        |                                                                        |                                                                     |  | 橫軸中心點
            ... |                                                                       |                                                                        |                                                                        |                                                                     |  |
      long[254] |--------|--------|--------|--------|--------|--------|--------|--------|--------|--------|--------|--------|--------|--------|--------|------- -| --------|--------|--------|--------|--------|--------|--------|--------|--------|--------|--------|--------|--------|--------|--------|------|--|
     */
    /** 視圖計算 */
    private final long[] viewData;
    /** 單行 long 數量 */
    private final int rowStack;
    /** 半行 long 數量 */
    private final int rowStackOffset;
    /** 最大半徑塊數 */
    private final int maxRadius;
    /** 最大直徑塊數 */
    private final int maxDiameter;

    public LongXInfinitelyViewMap(ViewShape viewShape, int row) {
        super(viewShape);
        rowStack = row;
        rowStackOffset = rowStack / 2;
        maxRadius = rowStackOffset * 64 - 1;
        maxDiameter = rowStack * 64 - 1;
        viewData = new long[maxDiameter << rowStackOffset];
    }

    public List<Long> movePosition(Location location) {
        return movePosition(blockToChunk(location.getX()), blockToChunk(location.getZ()));
    }

    /**
     * 移動到區塊位置 (中心點)
     *
     * @param moveX 區塊座標X
     * @param moveZ 區塊座標Z
     * @return 如果有區塊被移除, 則會集中回傳在這
     */
    public List<Long> movePosition(int moveX, int moveZ) {
        if (moveX != centerX || moveZ != centerZ) {
            /*
               -      +
               X
            +Z |-------|
               | Chunk |
               | Map   |
            -  |-------|
             */
            int viewDistance = Math.min(maxRadius, extendDistance + 1);
            // 上一個紀錄的區塊位置 (中心點)
            List<Long> removeKeys = new ArrayList<>();
            // 將那些已經不再範圍內的區塊, 增加到緩存中
            int hitDistance = Math.max(serverDistance, viewDistance + 1);
            int pointerX;
            int pointerZ;
            int chunkX;
            int chunkZ;
            for (pointerX = 0; pointerX < maxDiameter; ++pointerX) {
                for (pointerZ = 0; pointerZ < maxDiameter; ++pointerZ) {
                    chunkX = (centerX - pointerX) + maxRadius;
                    chunkZ = (centerZ - pointerZ) + maxRadius;
                    // 是否已經不再範圍內
                    if (isSendSafe(pointerX, pointerZ)
                            && !viewShape.isInside(centerX, centerZ, chunkX, chunkZ, hitDistance)
                            && !viewShape.isInside(moveX, moveZ, chunkX, chunkZ, hitDistance)
                            && markWaitSafe(pointerX, pointerZ)) {
                        removeKeys.add(getPositionKey(chunkX, chunkZ));
                    }
                }
            }

            int offsetX = centerX - moveX;
            int offsetZ = centerZ - moveZ;
            // 座標X 發生改動
            if (offsetX != 0) {
                long[] dataX = new long[rowStack];
                long newX;
                int rowX;
                int migrate;
                int redressX;
                for (pointerZ = 0; pointerZ < maxDiameter; pointerZ++) {
                    for (rowX = 0; rowX < rowStack; rowX++)
                        dataX[rowX] = viewData[(pointerZ << rowStackOffset) | rowX];
                    for (rowX = 0; rowX < rowStack; rowX++) {
                        newX = 0;
                        for (migrate = 0; migrate < rowStack; migrate++) {
                            redressX = -(rowX - migrate) * 64 - offsetX;
                            if (redressX < -64 || redressX > 64) {
                            } else if (redressX < 0) {
                                newX |= dataX[migrate] << -redressX;
                            } else if (redressX > 0) {
                                newX |= dataX[migrate] >>> redressX;
                            }
                        }
                        if (rowX == rowStack - 1)
                            // 將沒有用到的地方標記為 0 (最右側)
                            newX &= 0b1111111111111111111111111111111111111111111111111111111111111110L;
                        viewData[(pointerZ << rowStackOffset) | rowX] = newX;
                    }
                }
            }

            // 座標Z 發生改動
            if (offsetZ < 0) {
                int redressZ;
                int rowX;
                for (pointerZ = maxDiameter - 1; pointerZ >= 0; pointerZ--) {
                    redressZ = pointerZ + offsetZ;
                    if (redressZ >= 0 && redressZ < maxDiameter) {
                        for (rowX = 0; rowX < rowStack; rowX++)
                            viewData[(pointerZ << rowStackOffset) | rowX] = viewData[(redressZ << rowStackOffset)
                                    | rowX];
                    } else {
                        for (rowX = 0; rowX < rowStack; rowX++)
                            viewData[(pointerZ << rowStackOffset) | rowX] = 0;
                    }
                }
            } else if (offsetZ > 0) {
                int redressZ;
                int rowX;
                for (pointerZ = 0; pointerZ < maxDiameter; pointerZ++) {
                    redressZ = pointerZ + offsetZ;
                    if (redressZ >= 0 && redressZ < maxDiameter) {
                        for (rowX = 0; rowX < rowStack; rowX++)
                            viewData[(pointerZ << rowStackOffset) | rowX] = viewData[(redressZ << rowStackOffset)
                                    | rowX];
                    } else {
                        for (rowX = 0; rowX < rowStack; rowX++)
                            viewData[(pointerZ << rowStackOffset) | rowX] = 0;
                    }
                }
            }

            if (moveX != centerX || moveZ != centerZ) {
                completedDistance.addAndGet(-Math.max(Math.abs(centerX - moveX), Math.abs(centerZ - moveZ)));
            }
            centerX = moveX;
            centerZ = moveZ;

            return removeKeys;
        } else {
            return new ArrayList<>(0);
        }
    }

    /**
     * 取得下一個應該要處裡的區塊
     *
     * @return positionKey, 若沒有需要處裡的區塊, 則回傳 null
     */
    public Long get() {
        int viewDistance = Math.min(maxRadius, extendDistance + 1);
        int edgeStepCount = 0;  // 每個邊, 移動幾次換方向
        int readX;
        int readZ;
        int pointerX;
        int pointerZ;
        int stepCount;
        int chunkX;
        int chunkZ;
        boolean notMiss = true;

        for (int distance = 0; distance <= maxRadius && distance <= viewDistance; distance++) {
            if (distance > completedDistance.get()) {
                // 總共有 4 次方向
                readX = distance;
                readZ = distance;
                pointerX = maxRadius + distance;
                pointerZ = maxRadius + distance;

                // Z--
                for (stepCount = 0; stepCount < edgeStepCount; ++stepCount) {
                    chunkX = centerX - readX;
                    chunkZ = centerZ - readZ;
                    if (!viewShape.isInsideEdge(centerX, centerZ, chunkX, chunkZ, serverDistance)
                            && viewShape.isInside(centerX, centerZ, chunkX, chunkZ, viewDistance)) {
                        if (isWaitSafe(pointerX, pointerZ)) {
                            markSendSafe(pointerX, pointerZ);
                            return getPositionKey(chunkX, chunkZ);
                        } else {
                            notMiss = false;
                        }
                    }

                    pointerZ--;
                    readZ--;
                }
                // X--
                for (stepCount = 0; stepCount < edgeStepCount; ++stepCount) {
                    chunkX = centerX - readX;
                    chunkZ = centerZ - readZ;
                    if (!viewShape.isInsideEdge(centerX, centerZ, chunkX, chunkZ, serverDistance)
                            && viewShape.isInside(centerX, centerZ, chunkX, chunkZ, viewDistance)) {
                        if (isWaitSafe(pointerX, pointerZ)) {
                            markSendSafe(pointerX, pointerZ);
                            return getPositionKey(chunkX, chunkZ);
                        } else {
                            notMiss = false;
                        }
                    }

                    pointerX--;
                    readX--;
                }
                // Z++
                for (stepCount = 0; stepCount < edgeStepCount; ++stepCount) {
                    chunkX = centerX - readX;
                    chunkZ = centerZ - readZ;
                    if (!viewShape.isInsideEdge(centerX, centerZ, chunkX, chunkZ, serverDistance)
                            && viewShape.isInside(centerX, centerZ, chunkX, chunkZ, viewDistance)) {
                        if (isWaitSafe(pointerX, pointerZ)) {
                            markSendSafe(pointerX, pointerZ);
                            return getPositionKey(chunkX, chunkZ);
                        } else {
                            notMiss = false;
                        }
                    }

                    pointerZ++;
                    readZ++;
                }
                // X++
                for (stepCount = 0; stepCount < edgeStepCount; ++stepCount) {
                    chunkX = centerX - readX;
                    chunkZ = centerZ - readZ;
                    if (!viewShape.isInsideEdge(centerX, centerZ, chunkX, chunkZ, serverDistance)
                            && viewShape.isInside(centerX, centerZ, chunkX, chunkZ, viewDistance)) {
                        if (isWaitSafe(pointerX, pointerZ)) {
                            markSendSafe(pointerX, pointerZ);
                            return getPositionKey(chunkX, chunkZ);
                        } else {
                            notMiss = false;
                        }
                    }

                    pointerX++;
                    readX++;
                }

                if (notMiss) {
                    completedDistance.set(distance);
                }
            }

            // 下一次循環
            edgeStepCount += 2;
        }
        return null;
    }

    private int blockToChunk(double location) {
        return blockToChunk((int) location);
    }

    private int blockToChunk(int blockLocation) {
        return blockLocation >> 4;
    }

    public boolean inPosition(int positionX, int positionZ) {
        int pointerX = maxRadius + (centerX - positionX);
        int pointerZ = maxRadius + (centerZ - positionZ);
        int viewDistance = Math.min(maxRadius, extendDistance);
        return pointerX <= maxRadius + viewDistance && pointerX >= maxRadius - viewDistance
                && pointerZ <= maxRadius + viewDistance && pointerZ >= maxRadius - viewDistance;
    }

    public boolean isWaitPosition(long positionKey) {
        return isWaitPosition(getX(positionKey), getZ(positionKey));
    }

    public boolean isWaitPosition(int positionX, int positionZ) {
        // 上一個紀錄的區塊位置 (中心點)
        int pointerX = maxRadius + (centerX - positionX);
        int pointerZ = maxRadius + (centerZ - positionZ);
        return pointerX >= 0 && pointerX < maxDiameter && pointerZ >= 0 && pointerZ < maxDiameter
                && isWaitSafe(pointerX, pointerZ);
    }

    public boolean isSendPosition(long positionKey) {
        return isSendPosition(getX(positionKey), getZ(positionKey));
    }

    public boolean isSendPosition(int positionX, int positionZ) {
        // 上一個紀錄的區塊位置 (中心點)
        int pointerX = maxRadius + (centerX - positionX);
        int pointerZ = maxRadius + (centerZ - positionZ);
        return pointerX >= 0 && pointerX < maxDiameter && pointerZ >= 0 && pointerZ < maxDiameter
                && isSendSafe(pointerX, pointerZ);
    }

    public void markWaitPosition(long positionKey) {
        markWaitPosition(getX(positionKey), getZ(positionKey));
    }

    public void markWaitPosition(int positionX, int positionZ) {
        // 上一個紀錄的區塊位置 (中心點)
        int pointerX = maxRadius + (centerX - positionX);
        int pointerZ = maxRadius + (centerZ - positionZ);
        if (pointerX >= 0 && pointerX < maxDiameter && pointerZ >= 0 && pointerZ < maxDiameter)
            markWaitSafe(pointerX, pointerZ);
    }

    public void markSendPosition(long positionKey) {
        markSendPosition(getX(positionKey), getZ(positionKey));
    }

    public void markSendPosition(int positionX, int positionZ) {
        // 上一個紀錄的區塊位置 (中心點)
        int pointerX = maxRadius + (centerX - positionX);
        int pointerZ = maxRadius + (centerZ - positionZ);
        if (pointerX >= 0 && pointerX < maxDiameter && pointerZ >= 0 && pointerZ < maxDiameter)
            markSendSafe(pointerX, pointerZ);
    }

    private int toViewPointer(int pointerX, int pointerZ) {
        return pointerZ << rowStackOffset | pointerX >>> 6;
    }

    public boolean isWaitSafe(int pointerX, int pointerZ) {
        return !isSendSafe(pointerX, pointerZ);
    }

    public boolean isSendSafe(int pointerX, int pointerZ) {
        return (viewData[toViewPointer(pointerX, pointerZ)] << (pointerX & 0b111111)
                & 0b1000000000000000000000000000000000000000000000000000000000000000L) == 0b1000000000000000000000000000000000000000000000000000000000000000L;
    }

    public boolean markWaitSafe(int pointerX, int pointerZ) {
        if (isSendSafe(pointerX, pointerZ)) {
            viewData[toViewPointer(pointerX,
                    pointerZ)] ^= (0b1000000000000000000000000000000000000000000000000000000000000000L >>> (pointerX
                            & 0b111111));
            return true;
        } else {
            return false;
        }
    }

    public void markSendSafe(int pointerX, int pointerZ) {
        viewData[toViewPointer(pointerX,
                pointerZ)] |= (0b1000000000000000000000000000000000000000000000000000000000000000L >>> (pointerX
                        & 0b111111));
    }

    /**
     * @param range 範圍外的區塊標記為等待中
     */
    public void markOutsideWait(int range) {
        // 確保只能是正數
        if (range < 0)
            range = Math.abs(range);
        int pointerX;
        int pointerZ;
        int chunkX;
        int chunkZ;
        for (pointerX = 0; pointerX < maxDiameter; ++pointerX) {
            for (pointerZ = 0; pointerZ < maxDiameter; ++pointerZ) {
                chunkX = centerX + pointerX - maxRadius;
                chunkZ = centerZ + pointerZ - maxRadius;
                if (!viewShape.isInside(centerX, centerZ, chunkX, chunkZ, range))
                    markWaitSafe(pointerX, pointerZ);
            }
        }
    }

    /**
     * @param range 範圍外的區塊標記為以發送
     */
    public void markOutsideSend(int range) {
        // 確保只能是正數
        if (range < 0)
            range = Math.abs(range);
        int pointerX;
        int pointerZ;
        int chunkX;
        int chunkZ;
        for (pointerX = 0; pointerX < maxDiameter; ++pointerX) {
            for (pointerZ = 0; pointerZ < maxDiameter; ++pointerZ) {
                chunkX = centerX + pointerX - maxRadius;
                chunkZ = centerZ + pointerZ - maxRadius;
                if (!viewShape.isInside(centerX, centerZ, chunkX, chunkZ, range))
                    markSendSafe(pointerX, pointerZ);
            }
        }
    }

    /**
     * @param range 範圍內的區塊標記為等待中
     */
    public void markInsideWait(int range) {
        // 確保只能是正數
        if (range < 0)
            range = Math.abs(range);
        int pointerX;
        int pointerZ;
        int chunkX;
        int chunkZ;
        for (pointerX = 0; pointerX < maxDiameter; ++pointerX) {
            for (pointerZ = 0; pointerZ < maxDiameter; ++pointerZ) {
                chunkX = centerX + pointerX - maxRadius;
                chunkZ = centerZ + pointerZ - maxRadius;
                if (viewShape.isInside(centerX, centerZ, chunkX, chunkZ, range))
                    markWaitSafe(pointerX, pointerZ);
            }
        }
    }

    /**
     * @param range 範圍內的區塊標記為以發送
     */
    public void markInsideSend(int range) {
        // 確保只能是正數
        if (range < 0)
            range = Math.abs(range);
        int pointerX;
        int pointerZ;
        int chunkX;
        int chunkZ;
        for (pointerX = 0; pointerX < maxDiameter; ++pointerX) {
            for (pointerZ = 0; pointerZ < maxDiameter; ++pointerZ) {
                chunkX = centerX + pointerX - maxRadius;
                chunkZ = centerZ + pointerZ - maxRadius;
                if (viewShape.isInside(centerX, centerZ, chunkX, chunkZ, range))
                    markSendSafe(pointerX, pointerZ);
            }
        }
    }

    public List<Long> getAll() {
        List<Long> chunkList = new ArrayList<>();
        int pointerX;
        int pointerZ;
        int chunkX;
        int chunkZ;
        for (pointerX = 0; pointerX < maxDiameter; ++pointerX) {
            for (pointerZ = 0; pointerZ < maxDiameter; ++pointerZ) {
                chunkX = centerX + pointerX - maxRadius;
                chunkZ = centerZ + pointerZ - maxRadius;
                chunkList.add(getPositionKey(chunkX, chunkZ));
            }
        }
        return chunkList;
    }

    public List<Long> getAllNotServer() {
        List<Long> chunkList = new ArrayList<>();
        int pointerX;
        int pointerZ;
        int chunkX;
        int chunkZ;
        for (pointerX = 0; pointerX < maxDiameter; ++pointerX) {
            for (pointerZ = 0; pointerZ < maxDiameter; ++pointerZ) {
                chunkX = centerX + pointerX - maxRadius;
                chunkZ = centerZ + pointerZ - maxRadius;
                if (isSendSafe(pointerX, pointerZ)
                        && !viewShape.isInside(centerX, centerZ, chunkX, chunkZ, serverDistance))
                    chunkList.add(getPositionKey(chunkX, chunkZ));
            }
        }
        return chunkList;
    }

    public void clear() {
        System.arraycopy(new long[viewData.length], 0, viewData, 0, viewData.length);
        completedDistance.set(-1);
    }

    public void debug(CommandSender sender) {
        StringBuilder builder = new StringBuilder();
        builder.append("LongXInfinitelyViewMap:\n");
        for (int index = 0; index < viewData.length; ++index) {
            if (index != 0 && index % rowStack == 0)
                builder.append('\n');
            long value = viewData[index];
            for (int read = 63; read >= 0; read--)
                builder.append((value >> read & 1) == 1 ? '■' : '□');
        }
        sender.sendMessage(builder.toString());
    }
}
