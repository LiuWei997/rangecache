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
  <version>0.2.0-SNAPSHOT</version>
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

- V0.2 的 range type 只支援 `Instant`；更多可排序型別預計在 V0.3 支援。
- Range 目前是一維且使用閉區間。
- `start` 與 `end` 都必須提供，`null` 不代表無界端點。
- 被攔截的方法必須回傳完整且 deterministic 的 `List`。
- 預設使用 local in-memory LRU；目前尚未支援 Redis 或 distributed cache。
- 資料應該是有順序且以 append-mostly 為主；對已覆蓋範圍進行補寫、更新或刪除
  時，需要主動清除相關 cache。
