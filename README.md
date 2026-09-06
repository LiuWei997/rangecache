# What rangecache solves

[中文說明 / Chinese version](docs/README.ch.md)

Requires Java 17+ and Spring Boot 3+.

[V0.1 implementation specification](docs/implementation-spec-v0.1.md)

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

## Configuration

```yaml
rangecache:
  enabled: true
  max-delta-queries: 5
  maximum-series: 1000
```

Enable decision logs when needed:

```yaml
logging:
  level:
    io.github.liuwei997.rangecache: DEBUG
```
