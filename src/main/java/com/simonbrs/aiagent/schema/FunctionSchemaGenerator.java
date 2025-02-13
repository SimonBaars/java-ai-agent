package com.simonbrs.aiagent.schema;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

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
            
            ObjectNode property = properties.putObject(paramName);
            addTypeInfo(property, paramType, paramName, method.getName());
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
        arg0.put("description", "First parameter for " + functionName);
        required.add("arg0");

        schema.put("additionalProperties", false);
        return schema;
    }

    private void addTypeInfo(ObjectNode property, Class<?> paramType, String paramName, String functionName) {
        if (paramType == int.class || paramType == long.class || 
            paramType == float.class || paramType == double.class ||
            Number.class.isAssignableFrom(paramType)) {
            property.put("type", "number");
            if (paramType == int.class || paramType == long.class) {
                property.put("description", String.format("Integer parameter %s for %s", paramName, functionName));
            } else {
                property.put("description", String.format("Decimal number parameter %s for %s", paramName, functionName));
            }
        } else if (paramType == boolean.class || paramType == Boolean.class) {
            property.put("type", "boolean");
            property.put("description", String.format("Boolean parameter %s for %s", paramName, functionName));
        } else if (paramType == String.class) {
            property.put("type", "string");
            property.put("description", String.format("Text parameter %s for %s", paramName, functionName));
            addStringFormatHints(property, paramName, functionName);
        } else if (paramType.isArray()) {
            property.put("type", "array");
            property.put("description", String.format("Array parameter %s for %s", paramName, functionName));
            ObjectNode items = property.putObject("items");
            items.put("type", getJsonType(paramType.getComponentType()));
        } else {
            property.put("type", "string");
            property.put("description", String.format("Parameter %s for %s (type: %s)", paramName, functionName, paramType.getSimpleName()));
        }
    }

    private void addStringFormatHints(ObjectNode property, String paramName, String functionName) {
        String lowerName = functionName.toLowerCase();
        if (lowerName.contains("sequence") || lowerName.contains("generate")) {
            if (paramName.equals("arg0")) {
                property.put("description", "Type of sequence to generate (e.g., 'fibonacci' or 'prime')");
                property.put("enum", new String[]{"fibonacci", "prime"});
            } else if (paramName.equals("arg1")) {
                property.put("description", "Length of sequence to generate");
                property.put("type", "number");
                property.put("minimum", 1);
            }
        } else if (lowerName.contains("memory")) {
            if (paramName.equals("arg0")) {
                property.put("description", "Key to store/recall value");
            } else if (paramName.equals("arg1")) {
                property.put("description", "Value to store in memory");
                property.put("type", "number");
            }
        } else if (lowerName.contains("date")) {
            property.put("format", "date");
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
        } else if (type.isArray()) {
            return "array";
        }
        return "string";
    }
} 