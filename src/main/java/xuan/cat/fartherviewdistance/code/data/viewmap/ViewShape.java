package xuan.cat.fartherviewdistance.code.data.viewmap;

public enum ViewShape {
    /** square */
    SQUARE((aX, aZ, bX, bZ, dist) -> {
        final int minX = bX - dist;
        final int minZ = bZ - dist;
        final int maxX = bX + dist;
        final int maxZ = bZ + dist;
        return aX >= minX && aZ >= minZ && aX <= maxX && aZ <= maxZ;
    }),
    /** round */
    ROUND((aX, aZ, bX, bZ, dist) -> {
        final int d = ViewShape.distanceSquared(aX, aZ, bX, bZ);
        return d <= dist * dist + dist;
    }, (aX, aZ, bX, bZ, dist) -> {
        final int d2 = dist * dist + dist;
        return ViewShape.distanceSquared(aX, aZ, bX, bZ) <= d2
                && (ViewShape.distanceSquared(aX + 1, aZ, bX, bZ) > d2
                        || ViewShape.distanceSquared(aX - 1, aZ, bX, bZ) > d2
                        || ViewShape.distanceSquared(aX, aZ + 1, bX, bZ) > d2
                        || ViewShape.distanceSquared(aX, aZ - 1, bX, bZ) > d2);
    });

    // helpers
    private static int distanceSquared(final int aX, final int aZ, final int bX, final int bZ) {
        final int dx = aX - bX;
        final int dz = aZ - bZ;
        return dx * dx + dz * dz;
    }

    @FunctionalInterface
    public interface JudgeInside {
        boolean test(int aX, int aZ, int bX, int bZ, int viewDistance);
    }

    private final JudgeInside judgeInside;
    private final JudgeInside judgeInsideEdge;

    ViewShape(final JudgeInside judgeInside) {
        this(judgeInside, judgeInside);
    }

    ViewShape(final JudgeInside judgeInside, final JudgeInside judgeInsideEdge) {
        this.judgeInside = judgeInside;
        this.judgeInsideEdge = judgeInsideEdge;
    }

    public boolean isInside(final int aX, final int aZ, final int bX, final int bZ, final int viewDistance) {
        return this.judgeInside.test(aX, aZ, bX, bZ, viewDistance);
    }

    public boolean isInsideEdge(final int aX, final int aZ, final int bX, final int bZ, final int viewDistance) {
        return this.judgeInsideEdge.test(aX, aZ, bX, bZ, viewDistance);
    }
}