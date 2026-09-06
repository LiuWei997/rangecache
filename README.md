# What rangecache solves

[中文說明 / Chinese version](docs/README.ch.md)

Requires Java 17+ and Spring Boot 3+.

The current V0.2 implementation uses `Instant` as its range type. Support for
other naturally ordered types such as `LocalDate`, `LocalDateTime`, numeric
types, and `String` is planned for V0.3; see
[`docs/implementation-spec-v0.3.md`](docs/implementation-spec-v0.3.md) for the
design and database-ordering constraints.

Most caches treat each query range as a different cache key. For example, after loading `[01:00, 14:00]`, a request for `[00:00, 15:00]` may query the entire second range again, even though most of it was already read.

rangecache is designed for ordered, append-mostly data such as event logs, transaction history, and audit records.

It remembers two things for each logical query key:

- the cached rows
- the ranges that have already been checked at the data source

When a new request overlaps an already checked range, rangecache can fetch only the uncovered parts and combine them with existing rows. It also remembers checked ranges that have no rows, so empty intervals do not need to be queried repeatedly.

```text
already checked: [01:00, 14:00]
new request:     [00:00, 15:00]
only fetch:      [00:00, 01:00], [14:00, 15:00]
```

## Usage

### Use the source code directly

Clone the repository and install the library into your local Maven repository:

```bash
git clone https://github.com/LiuWei997/rangecache.git
cd rangecache
mvn install
```

Then add the locally installed starter to your Spring Boot application:

```xml
<dependency>
  <groupId>io.github.liuwei997</groupId>
  <artifactId>rangecache-spring-boot-starter</artifactId>
  <version>0.2.0-SNAPSHOT</version>
</dependency>
```

To update an existing checkout before rebuilding:

```bash
git pull
mvn install
```

### Use a Maven dependency

Add the starter dependency:

```xml
<dependency>
  <groupId>io.github.liuwei997</groupId>
  <artifactId>rangecache-spring-boot-starter</artifactId>
  <version>0.2.0-SNAPSHOT</version>
</dependency>
```

Annotate a Spring-managed method whose range follows `[start, end]` semantics:

```java
@RangeCacheable(
    rangeProperty = "createdAt",
    uniqueKeyProperty = "id"
)
List<Transaction> findTransactions(
    Long accountKey,
    TransactionType type,
    @RangeStart Instant from,
    @RangeEnd Instant to
);
```

`cacheName` and `key` are optional. When omitted, the cache name is derived
from the method identity, while the key is generated from all non-range
arguments (`accountKey` and `type` in this example). Use `cacheName` or a SpEL
`key` only when you need to override those defaults.

### `cacheName` and `seriesKey`

These values have different roles:

- `cacheName` is the logical cache name or namespace for an annotated method.
  It is a readable label and can be supplied explicitly in `@RangeCacheable`.
- `seriesKey` is the complete internal identity of one cached data series. It
  combines the method identity, the cache name, and the generated key from all
  non-range arguments.

For example, calls with `accountKey=10` and `accountKey=20` normally belong to
two different series, while different time ranges for `accountKey=10` belong to
the same series and can share coverage. With the default key generation,
`rangeStart` and `rangeEnd` are not part of the series key; they select a slice
of the series instead. A custom SpEL `key` can override this behavior.

When `cacheName` and `key` are omitted, the library generates stable defaults;
users do not need to reserve a field named `userId` or follow any business-field
naming convention.

With the following conventional names, only `@RangeCacheable` is required:

- method parameters named `rangeStart` and `rangeEnd`;
- both range parameters typed as `java.time.Instant`;
- a return type of `List<?>`;
- each returned row has a `cachedRange` property of type `Instant`;
- each returned row has a `nonRepeatedKey` property used for deduplication.

The application must retain Java parameter names (the Maven compiler setting
`<parameters>true</parameters>` does this). If parameter names cannot be
retained, annotate the two parameters explicitly with `@RangeStart` and
`@RangeEnd`. You can also override the row property conventions with
`rangeProperty` and `uniqueKeyProperty`:

```java
@RangeCacheable
List<Event> findEvents(Long accountKey, Instant rangeStart, Instant rangeEnd);
```

The method must return a complete, deterministic `List` for the requested range. Both range endpoints are required and must be non-null; `start` may equal `end` to query one point.

## Configuration template

All V0.2 properties use the `rangecache` prefix. The following is a complete
`application.yml` template with the current defaults:

```yaml
rangecache:
  # Enable annotation-driven range caching.
  enabled: true                         # default: true

  # Maximum number of uncovered intervals fetched as separate database queries.
  # If the missing interval count is greater than this value, one full-range
  # query is used instead. A value of 0 always prefers FULL_FETCH when data is missing.
  max-delta-queries: 5                  # default: 5, must be >= 0

  # Maximum number of logical series retained by the local in-memory LRU cache.
  # When the limit is reached, the least recently used inactive series is evicted.
  maximum-series: 512                   # default: 512, must be >= 1
```

Minimal configuration (all defaults):

```yaml
rangecache: {}
```

Disable the starter without removing the dependency:

```yaml
rangecache:
  enabled: false
```

For example, to favor fewer database round trips and keep only 500 logical
series in memory:

```yaml
rangecache:
  max-delta-queries: 2
  maximum-series: 500
```

Invalid values fail application startup: `max-delta-queries` cannot be
negative, and `maximum-series` must be at least `1`.

## Cache invalidation

`RangeCacheManager` supports three invalidation levels:

```java
// 1. One logical series: method + non-range arguments
rangeCacheManager.clearSeries(seriesKey);

// 2. Every series belonging to one method
rangeCacheManager.clearMethod(
    EventService.class.getMethod(
        "findEvents", Long.class, Instant.class, Instant.class));

// 3. Every series of every method
rangeCacheManager.clearAll();
```

Clearing a series removes both its rows and coverage metadata. The method-level
operation accepts a Java reflection `Method` and resolves its stable identity
internally, while the global operation clears the local LRU store completely.

Enable decision logs when needed:

```yaml
logging:
  level:
    io.github.liuwei997.rangecache: DEBUG
```
