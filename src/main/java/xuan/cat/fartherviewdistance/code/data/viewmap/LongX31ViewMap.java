package xuan.cat.fartherviewdistance.code.data.viewmap;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.command.CommandSender;

/**
 * 表示區塊視野
 */
public final class LongX31ViewMap extends ViewMap {
    /*
    每位玩家都有一個 long 陣列
    最高 63 * 63 (因為求奇數)
        0 表示等待中
        1 表示已發送區塊
    63 / 2 = 31 所以實際上最遠只能擴充 31 個視野距離

    每個 long 的最後一位數用於其他資料標記

    long[].length = 63

                    chunkMap
                    6   6          5          4            3          2          1          0
                    3 2109876 54321098 76543210 98765432 1 0987654 32109876 54321098 76543210  位元位移

                      33          2          1         0 0 0         1          2          33
                      1098765 43210987 65432109 87654321 0 1234567 89012345 67890123 45678901  區塊離中心點多遠

                   |-|-------|--------|--------|--------|- -------|--------|--------|--------|
                   | |                                   *                                   | 表示 列 中心點
                   |*|                                                                       | 不使用
                   |-|------- -------- -------- -------- - ------- -------- -------- --------|
          long[ 0] |0|0000000 00000000 00000000 00000000 0 0000000 00000000 00000000 00000000|
          ...      | |                                 ...                                   |
          long[31] | |                                 ...                                   | 表示 行 中心點
          ...      | |                                 ...                                   |
          long[62] |0|0000000 00000000 00000000 00000000 0 0000000 00000000 00000000 00000000|
                   |-|-----------------------------------------------------------------------|


     */
    /** 距離 */
    private static final int DISTANCE = 32;
    /** 中心 */
    private static final int CENTER = 31;
    /** 長度 */
    private static final int LENGTH = 63;
    /** 視圖計算 */
    private final long[] chunkMap = new long[LENGTH];

