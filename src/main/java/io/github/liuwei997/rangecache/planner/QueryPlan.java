package io.github.liuwei997.rangecache.planner;

import com.google.common.collect.Range;
import java.util.List;

public record QueryPlan<R extends Comparable<? super R>>(
        QueryDecision decision,
        int missingRangeCount,
        List<Range<R>> rangesToFetch) {

    public QueryPlan {
        if (missingRangeCount < 0) {
            throw new IllegalArgumentException("missingRangeCount must be at least 0");
        }
        rangesToFetch = List.copyOf(rangesToFetch);
    }
}
