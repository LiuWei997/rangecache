package io.github.liuwei997.rangecache.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RangeCacheable {

    /** Empty means the annotated method identity is used as the cache name. */
    String cacheName() default "";

    /** Empty means a key is generated from the non-range method arguments. */
    String key() default "";

    /** Name of the returned row property that supplies its range coordinate. */
    String rangeProperty() default "cachedRange";

    /**
     * Name of a non-null Comparable row property that uniquely identifies a
     * row within one {@code SeriesKey}; commonly an entity primary key.
     */
    String uniqueKeyProperty() default "nonRepeatedKey";
}
