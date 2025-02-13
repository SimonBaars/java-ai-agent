package com.simonbrs.aiagent.conversion;

import java.lang.reflect.Array;
import java.util.List;

public class TypeConverter {
    public static Object convert(Object value, Class<?> targetType) {
        if (value == null) return null;
        
        try {
            // Handle String conversion first
            if (targetType == String.class) {
                return value.toString();
            }
            
            // Handle numeric conversions
            if (Number.class.isAssignableFrom(targetType) || isPrimitiveNumber(targetType)) {
                if (value instanceof Number) {
                    return convertNumber((Number) value, targetType);
                } else if (value instanceof String) {
                    // Try parsing the string as a number
                    try {
                        Double parsed = Double.parseDouble((String) value);
                        return convertNumber(parsed, targetType);
                    } catch (NumberFormatException e) {
                        throw new TypeConversionException("Cannot convert string to number: " + value);
                    }
                }
            }
            
            // Handle boolean conversion
            if (targetType == boolean.class || targetType == Boolean.class) {
                if (value instanceof Boolean) {
                    return value;
                } else if (value instanceof String) {
                    String str = ((String) value).toLowerCase().trim();
                    return str.equals("true") || str.equals("1") || str.equals("yes");
                } else if (value instanceof Number) {
                    return ((Number) value).intValue() != 0;
                }
            }
            
            // Handle array conversion
            if (targetType.isArray() && value instanceof List) {
                return convertArray((List<?>) value, targetType);
            }
            
            // If the value is already of the target type, return it
            if (targetType.isInstance(value)) {
                return value;
            }
            
            throw new TypeConversionException("Unsupported conversion from " + value.getClass() + " to " + targetType);
        } catch (Exception e) {
            if (e instanceof TypeConversionException) {
                throw e;
            }
            throw new TypeConversionException("Failed to convert value " + value + " to type " + targetType, e);
        }
    }
    
    private static boolean isPrimitiveNumber(Class<?> type) {
        return type == int.class || type == long.class || 
               type == float.class || type == double.class ||
               type == byte.class || type == short.class;
    }
    
    private static Object convertNumber(Number value, Class<?> targetType) {
        if (targetType == int.class || targetType == Integer.class) {
            return value.intValue();
        } else if (targetType == long.class || targetType == Long.class) {
            return value.longValue();
        } else if (targetType == float.class || targetType == Float.class) {
            return value.floatValue();
        } else if (targetType == double.class || targetType == Double.class) {
            return value.doubleValue();
        } else if (targetType == byte.class || targetType == Byte.class) {
            return value.byteValue();
        } else if (targetType == short.class || targetType == Short.class) {
            return value.shortValue();
        }
        throw new TypeConversionException("Unsupported number type: " + targetType);
    }
    
    private static Object convertArray(List<?> list, Class<?> arrayType) {
        Class<?> componentType = arrayType.getComponentType();
        Object array = Array.newInstance(componentType, list.size());
        
        for (int i = 0; i < list.size(); i++) {
            Object element = list.get(i);
            Array.set(array, i, convert(element, componentType));
        }
        
        return array;
    }
    
    public static class TypeConversionException extends RuntimeException {
        public TypeConversionException(String message) {
            super(message);
        }
        
        public TypeConversionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
} 