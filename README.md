# rangecache

[中文說明 / Chinese version](docs/README.ch.md)

rangecache is a Spring Boot cache for ordered range queries. When a new query
overlaps data that has already been checked, it queries only the uncovered
gaps and merges the result with the cached rows.

In the diagram, `[01:00, 14:00]` is already cached. A later request for
`[00:00, 15:00]` queries only `[00:00, 01:00]` and `[14:00, 15:00]`.

![rangecache missing-range query](docs/p0.png)

## Quick start

Annotate a Spring-managed repository or service method:

```java
@RangeCacheable(
    cacheName = "events",
    rangeProperty = "createdAt",
    uniqueKeyProperty = "id"
)
List<Event> findEvents(
    String accountKey,
    @RangeStart Instant rangeStart,
    @RangeEnd Instant rangeEnd
);
```

The method should return a deterministic `List`. The two range parameters are
closed-closed (`[start, end]`) and must be non-null. Different non-range
arguments represent different logical data series; different ranges for the
same series share coverage.

With the default conventions, `rangeProperty` is `cachedRange` and
`uniqueKeyProperty` is `nonRepeatedKey`. Parameters named `rangeStart` and
`rangeEnd` can omit `@RangeStart` and `@RangeEnd` when Java parameter names are
retained.

## Gap query threshold

`max-delta-queries` controls how many uncovered gaps can be fetched separately:

```yaml
rangecache:
  max-delta-queries: 5
```

- `0` or a missing range with more than the threshold: use one full-range query;
- up to the threshold: query each missing gap and merge it with cached data;
- default: `5`.

The local cache keeps up to 512 logical series by default. This can be changed
with `maximum-series`:

```yaml
rangecache:
  enabled: true
  max-delta-queries: 5
  maximum-series: 512
```

Set `rangecache.enabled=false` to disable the starter.

## Maven coordinates

```xml
<dependency>
  <groupId>io.github.liuwei997</groupId>
  <artifactId>rangecache-spring-boot-starter</artifactId>
  <version>0.3.1</version>
</dependency>
```

The current version is a local snapshot. To use the source directly:

```bash
git clone https://github.com/LiuWei997/rangecache.git
cd rangecache
mvn install
```

Requires Java 17+ and Spring Boot 3+.

## Current limitations

- Range endpoints and the row range property must use the exact same concrete
  naturally comparable type. V0.3 is tested with `Instant`, `LocalDateTime`,
  `LocalDate`, `OffsetDateTime`, `ZonedDateTime`, `Integer`, `Long`,
  `BigDecimal`, and `String`. Other concrete `Comparable` types also work when
  their database ordering matches Java `compareTo`.
- `String` requires a database collation compatible with Java Unicode order.
  Align database/JDBC timezone and fractional-second precision with the Java
  type; rangecache does not convert values.
- The range is one-dimensional and closed-closed.
- Both range endpoints are required; `null` is not an unbounded endpoint.
- The method must return a complete, deterministic `List`.
- The default store is local in-memory LRU; Redis and distributed cache storage
  are not included yet.
- The data should be ordered and append-mostly. Backdated updates or deletes
  require explicit invalidation.

## Cache keys and invalidation

rangecache uses two different keys. They have different scopes and must not be
confused:

| Name | Scope | Source | Purpose |
| --- | --- | --- | --- |
| `SeriesKey` | One logical query series | `cacheName` + annotated method identity + `argumentKey` | Selects a cache entry and its range coverage |
| `uniqueKey` | One returned row inside that series | The value of `uniqueKeyProperty` (for example, `Event.id`) | Deduplicates rows, updates a cached row, and targets `evictEntity` |

The range endpoints are deliberately **not** part of `SeriesKey`: requests with
the same non-range arguments share coverage, so the cache can fetch only their
gaps. `MethodIdentity` is the annotated method's declaring type, method name,
and declared parameter types; it prevents two methods with the same cache name
from accidentally sharing entries.

`argumentKey` is created as follows:

| `@RangeCacheable.key` | Non-range arguments | `argumentKey` |
| --- | --- | --- |
| Empty (default) | None | `SimpleKey.EMPTY` |
| Empty (default) | One | That argument itself |
| Empty (default) | Two or more | Spring `SimpleKey` containing those arguments in parameter order |
| SpEL expression | Any | The expression result; `null` becomes `SimpleKey.EMPTY` |

For example, a query declared as below uses `"account-a"` as its argument key;
the range endpoints do not participate:

```java
Method method = EventRepository.class.getMethod(
    "findEvents", String.class, Instant.class, Instant.class);
SeriesKey accountAEvents = new SeriesKey(
    "events", MethodIdentity.of(method), "account-a");
```

For a method with `String tenant, String kind, @RangeStart ..., @RangeEnd ...`,
the equivalent automatic key is `new SimpleKey(tenant, kind)`. For
`@RangeCacheable(key = "#tenant + ':' + #kind")`, it is the resulting string,
such as `"tenant-a:orders"`.

`uniqueKeyProperty` must name a non-null, mutually comparable property that is
unique **within the SeriesKey**. It is usually the entity primary key:

```java
@RangeCacheable(
    cacheName = "events",
    rangeProperty = "createdAt",
    uniqueKeyProperty = "id"
)
```

Pass exactly that property value to `evictEntity`; for the example above it is
the `Long id`, not an entity instance or a `SeriesKey` field.

Inject `RangeCacheManager` to invalidate data after a write. All operations use
the exact `SeriesKey` of the cached method.

| Operation | Effect on cached rows | Effect on range coverage | When the same range is requested again |
| --- | --- | --- | --- |
| `evictEntity(seriesKey, uniqueKey)` | Removes only that entity | Unchanged | Cache is used; the removed entity is not reloaded |
| `invalidateRange(seriesKey, Range.closed(from, to))` | Removes entities inside the range | Removes that covered range | The uncovered interval is fetched again |
| `clearSeries(seriesKey)` | Removes the series | Removes all coverage for the series | The whole request is fetched again |
| `clearMethod(cacheName)` | Removes every series for that cache name | Removes all their coverage | Each affected series is fetched again |
| `clearAll()` | Removes every cached series | Removes all coverage | Every request is fetched again |

Entity eviction is useful when a deleted or unauthorized entity must disappear
immediately, while retaining known coverage and avoiding a query. It is not a
refresh operation: a later cached read will still omit that entity. Use range
invalidation after inserts, updates, or deletes when the database should be
queried again for that interval.

```java
@Service
class EventWriter {
    private final RangeCacheManager rangeCacheManager;

    void deleteEvent(long id, Instant createdAt) {
        // delete from the database first
        rangeCacheManager.evictEntity(accountAEvents, id);
    }

    void updateOrInsertEvent(Instant createdAt) {
        // write to the database first; this also invalidates negative coverage
        rangeCacheManager.invalidateRange(
            accountAEvents, Range.closed(createdAt, createdAt));
    }
}
```

---
🌟 Support this projectIf you like this project, please give it a Star! It helps more people discover the repository and is the best encouragement for open-source creators.
