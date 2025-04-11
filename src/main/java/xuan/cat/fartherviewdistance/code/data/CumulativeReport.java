package xuan.cat.fartherviewdistance.code.data;

/**
 * The `CumulativeReport` class tracks and reports cumulative data for
 * high-speed load, slow-speed
 * load, and consumption over different time intervals.
 */
public final class CumulativeReport {

    private volatile int[] loadFast = new int[300];
    private volatile int[] loadSlow = new int[300];
    private volatile int[] consume = new int[300];

    /**
     * The `next` function creates clones of three arrays removing the last element
     * of each
     */
    public void next() {
        try {
            final int[] loadFastClone = new int[300];
            final int[] loadSlowClone = new int[300];
            final int[] consumeClone = new int[300];
            System.arraycopy(this.loadFast, 0, loadFastClone, 1, this.loadFast.length - 1);
            System.arraycopy(this.loadSlow, 0, loadSlowClone, 1, this.loadSlow.length - 1);
            System.arraycopy(this.consume, 0, consumeClone, 1, this.consume.length - 1);
            this.loadFast = loadFastClone;
            this.loadSlow = loadSlowClone;
            this.consume = consumeClone;
        } catch (final Exception e) {
            e.printStackTrace();
        }
    }

    public void increaseLoadFast() {
        this.loadFast[0]++;
    }

    public void increaseLoadSlow() {
        this.loadSlow[0]++;
    }

    public void addConsume(final int value) {
        this.consume[0] += value;
    }

    public int reportLoadFast5s() {
        int total = 0;
        for (int i = 0; i < 5; ++i)
            total += this.loadFast[i];
        return total;
    }

    public int reportLoadFast1m() {
        int total = 0;
        for (int i = 0; i < 60; ++i)
            total += this.loadFast[i];
        return total;
    }

    public int reportLoadFast5m() {
        int total = 0;
        for (int i = 0; i < 300; ++i)
            total += this.loadFast[i];
        return total;
    }

    public int reportLoadSlow5s() {
        int total = 0;
        for (int i = 0; i < 5; ++i)
            total += this.loadSlow[i];
        return total;
    }

    public int reportLoadSlow1m() {
        int total = 0;
        for (int i = 0; i < 60; ++i)
            total += this.loadSlow[i];
        return total;
    }

    public int reportLoadSlow5m() {
        int total = 0;
        for (int i = 0; i < 300; ++i)
            total += this.loadSlow[i];
        return total;
    }

    public long reportConsume5s() {
        long total = 0;
        for (int i = 0; i < 5; ++i)
            total += this.consume[i];
        return total;
    }

    public long reportConsume1m() {
        long total = 0;
        for (int i = 0; i < 60; ++i)
            total += this.consume[i];
        return total;
    }

    public long reportConsume5m() {
        long total = 0;
        for (int i = 0; i < 300; ++i)
            total += this.consume[i];
        return total;
    }
}
