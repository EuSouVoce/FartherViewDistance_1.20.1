package xuan.cat.fartherviewdistance.code.data.viewmap;

public enum ViewShape {
    /** square */
    SQUARE((aX, aZ, bX, bZ, dist) -> {
        int minX = bX - dist;
        int minZ = bZ - dist;
        int maxX = bX + dist;
        int maxZ = bZ + dist;
        return aX >= minX && aZ >= minZ && aX <= maxX && aZ <= maxZ;
    }),
    /** round */
    ROUND((aX, aZ, bX, bZ, dist) -> {
        int d = distanceSquared(aX, aZ, bX, bZ);
        return d <= dist * dist + dist;
    }, (aX, aZ, bX, bZ, dist) -> {
        int d2 = dist * dist + dist;
        return distanceSquared(aX, aZ, bX, bZ) <= d2
                && (distanceSquared(aX + 1, aZ, bX, bZ) > d2
                || distanceSquared(aX - 1, aZ, bX, bZ) > d2
                || distanceSquared(aX, aZ + 1, bX, bZ) > d2
                || distanceSquared(aX, aZ - 1, bX, bZ) > d2);
    });

    // helpers
    private static int distanceSquared(int aX, int aZ, int bX, int bZ) {
        int dx = aX - bX;
        int dz = aZ - bZ;
        return dx * dx + dz * dz;
    }

    @FunctionalInterface
    public interface JudgeInside {
        boolean test(int aX, int aZ, int bX, int bZ, int viewDistance);
    }

    private final JudgeInside judgeInside;
    private final JudgeInside judgeInsideEdge;

    ViewShape(JudgeInside judgeInside) {
        this(judgeInside, judgeInside);
    }

    ViewShape(JudgeInside judgeInside, JudgeInside judgeInsideEdge) {
        this.judgeInside = judgeInside;
        this.judgeInsideEdge = judgeInsideEdge;
    }

    public boolean isInside(int aX, int aZ, int bX, int bZ, int viewDistance) {
        return judgeInside.test(aX, aZ, bX, bZ, viewDistance);
    }

    public boolean isInsideEdge(int aX, int aZ, int bX, int bZ, int viewDistance) {
        return judgeInsideEdge.test(aX, aZ, bX, bZ, viewDistance);
    }
}