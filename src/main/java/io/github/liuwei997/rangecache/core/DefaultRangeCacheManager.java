package io.github.liuwei997.rangecache.core;

import java.util.Objects;

public final class DefaultRangeCacheManager implements RangeCacheManager {

    private final RangeCacheExecutor executor;

    public DefaultRangeCacheManager(RangeCacheExecutor executor) {
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    @Override
    public void clear(String cacheName) {
        executor.clear(cacheName);
    }

    @Override
    public void clearAll() {
        executor.clearAll();
    }
}
