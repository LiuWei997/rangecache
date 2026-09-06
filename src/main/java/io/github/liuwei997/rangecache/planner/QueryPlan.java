package io.github.liuwei997.rangecache.planner;

import com.google.common.collect.Range;
import java.time.Instant;
import java.util.List;

public record QueryPlan(
        QueryDecision decision,
        int missingRangeCount,
        List<Range<Instant>> rangesToFetch) {

    public QueryPlan {
        if (missingRangeCount < 0) {
            throw new IllegalArgumentException("missingRangeCount must be at least 0");
        }
        rangesToFetch = List.copyOf(rangesToFetch);
    }
}
