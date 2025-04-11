package xuan.cat.fartherviewdistance.code.data.viewmap;

import java.util.function.Function;

public enum ViewMapMode {
    X15(IntX15ViewMap::new, 15),
    X31(LongX31ViewMap::new, 31),
    X63(viewShape -> new LongXInfinitelyViewMap(viewShape, 2), 63),
    X127(viewShape -> new LongXInfinitelyViewMap(viewShape, 4), 127),
    X383(viewShape -> new LongXInfinitelyViewMap(viewShape, 6), 383),
    ;

    private final int extend;
    private final Function<ViewShape, ViewMap> create;

    ViewMapMode(final Function<ViewShape, ViewMap> create, final int extend) {
        this.extend = extend;
        this.create = create;
    }

    public ViewMap createMap(final ViewShape viewShape) {
        return this.create.apply(viewShape);
    }

    public int getExtend() {
        return this.extend;
    }
}
