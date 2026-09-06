package io.github.liuwei997.rangecache.aop;

import io.github.liuwei997.rangecache.annotation.RangeCacheable;
import io.github.liuwei997.rangecache.annotation.RangeCacheBypass;
import io.github.liuwei997.rangecache.annotation.RangeEnd;
import io.github.liuwei997.rangecache.annotation.RangeStart;
import io.github.liuwei997.rangecache.core.InvalidRangeCacheMethodException;
import io.github.liuwei997.rangecache.core.MethodIdentity;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.BridgeMethodResolver;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.ClassUtils;

public final class RangeCacheOperationSource {

    private final ParameterNameDiscoverer parameterNames = new DefaultParameterNameDiscoverer();
    private final Map<CacheKey, Optional<RangeCacheOperation>> cache = new ConcurrentHashMap<>();

    public RangeCacheOperation getOperation(Method method, Class<?> targetClass) {
        Class<?> userClass = targetClass == null ? method.getDeclaringClass() : ClassUtils.getUserClass(targetClass);
        return cache.computeIfAbsent(new CacheKey(method, userClass), this::resolve).orElse(null);
    }

    private Optional<RangeCacheOperation> resolve(CacheKey key) {
        Method invokedMethod = key.method();
        Method specificMethod = BridgeMethodResolver.findBridgedMethod(
            AopUtils.getMostSpecificMethod(invokedMethod, key.targetClass()));

        AnnotatedMethod annotated = findAnnotation(invokedMethod, specificMethod, key.targetClass());
        if (annotated == null) {
            return Optional.empty();
        }

        Method metadataMethod = annotated.method();
        if (!List.class.isAssignableFrom(metadataMethod.getReturnType())) {
            throw invalid(metadataMethod, "return type must be List");
        }

        int startIndex = resolveParameterIndex(metadataMethod, specificMethod, RangeStart.class, "rangeStart");
        int endIndex = resolveParameterIndex(metadataMethod, specificMethod, RangeEnd.class, "rangeEnd");
        if (startIndex == endIndex) {
            throw invalid(metadataMethod, "range start and end must be different parameters");
        }
        requireInstantParameter(metadataMethod, startIndex, "start");
        requireInstantParameter(metadataMethod, endIndex, "end");

        RangeCacheable annotation = annotated.annotation();
        if (annotation.rangeProperty().isBlank()) {
            throw invalid(metadataMethod, "rangeProperty must not be blank");
        }
        if (annotation.uniqueKeyProperty().isBlank()) {
            throw invalid(metadataMethod, "uniqueKeyProperty must not be blank");
        }

        boolean bypass = resolveBypassMarker(metadataMethod, specificMethod);

        MethodIdentity identity = MethodIdentity.of(metadataMethod);
        String cacheName = annotation.cacheName().isBlank()
            ? defaultCacheName(identity)
            : annotation.cacheName();

        return Optional.of(new RangeCacheOperation(
            metadataMethod,
            identity,
            cacheName,
            annotation.key(),
            bypass,
            startIndex,
            endIndex,
            annotation.rangeProperty(),
            annotation.uniqueKeyProperty()));
    }

    private AnnotatedMethod findAnnotation(Method invoked, Method specific, Class<?> targetClass) {
        RangeCacheable direct = AnnotatedElementUtils.getMergedAnnotation(invoked, RangeCacheable.class);
        if (direct != null) {
            return new AnnotatedMethod(invoked, direct);
        }
        RangeCacheable onSpecific = AnnotatedElementUtils.getMergedAnnotation(specific, RangeCacheable.class);
        if (onSpecific != null) {
            return new AnnotatedMethod(specific, onSpecific);
        }
        return findOnInterfaces(targetClass, invoked);
    }

