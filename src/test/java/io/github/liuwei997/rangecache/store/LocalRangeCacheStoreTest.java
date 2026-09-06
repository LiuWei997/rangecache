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

    private SeriesKey key(String value) {
        return new SeriesKey("cache", new MethodIdentity("Type", "query", List.of()), value);
    }
}
