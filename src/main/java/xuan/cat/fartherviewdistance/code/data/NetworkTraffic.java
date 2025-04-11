package xuan.cat.fartherviewdistance.code.data;

/**
 * 網路流量
 */
public final class NetworkTraffic {
    // /** 寫入紀錄 */
    // private volatile int[] writeArray = new int[20];
    // /** 寫入累計 */
    // private final AtomicInteger writeTotal = new AtomicInteger(0);
    private volatile int value = 0;

    /**
     * 已使用
     * 
     * @param length 位元組數量
     */
    public synchronized void use(final int length) {
        this.value += length;
        // synchronized (writeTotal) {
        // writeArray[0] += length;
        // writeTotal.addAndGet(length);
        // }
    }

    /**
     * @return 當前的狀態
     */
    public synchronized int get() {
        return this.value;
        // synchronized (writeTotal) {
        // return writeTotal.get();
        // }
    }

    /**
     * @param length 位元組數量
     * @return 是否低於使用量
     */
    public synchronized boolean exceed(final int length) {
        return this.value >= length;
        // synchronized (writeTotal) {
        // return writeTotal.get() >= length;
        // }
    }

    /**
     * 下一個 tick
     */
    public void next() {
        this.value = 0;
        // synchronized (writeTotal) {
        // writeTotal.addAndGet(-writeArray[writeArray.length - 1]);
        // int[] writeArrayClone = new int[writeArray.length];
        // System.arraycopy(writeArray, 0, writeArrayClone, 1, writeArray.length - 1);
        // writeArray = writeArrayClone;
        // }
    }
}
