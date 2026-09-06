package io.github.liuwei997.rangecache.store;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.liuwei997.rangecache.core.MethodIdentity;
import io.github.liuwei997.rangecache.core.SeriesKey;
import java.util.List;
import org.junit.jupiter.api.Test;

class LocalRangeCacheStoreTest {

    @Test
    void evictsTheLeastRecentlyUsedSeries() {
        LocalRangeCacheStore store = new LocalRangeCacheStore(2);
        SeriesKey first = key("first");
        SeriesKey second = key("second");
        SeriesKey third = key("third");

        LocalRangeCacheEntry originalFirst;
        LocalRangeCacheEntry originalSecond;
        try (var lease = store.acquire(first)) {
            originalFirst = lease.entry();
        }
        try (var lease = store.acquire(second)) {
            originalSecond = lease.entry();
        }
        try (var ignored = store.acquire(first)) {
            // Make the first entry most recently used.
        }
        try (var ignored = store.acquire(third)) {
            // Evicts the second entry.
        }

        LocalRangeCacheEntry retainedFirst;
        LocalRangeCacheEntry recreatedSecond;
        try (var lease = store.acquire(first)) {
            retainedFirst = lease.entry();
        }
        try (var lease = store.acquire(second)) {
            recreatedSecond = lease.entry();
        }

        assertThat(retainedFirst).isSameAs(originalFirst);
        assertThat(recreatedSecond).isNotSameAs(originalSecond);
        assertThat(store.size()).isEqualTo(2);
    }

    @Test
    void clearsOneSeriesWithoutRemovingItsSiblingSeries() {
        LocalRangeCacheStore store = new LocalRangeCacheStore(10);
        SeriesKey first = key("first");
        SeriesKey second = key("second");

        LocalRangeCacheEntry firstEntry;
        LocalRangeCacheEntry secondEntry;
        try (var lease = store.acquire(first)) {
            firstEntry = lease.entry();
        }
        try (var lease = store.acquire(second)) {
            secondEntry = lease.entry();
        }

        store.clearSeries(first);

        try (var lease = store.acquire(first)) {
            assertThat(lease.entry()).isNotSameAs(firstEntry);
        }
        try (var lease = store.acquire(second)) {
            assertThat(lease.entry()).isSameAs(secondEntry);
        }
    }

    @Test
    void clearsEverySeriesForOneMethodOnly() {
        LocalRangeCacheStore store = new LocalRangeCacheStore(10);
        MethodIdentity query = new MethodIdentity("Type", "query", List.of());
        MethodIdentity other = new MethodIdentity("Type", "other", List.of());
        SeriesKey queryFirst = new SeriesKey("cache", query, "first");
        SeriesKey querySecond = new SeriesKey("cache", query, "second");
        SeriesKey otherSeries = new SeriesKey("cache", other, "first");

        LocalRangeCacheEntry queryFirstEntry;
        LocalRangeCacheEntry otherEntry;
        try (var lease = store.acquire(queryFirst)) {
            queryFirstEntry = lease.entry();
        }
        try (var ignored = store.acquire(querySecond)) {
            // Populate a second series for the same method.
        }
        try (var lease = store.acquire(otherSeries)) {
            otherEntry = lease.entry();
        }

        store.clearMethod(query);

        try (var lease = store.acquire(queryFirst)) {
            assertThat(lease.entry()).isNotSameAs(queryFirstEntry);
        }
        try (var lease = store.acquire(otherSeries)) {
            assertThat(lease.entry()).isSameAs(otherEntry);
        }
    }

    @Test
    void clearsEveryMethodAndSeries() {
        LocalRangeCacheStore store = new LocalRangeCacheStore(10);
        SeriesKey first = key("first");
        SeriesKey second = new SeriesKey(
            "other-cache", new MethodIdentity("OtherType", "query", List.of()), "second");

        try (var ignored = store.acquire(first)) {
            // Populate the first series.
        }
        try (var ignored = store.acquire(second)) {
            // Populate the second series.
        }
        assertThat(store.size()).isEqualTo(2);

        store.clearAll();

        assertThat(store.size()).isZero();
    }

    private SeriesKey key(String value) {
        return new SeriesKey("cache", new MethodIdentity("Type", "query", List.of()), value);
    }
}
