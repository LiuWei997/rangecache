package io.github.liuwei997.rangecache.aop;

import com.google.common.collect.Range;
import io.github.liuwei997.rangecache.core.InvalidRangeCacheMethodException;
import io.github.liuwei997.rangecache.core.RangeCacheExecution;
import io.github.liuwei997.rangecache.core.RangeCacheExecutor;
import io.github.liuwei997.rangecache.core.SeriesKey;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.ProxyMethodInvocation;
import org.springframework.aop.framework.AopProxyUtils;

public final class RangeCacheInterceptor implements MethodInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(RangeCacheInterceptor.class);

    private final RangeCacheOperationSource operationSource;
    private final RangeCacheKeyGenerator keyGenerator;
    private final RangeCacheExecutor executor;

    public RangeCacheInterceptor(
            RangeCacheOperationSource operationSource,
            RangeCacheKeyGenerator keyGenerator,
            RangeCacheExecutor executor) {
        this.operationSource = operationSource;
        this.keyGenerator = keyGenerator;
        this.executor = executor;
    }

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        Object target = invocation.getThis();
        Class<?> targetClass = target == null
            ? invocation.getMethod().getDeclaringClass()
            : AopProxyUtils.ultimateTargetClass(target);
        RangeCacheOperation operation = operationSource.getOperation(invocation.getMethod(), targetClass);
        if (operation == null) {
            return invocation.proceed();
        }
        if (!(invocation instanceof ProxyMethodInvocation proxyInvocation)) {
            throw new InvalidRangeCacheMethodException(
                "Range caching requires a Spring ProxyMethodInvocation for "
                    + operation.annotatedMethod().toGenericString());
        }

        Object[] originalArguments = invocation.getArguments();
        Instant start = requireInstant(originalArguments, operation.startIndex(), "start", operation);
        Instant end = requireInstant(originalArguments, operation.endIndex(), "end", operation);
        if (!start.isBefore(end)) {
            throw new IllegalArgumentException("Range must satisfy start < end for "
                + operation.annotatedMethod().toGenericString());
        }

        Range<Instant> requestedRange = Range.closedOpen(start, end);
        SeriesKey seriesKey = keyGenerator.generate(operation, target, originalArguments);
        RangeCacheExecution execution = executor.execute(
            seriesKey,
            requestedRange,
            operation.rangeProperty(),
            operation.uniqueKeyProperty(),
            range -> invokeRange(proxyInvocation, originalArguments, operation, range));

        logger.debug(
            "Range cache decision={} cache={} method={} request={} missingRanges={} databaseQueries={}",
            execution.decision(),
            operation.cacheName(),
            operation.methodIdentity().displayName(),
            requestedRange,
            execution.missingRangeCount(),
            execution.databaseQueries());
        return execution.rows();
    }

    private List<?> invokeRange(
            ProxyMethodInvocation invocation,
            Object[] originalArguments,
            RangeCacheOperation operation,
            Range<Instant> range) throws Throwable {

        Object[] modified = originalArguments.clone();
        modified[operation.startIndex()] = range.lowerEndpoint();
        modified[operation.endIndex()] = range.upperEndpoint();
        Object result = invocation.invocableClone(modified).proceed();
        if (!(result instanceof List<?> list)) {
            throw new InvalidRangeCacheMethodException(
                "Annotated method must return List: " + operation.annotatedMethod().toGenericString());
        }
        return list;
    }

    private Instant requireInstant(
            Object[] arguments,
            int index,
            String label,
            RangeCacheOperation operation) {
        Object value = arguments[index];
        if (!(value instanceof Instant instant)) {
            throw new IllegalArgumentException("Range " + label + " must be a non-null Instant for "
                + operation.annotatedMethod().toGenericString());
        }
        return instant;
    }
}
