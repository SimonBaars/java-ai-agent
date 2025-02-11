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
                
                // Add system message
                ObjectNode systemMessage = messages.addObject();
                systemMessage.put("role", "system");
                systemMessage.put("content", "You are a helpful assistant that can use provided functions. " +
                    "When using functions, you MUST provide all required arguments according to the function schema. " +
                    "For example:\n" +
                    "- For add/subtract/multiply/divide, provide: {\"arg0\": number, \"arg1\": number}\n" +
                    "- For setMemory, provide: {\"arg0\": number}\n" +
                    "- For getMemory, provide: {}\n" +
                    "Never send empty argument objects for functions that require arguments. " +
                    "For questions that don't require function calls, provide direct and concise answers.");
                
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
                        function.put("description", "Execute " + entry.getKey());
                        
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
        ObjectNode properties = schema.putObject("properties");
        ArrayNode required = schema.putArray("required");
        
        // For now, we assume all functions take 2 parameters
        for (int i = 0; i < 2; i++) {
            String paramName = "arg" + i;
            ObjectNode param = properties.putObject(paramName);
            param.put("type", "number");
            param.put("description", "Parameter " + paramName + " for " + functionName);
            required.add(paramName);
        }
        schema.put("additionalProperties", false);
        return schema;
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
} 