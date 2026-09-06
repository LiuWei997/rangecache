package io.github.liuwei997.rangecache.core;

public final class InvalidRangeCacheResultException extends RuntimeException {

    public InvalidRangeCacheResultException(String message) {
        super(message);
    }

    public InvalidRangeCacheResultException(String message, Throwable cause) {
        super(message, cause);
    }
}
