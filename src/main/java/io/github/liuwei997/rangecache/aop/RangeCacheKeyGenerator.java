package io.github.liuwei997.rangecache.aop;

import io.github.liuwei997.rangecache.core.SeriesKey;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.cache.interceptor.SimpleKey;
import org.springframework.cache.interceptor.SimpleKeyGenerator;

public final class RangeCacheKeyGenerator {

    private final SpelExpressionParser parser = new SpelExpressionParser();
    private final DefaultParameterNameDiscoverer parameterNames = new DefaultParameterNameDiscoverer();
    private final Map<ExpressionKey, Expression> expressions = new ConcurrentHashMap<>();

    public SeriesKey generate(RangeCacheOperation operation, Object target, Object[] arguments) {
        Object argumentKey = operation.keyExpression().isBlank()
            ? automaticKey(operation, arguments)
            : expressionKey(operation, target, arguments);

        if (argumentKey == null) {
            argumentKey = SimpleKey.EMPTY;
        }
        return new SeriesKey(operation.cacheName(), operation.methodIdentity(), argumentKey);
    }

    private Object automaticKey(RangeCacheOperation operation, Object[] arguments) {
        List<Object> keyArguments = new ArrayList<>(arguments.length - 2);
        for (int index = 0; index < arguments.length; index++) {
            if (index != operation.startIndex() && index != operation.endIndex()) {
                keyArguments.add(arguments[index]);
            }
        }
        return SimpleKeyGenerator.generateKey(keyArguments.toArray());
    }

    private Object expressionKey(RangeCacheOperation operation, Object target, Object[] arguments) {
        Expression expression = expressions.computeIfAbsent(
            new ExpressionKey(operation.annotatedMethod(), operation.keyExpression()),
            key -> parser.parseExpression(key.expression()));
        MethodBasedEvaluationContext context = new MethodBasedEvaluationContext(
            target, operation.annotatedMethod(), arguments, parameterNames);
        return expression.getValue(context);
    }

    private record ExpressionKey(Method method, String expression) {
    }
}