    public LongX31ViewMap(ViewShape viewShape) {
        super(viewShape);
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
            先對 chunkMap 進行座標位移
            再把伺服器視野距離的範圍標記為以加載
             */
            // 上一個紀錄的區塊位置 (中心點)
            int viewDistance = Math.min(extendDistance + 1, DISTANCE);
            // 移除的區塊清單
            List<Long> removeKeys = new ArrayList<>();
            // 將那些已經不再範圍內的區塊, 增加到緩存中
            int hitDistance = Math.max(serverDistance, viewDistance + 1);
            int pointerX;
            int pointerZ;
            int chunkX;
            int chunkZ;
            for (pointerX = 0; pointerX < LENGTH; ++pointerX) {
                for (pointerZ = 0; pointerZ < LENGTH; ++pointerZ) {
                    chunkX = (centerX - pointerX) + CENTER;
                    chunkZ = (centerZ - pointerZ) + CENTER;
                    // 是否已經不再範圍內
                    if (isSendSafe(pointerX, pointerZ)
                            && !viewShape.isInside(centerX, centerZ, chunkX, chunkZ, hitDistance)
                            && !viewShape.isInside(moveX, moveZ, chunkX, chunkZ, hitDistance)
                            && markWaitSafe(pointerX, pointerZ)) {
                        removeKeys.add(getPositionKey(chunkX, chunkZ));
                    }
                }
            }

            /*
               -      +
               X
            +Z |-------|
               | Chunk |
               | Map   |
            -  |-------|

            當座標發生移動
            x:0    -> x:1
            000000    000000
            011110    111100
            011110    111100
            011110    111100
            011110    111100
            000000    000000

            z:0    -> z:1
            000000    000000
            011110    000000
            011110    011110
            011110    011110
            011110    011110
            000000    011110
             */
            int offsetX = centerX - moveX;
            int offsetZ = centerZ - moveZ;
            // 座標X 發生改動
            if (offsetX != 0) {
                for (pointerZ = 0; pointerZ < LENGTH; pointerZ++) {
                    chunkMap[pointerZ] = offsetX > 0 ? chunkMap[pointerZ] >> offsetX
                            : chunkMap[pointerZ] << Math.abs(offsetX);
                }
            }
            // 座標Z 發生改動
            if (offsetZ != 0) {
                long[] newChunkMap = new long[LENGTH];
                int pointerToZ;
                for (int pointerFromZ = 0; pointerFromZ < LENGTH; pointerFromZ++) {
                    pointerToZ = pointerFromZ - offsetZ;
                    if (pointerToZ >= 0 && pointerToZ < LENGTH) {
                        newChunkMap[pointerToZ] = chunkMap[pointerFromZ];
                    }
                }
                System.arraycopy(newChunkMap, 0, chunkMap, 0, chunkMap.length);
            }

            // 如果座標有發生改動, 更新目前儲存的座標
            if (offsetX != 0 || offsetZ != 0) {
                // 將沒有用到的地方標記為 0 (最左側)
                if (offsetX < 0) {
                    for (pointerZ = 0; pointerZ < LENGTH; pointerZ++)
                        chunkMap[pointerZ] &= 0b0111111111111111111111111111111111111111111111111111111111111111L;
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
        /*
        尋找過程
        會從中心慢慢往外找

        順時針, 從最上方開始
         -----      -----      -----      -----      -----      -----      -----      -----      -----      -----      -----

                                           1          11         111        111        111        111        111        111
           +    ->    +    ->   1+    ->   1+    ->   1+    ->   1+    ->   1+1   ->   1+1   ->   1+1   ->   1+1   ->   1+1
                     1          1          1          1          1          1          1 1        111        111       1111
                                                                                                            1          1
         -----      -----      -----      -----      -----      -----      -----      -----      -----      -----      -----


        算公式
         單個邊長
        1 = 1 + (1 - 1)
        3 = 2 + (2 - 1)
        5 = 3 + (3 - 1)
        7 = 4 + (4 - 1)

         總邊長 (不重複步數)
        0  = 1 * 4 - 4
        8  = 3 * 4 - 4
        16 = 5 * 4 - 4
        24 = 7 * 4 - 4

         edgeStepCount = 每移動?次 換方向 總要要換4次方向
        0  / 4 = 0
        8  / 4 = 2
        16 / 4 = 4
        24 / 4 = 6

        得出的公式
        每 距離+1 所需移動的次數+2

        distance = 1    //
        1               // 由於不可為 1
        + 1             // 中心點掠過


        distance = 2
         3
        |-|
        | | 8
        |-|


        distance = 3
          5
        |---|
        |   |
        |   | 16
        |   |
        |---|


        distance = 4
           7
        |-----|
        |     |
        |     |
        |     | 24
        |     |
        |     |
        |-----|

         */

        int viewDistance = Math.min(extendDistance + 1, DISTANCE);
        int edgeStepCount = 0;  // 每個邊, 移動幾次換方向
        int readX;
        int readZ;
        int pointerX;
        int pointerZ;
        int stepCount;
        int chunkX;
        int chunkZ;
        boolean notMiss = true;

        for (int distance = 0; distance < DISTANCE && distance < viewDistance; distance++) {
            if (distance > completedDistance.get()) {
                // 總共有 4 次方向
                readX = distance;
                readZ = distance;
                pointerX = CENTER + distance;
                pointerZ = CENTER + distance;

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

    public boolean isWaitSafe(int pointerX, int pointerZ) {
        return !isSendSafe(pointerX, pointerZ);
    }

    public boolean isSendSafe(int pointerX, int pointerZ) {
        return ((chunkMap[pointerZ] >> pointerX)
                & 0b0000000000000000000000000000000000000000000000000000000000000001L) == 0b0000000000000000000000000000000000000000000000000000000000000001L;
    }

    public boolean markWaitSafe(int pointerX, int pointerZ) {
        if (isSendSafe(pointerX, pointerZ)) {
            chunkMap[pointerZ] ^= (0b0000000000000000000000000000000000000000000000000000000000000001L << pointerX);
            return true;
        } else {
            return false;
        }
    }

    public void markSendSafe(int pointerX, int pointerZ) {
        chunkMap[pointerZ] |= (0b0000000000000000000000000000000000000000000000000000000000000001L << pointerX);
    }

    public boolean inPosition(int positionX, int positionZ) {
        int pointerX = CENTER + (centerX - positionX);
        int pointerZ = CENTER + (centerZ - positionZ);
        return pointerX <= CENTER + extendDistance && pointerX >= CENTER - extendDistance
                && pointerZ <= CENTER + extendDistance && pointerZ >= CENTER - extendDistance;
    }

    public boolean isWaitPosition(long positionKey) {
        int x = getX(positionKey);
        int z = getZ(positionKey);
        return isWaitPosition(x, z);
    }

    public boolean isWaitPosition(int positionX, int positionZ) {
        // 上一個紀錄的區塊位置 (中心點)
        int pointerX = CENTER + (centerX - positionX);
        int pointerZ = CENTER + (centerZ - positionZ);
        return pointerX >= 0 && pointerX < LENGTH && pointerZ >= 0 && pointerZ < LENGTH
                && isWaitSafe(pointerX, pointerZ);
    }

    public boolean isSendPosition(long positionKey) {
        int x = getX(positionKey);
        int z = getZ(positionKey);
        return isSendPosition(x, z);
    }

    public boolean isSendPosition(int positionX, int positionZ) {
        // 上一個紀錄的區塊位置 (中心點)
        int pointerX = CENTER + (centerX - positionX);
        int pointerZ = CENTER + (centerZ - positionZ);
        return pointerX >= 0 && pointerX < LENGTH && pointerZ >= 0 && pointerZ < LENGTH
                && isSendSafe(pointerX, pointerZ);
    }

    public void markWaitPosition(long positionKey) {
        int x = getX(positionKey);
        int z = getZ(positionKey);
        markWaitPosition(x, z);
    }

    public void markWaitPosition(int positionX, int positionZ) {
        // 上一個紀錄的區塊位置 (中心點)
        int pointerX = CENTER + (centerX - positionX);
        int pointerZ = CENTER + (centerZ - positionZ);
        if (pointerX >= 0 && pointerX < LENGTH && pointerZ >= 0 && pointerZ < LENGTH)
            markWaitSafe(pointerX, pointerZ);
    }

    public void markSendPosition(long positionKey) {
        int x = getX(positionKey);
        int z = getZ(positionKey);
        markSendPosition(x, z);
    }

    public void markSendPosition(int positionX, int positionZ) {
        // 上一個紀錄的區塊位置 (中心點)
        int pointerX = CENTER + (centerX - positionX);
        int pointerZ = CENTER + (centerZ - positionZ);
        if (pointerX >= 0 && pointerX < LENGTH && pointerZ >= 0 && pointerZ < LENGTH)
            markSendSafe(pointerX, pointerZ);
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
        for (pointerX = 0; pointerX < LENGTH; ++pointerX) {
            for (pointerZ = 0; pointerZ < LENGTH; ++pointerZ) {
                chunkX = centerX + pointerX - CENTER;
                chunkZ = centerZ + pointerZ - CENTER;
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
        for (pointerX = 0; pointerX < LENGTH; ++pointerX) {
            for (pointerZ = 0; pointerZ < LENGTH; ++pointerZ) {
                chunkX = centerX + pointerX - CENTER;
                chunkZ = centerZ + pointerZ - CENTER;
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
        for (pointerX = 0; pointerX < LENGTH; ++pointerX) {
            for (pointerZ = 0; pointerZ < LENGTH; ++pointerZ) {
                chunkX = centerX + pointerX - CENTER;
                chunkZ = centerZ + pointerZ - CENTER;
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
        for (pointerX = 0; pointerX < LENGTH; ++pointerX) {
            for (pointerZ = 0; pointerZ < LENGTH; ++pointerZ) {
                chunkX = centerX + pointerX - CENTER;
                chunkZ = centerZ + pointerZ - CENTER;
                if (viewShape.isInside(centerX, centerZ, chunkX, chunkZ, range))
                    markSendSafe(pointerX, pointerZ);
            }
        }
    }

    public void clear() {
        System.arraycopy(new long[LENGTH], 0, chunkMap, 0, chunkMap.length);
        completedDistance.set(-1);
    }

    public long[] getChunkMap() {
        return chunkMap;
    }

    public List<Long> getAll() {
        List<Long> chunkList = new ArrayList<>();
        int pointerX;
        int pointerZ;
        int chunkX;
        int chunkZ;
        for (pointerX = 0; pointerX < LENGTH; ++pointerX) {
            for (pointerZ = 0; pointerZ < LENGTH; ++pointerZ) {
                chunkX = centerX + pointerX - CENTER;
                chunkZ = centerZ + pointerZ - CENTER;
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
        for (pointerX = 0; pointerX < LENGTH; ++pointerX) {
            for (pointerZ = 0; pointerZ < LENGTH; ++pointerZ) {
                chunkX = centerX + pointerX - CENTER;
                chunkZ = centerZ + pointerZ - CENTER;
                if (isSendSafe(pointerX, pointerZ)
                        && !viewShape.isInside(centerX, centerZ, chunkX, chunkZ, serverDistance))
                    chunkList.add(getPositionKey(chunkX, chunkZ));
            }
        }
        return chunkList;
    }

    public static int blockToChunk(double blockLocation) {
        return blockToChunk((int) blockLocation);
    }

    public static int blockToChunk(int blockLocation) {
        return blockLocation >> 4;
    }

    public static String debug(long value) {
        StringBuilder builder = new StringBuilder(LENGTH);
        for (int i = LENGTH; i >= 0; i--) {
            builder.append((value >> i & 1) == 1 ? '■' : '□');
        }
        return builder.toString();
    }

    public void debug(CommandSender sender) {
        StringBuilder builder = new StringBuilder();
        builder.append("LongX31ViewMap:\n");
        for (int index = 0; index < LENGTH; ++index) {
            if (index != 0)
                builder.append('\n');
            builder.append(debug(getChunkMap()[index]));
        }
        sender.sendMessage(builder.toString());
    }
}
