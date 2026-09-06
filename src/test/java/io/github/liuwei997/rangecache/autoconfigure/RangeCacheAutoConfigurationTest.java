package io.github.liuwei997.rangecache.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.Range;
import io.github.liuwei997.rangecache.annotation.RangeCacheBypass;
import io.github.liuwei997.rangecache.annotation.RangeCacheable;
import io.github.liuwei997.rangecache.annotation.RangeEnd;
import io.github.liuwei997.rangecache.annotation.RangeStart;
import io.github.liuwei997.rangecache.aop.RangeCacheAdvisor;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.junit.jupiter.api.Test;

class RangeCacheAutoConfigurationTest {

    private static final Instant ZERO = Instant.parse("2026-01-01T00:00:00Z");

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(
            AopAutoConfiguration.class,
            RangeCacheAutoConfiguration.class))
        .withUserConfiguration(TestConfiguration.class);

    @Test
    void annotationWorksWithoutUserAopConfiguration() {
        contextRunner.run(context -> {
            QueryService service = context.getBean(QueryService.class);

            assertThat(service.query("user-1", at(1), at(10))).hasSize(2);
            assertThat(service.query("user-1", at(1), at(15))).hasSize(3);
            assertThat(service.calls()).containsExactly(
                Range.closedOpen(at(1), at(10)),
                Range.closedOpen(at(10), at(15)));
        });
    }

    @Test
    void disabledPropertyDoesNotRegisterTheAdvisor() {
        contextRunner.withPropertyValues("rangecache.enabled=false").run(context -> {
            assertThat(context).doesNotHaveBean(RangeCacheAdvisor.class);
            QueryService service = context.getBean(QueryService.class);
            service.query("user-1", at(1), at(10));
            service.query("user-1", at(1), at(10));
            assertThat(service.calls()).hasSize(2);
        });
    }

    @Test
    void firstParameterBypassMarkerSkipsReadAndWrite() {
        contextRunner.run(context -> {
            QueryService service = context.getBean(QueryService.class);

            assertThat(service.bypassedQuery("user-1", at(1), at(10))).hasSize(2);
            assertThat(service.bypassedQuery("user-1", at(1), at(10))).hasSize(2);
            assertThat(service.calls()).containsExactly(
                Range.closedOpen(at(1), at(10)),
                Range.closedOpen(at(1), at(10)));
        });
    }

    private static Instant at(long second) {
        return ZERO.plusSeconds(second);
    }

    interface QueryService {

        @RangeCacheable(rangeProperty = "createdAt", uniqueKeyProperty = "id")
        List<Row> query(String userId, @RangeStart Instant from, @RangeEnd Instant to);

        @RangeCacheable(rangeProperty = "createdAt", uniqueKeyProperty = "id")
        List<Row> bypassedQuery(
            @RangeCacheBypass String userId,
            @RangeStart Instant from,
            @RangeEnd Instant to);

        List<Range<Instant>> calls();
    }

    static class DefaultQueryService implements QueryService {
        private final List<Row> source = List.of(
            new Row(at(1), 1L), new Row(at(5), 2L), new Row(at(13), 3L));
        private final List<Range<Instant>> calls = new ArrayList<>();

        @Override
        public List<Row> query(String userId, Instant from, Instant to) {
            Range<Instant> range = Range.closedOpen(from, to);
            calls.add(range);
            return source.stream().filter(row -> range.contains(row.getCreatedAt())).toList();
        }

        @Override
        public List<Row> bypassedQuery(String userId, Instant from, Instant to) {
            Range<Instant> range = Range.closedOpen(from, to);
            calls.add(range);
            return source.stream().filter(row -> range.contains(row.getCreatedAt())).toList();
        }

        @Override
        public List<Range<Instant>> calls() {
            return calls;
        }
    }

    static final class Row {
        private final Instant createdAt;
        private final Long id;

        Row(Instant createdAt, Long id) {
            this.createdAt = createdAt;
            this.id = id;
        }

        public Instant getCreatedAt() {
            return createdAt;
        }

        public Long getId() {
            return id;
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class TestConfiguration {
        @Bean
        QueryService queryService() {
            return new DefaultQueryService();
        }
    }
}
