package io.github.liuwei997.rangecache.core;

import com.google.common.collect.Range;

public interface RangeCacheManager {

    /**
     * Removes one cached entity from a logical series without changing the
     * series' covered ranges. A following request for an already covered range
     * therefore does not invoke the loader for this entity.
     */
    void evictEntity(SeriesKey seriesKey, Object uniqueKey);

    /**
     * Removes cached entities and coverage in {@code range} from one logical
     * series. A following request that intersects this range fetches the
     * uncovered portion again.
     */
    <R extends Comparable<? super R>> void invalidateRange(SeriesKey seriesKey, Range<R> range);

    /** Removes one logical series, identified by method and non-range arguments. */
    void clearSeries(SeriesKey seriesKey);

    /** Removes every logical series belonging to the supplied cache name. */
    void clearMethod(String cacheName);

    void clearAll();
}
