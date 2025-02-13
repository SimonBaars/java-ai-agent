package com.simonbrs.aiagent;

import java.util.*;
import java.util.concurrent.*;
import java.net.http.*;
import java.net.URI;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of the Agent interface using OpenAI's API directly.
 */
public class OpenAIAgent implements Agent {
    private static final Logger logger = LoggerFactory.getLogger(OpenAIAgent.class);
    private static final String OPENAI_API_URL = "https://api.openai.com/v1/chat/completions";
    
    private final String apiKey;
    private final String model;
    private final Map<String, AgentFunction> functions;
    private final Map<String, ObjectNode> functionSchemas;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final List<Map<String, Object>> conversationHistory;

    /**
     * Creates a new OpenAIAgent with the specified API key and model.
     *
     * @param apiKey The OpenAI API key
     * @param model The model to use (e.g., "gpt-4o")
     * @throws IllegalArgumentException if apiKey is null or empty
     */
    public OpenAIAgent(String apiKey, String model) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalArgumentException("API key cannot be null or empty");
        }
        if (model == null || model.trim().isEmpty()) {
            throw new IllegalArgumentException("Model name cannot be null or empty");
        }
        
        this.apiKey = apiKey;
        this.model = model;
        this.functions = new ConcurrentHashMap<>();
        this.functionSchemas = new ConcurrentHashMap<>();
        this.objectMapper = new ObjectMapper();
        this.httpClient = HttpClient.newHttpClient();
        this.conversationHistory = new ArrayList<>();
    }

    @Override
    public CompletableFuture<String> sendMessage(String message) {
        return sendMessage(message, new HashMap<>());
    }

    @Override
    public CompletableFuture<String> sendMessage(String userMessage, Map<String, Object> context) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Add user message to conversation history
                addMessageToHistory("user", userMessage, null);

                // Create request body
                ObjectNode requestBody = objectMapper.createObjectNode();
                requestBody.put("model", model);
                
                // Add messages
                ArrayNode messages = requestBody.putArray("messages");
                
                // Add system message with dynamic function descriptions
                ObjectNode systemMessage = messages.addObject();
                systemMessage.put("role", "system");
                systemMessage.put("content", generateSystemPrompt());
                
                // Add conversation history
                for (Map<String, Object> msg : conversationHistory) {
                    ObjectNode messageNode = messages.addObject();
                    messageNode.put("role", (String) msg.get("role"));
                    
                    // Handle tool responses differently
                    if ("tool".equals(msg.get("role"))) {
                        messageNode.put("tool_call_id", (String) msg.get("tool_call_id"));
                        messageNode.put("content", (String) msg.get("content"));
                    } else {
                        messageNode.put("content", (String) msg.get("content"));
                        if (msg.containsKey("tool_calls")) {
                            messageNode.set("tool_calls", (JsonNode) msg.get("tool_calls"));
                        }
                    }
                }

                // Add tools (functions)
                if (!functions.isEmpty()) {
                    ArrayNode tools = requestBody.putArray("tools");
                    for (Map.Entry<String, AgentFunction> entry : functions.entrySet()) {
                        ObjectNode tool = tools.addObject();
                        tool.put("type", "function");
                        
                        ObjectNode function = tool.putObject("function");
                        function.put("name", entry.getKey());
                        
                        // Get method description if available
                        String description = getFunctionDescription(entry.getKey(), entry.getValue());
                        function.put("description", description);
                        
                        // Use schema if available, otherwise create default schema
                        ObjectNode schema = functionSchemas.getOrDefault(entry.getKey(), createDefaultSchema(entry.getKey()));
                        function.setAll(schema);
                    }
                }

                // Log request body
                String requestBodyStr = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(requestBody);
                logger.info("Request body: {}", requestBodyStr);

                // Create HTTP request
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(OPENAI_API_URL))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBodyStr))
                    .build();

                // Send request and get response
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                
                // Log response
                logger.info("Response status: {}", response.statusCode());
                logger.info("Response body: {}", response.body());

                if (response.statusCode() != 200) {
                    // If there are any pending tool calls, add error responses for them
                    JsonNode lastToolCalls = null;
                    for (int i = conversationHistory.size() - 1; i >= 0; i--) {
                        Map<String, Object> msg = conversationHistory.get(i);
                        if (msg.containsKey("tool_calls")) {
                            lastToolCalls = (JsonNode) msg.get("tool_calls");
                            break;
                        }
                    }
                    
                    if (lastToolCalls != null && lastToolCalls.isArray()) {
                        for (JsonNode toolCall : lastToolCalls) {
                            String toolCallId = toolCall.path("id").asText();
                            addToolResponseToHistory(toolCallId, "Error: " + response.body());
                        }
                    }
                    
                    throw new RuntimeException("API request failed with status code: " + response.statusCode() + ", body: " + response.body());
                }

                // Parse response
                ObjectNode responseJson = (ObjectNode) objectMapper.readTree(response.body());
                ObjectNode responseMessage = (ObjectNode) responseJson.path("choices").get(0).path("message");
                JsonNode toolCalls = responseMessage.path("tool_calls");

                // Process the response
                if (!toolCalls.isMissingNode() && toolCalls.isArray() && toolCalls.size() > 0) {
                    String responseContent = responseMessage.path("content").isNull() ? null : responseMessage.path("content").asText();
                    addMessageToHistory("assistant", responseContent, toolCalls);

                    StringBuilder finalResponse = new StringBuilder();
                    // Execute each tool call
                    for (JsonNode toolCall : toolCalls) {
                        String functionName = toolCall.path("function").path("name").asText();
                        String arguments = toolCall.path("function").path("arguments").asText();
                        String toolCallId = toolCall.path("id").asText();
                        logger.info("Function call: {} with arguments: {}", functionName, arguments);

                        AgentFunction function = functions.get(functionName);
                        if (function != null) {
                            Map<String, Object> args = objectMapper.readValue(arguments, Map.class);
                            try {
                                Object result = function.execute(args);
                                String resultStr = String.valueOf(result);
                                addToolResponseToHistory(toolCallId, resultStr);
                                if (finalResponse.length() > 0) {
                                    finalResponse.append("\n");
                                }
                                finalResponse.append(resultStr);
                            } catch (Exception e) {
                                String errorMsg = "Error calling method: " + functionName + " - " + e.getMessage();
                                addToolResponseToHistory(toolCallId, errorMsg);
                                throw new RuntimeException(errorMsg, e);
                            }
                        } else {
                            String errorMsg = "Function not found: " + functionName;
                            addToolResponseToHistory(toolCallId, errorMsg);
                            throw new RuntimeException(errorMsg);
                        }
                    }
                    return finalResponse.toString();
                } else {
                    String content = responseMessage.path("content").asText();
                    addMessageToHistory("assistant", content, null);
                    return content;
                }
            } catch (Exception e) {
                logger.error("Error while processing message", e);
                throw new RuntimeException("Failed to process message", e);
            }
        });
    }

    @Override
    public void registerFunction(String name, AgentFunction function) {
        functions.put(name, function);
    }

    /**
     * Registers a function with a specific schema.
     */
    public void registerFunction(String functionName, AgentFunction function, ObjectNode schema) {
        registerFunction(functionName, function);
        functionSchemas.put(functionName, schema);
    }

    /**
     * Clears the conversation history.
     */
    public void clearConversationHistory() {
        conversationHistory.clear();
    }

    private void addMessageToHistory(String role, String content, JsonNode toolCalls) {
        Map<String, Object> messageMap = new HashMap<>();
        messageMap.put("role", role);
        messageMap.put("content", content);
        if (toolCalls != null) {
            messageMap.put("tool_calls", toolCalls);
        }
        conversationHistory.add(messageMap);
    }

    private void addToolResponseToHistory(String toolCallId, String content) {
        Map<String, Object> toolMessage = new HashMap<>();
        toolMessage.put("role", "tool");
        toolMessage.put("tool_call_id", toolCallId);
        toolMessage.put("content", content);
        conversationHistory.add(toolMessage);
    }

    private ObjectNode createDefaultSchema(String functionName) {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");
        ArrayNode required = schema.putArray("required");

        // Get method by name from registered functions
        Method[] methods = functions.values().stream()
            .filter(f -> f instanceof MethodFunction)
            .map(f -> ((MethodFunction) f).getMethod())
            .filter(m -> m.getName().equals(functionName))
            .toArray(Method[]::new);

        if (methods.length > 0) {
            Method method = methods[0];
            Parameter[] parameters = method.getParameters();
            
            for (Parameter param : parameters) {
                String paramName = param.getName();
                Class<?> paramType = param.getType();
                
                ObjectNode property = properties.putObject(paramName);
                
                // Map Java types to JSON schema types with detailed descriptions
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
                    // Add format hints for common string patterns
                    if (paramName.toLowerCase().contains("json")) {
                        property.put("format", "json");
                    } else if (paramName.toLowerCase().contains("date")) {
                        property.put("format", "date");
                    }
                } else if (paramType.isArray()) {
                    property.put("type", "array");
                    property.put("description", String.format("Array parameter %s for %s", paramName, functionName));
                    ObjectNode items = property.putObject("items");
                    items.put("type", getJsonType(paramType.getComponentType()));
                } else {
                    // Default to string for other types
                    property.put("type", "string");
                    property.put("description", String.format("Parameter %s for %s (type: %s)", paramName, functionName, paramType.getSimpleName()));
                }
                
                required.add(paramName);
            }
        } else {
            // Fallback for functions without reflection info
            ObjectNode arg0 = properties.putObject("arg0");
            arg0.put("type", "number");
            arg0.put("description", "First parameter for " + functionName);
            required.add("arg0");
        }

        schema.put("additionalProperties", false);
        return schema;
    }

    private String getJsonType(Class<?> type) {
        if (type == int.class || type == long.class || 
            type == float.class || type == double.class ||
            Number.class.isAssignableFrom(type)) {
            return "number";
        } else if (type == boolean.class || type == Boolean.class) {
            return "boolean";
        } else {
            return "string";
        }
    }

    // Helper class to store method information
    private static class MethodFunction implements AgentFunction {
        private final Method method;
        private final Object instance;

        public MethodFunction(Method method, Object instance) {
            this.method = method;
            this.instance = instance;
        }

        public Method getMethod() {
            return method;
        }

        @Override
        public Object execute(Map<String, Object> parameters) throws Exception {
            Parameter[] methodParams = method.getParameters();
            Object[] args = new Object[methodParams.length];
            
            for (int i = 0; i < methodParams.length; i++) {
                Parameter param = methodParams[i];
                String paramName = param.getName();
                Class<?> paramType = param.getType();
                Object value = parameters.get(paramName);
                
                if (value != null) {
                    args[i] = convertValue(value, paramType);
                }
            }
            
            return method.invoke(instance, args);
        }

        private Object convertValue(Object value, Class<?> targetType) {
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
                } else if (targetType.isArray()) {
                    // Handle array conversion
                    if (value instanceof List) {
                        List<?> list = (List<?>) value;
                        Object array = Array.newInstance(targetType.getComponentType(), list.size());
                        for (int i = 0; i < list.size(); i++) {
                            Array.set(array, i, convertValue(list.get(i), targetType.getComponentType()));
                        }
                        return array;
                    }
                }
                return value;
            } catch (Exception e) {
                throw new IllegalArgumentException("Failed to convert value " + value + " to type " + targetType, e);
            }
        }
    }

    /**
     * Registers all public methods from a class instance as functions.
     * Methods with more than 2 parameters are ignored.
     * The 'main' method is also ignored.
     *
     * @param instance The class instance whose methods should be registered
     */
    public void registerMethods(Object instance) {
        ObjectMapper mapper = new ObjectMapper();

        for (Method method : instance.getClass().getDeclaredMethods()) {
            if (method.getParameterCount() <= 2 && !method.getName().equals("main")) {
                // Create function schema
                ObjectNode schema = mapper.createObjectNode();
                schema.put("type", "object");
                
                ObjectNode properties = schema.putObject("properties");
                Parameter[] parameters = method.getParameters();
                ArrayNode required = schema.putArray("required");

                // Add parameters to schema
                for (int i = 0; i < parameters.length; i++) {
                    Parameter param = parameters[i];
                    String paramName = param.getName();
                    Class<?> paramType = param.getType();
                    
                    ObjectNode property = properties.putObject(paramName);
                    
                    // Map Java types to JSON schema types
                    if (paramType == int.class || paramType == long.class || 
                        paramType == float.class || paramType == double.class ||
                        Number.class.isAssignableFrom(paramType)) {
                        property.put("type", "number");
                    } else if (paramType == boolean.class || paramType == Boolean.class) {
                        property.put("type", "boolean");
                    } else if (paramType == String.class) {
                        property.put("type", "string");
                    } else {
                        // Default to string for other types
                        property.put("type", "string");
                    }
                    
                    property.put("description", String.format("Parameter %s for %s", paramName, method.getName()));
                    required.add(paramName);
                }

                schema.put("additionalProperties", false);

                // Register function with schema
                registerFunction(method.getName(), params -> {
                    try {
                        Object[] args = new Object[parameters.length];
                        for (int i = 0; i < parameters.length; i++) {
                            Parameter param = parameters[i];
                            String paramName = param.getName();
                            Class<?> paramType = param.getType();
                            Object value = params.get(paramName);
                            
                            if (value != null) {
                                // Convert value to the expected type
                                if (paramType == int.class || paramType == Integer.class) {
                                    args[i] = ((Number) value).intValue();
                                } else if (paramType == long.class || paramType == Long.class) {
                                    args[i] = ((Number) value).longValue();
                                } else if (paramType == float.class || paramType == Float.class) {
                                    args[i] = ((Number) value).floatValue();
                                } else if (paramType == double.class || paramType == Double.class) {
                                    args[i] = ((Number) value).doubleValue();
                                } else if (paramType == boolean.class || paramType == Boolean.class) {
                                    args[i] = (Boolean) value;
                                } else if (paramType == String.class) {
                                    args[i] = value.toString();
                                } else {
                                    args[i] = value;
                                }
                            }
                        }
                        return method.invoke(instance, args);
                    } catch (Exception e) {
                        throw new RuntimeException("Error calling method: " + method.getName(), e);
                    }
                }, schema);
            }
        }
    }

    private String generateSystemPrompt() {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are a helpful assistant that can use provided functions. ");
        prompt.append("Please follow these guidelines when using functions:\n\n");
        
        // Add general guidelines
        prompt.append("1. Always provide ALL required arguments according to the function schema\n");
        prompt.append("2. Use the correct data types for arguments:\n");
        prompt.append("   - For numbers: Use numeric values without quotes\n");
        prompt.append("   - For strings: Use quoted text\n");
        prompt.append("   - For arrays: Use JSON array format\n");
        prompt.append("   - For booleans: Use true or false\n\n");
        
        // Add function-specific guidelines
        prompt.append("3. Function-specific guidelines:\n");
        for (Map.Entry<String, AgentFunction> entry : functions.entrySet()) {
            String functionName = entry.getKey();
            ObjectNode schema = functionSchemas.getOrDefault(functionName, createDefaultSchema(functionName));
            
            prompt.append(String.format("   - %s:\n", functionName));
            JsonNode properties = schema.path("properties");
            if (properties.isObject()) {
                properties.fields().forEachRemaining(field -> {
                    String paramName = field.getKey();
                    JsonNode paramInfo = field.getValue();
                    String type = paramInfo.path("type").asText("any");
                    String description = paramInfo.path("description").asText("");
                    prompt.append(String.format("     * %s (%s): %s\n", paramName, type, description));
                });
            }
        }
        
        // Add error handling guidelines
        prompt.append("\n4. Error handling:\n");
        prompt.append("   - If a function call fails, I will provide an error message\n");
        prompt.append("   - For numeric operations, ensure inputs are within valid ranges\n");
        prompt.append("   - For string operations, ensure inputs are properly formatted\n");
        
        return prompt.toString();
    }

    private String getFunctionDescription(String functionName, AgentFunction function) {
        if (function instanceof MethodFunction) {
            Method method = ((MethodFunction) function).getMethod();
            StringBuilder description = new StringBuilder();
            
            // Add return type information
            description.append("Returns ").append(method.getReturnType().getSimpleName()).append(". ");
            
            // Add parameter information
            Parameter[] params = method.getParameters();
            if (params.length > 0) {
                description.append("Takes ");
                for (int i = 0; i < params.length; i++) {
                    if (i > 0) {
                        description.append(i == params.length - 1 ? " and " : ", ");
                    }
                    description.append(params[i].getType().getSimpleName())
                             .append(" ")
                             .append(params[i].getName());
                }
                description.append(".");
            }
            
            return description.toString();
        }
        
        return "Execute " + functionName;
    }
} 