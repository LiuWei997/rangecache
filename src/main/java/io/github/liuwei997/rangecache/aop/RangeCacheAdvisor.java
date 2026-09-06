package io.github.liuwei997.rangecache.aop;

import java.lang.reflect.Method;
import org.aopalliance.aop.Advice;
import org.springframework.aop.Pointcut;
import org.springframework.aop.support.AbstractPointcutAdvisor;
import org.springframework.aop.support.StaticMethodMatcherPointcut;
import org.springframework.core.Ordered;

public final class RangeCacheAdvisor extends AbstractPointcutAdvisor {

    private static final int ORDER = Ordered.LOWEST_PRECEDENCE - 100;

    private final RangeCacheInterceptor interceptor;
    private final Pointcut pointcut;

    public RangeCacheAdvisor(
            RangeCacheOperationSource operationSource,
            RangeCacheInterceptor interceptor) {
        this.interceptor = interceptor;
        this.pointcut = new StaticMethodMatcherPointcut() {
            @Override
            public boolean matches(Method method, Class<?> targetClass) {
                return operationSource.getOperation(method, targetClass) != null;
            }
        };
    }

    @Override
    public Advice getAdvice() {
        return interceptor;
    }

    @Override
    public Pointcut getPointcut() {
        return pointcut;
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
