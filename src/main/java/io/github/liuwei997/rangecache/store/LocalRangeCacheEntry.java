package io.github.liuwei997.rangecache.store;

import com.google.common.collect.RangeSet;
import com.google.common.collect.TreeRangeSet;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.concurrent.locks.ReentrantLock;

public final class LocalRangeCacheEntry {

    private final ReentrantLock lock = new ReentrantLock();
    private final RangeSet<Instant> coverage = TreeRangeSet.create();
    private final NavigableMap<Instant, List<Object>> rowIdsByCoordinate = new TreeMap<>();
    private final Map<Object, Object> rowsById = new HashMap<>();
    private final Map<Object, Instant> coordinateById = new HashMap<>();

    public ReentrantLock lock() {
        return lock;
    }

    public RangeSet<Instant> coverage() {
        return coverage;
    }

    public NavigableMap<Instant, List<Object>> rowIdsByCoordinate() {
        return rowIdsByCoordinate;
    }

    public Map<Object, Object> rowsById() {
        return rowsById;
    }

    public Map<Object, Instant> coordinateById() {
        return coordinateById;
    }
}
