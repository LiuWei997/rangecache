package io.github.liuwei997.rangecache.planner;

import com.google.common.collect.Range;
import com.google.common.collect.RangeSet;
import java.time.Instant;

public interface RangeQueryPlanner {

    QueryPlan plan(Range<Instant> requestedRange, RangeSet<Instant> coverage);
}
