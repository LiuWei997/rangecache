package io.github.liuwei997.rangecache.core;

import java.lang.reflect.Method;
import java.util.Objects;

public final class DefaultRangeCacheManager implements RangeCacheManager {

    private final RangeCacheExecutor executor;

    public DefaultRangeCacheManager(RangeCacheExecutor executor) {
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    @Override
    public void clearSeries(SeriesKey seriesKey) {
        executor.clearSeries(seriesKey);
    }

    @Override
    public void clearMethod(Method method) {
        executor.clearMethod(method);
    }

    @Override
    public void clearAll() {
        executor.clearAll();
    }
}
