package io.github.liuwei997.rangecache.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.common.collect.Range;
import io.github.liuwei997.rangecache.planner.DefaultRangeQueryPlanner;
import io.github.liuwei997.rangecache.planner.QueryDecision;
import io.github.liuwei997.rangecache.store.LocalRangeCacheStore;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class RangeCacheExecutorTest {

    private static final Instant ZERO = Instant.parse("2026-01-01T00:00:00Z");
    private final RangeCacheExecutor executor = new RangeCacheExecutor(
        new LocalRangeCacheStore(100), new DefaultRangeQueryPlanner(5));
    private final SeriesKey key = new SeriesKey(
        "events", new MethodIdentity("Example", "query", List.of()), "user-1");

    @Test
    void fetchesOnlyAnUncoveredTailAndReturnsMergedRows() throws Throwable {
        List<Row> source = List.of(row(1, 1), row(5, 2), row(13, 3));
        List<Range<Instant>> calls = new ArrayList<>();
        RangeLoader loader = range -> {
            calls.add(range);
            return source.stream().filter(row -> range.contains(row.getCachedRange())).toList();
        };

        RangeCacheExecution first = execute(range(1, 10), loader);
        RangeCacheExecution second = execute(range(1, 15), loader);

        assertThat(first.decision()).isEqualTo(QueryDecision.DELTA_FETCH);
        assertThat(second.decision()).isEqualTo(QueryDecision.DELTA_FETCH);
        assertThat(second.rows()).isEqualTo(source);
        assertThat(calls).containsExactly(range(1, 10), range(10, 15));
    }

    @Test
    void successfulEmptyResultCreatesNegativeCoverage() throws Throwable {
        List<Range<Instant>> calls = new ArrayList<>();
        RangeLoader loader = range -> {
            calls.add(range);
            return List.of();
        };

        execute(range(1, 3), loader);
        RangeCacheExecution second = execute(range(1, 3), loader);

        assertThat(second.decision()).isEqualTo(QueryDecision.CACHE_ONLY);
        assertThat(calls).containsExactly(range(1, 3));
    }

    @Test
    void failedDeltaLeavesAllNewCoverageUncommitted() throws Throwable {
        execute(range(2, 3), ignored -> List.of(row(2, 1)));
        execute(range(4, 5), ignored -> List.of(row(4, 2)));

        List<Range<Instant>> failedCalls = new ArrayList<>();
        assertThatThrownBy(() -> execute(range(1, 6), range -> {
            failedCalls.add(range);
            if (failedCalls.size() == 2) {
                throw new IllegalStateException("database failed");
            }
            return List.of();
        })).isInstanceOf(IllegalStateException.class);

        List<Range<Instant>> retryCalls = new ArrayList<>();
        execute(range(1, 6), range -> {
            retryCalls.add(range);
            return List.of();
        });

        assertThat(retryCalls).containsExactly(range(1, 2), range(3, 4), range(5, 6));
    }

    @Test
    void rejectsRowsOutsideTheFetchedRange() {
        assertThatThrownBy(() -> execute(range(1, 2), ignored -> List.of(row(3, 1))))
            .isInstanceOf(InvalidRangeCacheResultException.class);
    }

    @Test
    void supportsAClosedSinglePointRange() throws Throwable {
        RangeCacheExecution result = execute(range(1, 1), ignored -> List.of(row(1, 1)));

        assertThat(result.rows()).hasSize(1);
    }

    @Test
    void supportsRecordProperties() throws Throwable {
        SeriesKey recordKey = new SeriesKey(
            "records", new MethodIdentity("Example", "records", List.of()), "user-1");

        RangeCacheExecution result = executor.execute(
            recordKey,
            range(1, 3),
            "cachedRange",
            "nonRepeatedKey",
            ignored -> List.of(new RecordRow(ZERO.plusSeconds(2), 7L)));

        assertThat(result.rows()).hasSize(1);
    }

    @Test
    void serializesConcurrentFetchesForTheSameSeries() throws Exception {
        AtomicInteger queries = new AtomicInteger();
        CountDownLatch firstQueryStarted = new CountDownLatch(1);
        CountDownLatch allowFirstQueryToFinish = new CountDownLatch(1);
        RangeLoader loader = ignored -> {
            queries.incrementAndGet();
            firstQueryStarted.countDown();
            if (!allowFirstQueryToFinish.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("test timed out");
            }
            return List.of(row(1, 1));
        };

        var pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> executeForConcurrentTest(loader));
            assertThat(firstQueryStarted.await(5, TimeUnit.SECONDS)).isTrue();
            var second = pool.submit(() -> executeForConcurrentTest(loader));
            allowFirstQueryToFinish.countDown();

            assertThat(first.get(5, TimeUnit.SECONDS).rows()).hasSize(1);
            assertThat(second.get(5, TimeUnit.SECONDS).rows()).hasSize(1);
            assertThat(queries).hasValue(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void clearsAllSeriesForACacheName() throws Throwable {
        LocalRangeCacheStore store = new LocalRangeCacheStore(100);
        RangeCacheExecutor manager = new RangeCacheExecutor(
            store, new DefaultRangeQueryPlanner(5));
        SeriesKey methodKey = new SeriesKey(
            "events", new MethodIdentity("Example", "query", List.of()), "user-1");
        AtomicInteger queries = new AtomicInteger();
        RangeLoader loader = ignored -> {
            queries.incrementAndGet();
            return List.of(row(1, 1));
        };

        manager.execute(methodKey, range(1, 2), "cachedRange", "nonRepeatedKey", loader);
        manager.clearMethod("events");
        manager.execute(methodKey, range(1, 2), "cachedRange", "nonRepeatedKey", loader);

        assertThat(queries).hasValue(2);
    }

    private RangeCacheExecution execute(Range<Instant> range, RangeLoader loader) throws Throwable {
        return executor.execute(key, range, "cachedRange", "nonRepeatedKey", loader);
    }

    private RangeCacheExecution executeForConcurrentTest(RangeLoader loader) {
        try {
            return execute(range(1, 2), loader);
        } catch (Throwable throwable) {
            throw new RuntimeException(throwable);
        }
    }

    private Row row(long second, long id) {
        return new Row(ZERO.plusSeconds(second), id);
    }

    private Range<Instant> range(long start, long end) {
        return Range.closed(ZERO.plusSeconds(start), ZERO.plusSeconds(end));
    }

    static final class Row {
        private final Instant cachedRange;
        private final Long nonRepeatedKey;

        Row(Instant cachedRange, Long nonRepeatedKey) {
            this.cachedRange = cachedRange;
            this.nonRepeatedKey = nonRepeatedKey;
        }

        public Instant getCachedRange() {
            return cachedRange;
        }

        public Long getNonRepeatedKey() {
            return nonRepeatedKey;
        }
    }

    record RecordRow(Instant cachedRange, Long nonRepeatedKey) {
    }
}
