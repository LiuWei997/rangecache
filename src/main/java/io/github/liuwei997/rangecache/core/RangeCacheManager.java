package io.github.liuwei997.rangecache.core;

public interface RangeCacheManager {

    /** Removes one logical series, identified by method and non-range arguments. */
    void clearSeries(SeriesKey seriesKey);

    /** Removes every logical series belonging to the supplied cache name. */
    void clearMethod(String cacheName);

    void clearAll();
}
