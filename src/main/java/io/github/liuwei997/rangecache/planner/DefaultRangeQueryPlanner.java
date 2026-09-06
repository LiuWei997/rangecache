package io.github.liuwei997.rangecache.planner;

import com.google.common.collect.Range;
import com.google.common.collect.RangeSet;
import java.time.Instant;
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
    public QueryPlan plan(Range<Instant> requestedRange, RangeSet<Instant> coverage) {
        List<Range<Instant>> missing = new ArrayList<>(
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

    private Range<Instant> closedEnvelope(Range<Instant> range) {
        return Range.closed(range.lowerEndpoint(), range.upperEndpoint());
    }
}
