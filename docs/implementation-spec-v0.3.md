# rangecache v0.3.0 implementation specification

## Goal

v0.3 generalizes rangecache's single range dimension from `Instant` to a single, concrete, naturally ordered Java type per cached method. The annotation API, local LRU store, coverage model, and closed-closed query contract remain unchanged.

This is deliberately not a new public `RangeCache<K, R, V>` abstraction: there is no such API in v0.2, and adding it would expand the library surface without helping the Spring AOP use case. Generic types stay in the existing planner, loader, executor, entry, coverage set, and coordinate index.

## Public API

Existing annotations are unchanged:

```java
@RangeCacheable(rangeProperty = "cachedRange", uniqueKeyProperty = "id")
List<Price> findPrices(String symbol,
                       @RangeStart BigDecimal rangeStart,
                       @RangeEnd BigDecimal rangeEnd);
```

`@RangeStart` / `@RangeEnd` may still be omitted when the compiled parameter names are `rangeStart` and `rangeEnd`. The start and end declarations must be the same type, must implement `Comparable`, and their non-null runtime values must have exactly that declared (boxed, for primitives) type. Each returned row's `rangeProperty` must have that same type. A non-null comparable `uniqueKeyProperty` remains required.

The internal extension points become generic at source level:

```java
<R extends Comparable<? super R>> QueryPlan<R> plan(Range<R> request, RangeSet<R> coverage);
<R extends Comparable<? super R>> List<?> load(Range<R> range);
<R extends Comparable<? super R>> RangeCacheExecution execute(..., Range<R> request, ...);
```

Their erasures stay the same, so existing compiled consumers of the released v0.2 signatures remain binary compatible.

## Supported values and ordering

The implementation accepts one concrete Java type implementing natural `Comparable`; there is intentionally no registry and no custom comparator in v0.3. This covers the documented first-class values:

| Category | Values |
| --- | --- |
| Temporal | `Instant`, `LocalDateTime`, `LocalDate`, `OffsetDateTime`, `ZonedDateTime` |
| Numeric | `Integer`, `Long`, `BigDecimal` (and other same-type comparable numeric classes) |
| Text | `String` |
| Extension | another concrete naturally comparable value type, if its database ordering exactly matches Java `compareTo` |

`Float` and `Double` are technically accepted but discouraged: NaN and database floating-point semantics make them poor cache coordinates. `String` requires a database collation whose order matches Java Unicode comparison. `BigDecimal` coordinates are ordered by numeric value, so values such as 1.0 and 1.00 share a coordinate; this is the correct `compareTo` behaviour.

No normalization is performed. The application and database own timezone and precision alignment: use a consistent JDBC/database zone for instant-like values; use the same fractional-second precision in query predicates and row mapping. `ZonedDateTime` / `OffsetDateTime` are treated using their Java natural ordering, not normalized to `Instant`.

## Range semantics and Guava

The cache uses Guava `Range<R>`, `RangeSet<R>`, and `TreeMap<R, ...>` with the same natural order. Requests and database invocations remain `[start, end]`. Boundary policy is not configurable in v0.3: adding open/half-open policies would require changing method predicate contracts and is outside this release.

When coverage leaves an open gap (for example existing `[a,b]` then requested `[a,c]`), Guava represents the gap as `(b,c]`. A generic cache cannot derive a safe successor for all types (there is none for arbitrary decimals, strings, or nanosecond precision policies), so the planner fetches the closed envelope `[b,c]`. This can requery an already cached boundary row, but never omits a value and preserves v0.2 behaviour. Later rows with the same unique key still replace earlier rows.

## Validation and errors

Operation metadata validation fails at Spring proxy resolution when start/end types differ or are not comparable. Runtime validation fails before cache use for null endpoints, values whose class differs from the declared range type, or `start > end`. Result validation fails for null rows/coordinates, a row coordinate of a different type, or a row outside the fetched interval. Error messages include the annotated method, property, declared type, and actual type/value where useful.

The row coordinate and method endpoint types must be identical. Supporting cross-type coercion (for example `Long` endpoints and `Integer` rows, or an `Instant` row for an `OffsetDateTime` request) would make coverage and database ordering ambiguous, so it is explicitly excluded.

## Code changes

- `aop/RangeCacheOperationSource`: comparable/type validation and metadata.
- `aop/RangeCacheOperation`, `RangeCacheInterceptor`: retain the declared range type, validate runtime endpoints, and pass typed values unchanged.
- `planner/{RangeQueryPlanner,DefaultRangeQueryPlanner,QueryPlan}`: generic Guava ranges.
- `core/{RangeLoader,RangeCacheExecutor}`: generic extraction, slicing, merge, and error diagnostics.
- `store/{LocalRangeCacheEntry,LocalRangeCacheStore}`: generic coverage and coordinate indexes while a store lease safely binds the series type.
- README, sample/integration tests, and Maven version metadata: document the range-type matrix and set `0.3.0-SNAPSHOT`.

## Compatibility and non-goals

All v0.2 `Instant` annotations, defaults, keys, invalidation APIs, logs, LRU, and closed-closed semantics keep their behaviour. No application code changes are needed for `Instant` users.

Potential behavioural tightening: methods which previously declared `Instant` but supplied null or a wrong runtime value now report an explicit method/type diagnostic; rows with a wrong coordinate type report the expected declared type. This is a bug-fix-level validation improvement, not a query semantic change.

Out of scope: multiple dimensions, null/unbounded ranges, custom comparators, automatic timezone/collation/precision conversion, automatic SQL generation, distributed storage, and open/half-open query policies.

## Test matrix

- Unit parameterization: `Instant`, `LocalDateTime`, `LocalDate`, `OffsetDateTime`, `ZonedDateTime`, `Integer`, `Long`, `BigDecimal`, and `String`; for each, miss → contained hit, overlap/gap merge, and single point range.
- Core edge cases: empty-result coverage, duplicate identity replacement, coordinate relocation, invalid outside/null/type-mismatched rows, null and reversed endpoints, and unsupported/non-comparable declarations.
- AOP integration: marker and conventional-name resolution, with typed values passed unmodified to the target method; use JPA/H2 for `LocalDate` and a numeric range in addition to the retained `Instant` suite.
- Regression: planner decisions, concurrency, eviction, and all three cache invalidation levels.
