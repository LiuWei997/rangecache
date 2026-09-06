package io.github.liuwei997.rangecache.aop;

import io.github.liuwei997.rangecache.core.MethodIdentity;
import java.lang.reflect.Method;

public record RangeCacheOperation(
        Method annotatedMethod,
        MethodIdentity methodIdentity,
        String cacheName,
        String keyExpression,
        boolean bypass,
        int startIndex,
        int endIndex,
        String rangeProperty,
        String uniqueKeyProperty) {
}
