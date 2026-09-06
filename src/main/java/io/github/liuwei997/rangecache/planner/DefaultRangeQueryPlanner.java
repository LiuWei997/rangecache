package io.github.liuwei997.rangecache.planner;

import com.google.common.collect.Range;
import com.google.common.collect.RangeSet;
import java.util.ArrayList;
import java.util.List;

public final class DefaultRangeQueryPlanner implements RangeQueryPlanner {

    private final int maxDeltaQueries;

    public DefaultRangeQueryPlanner(int maxDeltaQueries) {
        if (maxDeltaQueries < 0) {
            throw new IllegalArgumentException("maxDeltaQueries must be at least 0");
        }
        this.maxDeltaQueries = maxDeltaQueries;
    }

    @Override
    public <R extends Comparable<? super R>> QueryPlan<R> plan(
            Range<R> requestedRange, RangeSet<R> coverage) {
        List<Range<R>> missing = new ArrayList<>(
            coverage.complement().subRangeSet(requestedRange).asRanges().stream()
                .map(this::closedEnvelope)
                .toList());

        if (missing.isEmpty()) {
            return new QueryPlan(QueryDecision.CACHE_ONLY, 0, List.of());
        }
        if (missing.size() <= maxDeltaQueries) {
            return new QueryPlan(QueryDecision.DELTA_FETCH, missing.size(), missing);
        }
        return new QueryPlan(QueryDecision.FULL_FETCH, missing.size(), List.of(requestedRange));
    }

    private <R extends Comparable<? super R>> Range<R> closedEnvelope(Range<R> range) {
        return Range.closed(range.lowerEndpoint(), range.upperEndpoint());
    }
}
