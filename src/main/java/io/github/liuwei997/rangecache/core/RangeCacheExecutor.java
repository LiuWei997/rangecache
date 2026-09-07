package io.github.liuwei997.rangecache.core;

import com.google.common.collect.BoundType;
import com.google.common.collect.Range;
import io.github.liuwei997.rangecache.planner.QueryDecision;
import io.github.liuwei997.rangecache.planner.QueryPlan;
import io.github.liuwei997.rangecache.planner.RangeQueryPlanner;
import io.github.liuwei997.rangecache.store.LocalRangeCacheEntry;
import io.github.liuwei997.rangecache.store.LocalRangeCacheStore;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;

public final class RangeCacheExecutor implements RangeCacheManager {
    private final LocalRangeCacheStore store;
    private final RangeQueryPlanner planner;

    public RangeCacheExecutor(LocalRangeCacheStore store, RangeQueryPlanner planner) {
        this.store = Objects.requireNonNull(store, "store");
        this.planner = Objects.requireNonNull(planner, "planner");
    }

    public <R extends Comparable<? super R>> RangeCacheExecution execute(
            SeriesKey seriesKey, Range<R> requestedRange, String rangeProperty,
            String uniqueKeyProperty, RangeLoader<R> loader) throws Throwable {
        Objects.requireNonNull(seriesKey, "seriesKey");
        Objects.requireNonNull(requestedRange, "requestedRange");
        Objects.requireNonNull(loader, "loader");
        try (LocalRangeCacheStore.Lease<R> lease = store.acquire(seriesKey)) {
            LocalRangeCacheEntry<R> entry = lease.entry();
            entry.lock().lock();
            try {
                entry.bindRangeType(requestedRange.lowerEndpoint().getClass());
                QueryPlan<R> plan = planner.plan(requestedRange, entry.coverage());
                if (plan.decision() == QueryDecision.CACHE_ONLY) {
                    return new RangeCacheExecution(plan.decision(), plan.missingRangeCount(), 0, slice(entry, requestedRange));
                }
                List<FetchedBatch<R>> batches = new ArrayList<>();
                for (Range<R> range : plan.rangesToFetch()) {
                    batches.add(resolveBatch(loader.load(range), range, rangeProperty, uniqueKeyProperty,
                        requestedRange.lowerEndpoint().getClass()));
                }
                if (plan.decision() == QueryDecision.FULL_FETCH) entry.removeRange(requestedRange);
                commit(entry, batches);
                return new RangeCacheExecution(plan.decision(), plan.missingRangeCount(), plan.rangesToFetch().size(),
                    slice(entry, requestedRange));
            } finally { entry.lock().unlock(); }
        }
    }

    @Override
    public void clearSeries(SeriesKey seriesKey) {
        store.clearSeries(Objects.requireNonNull(seriesKey, "seriesKey"));
    }

    @Override
    public void clearMethod(String cacheName) {
        store.clearMethod(Objects.requireNonNull(cacheName, "cacheName"));
    }

    @Override
    public void clearAll() {
        store.clearAll();
    }

    @Override
    public void evictEntity(SeriesKey seriesKey, Object uniqueKey) {
        Objects.requireNonNull(seriesKey, "seriesKey");
        Objects.requireNonNull(uniqueKey, "uniqueKey");
        try (LocalRangeCacheStore.Lease<?> lease = store.acquireIfPresent(seriesKey)) {
            if (lease == null) return;
            LocalRangeCacheEntry<?> entry = lease.entry();
            entry.lock().lock();
            try {
                entry.removeEntity(uniqueKey);
            } finally {
                entry.lock().unlock();
            }
        }
    }

    @Override
    public <R extends Comparable<? super R>> void invalidateRange(SeriesKey seriesKey, Range<R> range) {
        Objects.requireNonNull(seriesKey, "seriesKey");
        Objects.requireNonNull(range, "range");
        requireClosedRange(range);
        try (LocalRangeCacheStore.Lease<R> lease = store.acquireIfPresent(seriesKey)) {
            if (lease == null) return;
            LocalRangeCacheEntry<R> entry = lease.entry();
            entry.lock().lock();
            try {
                entry.bindRangeType(range.lowerEndpoint().getClass());
                entry.removeRange(range);
                entry.coverage().remove(range);
            } finally {
                entry.lock().unlock();
            }
        }
    }

