package io.github.liuwei997.rangecache.core;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

public record MethodIdentity(String declaringType, String methodName, List<String> parameterTypes) {

    public MethodIdentity {
        parameterTypes = List.copyOf(parameterTypes);
    }

    public static MethodIdentity of(Method method) {
        return new MethodIdentity(
            method.getDeclaringClass().getName(),
            method.getName(),
            Arrays.stream(method.getParameterTypes()).map(Class::getName).toList());
    }

    public String displayName() {
        return declaringType + "." + methodName;
    }
}
