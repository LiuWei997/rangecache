package io.github.liuwei997.rangecache.planner;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.Range;
import com.google.common.collect.TreeRangeSet;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class DefaultRangeQueryPlannerTest {

    private static final Instant ZERO = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void usesCacheOnlyWhenCoverageContainsTheRequest() {
        var coverage = TreeRangeSet.<Instant>create();
        coverage.add(range(0, 10));

        QueryPlan plan = new DefaultRangeQueryPlanner(5).plan(range(2, 8), coverage);

        assertThat(plan.decision()).isEqualTo(QueryDecision.CACHE_ONLY);
        assertThat(plan.missingRangeCount()).isZero();
        assertThat(plan.rangesToFetch()).isEmpty();
    }

    @Test
    void fetchesOnlyMissingEdges() {
        var coverage = TreeRangeSet.<Instant>create();
        coverage.add(range(1, 14));

        QueryPlan plan = new DefaultRangeQueryPlanner(5).plan(range(0, 15), coverage);

        assertThat(plan.decision()).isEqualTo(QueryDecision.DELTA_FETCH);
        assertThat(plan.missingRangeCount()).isEqualTo(2);
        assertThat(plan.rangesToFetch()).containsExactly(range(0, 1), range(14, 15));
    }

    @Test
    void usesOneFullQueryWhenGapCountExceedsThreshold() {
        var coverage = TreeRangeSet.<Instant>create();
        for (int second = 1; second < 12; second += 2) {
            coverage.add(range(second, second + 1));
        }

        QueryPlan plan = new DefaultRangeQueryPlanner(5).plan(range(0, 12), coverage);

        assertThat(plan.decision()).isEqualTo(QueryDecision.FULL_FETCH);
        assertThat(plan.missingRangeCount()).isEqualTo(6);
        assertThat(plan.rangesToFetch()).containsExactly(range(0, 12));
    }

    private Range<Instant> range(long start, long end) {
        return Range.closed(ZERO.plusSeconds(start), ZERO.plusSeconds(end));
    }
}
