package io.github.liuwei997.rangecache.core;

import com.google.common.collect.Range;
import io.github.liuwei997.rangecache.planner.QueryDecision;
import io.github.liuwei997.rangecache.planner.QueryPlan;
import io.github.liuwei997.rangecache.planner.RangeQueryPlanner;
import io.github.liuwei997.rangecache.store.LocalRangeCacheEntry;
import io.github.liuwei997.rangecache.store.LocalRangeCacheStore;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
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

    public RangeCacheExecution execute(
            SeriesKey seriesKey,
            Range<Instant> requestedRange,
            String rangeProperty,
            String uniqueKeyProperty,
            RangeLoader loader) throws Throwable {

        Objects.requireNonNull(seriesKey, "seriesKey");
        Objects.requireNonNull(requestedRange, "requestedRange");
        Objects.requireNonNull(loader, "loader");

        try (LocalRangeCacheStore.Lease lease = store.acquire(seriesKey)) {
            LocalRangeCacheEntry entry = lease.entry();
            entry.lock().lock();
            try {
                QueryPlan plan = planner.plan(requestedRange, entry.coverage());
                if (plan.decision() == QueryDecision.CACHE_ONLY) {
                    return new RangeCacheExecution(
                        plan.decision(), plan.missingRangeCount(), 0, slice(entry, requestedRange));
                }

                List<FetchedBatch> batches = new ArrayList<>();
                for (Range<Instant> range : plan.rangesToFetch()) {
                    List<?> values = loader.load(range);
                    batches.add(resolveBatch(values, range, rangeProperty, uniqueKeyProperty));
                }

                validateAgainstEntry(entry, batches, plan.decision(), requestedRange);
                if (plan.decision() == QueryDecision.FULL_FETCH) {
                    removeRange(entry, requestedRange);
                }
                commit(entry, batches);
                return new RangeCacheExecution(
                    plan.decision(),
                    plan.missingRangeCount(),
                    plan.rangesToFetch().size(),
                    slice(entry, requestedRange));
            } finally {
                entry.lock().unlock();
            }
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

    private FetchedBatch resolveBatch(
            List<?> values,
            Range<Instant> fetchedRange,
            String rangeProperty,
            String uniqueKeyProperty) {

        if (values == null) {
            throw new InvalidRangeCacheResultException("Annotated method returned null; expected List");
        }

        Map<Object, ResolvedRow> rows = new HashMap<>();
        for (Object value : values) {
            if (value == null) {
                throw new InvalidRangeCacheResultException("Annotated method returned a null row");
            }
            BeanWrapper wrapper = new BeanWrapperImpl(value);
            Object coordinateValue = readProperty(wrapper, rangeProperty);
            Object id = readProperty(wrapper, uniqueKeyProperty);
            if (!(coordinateValue instanceof Instant coordinate)) {
                throw new InvalidRangeCacheResultException(
                    "Property '" + rangeProperty + "' must be a non-null Instant");
            }
            if (!(id instanceof Comparable<?>)) {
                throw new InvalidRangeCacheResultException(
                    "Property '" + uniqueKeyProperty + "' must be non-null and Comparable");
            }
            if (!fetchedRange.contains(coordinate)) {
                throw new InvalidRangeCacheResultException(
                    "Row coordinate " + coordinate + " is outside fetched range " + fetchedRange);
            }

            ResolvedRow row = new ResolvedRow(id, coordinate, value);
            ResolvedRow previous = rows.putIfAbsent(id, row);
            if (previous != null
                    && (!previous.coordinate().equals(coordinate) || !Objects.equals(previous.value(), value))) {
                throw new InvalidRangeCacheResultException(
                    "Unique key '" + id + "' identifies conflicting rows in one fetch");
            }
        }
        return new FetchedBatch(fetchedRange, List.copyOf(rows.values()));
    }

    private Object readProperty(BeanWrapper wrapper, String property) {
        if (property == null || property.isBlank() || !wrapper.isReadableProperty(property)) {
            throw new InvalidRangeCacheResultException(
                "Result type " + wrapper.getWrappedClass().getName()
                    + " has no readable property '" + property + "'");
        }
        try {
            return wrapper.getPropertyValue(property);
        } catch (RuntimeException exception) {
            throw new InvalidRangeCacheResultException(
                "Could not read property '" + property + "' from " + wrapper.getWrappedClass().getName(),
                exception);
        }
    }

    private void validateAgainstEntry(
            LocalRangeCacheEntry entry,
            List<FetchedBatch> batches,
            QueryDecision decision,
            Range<Instant> requestedRange) {

        Map<Object, ResolvedRow> incoming = new HashMap<>();
        for (FetchedBatch batch : batches) {
            for (ResolvedRow row : batch.rows()) {
                ResolvedRow previous = incoming.putIfAbsent(row.id(), row);
                if (previous != null
                        && (!previous.coordinate().equals(row.coordinate())
                        || !Objects.equals(previous.value(), row.value()))) {
                    throw new InvalidRangeCacheResultException(
                        "Unique key '" + row.id() + "' identifies conflicting fetched rows");
                }

                Instant existingCoordinate = entry.coordinateById().get(row.id());
                if (existingCoordinate == null) {
                    continue;
                }
                boolean willBeReplaced = decision == QueryDecision.FULL_FETCH
                    && requestedRange.contains(existingCoordinate);
                if (!willBeReplaced && (!existingCoordinate.equals(row.coordinate())
                        || !Objects.equals(entry.rowsById().get(row.id()), row.value()))) {
                    throw new InvalidRangeCacheResultException(
                        "Unique key '" + row.id() + "' conflicts with an existing cached row");
                }
            }
        }

        Map<Instant, List<Object>> idsToCheck = new HashMap<>();
        entry.rowIdsByCoordinate().forEach(
            (coordinate, ids) -> idsToCheck.put(coordinate, new ArrayList<>(ids)));
        incoming.values().forEach(
            row -> idsToCheck.computeIfAbsent(row.coordinate(), ignored -> new ArrayList<>()).add(row.id()));
        idsToCheck.values().forEach(ids -> ids.sort(this::compareIds));
    }

    private void commit(LocalRangeCacheEntry entry, List<FetchedBatch> batches) {
        for (FetchedBatch batch : batches) {
            for (ResolvedRow row : batch.rows()) {
                if (!entry.rowsById().containsKey(row.id())) {
                    entry.rowIdsByCoordinate()
                        .computeIfAbsent(row.coordinate(), ignored -> new ArrayList<>())
                        .add(row.id());
                    entry.rowIdsByCoordinate().get(row.coordinate()).sort(this::compareIds);
                }
                entry.rowsById().put(row.id(), row.value());
                entry.coordinateById().put(row.id(), row.coordinate());
            }
            entry.coverage().add(batch.range());
        }
    }

    private void removeRange(LocalRangeCacheEntry entry, Range<Instant> range) {
        NavigableMap<Instant, List<Object>> selected = entry.rowIdsByCoordinate().subMap(
            range.lowerEndpoint(), true, range.upperEndpoint(), true);
        List<Instant> coordinates = new ArrayList<>(selected.keySet());
        for (Instant coordinate : coordinates) {
            List<Object> ids = entry.rowIdsByCoordinate().remove(coordinate);
            if (ids != null) {
                for (Object id : ids) {
                    entry.rowsById().remove(id);
                    entry.coordinateById().remove(id);
                }
            }
        }
    }

    private List<?> slice(LocalRangeCacheEntry entry, Range<Instant> range) {
        List<Object> values = new ArrayList<>();
        entry.rowIdsByCoordinate()
            .subMap(range.lowerEndpoint(), true, range.upperEndpoint(), true)
            .values()
            .forEach(ids -> ids.forEach(id -> values.add(entry.rowsById().get(id))));
        return List.copyOf(values);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private int compareIds(Object left, Object right) {
        try {
            return ((Comparable) left).compareTo(right);
        } catch (RuntimeException exception) {
            throw new InvalidRangeCacheResultException(
                "Unique-key values must be mutually comparable: "
                    + left.getClass().getName() + " and " + right.getClass().getName(), exception);
        }
    }

    private record ResolvedRow(Object id, Instant coordinate, Object value) {
    }

    private record FetchedBatch(Range<Instant> range, List<ResolvedRow> rows) {
    }
}
