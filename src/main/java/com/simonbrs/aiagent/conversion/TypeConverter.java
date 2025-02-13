package com.simonbrs.aiagent.conversion;

import java.lang.reflect.Array;
import java.util.List;

public class TypeConverter {
    public static Object convert(Object value, Class<?> targetType) {
        if (value == null) return null;
        
        try {
            if (targetType == String.class) {
                return value.toString();
            } else if (targetType == int.class || targetType == Integer.class) {
                return ((Number) value).intValue();
            } else if (targetType == long.class || targetType == Long.class) {
                return ((Number) value).longValue();
            } else if (targetType == float.class || targetType == Float.class) {
                return ((Number) value).floatValue();
            } else if (targetType == double.class || targetType == Double.class) {
                return ((Number) value).doubleValue();
            } else if (targetType == boolean.class || targetType == Boolean.class) {
                if (value instanceof String) {
                    return Boolean.parseBoolean((String) value);
                }
                return (Boolean) value;
            } else if (targetType.isArray() && value instanceof List) {
                return convertArray((List<?>) value, targetType);
            }
            return value;
        } catch (Exception e) {
            throw new TypeConversionException("Failed to convert value " + value + " to type " + targetType, e);
        }
    }

    private static Object convertArray(List<?> list, Class<?> arrayType) {
        Class<?> componentType = arrayType.getComponentType();
        Object array = Array.newInstance(componentType, list.size());
        
        for (int i = 0; i < list.size(); i++) {
            Array.set(array, i, convert(list.get(i), componentType));
        }
        
        return array;
    }
}

class TypeConversionException extends RuntimeException {
    public TypeConversionException(String message, Throwable cause) {
        super(message, cause);
    }
} 