# What rangecache solves

[中文說明 / Chinese version](docs/README.ch.md)

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
