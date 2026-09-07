package io.github.liuwei997.rangecache.store;

import com.google.common.collect.Range;
import com.google.common.collect.RangeSet;
import com.google.common.collect.TreeRangeSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.concurrent.locks.ReentrantLock;

public final class LocalRangeCacheEntry<R extends Comparable<? super R>> {

    private final ReentrantLock lock = new ReentrantLock();
    private final RangeSet<R> coverage = TreeRangeSet.create();
    private final NavigableMap<R, List<Object>> rowIdsByCoordinate = new TreeMap<>();
    private final Map<Object, Object> rowsById = new HashMap<>();
    private final Map<Object, R> coordinateById = new HashMap<>();
    private Class<?> rangeType;

    public ReentrantLock lock() {
        return lock;
    }

    /** Must be called while {@link #lock()} is held. */
    public void bindRangeType(Class<?> requestedType) {
        if (rangeType == null) {
            rangeType = requestedType;
        } else if (rangeType != requestedType) {
            throw new IllegalArgumentException("Cached series range type is " + rangeType.getName()
                + " but request uses " + requestedType.getName());
        }
    }

    public RangeSet<R> coverage() {
        return coverage;
    }

    public NavigableMap<R, List<Object>> rowIdsByCoordinate() {
        return rowIdsByCoordinate;
    }

    public Map<Object, Object> rowsById() {
        return rowsById;
    }

    public Map<Object, R> coordinateById() {
        return coordinateById;
    }

    /** Removes a row from every index without changing coverage. Must hold {@link #lock()}. */
    public void removeEntity(Object uniqueKey) {
        R coordinate = coordinateById.remove(uniqueKey);
        rowsById.remove(uniqueKey);
        if (coordinate == null) {
            return;
        }
        removeFromCoordinateIndex(coordinate, uniqueKey);
    }

    /** Removes rows in a range from every index without changing coverage. Must hold {@link #lock()}. */
    public void removeRange(Range<R> range) {
        NavigableMap<R, List<Object>> selected = rowIdsByCoordinate.subMap(
            range.lowerEndpoint(), true, range.upperEndpoint(), true);
        for (R coordinate : new ArrayList<>(selected.keySet())) {
            for (Object uniqueKey : rowIdsByCoordinate.remove(coordinate)) {
                rowsById.remove(uniqueKey);
                coordinateById.remove(uniqueKey);
            }
        }
    }

    private void removeFromCoordinateIndex(R coordinate, Object uniqueKey) {
        List<Object> keys = rowIdsByCoordinate.get(coordinate);
        if (keys == null) {
            return;
        }
        keys.removeIf(existing -> java.util.Objects.equals(existing, uniqueKey));
        if (keys.isEmpty()) {
            rowIdsByCoordinate.remove(coordinate);
        }
    }
}
