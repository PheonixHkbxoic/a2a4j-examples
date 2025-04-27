package io.github.pheonixhkbxoic.a2a4j.examples.agents.mathagent.core;

import dev.langchain4j.agent.tool.Tool;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

/**
 * @author PheonixHkbxoic
 * @date 2025/4/27 14:54
 * @desc
 */
public class ToolUtil {

    /**
     * 执行tool类的指定方法
     *
     * @param obj        指定的tool类，eg. Calculator
     * @param methodName tool method name, eg. squareRoot
     * @param params     the params of llm return
     * @return tool method return value, nullable
     */
    public static Object invoke(Object obj, String methodName, Map<String, Object> params) throws InvocationTargetException, IllegalAccessException {
        Optional<Method> first = Arrays.stream(obj.getClass().getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(Tool.class) && m.getName().equals(methodName))
                .findFirst();
        if (first.isEmpty()) {
            return obj;
        }
        Method method = first.get();
        return method.invoke(obj, adaptParams(method, params));
    }

    private static Object[] adaptParams(Method method, Map<String, Object> params) throws IllegalArgumentException {
        return Arrays.stream(method.getParameters())
                .map(p -> {
                    String name = p.getName();
                    Class<?> type = p.getType();
                    Object param = params.get(name);
                    if (param == null) {
                        return null;
                    }
                    if (type.isAssignableFrom(param.getClass())) {
                        return param;
                    }

                    String error = String.format("param %s of %s can not be convert to %s",
                            param, param.getClass().getName(), type.getName());
                    return switch (type.getName()) {
                        case "double" -> Double.parseDouble(String.valueOf(param));
                        case "int", "integer" -> Integer.valueOf(String.valueOf(param));
                        case "string" -> String.valueOf(param);
                        // enum, list/set, map, POJOs
                        case "java.util.List" -> {
                            if (param instanceof Set<?>) {
                                yield new ArrayList<>(((Set<?>) param));
                            }
                            throw new IllegalArgumentException(error);
                        }
                        case "java.util.Set" -> {
                            if (param instanceof List<?>) {
                                yield new HashSet<>(((List<?>) param));
                            }
                            throw new IllegalArgumentException(error);
                        }
                        default -> throw new IllegalArgumentException(error);
                    };
                })
                .toArray();
    }

}