    private <R extends Comparable<? super R>> FetchedBatch<R> resolveBatch(List<?> values,
            Range<R> fetchedRange, String rangeProperty, String uniqueKeyProperty, Class<?> expectedType) {
        if (values == null) throw new InvalidRangeCacheResultException("Annotated method returned null; expected List");
        Map<Object, ResolvedRow<R>> rows = new HashMap<>();
        for (Object value : values) {
            if (value == null) throw new InvalidRangeCacheResultException("Annotated method returned a null row");
            BeanWrapper wrapper = new BeanWrapperImpl(value);
            R coordinate = coordinate(readProperty(wrapper, rangeProperty), expectedType, rangeProperty);
            Object id = readProperty(wrapper, uniqueKeyProperty);
            if (!(id instanceof Comparable<?>)) throw new InvalidRangeCacheResultException(
                "Property '" + uniqueKeyProperty + "' must be non-null and Comparable");
            if (!fetchedRange.contains(coordinate)) throw new InvalidRangeCacheResultException(
                "Row coordinate " + coordinate + " is outside fetched range " + fetchedRange);
            rows.put(id, new ResolvedRow<>(id, coordinate, value));
        }
        return new FetchedBatch<>(fetchedRange, List.copyOf(rows.values()));
    }

    private void requireClosedRange(Range<?> range) {
        if (!range.hasLowerBound() || !range.hasUpperBound()
                || range.lowerBoundType() != BoundType.CLOSED || range.upperBoundType() != BoundType.CLOSED) {
            throw new IllegalArgumentException("Invalidated range must be closed and bounded");
        }
        if (range.lowerEndpoint().getClass() != range.upperEndpoint().getClass()) {
            throw new IllegalArgumentException("Invalidated range endpoints must have the same concrete type");
        }
    }

    @SuppressWarnings("unchecked")
    private <R extends Comparable<? super R>> R coordinate(Object value, Class<?> expectedType, String property) {
        if (value == null) throw new InvalidRangeCacheResultException("Property '" + property + "' must be non-null " + expectedType.getName());
        if (value.getClass() != expectedType) throw new InvalidRangeCacheResultException("Property '" + property
            + "' must be " + expectedType.getName() + " but was " + value.getClass().getName());
        return (R) value;
    }

    private Object readProperty(BeanWrapper wrapper, String property) {
        if (property == null || property.isBlank() || !wrapper.isReadableProperty(property)) throw new InvalidRangeCacheResultException(
            "Result type " + wrapper.getWrappedClass().getName() + " has no readable property '" + property + "'");
        try { return wrapper.getPropertyValue(property); }
        catch (RuntimeException exception) { throw new InvalidRangeCacheResultException(
            "Could not read property '" + property + "' from " + wrapper.getWrappedClass().getName(), exception); }
    }

    private <R extends Comparable<? super R>> void commit(LocalRangeCacheEntry<R> entry, List<FetchedBatch<R>> batches) {
        for (FetchedBatch<R> batch : batches) {
            for (ResolvedRow<R> row : batch.rows()) {
                R previous = entry.coordinateById().get(row.id());
                if (previous == null) add(entry, row);
                // The unique key identifies a row. A later row with that key always
                // replaces the cached value; only a changed coordinate needs an
                // index move. compareTo matches the TreeMap/Guava range semantics
                // (for example, BigDecimal 1.0 and 1.00 share one coordinate).
                else if (previous.compareTo(row.coordinate()) != 0) {
                    entry.removeEntity(row.id());
                    add(entry, row);
                }
                entry.rowsById().put(row.id(), row.value());
                entry.coordinateById().put(row.id(), row.coordinate());
            }
            entry.coverage().add(batch.range());
        }
    }

    private <R extends Comparable<? super R>> void add(LocalRangeCacheEntry<R> entry, ResolvedRow<R> row) {
        entry.rowIdsByCoordinate().computeIfAbsent(row.coordinate(), ignored -> new ArrayList<>()).add(row.id());
        entry.rowIdsByCoordinate().get(row.coordinate()).sort(this::compareIds);
    }

    private <R extends Comparable<? super R>> List<?> slice(LocalRangeCacheEntry<R> entry, Range<R> range) {
        List<Object> values = new ArrayList<>();
        entry.rowIdsByCoordinate().subMap(range.lowerEndpoint(), true, range.upperEndpoint(), true).values()
            .forEach(ids -> ids.forEach(id -> values.add(entry.rowsById().get(id))));
        return List.copyOf(values);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private int compareIds(Object left, Object right) {
        try { return ((Comparable) left).compareTo(right); }
        catch (RuntimeException exception) { throw new InvalidRangeCacheResultException("Unique-key values must be mutually comparable: "
            + left.getClass().getName() + " and " + right.getClass().getName(), exception); }
    }

    private record ResolvedRow<R extends Comparable<? super R>>(Object id, R coordinate, Object value) { }
    private record FetchedBatch<R extends Comparable<? super R>>(Range<R> range, List<ResolvedRow<R>> rows) { }
}
