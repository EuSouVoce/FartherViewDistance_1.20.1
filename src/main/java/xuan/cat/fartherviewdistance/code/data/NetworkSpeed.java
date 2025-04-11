package xuan.cat.fartherviewdistance.code.data;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * The `NetworkSpeed` class tracks network speed and latency measurements,
 * allowing for recording,
 * averaging, and updating of data.
 */
public final class NetworkSpeed {

    public volatile long speedTimestamp = 0;

    public volatile int speedConsume = 0;
    public volatile Long speedID = null;

    public volatile long pingTimestamp = 0;
    public volatile Long pingID = null;
    public volatile int lastPing = 0;

    private volatile int[] writeArray = new int[50];
    private volatile int[] consumeArray = new int[50];
    private final AtomicInteger writeTotal = new AtomicInteger(0);
    private final AtomicInteger consumeTotal = new AtomicInteger(0);

    /**
     * The `add` function updates various counters and arrays atomically in a
     * synchronized block.
     * 
     * @param ping   The `ping` parameter represents the time taken for a network
     *               packet to travel from
     *               the sender to the receiver and back. It is typically measured
     *               in milliseconds.
     * @param length The `length` parameter represents the length of data being
     *               added or written. It is
     *               used to update various counters and arrays related to writing
     *               and consuming data.
     */
    public void add(final int ping, final int length) {
        synchronized (this.writeTotal) {
            this.writeTotal.addAndGet(length);
            this.consumeTotal.addAndGet(ping);
            this.writeArray[0] += length;
            this.consumeArray[0] += ping;
        }
    }

    /**
     * This Java function calculates the average value of write operations divided
     * by the maximum of 1
     * and consume operations.
     * 
     * @return The method `avg()` is returning the average value of `writeTotal`
     *         divided by
     *         `consumeTotal`, with a minimum value of 1 for `consumeTotal`. If
     *         `writeTotal` is 0, then the
     *         method returns 0.
     */
    public int avg() {
        synchronized (this.writeTotal) {
            final int writeGet = this.writeTotal.get();
            final int consumeGet = Math.max(1, this.consumeTotal.get());
            if (writeGet == 0) {
                return 0;
            } else {
                return writeGet / consumeGet;
            }
        }
    }

    public void next() {
        synchronized (this.writeTotal) {
            this.writeTotal.addAndGet(-this.writeArray[this.writeArray.length - 1]);
            this.consumeTotal.addAndGet(-this.consumeArray[this.consumeArray.length - 1]);
            final int[] writeArrayClone = new int[this.writeArray.length];
            final int[] consumeArrayClone = new int[this.consumeArray.length];
            System.arraycopy(this.writeArray, 0, writeArrayClone, 1, this.writeArray.length - 1);
            System.arraycopy(this.consumeArray, 0, consumeArrayClone, 1, this.consumeArray.length - 1);
            this.writeArray = writeArrayClone;
            this.consumeArray = consumeArrayClone;
        }
    }
}
