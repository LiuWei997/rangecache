package io.github.liuwei997.rangecache.core;

import com.google.common.collect.Range;
import java.util.Objects;

public final class DefaultRangeCacheManager implements RangeCacheManager {

    private final RangeCacheExecutor executor;

    public DefaultRangeCacheManager(RangeCacheExecutor executor) {
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    @Override
    public void evictEntity(SeriesKey seriesKey, Object uniqueKey) {
        executor.evictEntity(seriesKey, uniqueKey);
    }

    @Override
    public <R extends Comparable<? super R>> void invalidateRange(SeriesKey seriesKey, Range<R> range) {
        executor.invalidateRange(seriesKey, range);
    }

    @Override
    public void clearSeries(SeriesKey seriesKey) {
        executor.clearSeries(seriesKey);
    }

    @Override
    public void clearMethod(String cacheName) {
        executor.clearMethod(cacheName);
    }

    @Override
    public void clearAll() {
        executor.clearAll();
    }
}