    private AnnotatedMethod findOnInterfaces(Class<?> type, Method method) {
        for (Class<?> interfaceType : ClassUtils.getAllInterfacesForClassAsSet(type)) {
            try {
                Method interfaceMethod = interfaceType.getMethod(method.getName(), method.getParameterTypes());
                RangeCacheable annotation = AnnotatedElementUtils.findMergedAnnotation(
                    interfaceMethod, RangeCacheable.class);
                if (annotation != null) {
                    return new AnnotatedMethod(interfaceMethod, annotation);
                }
            } catch (NoSuchMethodException ignored) {
                // Continue through the interface hierarchy.
            }
        }
        return null;
    }

    private int resolveParameterIndex(
            Method primary,
            Method secondary,
            Class<? extends Annotation> marker,
            String conventionalName) {

        int marked = findMarkedParameter(primary, marker);
        if (marked < 0 && !primary.equals(secondary)) {
            marked = findMarkedParameter(secondary, marker);
        }
        if (marked >= 0) {
            return marked;
        }

        int named = findNamedParameter(primary, conventionalName);
        if (named < 0 && !primary.equals(secondary)) {
            named = findNamedParameter(secondary, conventionalName);
        }
        if (named < 0) {
            throw invalid(primary,
                "cannot resolve " + conventionalName + "; use @"
                    + marker.getSimpleName() + " or retain that parameter name");
        }
        return named;
    }

    private int findMarkedParameter(Method method, Class<? extends Annotation> marker) {
        int found = -1;
        Annotation[][] annotations = method.getParameterAnnotations();
        for (int index = 0; index < annotations.length; index++) {
            for (Annotation annotation : annotations[index]) {
                if (annotation.annotationType() == marker) {
                    if (found >= 0) {
                        throw invalid(method, "multiple parameters use @" + marker.getSimpleName());
                    }
                    found = index;
                }
            }
        }
        return found;
    }

    private boolean resolveBypassMarker(Method primary, Method secondary) {
        boolean primaryMarker = hasBypassMarker(primary, 0);
        boolean secondaryMarker = !primary.equals(secondary) && hasBypassMarker(secondary, 0);
        if (hasBypassMarkerOutsideFirst(primary) || hasBypassMarkerOutsideFirst(secondary)) {
            throw invalid(primary, "@RangeCacheBypass must annotate the first method parameter");
        }
        return primaryMarker || secondaryMarker;
    }

    private boolean hasBypassMarker(Method method, int index) {
        if (method == null || method.getParameterCount() <= index) {
            return false;
        }
        return method.getParameterAnnotations()[index].length > 0
            && java.util.Arrays.stream(method.getParameterAnnotations()[index])
                .anyMatch(annotation -> annotation.annotationType() == RangeCacheBypass.class);
    }

    private boolean hasBypassMarkerOutsideFirst(Method method) {
        if (method == null) {
            return false;
        }
        Annotation[][] annotations = method.getParameterAnnotations();
        for (int index = 1; index < annotations.length; index++) {
            for (Annotation annotation : annotations[index]) {
                if (annotation.annotationType() == RangeCacheBypass.class) {
                    return true;
                }
            }
        }
        return false;
    }

    private int findNamedParameter(Method method, String name) {
        String[] names = parameterNames.getParameterNames(method);
        if (names == null) {
            return -1;
        }
        for (int index = 0; index < names.length; index++) {
            if (name.equals(names[index])) {
                return index;
            }
        }
        return -1;
    }

    private void requireInstantParameter(Method method, int index, String label) {
        if (method.getParameterTypes()[index] != Instant.class) {
            throw invalid(method, "range " + label + " parameter must be Instant");
        }
    }

    private String defaultCacheName(MethodIdentity identity) {
        return identity.declaringType() + "#" + identity.methodName() + "("
            + String.join(",", identity.parameterTypes()) + ")";
    }

    private InvalidRangeCacheMethodException invalid(Method method, String reason) {
        return new InvalidRangeCacheMethodException(method.toGenericString() + ": " + reason);
    }

    private record CacheKey(Method method, Class<?> targetClass) {
    }

    private record AnnotatedMethod(Method method, RangeCacheable annotation) {
    }
}
