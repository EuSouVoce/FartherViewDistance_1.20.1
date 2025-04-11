package xuan.cat.fartherviewdistance.code.data.viewmap;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.bukkit.Location;
import org.bukkit.command.CommandSender;

/**
 * The `ViewMap` class provides methods for managing and processing block
 * positions within a view
 * shape.
 */
public abstract class ViewMap {
    public final ViewShape viewShape;
    public int extendDistance = 1;
    public int serverDistance = 1;
    /** Completed distance */
    public final AtomicInteger completedDistance = new AtomicInteger(-1);
    protected int centerX = 0;
    protected int centerZ = 0;

    protected ViewMap(final ViewShape viewShape) {
        this.viewShape = viewShape;
    }

    public abstract List<Long> movePosition(Location location);

    /**
     * Move to the block position (center point).
     *
     * @param moveX Block coordinate X
     * @param moveZ Block coordinate Z
     * @return If any blocks are removed, they will be returned here.
     */

    public abstract List<Long> movePosition(int moveX, int moveZ);

    /**
     * Get the next block that should be processed
     * 
     * @return positionKey, if there is no block that needs to be processed, return
     *         null
     */
    public abstract Long get();

    public int getCenterX() {
        return this.centerX;
    }

    public int getCenterZ() {
        return this.centerZ;
    }

    public final void setCenter(final Location location) {
        this.setCenter(location.getBlockX() >> 4, location.getBlockZ() >> 4);
    }

    public final void setCenter(final int positionX, final int positionZ) {
        this.setCenterX(positionX);
        this.setCenterZ(positionZ);
    }

    public void setCenterX(final int centerX) {
        this.centerX = centerX;
    }

    public void setCenterZ(final int centerZ) {
        this.centerZ = centerZ;
    }

    public static int getX(final long positionKey) {
        return (int) (positionKey);
    }

    public static int getZ(final long positionKey) {
        return (int) (positionKey >> 32);
    }

    public static long getPositionKey(final int x, final int z) {
        return ((long) z << 32) & 0b1111111111111111111111111111111100000000000000000000000000000000L
                | x & 0b0000000000000000000000000000000011111111111111111111111111111111L;
    }

    public abstract boolean inPosition(int positionX, int positionZ);

    public abstract boolean isWaitPosition(long positionKey);

    public abstract boolean isWaitPosition(int positionX, int positionZ);

    public abstract boolean isSendPosition(long positionKey);

    public abstract boolean isSendPosition(int positionX, int positionZ);

    public abstract void markWaitPosition(long positionKey);

    public abstract void markWaitPosition(int positionX, int positionZ);

    public abstract void markSendPosition(long positionKey);

    public abstract void markSendPosition(int positionX, int positionZ);

    /**
     * @param range Blocks outside the range are marked as pending
     */
    public abstract void markOutsideWait(int range);

    /**
     * @param range Out-of-range blocks are marked for sending
     */
    public abstract void markOutsideSend(int range);

    /**
     * @param range Blocks within the range are marked as pending
     */
    public abstract void markInsideWait(int range);

    /**
     * @param range Blocks within the range are marked for sending
     */
    public abstract void markInsideSend(int range);

    public abstract List<Long> getAll();

    public abstract List<Long> getAllNotServer();

    public abstract boolean isWaitSafe(int pointerX, int pointerZ);

    public abstract boolean isSendSafe(int pointerX, int pointerZ);

    public abstract boolean markWaitSafe(int pointerX, int pointerZ);

    public abstract void markSendSafe(int pointerX, int pointerZ);

    public abstract void clear();

    public abstract void debug(CommandSender sender);
}
