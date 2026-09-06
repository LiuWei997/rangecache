package io.github.liuwei997.rangecache.core;

public final class InvalidRangeCacheMethodException extends RuntimeException {

    public InvalidRangeCacheMethodException(String message) {
        super(message);
    }

    public InvalidRangeCacheMethodException(String message, Throwable cause) {
        super(message, cause);
    }
}
