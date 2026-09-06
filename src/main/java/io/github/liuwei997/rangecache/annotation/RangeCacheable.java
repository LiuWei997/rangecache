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

    String rangeProperty() default "cachedRange";

    String uniqueKeyProperty() default "nonRepeatedKey";
}
