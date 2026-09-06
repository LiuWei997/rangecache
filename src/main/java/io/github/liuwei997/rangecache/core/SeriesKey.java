package io.github.liuwei997.rangecache.core;

import java.util.Objects;

public record SeriesKey(String cacheName, MethodIdentity methodIdentity, Object argumentKey) {

    public SeriesKey {
        Objects.requireNonNull(cacheName, "cacheName");
        Objects.requireNonNull(methodIdentity, "methodIdentity");
        Objects.requireNonNull(argumentKey, "argumentKey");
    }
}
