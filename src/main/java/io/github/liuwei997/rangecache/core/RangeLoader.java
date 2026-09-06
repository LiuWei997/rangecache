package io.github.liuwei997.rangecache.core;

import com.google.common.collect.Range;
import java.util.List;

@FunctionalInterface
public interface RangeLoader<R extends Comparable<? super R>> {

    List<?> load(Range<R> range) throws Throwable;
}
