package io.github.liuwei997.rangecache.store;

import io.github.liuwei997.rangecache.core.SeriesKey;
import io.github.liuwei997.rangecache.core.MethodIdentity;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class LocalRangeCacheStore {

    private final int maximumSeries;
    private final Map<SeriesKey, Holder> entries = new HashMap<>();
    private final RangeCacheEvictionPolicy<SeriesKey> evictionPolicy = new LruRangeCacheEvictionPolicy<>();

    public LocalRangeCacheStore(int maximumSeries) {
        if (maximumSeries < 1) {
            throw new IllegalArgumentException("maximumSeries must be at least 1");
        }
        this.maximumSeries = maximumSeries;
    }

    public synchronized Lease acquire(SeriesKey key) {
        Holder holder = entries.computeIfAbsent(key, ignored -> new Holder());
        holder.activeLeases++;
        evictionPolicy.onAccess(key);
        evictInactiveEntries();
        return new Lease(this, key, holder.entry);
    }

    public synchronized void clearSeries(SeriesKey seriesKey) {
        Objects.requireNonNull(seriesKey, "seriesKey");
        if (entries.remove(seriesKey) != null) {
            evictionPolicy.onRemoval(seriesKey);
        }
    }

    public synchronized void clearMethod(MethodIdentity methodIdentity) {
        Objects.requireNonNull(methodIdentity, "methodIdentity");
        entries.keySet().removeIf(key -> {
            if (key.methodIdentity().equals(methodIdentity)) {
                evictionPolicy.onRemoval(key);
                return true;
            }
            return false;
        });
    }

    public synchronized void clearAll() {
        entries.clear();
        evictionPolicy.clear();
    }

    synchronized int size() {
        return entries.size();
    }

    private synchronized void release(SeriesKey key, LocalRangeCacheEntry entry) {
        Holder holder = entries.get(key);
        if (holder != null && holder.entry == entry) {
            holder.activeLeases--;
        }
        evictInactiveEntries();
    }

    private void evictInactiveEntries() {
        int attempts = entries.size();
        while (entries.size() > maximumSeries && attempts-- > 0) {
            SeriesKey candidate = evictionPolicy.selectEvictionCandidate();
            if (candidate == null) {
                return;
            }
            Holder holder = entries.get(candidate);
            if (holder == null) {
                evictionPolicy.onRemoval(candidate);
            } else if (holder.activeLeases == 0) {
                entries.remove(candidate);
                evictionPolicy.onRemoval(candidate);
            } else {
                evictionPolicy.onAccess(candidate);
            }
        }
    }

    private static final class Holder {
        private final LocalRangeCacheEntry entry = new LocalRangeCacheEntry();
        private int activeLeases;
    }

    public static final class Lease implements AutoCloseable {
        private final LocalRangeCacheStore store;
        private final SeriesKey key;
        private final LocalRangeCacheEntry entry;
        private boolean closed;

        private Lease(LocalRangeCacheStore store, SeriesKey key, LocalRangeCacheEntry entry) {
            this.store = store;
            this.key = key;
            this.entry = entry;
        }

        public LocalRangeCacheEntry entry() {
            return entry;
        }

        @Override
        public void close() {
            synchronized (store) {
                if (!closed) {
                    closed = true;
                    store.release(key, entry);
                }
            }
        }
    }
}
