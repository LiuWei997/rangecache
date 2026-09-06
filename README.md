# What rangecache solves

[中文說明 / Chinese version](docs/README.ch.md)

Requires Java 17+ and Spring Boot 3+.

Most caches treat each query range as a different cache key. For example, after loading `[01:00, 14:00)`, a request for `[00:00, 15:00)` may query the entire second range again, even though most of it was already read.

rangecache is designed for ordered, append-mostly data such as event logs, transaction history, and audit records.

It remembers two things for each logical query key:

- the cached rows
- the ranges that have already been checked at the data source

When a new request overlaps an already checked range, rangecache can fetch only the uncovered parts and combine them with existing rows. It also remembers checked ranges that have no rows, so empty intervals do not need to be queried repeatedly.

```text
already checked: [01:00, 14:00)
new request:     [00:00, 15:00)
only fetch:      [00:00, 01:00), [14:00, 15:00)
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
  <version>0.1.0-SNAPSHOT</version>
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
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

Annotate a Spring-managed method whose range follows `[start, end)` semantics:

```java
@RangeCacheable(
    cacheName = "transactions",
    key = "#userId + ':' + #type",
    rangeProperty = "createdAt",
    uniqueKeyProperty = "id"
)
List<Transaction> findTransactions(
    Long userId,
    TransactionType type,
    @RangeStart Instant from,
    @RangeEnd Instant to
);
```

With conventional parameter and row property names, only `@RangeCacheable` is required:

```java
@RangeCacheable
List<Event> findEvents(Long userId, Instant rangeStart, Instant rangeEnd);
```

The method must return a complete, deterministic `List` for the requested range. Both range endpoints are required and must be non-null.

## Configuration template

All V0.1 properties use the `rangecache` prefix. The following is a complete
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
  maximum-series: 1000                  # default: 1000, must be >= 1
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

Enable decision logs when needed:

```yaml
logging:
  level:
    io.github.liuwei997.rangecache: DEBUG
```
