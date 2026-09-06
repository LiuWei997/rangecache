package io.github.liuwei997.rangecache.store;

import java.util.Iterator;
import java.util.LinkedHashMap;

final class LruRangeCacheEvictionPolicy<K> implements RangeCacheEvictionPolicy<K> {

    private final LinkedHashMap<K, Boolean> accessOrder = new LinkedHashMap<>(16, 0.75f, true);

    @Override
    public void onAccess(K key) {
        accessOrder.put(key, Boolean.TRUE);
    }

    @Override
    public K selectEvictionCandidate() {
        Iterator<K> iterator = accessOrder.keySet().iterator();
        return iterator.hasNext() ? iterator.next() : null;
    }

    @Override
    public void onRemoval(K key) {
        accessOrder.remove(key);
    }

    @Override
    public void clear() {
        accessOrder.clear();
    }
}
