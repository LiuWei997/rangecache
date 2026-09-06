package io.github.liuwei997.rangecache.core;

import java.lang.reflect.Method;

public interface RangeCacheManager {

    /** Removes one logical series, identified by method and non-range arguments. */
    void clearSeries(SeriesKey seriesKey);

    /** Removes every logical series belonging to the supplied method. */
    void clearMethod(Method method);

    void clearAll();
}
