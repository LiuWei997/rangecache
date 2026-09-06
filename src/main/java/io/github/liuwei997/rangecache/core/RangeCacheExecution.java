package io.github.liuwei997.rangecache.core;

import io.github.liuwei997.rangecache.planner.QueryDecision;
import java.util.List;

public record RangeCacheExecution(
        QueryDecision decision,
        int missingRangeCount,
        int databaseQueries,
        List<?> rows) {

    public RangeCacheExecution {
        rows = List.copyOf(rows);
    }
}
