package io.github.liuwei997.rangecache.autoconfigure;

import com.google.common.collect.RangeSet;
import io.github.liuwei997.rangecache.aop.RangeCacheAdvisor;
import io.github.liuwei997.rangecache.aop.RangeCacheInterceptor;
import io.github.liuwei997.rangecache.aop.RangeCacheKeyGenerator;
import io.github.liuwei997.rangecache.aop.RangeCacheOperationSource;
import io.github.liuwei997.rangecache.config.RangeCacheProperties;
import io.github.liuwei997.rangecache.core.DefaultRangeCacheManager;
import io.github.liuwei997.rangecache.core.RangeCacheExecutor;
import io.github.liuwei997.rangecache.core.RangeCacheManager;
import io.github.liuwei997.rangecache.planner.DefaultRangeQueryPlanner;
import io.github.liuwei997.rangecache.planner.RangeQueryPlanner;
import io.github.liuwei997.rangecache.store.LocalRangeCacheStore;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Role;

@AutoConfiguration
@ConditionalOnClass({MethodInterceptor.class, RangeSet.class})
@ConditionalOnProperty(prefix = "rangecache", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(RangeCacheProperties.class)
public class RangeCacheAutoConfiguration {

    @Bean
    LocalRangeCacheStore rangeCacheStore(RangeCacheProperties properties) {
        properties.validate();
        return new LocalRangeCacheStore(properties.getMaximumSeries());
    }

    @Bean
    @ConditionalOnMissingBean
    RangeQueryPlanner rangeQueryPlanner(RangeCacheProperties properties) {
        return new DefaultRangeQueryPlanner(properties.getMaxDeltaQueries());
    }

    @Bean
    RangeCacheExecutor rangeCacheExecutor(
            LocalRangeCacheStore store,
            RangeQueryPlanner planner) {
        return new RangeCacheExecutor(store, planner);
    }

    @Bean
    @ConditionalOnMissingBean(RangeCacheManager.class)
    RangeCacheManager rangeCacheManager(RangeCacheExecutor executor) {
        return new DefaultRangeCacheManager(executor);
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    RangeCacheOperationSource rangeCacheOperationSource() {
        return new RangeCacheOperationSource();
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    RangeCacheKeyGenerator rangeCacheKeyGenerator() {
        return new RangeCacheKeyGenerator();
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    RangeCacheInterceptor rangeCacheInterceptor(
            RangeCacheOperationSource operationSource,
            RangeCacheKeyGenerator keyGenerator,
            RangeCacheExecutor executor) {
        return new RangeCacheInterceptor(operationSource, keyGenerator, executor);
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    RangeCacheAdvisor rangeCacheAdvisor(
            RangeCacheOperationSource operationSource,
            RangeCacheInterceptor interceptor) {
        return new RangeCacheAdvisor(operationSource, interceptor);
    }
}
