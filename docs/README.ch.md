# rangecache

[English version](../README.md)

![rangecache 缺口查詢示意圖](p0.png)

rangecache 是一個給 Spring Boot 使用的 ordered range query cache。當新的
查詢和已經確認過的資料範圍重疊時，它只查詢尚未覆蓋的缺口，再將結果和
快取資料合併。

從圖中可以看到，`[01:00, 14:00]` 已經被快取。下一次查詢
`[00:00, 15:00]` 時，只需要查詢 `[00:00, 01:00]` 與 `[14:00, 15:00]`。

## 快速使用

將 `@RangeCacheable` 加到 Spring 管理的 repository 或 service method：

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

方法應回傳 deterministic 的 `List`。兩個 range 參數使用閉區間
`[start, end]`，且不可為 `null`。不同的非 range 參數會代表不同的 logical
data series；同一個 series 的不同時間範圍可以共用已覆蓋資料。

預設 row 欄位約定如下：`rangeProperty` 是 `cachedRange`，
`uniqueKeyProperty` 是 `nonRepeatedKey`。如果 Java method 的參數名稱有被
保留，參數命名為 `rangeStart` 與 `rangeEnd` 時，可以省略
`@RangeStart` 與 `@RangeEnd`。

## 缺口查詢閾值

`max-delta-queries` 控制一次最多可以拆成幾個缺口查詢：

```yaml
rangecache:
  max-delta-queries: 5
```

- 設為 `0`，或缺口數量大於閾值：使用一次完整範圍查詢；
- 缺口數量小於等於閾值：逐一查詢缺口，再和快取資料合併；
- 預設值：`5`。

Local cache 預設最多保留 512 個 logical series，也可以透過
`maximum-series` 調整：

```yaml
rangecache:
  enabled: true
  max-delta-queries: 5
  maximum-series: 512
```

若要停用 starter：

```yaml
rangecache:
  enabled: false
```

## Maven 座標

```xml
<dependency>
  <groupId>io.github.liuwei997</groupId>
  <artifactId>rangecache-spring-boot-starter</artifactId>
  <version>0.3.1</version>
</dependency>
```

目前版本是 local snapshot。若要直接使用原始碼：

```bash
git clone https://github.com/LiuWei997/rangecache.git
cd rangecache
mvn install
```

需要 Java 17+ 與 Spring Boot 3+。

## 目前限制

- Range endpoint 與 row 的 range property 必須是完全相同的 concrete、自然可排序型別。V0.3 已測試 `Instant`、`LocalDateTime`、`LocalDate`、`OffsetDateTime`、`ZonedDateTime`、`Integer`、`Long`、`BigDecimal` 與 `String`。其他 `Comparable` 型別也可使用，但資料庫排序必須和 Java `compareTo` 一致。
- `String` 需要資料庫 collation 與 Java Unicode 排序相容；時區與小數秒 precision 也必須由 JDBC/database 與 Java 型別共同對齊，rangecache 不會自行轉換。
- Range 目前是一維且使用閉區間。
- `start` 與 `end` 都必須提供，`null` 不代表無界端點。
- 被攔截的方法必須回傳完整且 deterministic 的 `List`。
- 預設使用 local in-memory LRU；目前尚未支援 Redis 或 distributed cache。
- 資料應該是有順序且以 append-mostly 為主；對已覆蓋範圍進行補寫、更新或刪除
  時，需要主動清除相關 cache。

## Cache key 與失效策略

rangecache 使用兩種不同層級的 key，請不要混用：

| 名稱 | 範圍 | 來源 | 用途 |
| --- | --- | --- | --- |
| `SeriesKey` | 一個 logical query series | `cacheName` + annotated method identity + `argumentKey` | 選擇 cache entry 與其 range coverage |
| `uniqueKey` | 該 series 內的一筆 row | `uniqueKeyProperty` 的值（例如 `Event.id`） | row 去重、更新 cached row，以及指定 `evictEntity` 的目標 |

range endpoint **不**是 `SeriesKey` 的一部分：相同非 range 參數的查詢可以共用
coverage，因此只需要讀取缺口。`MethodIdentity` 由 annotated method 的 declaring
type、method name 和 declared parameter types 組成，可避免不同 method 即使使用
相同 cache name 也意外共用 entry。

`argumentKey` 的組成規則：

| `@RangeCacheable.key` | 非 range 參數 | `argumentKey` |
| --- | --- | --- |
| 空白（預設） | 沒有 | `SimpleKey.EMPTY` |
| 空白（預設） | 一個 | 該參數本身 |
| 空白（預設） | 兩個以上 | 依 parameter order 放入 Spring `SimpleKey` |
| SpEL expression | 任意 | expression 的結果；`null` 會成為 `SimpleKey.EMPTY` |

例如下列 query 的 argument key 是 `"account-a"`，range endpoint 不會參與：

```java
Method method = EventRepository.class.getMethod(
    "findEvents", String.class, Instant.class, Instant.class);
SeriesKey accountAEvents = new SeriesKey(
    "events", MethodIdentity.of(method), "account-a");
```

若 method 是 `String tenant, String kind, @RangeStart ..., @RangeEnd ...`，對應的
自動 key 是 `new SimpleKey(tenant, kind)`；若使用
`@RangeCacheable(key = "#tenant + ':' + #kind")`，則是 expression 結果，例如
`"tenant-a:orders"`。

`uniqueKeyProperty` 必須是 non-null、可互相比較，且在該 `SeriesKey` 內唯一的
property；通常直接使用 entity 的 primary key：

```java
@RangeCacheable(
    cacheName = "events",
    rangeProperty = "createdAt",
    uniqueKeyProperty = "id"
)
```

呼叫 `evictEntity` 時傳入的就是這個 property value；上例是 `Long id`，不是
entity instance，也不是 `SeriesKey` 的欄位。

資料寫入後可注入 `RangeCacheManager` 進行失效。所有操作都需要傳入和被快取
方法完全相同的 `SeriesKey`。

| 操作 | 對 cached entity 的影響 | 對 range coverage 的影響 | 下次查詢相同 range |
| --- | --- | --- | --- |
| `evictEntity(seriesKey, uniqueKey)` | 只移除指定 entity | 不變 | 使用 cache，不會重新讀回該 entity |
| `invalidateRange(seriesKey, Range.closed(from, to))` | 移除 range 內 entity | 移除該 range coverage | 重新查詢未覆蓋的區間 |
| `clearSeries(seriesKey)` | 移除該 series | 移除該 series 的全部 coverage | 整個 request 重新查詢 |
| `clearMethod(cacheName)` | 移除該 cache name 的所有 series | 移除全部 coverage | 各 series 重新查詢 |
| `clearAll()` | 移除全部 series | 移除全部 coverage | 所有 request 重新查詢 |

`evictEntity` 適合在刪除資料或權限變更後，讓某筆 entity 立刻不再出現，並保留
已知 coverage、避免額外查詢。它不是 refresh：後續已覆蓋的讀取仍會省略該筆
entity。新增、更新或刪除後若應重新從資料庫取得該區間，請用 range invalidation。

```java
void deleteEvent(long id) {
    // 先刪除資料庫資料
    rangeCacheManager.evictEntity(accountAEvents, id);
}

void updateOrInsertEvent(Instant createdAt) {
    // 先寫入資料庫；也會清掉該點的 negative coverage
    rangeCacheManager.invalidateRange(
        accountAEvents, Range.closed(createdAt, createdAt));
}
```

---
🌟 支持一下如果您喜歡這個專案，請給它一個 Star ⭐！這可以讓更多人看到這個專案，也是對開源創作者最好的鼓勵與支持。
