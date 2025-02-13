package com.simonbrs.aiagent.schema;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Collection;

public class FunctionSchemaGenerator {
    private final ObjectMapper objectMapper;

    public FunctionSchemaGenerator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ObjectNode generateSchema(Method method) {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");
        ArrayNode required = schema.putArray("required");

        Parameter[] parameters = method.getParameters();
        for (int i = 0; i < parameters.length; i++) {
            Parameter param = parameters[i];
            String paramName = "arg" + i;
            Class<?> paramType = param.getType();
            Type genericType = param.getParameterizedType();
            
            ObjectNode property = properties.putObject(paramName);
            addTypeInfo(property, paramType, genericType, paramName);
            required.add(paramName);
        }

        schema.put("additionalProperties", false);
        return schema;
    }

    public ObjectNode generateDefaultSchema(String functionName) {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");
        ArrayNode required = schema.putArray("required");

        ObjectNode arg0 = properties.putObject("arg0");
        arg0.put("type", "number");
        arg0.put("description", "Parameter for " + functionName);
        required.add("arg0");

        schema.put("additionalProperties", false);
        return schema;
    }

    private void addTypeInfo(ObjectNode property, Class<?> paramType, Type genericType, String paramName) {
        if (paramType == int.class || paramType == long.class || 
            paramType == float.class || paramType == double.class ||
            Number.class.isAssignableFrom(paramType)) {
            property.put("type", "number");
            if (paramType == int.class || paramType == long.class) {
                property.put("description", String.format("Integer parameter %s", paramName));
            } else {
                property.put("description", String.format("Decimal number parameter %s", paramName));
            }
        } else if (paramType == boolean.class || paramType == Boolean.class) {
            property.put("type", "boolean");
            property.put("description", String.format("Boolean parameter %s", paramName));
        } else if (paramType == String.class) {
            property.put("type", "string");
            property.put("description", String.format("Text parameter %s", paramName));
        } else if (paramType.isArray()) {
            property.put("type", "array");
            property.put("description", String.format("Array parameter %s", paramName));
            ObjectNode items = property.putObject("items");
            items.put("type", getJsonType(paramType.getComponentType()));
        } else if (paramType == List.class || paramType == Collection.class || paramType == Iterable.class) {
            property.put("type", "array");
            property.put("description", String.format("List parameter %s", paramName));
            ObjectNode items = property.putObject("items");
            if (genericType instanceof ParameterizedType) {
                Type[] typeArgs = ((ParameterizedType) genericType).getActualTypeArguments();
                if (typeArgs.length > 0 && typeArgs[0] instanceof Class) {
                    items.put("type", getJsonType((Class<?>) typeArgs[0]));
                } else {
                    items.put("type", "string");
                }
            } else {
                items.put("type", "string");
            }
        } else if (paramType.isEnum()) {
            property.put("type", "string");
            property.put("description", String.format("Enum parameter %s (%s)", paramName, paramType.getSimpleName()));
            ArrayNode enumValues = property.putArray("enum");
            for (Object enumConstant : paramType.getEnumConstants()) {
                enumValues.add(enumConstant.toString());
            }
        } else {
            property.put("type", "string");
            property.put("description", String.format("Parameter %s (type: %s)", paramName, paramType.getSimpleName()));
        }
    }

    private String getJsonType(Class<?> type) {
        if (type == int.class || type == long.class || 
            type == float.class || type == double.class ||
            Number.class.isAssignableFrom(type)) {
            return "number";
        } else if (type == boolean.class || type == Boolean.class) {
            return "boolean";
        } else if (type == String.class) {
            return "string";
        } else if (type.isArray() || List.class.isAssignableFrom(type)) {
            return "array";
        }
        return "string";
    }
} 