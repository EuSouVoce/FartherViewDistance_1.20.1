package xuan.cat.fartherviewdistance.code.data.viewmap;

@SuppressWarnings("unused")
public enum ViewShape {
    /** square */
    SQUARE((final int aX, final int aZ, final int bX, final int bZ, final int viewDistance) -> {
        final int minX = bX - viewDistance;
        final int minZ = bZ - viewDistance;
        final int maxX = bX + viewDistance;
        final int maxZ = bZ + viewDistance;
        return aX >= minX && aZ >= minZ && aX <= maxX && aZ <= maxZ;
    }),
    /** round */
    ROUND((final int aX, final int aZ, final int bX, final int bZ, final int viewDistance) -> {
        final int viewDiameter = viewDistance * viewDistance + viewDistance;
        final int distanceX = aX - bX;
        final int distanceZ = aZ - bZ;
        final int distance = distanceX * distanceX + distanceZ * distanceZ;
        return distance <= viewDiameter;
    }, (final int aX, final int aZ, final int bX, final int bZ, final int viewDistance) -> {
        final JudgeInside inside = (final int _aX, final int _aZ, final int _bX, final int _bZ,
                final int viewDiameter) -> {
            final int distanceX = _aX - _bX;
            final int distanceZ = _aZ - _bZ;
            final int distance = distanceX * distanceX + distanceZ * distanceZ;
            return distance <= viewDiameter;
        };
        final int viewDiameter = viewDistance * viewDistance + viewDistance;
        return inside.test(aX, aZ, bX, bZ, viewDiameter) && !(!inside.test(aX + 1, aZ, bX, bZ, viewDiameter)
                || !inside.test(aX - 1, aZ, bX, bZ, viewDiameter) || !inside.test(aX, aZ + 1, bX, bZ, viewDiameter)
                || !inside.test(aX, aZ - 1, bX, bZ, viewDiameter));
    }),
    ;

    /**
     * Permission calculation
     */
    interface JudgeInside {
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
