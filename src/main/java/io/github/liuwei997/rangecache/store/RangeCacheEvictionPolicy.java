package io.github.liuwei997.rangecache.store;

interface RangeCacheEvictionPolicy<K> {

    void onAccess(K key);

    K selectEvictionCandidate();

    void onRemoval(K key);

    void clear();
}
