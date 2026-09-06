package io.github.liuwei997.rangecache.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.liuwei997.rangecache.annotation.RangeCacheable;
import io.github.liuwei997.rangecache.annotation.RangeEnd;
import io.github.liuwei997.rangecache.annotation.RangeStart;
import io.github.liuwei997.rangecache.core.RangeCacheManager;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootTest(
    classes = RangeCacheJpaIntegrationTest.TestApplication.class,
    properties = {
        "spring.datasource.url=jdbc:h2:mem:rangecache;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.open-in-view=false"
    })
class RangeCacheJpaIntegrationTest {

    private static final Instant ZERO = Instant.parse("2026-01-01T00:00:00Z");

    @Autowired
    private TestRepository repository;

    @Autowired
    private RangeCacheManager cacheManager;

    @BeforeEach
    void setUp() {
        cacheManager.clearAll();
        repository.deleteAll();
        repository.saveAllAndFlush(List.of(
            new TestEntity(1L, "user-1", ZERO.plusSeconds(1)),
            new TestEntity(2L, "user-1", ZERO.plusSeconds(5)),
            new TestEntity(3L, "user-1", ZERO.plusSeconds(13))));
    }

    @Test
    void interceptsAnAnnotationDeclaredOnJpaRepositoryInterface() {
        List<TestEntity> first = repository.findEvents("user-1", ZERO, ZERO.plusSeconds(10));
        repository.deleteAll();
        repository.flush();

        List<TestEntity> cached = repository.findEvents("user-1", ZERO, ZERO.plusSeconds(10));

        assertThat(first).extracting(TestEntity::getId).containsExactly(1L, 2L);
        assertThat(cached).extracting(TestEntity::getId).containsExactly(1L, 2L);
        cacheManager.clearMethod("events");
    }

    public interface TestRepository extends JpaRepository<TestEntity, Long> {

        @RangeCacheable(
            cacheName = "events",
            rangeProperty = "createdAt",
            uniqueKeyProperty = "id")
        @Query("""
            select event from RangeCacheTestEvent event
            where event.owner = :owner
              and event.createdAt >= :from
              and event.createdAt <= :to
            order by event.createdAt, event.id
            """)
        List<TestEntity> findEvents(
            @Param("owner") String owner,
            @Param("from") @RangeStart Instant from,
            @Param("to") @RangeEnd Instant to);
    }

    @Entity(name = "RangeCacheTestEvent")
    @Table(name = "range_cache_test_event")
    public static class TestEntity {
        @Id
        private Long id;

        @Column(nullable = false)
        private String owner;

        @Column(nullable = false)
        private Instant createdAt;

        protected TestEntity() {
        }

        TestEntity(Long id, String owner, Instant createdAt) {
            this.id = id;
            this.owner = owner;
            this.createdAt = createdAt;
        }

        public Long getId() {
            return id;
        }

        public String getOwner() {
            return owner;
        }

        public Instant getCreatedAt() {
            return createdAt;
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = TestEntity.class)
    @EnableJpaRepositories(
        basePackageClasses = TestRepository.class,
        considerNestedRepositories = true)
    static class TestApplication {
    }
}
