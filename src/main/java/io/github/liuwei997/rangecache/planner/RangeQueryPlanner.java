package io.github.liuwei997.rangecache.planner;

import com.google.common.collect.Range;
import com.google.common.collect.RangeSet;
public interface RangeQueryPlanner {

    <R extends Comparable<? super R>> QueryPlan<R> plan(Range<R> requestedRange, RangeSet<R> coverage);
}
