package io.github.liuwei997.rangecache.core;

import java.util.Objects;

/**
 * Identifies one logical range-query series. Range endpoints are intentionally
 * excluded so requests with the same method and non-range arguments can share
 * cached coverage.
 *
 * @param cacheName configured cache name
 * @param methodIdentity annotated method identity, preventing same-name methods from sharing a series
 * @param argumentKey automatic non-range argument key or the configured SpEL key result
 */
public record SeriesKey(String cacheName, MethodIdentity methodIdentity, Object argumentKey) {

    public SeriesKey {
        Objects.requireNonNull(cacheName, "cacheName");
        Objects.requireNonNull(methodIdentity, "methodIdentity");
        Objects.requireNonNull(argumentKey, "argumentKey");
    }
}
