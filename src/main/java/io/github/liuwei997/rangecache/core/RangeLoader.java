package io.github.liuwei997.rangecache.core;

import com.google.common.collect.Range;
import java.time.Instant;
import java.util.List;

@FunctionalInterface
public interface RangeLoader {

    List<?> load(Range<Instant> range) throws Throwable;
}
