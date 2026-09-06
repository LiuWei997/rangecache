package io.github.liuwei997.rangecache.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.common.collect.Range;
import io.github.liuwei997.rangecache.planner.DefaultRangeQueryPlanner;
import io.github.liuwei997.rangecache.planner.QueryDecision;
import io.github.liuwei997.rangecache.store.LocalRangeCacheStore;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class GenericRangeCacheExecutorTest {

    @Test
    void supportsDocumentedSortableCoordinates() throws Throwable {
        Instant instant = Instant.parse("2026-01-01T00:00:00Z");
        supports("Instant", instant, instant.plusSeconds(1), instant.plusSeconds(2));
        supports("LocalDateTime", LocalDateTime.of(2026, 1, 1, 0, 0), LocalDateTime.of(2026, 1, 2, 0, 0), LocalDateTime.of(2026, 1, 3, 0, 0));
        supports("LocalDate", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 2), LocalDate.of(2026, 1, 3));
        supports("OffsetDateTime", OffsetDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC), OffsetDateTime.of(2026, 1, 2, 0, 0, 0, 0, ZoneOffset.UTC), OffsetDateTime.of(2026, 1, 3, 0, 0, 0, 0, ZoneOffset.UTC));
        supports("ZonedDateTime", ZonedDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC), ZonedDateTime.of(2026, 1, 2, 0, 0, 0, 0, ZoneOffset.UTC), ZonedDateTime.of(2026, 1, 3, 0, 0, 0, 0, ZoneOffset.UTC));
        supports("Integer", 1, 2, 3); supports("Long", 1L, 2L, 3L);
        supports("BigDecimal", new BigDecimal("1.00"), new BigDecimal("2.00"), new BigDecimal("3.00"));
        supports("String", "a", "m", "z");
    }

    private <R extends Comparable<? super R>> void supports(String name, R start, R middle, R end) throws Throwable {
        RangeCacheExecutor executor = new RangeCacheExecutor(new LocalRangeCacheStore(10), new DefaultRangeQueryPlanner(5));
        SeriesKey key = new SeriesKey("generic", new MethodIdentity("Test", name, List.of()), "key");
        List<Range<R>> calls = new ArrayList<>();
        RangeLoader<R> loader = range -> {
            calls.add(range);
            List<Object> rows = new ArrayList<>();
            if (range.contains(start)) rows.add(new Row(start, 1L));
            if (range.contains(middle)) rows.add(new Row(middle, 2L));
            if (range.contains(end)) rows.add(new Row(end, 3L));
            return rows;
        };

        RangeCacheExecution first = executor.execute(key, Range.closed(start, middle), "cachedRange", "id", loader);
        RangeCacheExecution second = executor.execute(key, Range.closed(start, middle), "cachedRange", "id", loader);
        RangeCacheExecution third = executor.execute(key, Range.closed(start, end), "cachedRange", "id", loader);

        assertThat(first.rows()).hasSize(2);
        assertThat(second.decision()).isEqualTo(QueryDecision.CACHE_ONLY);
        assertThat(third.rows()).hasSize(3);
        assertThat(calls).containsExactly(Range.closed(start, middle), Range.closed(middle, end));
    }

    @Test
    void rejectsCoordinateTypeDifferentFromEndpointType() {
        RangeCacheExecutor executor = new RangeCacheExecutor(new LocalRangeCacheStore(10), new DefaultRangeQueryPlanner(5));
        SeriesKey key = new SeriesKey("generic", new MethodIdentity("Test", "bad", List.of()), "bad");
        assertThatThrownBy(() -> executor.execute(key, Range.closed(1, 3), "cachedRange", "id",
            range -> List.of(new Row("wrong-type", 1L))))
            .isInstanceOf(InvalidRangeCacheResultException.class)
            .hasMessageContaining("must be " + Integer.class.getName());
    }

    @Test
    void rejectsMixedCoordinateTypesForOneSeries() throws Throwable {
        RangeCacheExecutor executor = new RangeCacheExecutor(new LocalRangeCacheStore(10), new DefaultRangeQueryPlanner(5));
        SeriesKey key = new SeriesKey("generic", new MethodIdentity("Test", "mixed", List.of()), "mixed");
        executor.execute(key, Range.closed(1, 2), "cachedRange", "id", range -> List.of());
        assertThatThrownBy(() -> executor.execute(key, Range.closed("a", "b"), "cachedRange", "id", range -> List.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Cached series range type is " + Integer.class.getName());
    }

    record Row(Object cachedRange, Long id) { }
}
