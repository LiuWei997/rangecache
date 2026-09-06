package io.github.liuwei997.rangecache.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks the first method parameter as a range-cache bypass marker.
 *
 * <p>When present, the annotated method invocation proceeds directly to the
 * target method without reading from or writing to the range cache.</p>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RangeCacheBypass {
}
