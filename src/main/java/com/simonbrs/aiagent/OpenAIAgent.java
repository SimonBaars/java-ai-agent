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

import com.simonbrs.aiagent.schema.FunctionSchemaGenerator;
import com.simonbrs.aiagent.conversion.TypeConverter;
import com.simonbrs.aiagent.prompt.SystemPromptGenerator;
import com.simonbrs.aiagent.conversation.ConversationHistory;

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
    private final ConversationHistory conversationHistory;
    private final FunctionSchemaGenerator schemaGenerator;
    private final SystemPromptGenerator promptGenerator;

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
        this.conversationHistory = new ConversationHistory();
        this.schemaGenerator = new FunctionSchemaGenerator(objectMapper);
        this.promptGenerator = new SystemPromptGenerator();
    }

    @Override
    public CompletableFuture<String> sendMessage(String message) {
        return sendMessage(message, Map.of());
    }

    @Override
    public CompletableFuture<String> sendMessage(String userMessage, Map<String, Object> context) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                conversationHistory.addUserMessage(userMessage);

                ObjectNode requestBody = createRequestBody();
                String requestBodyStr = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(requestBody);
                logger.debug("Request body: {}", requestBodyStr);

                HttpResponse<String> response = sendRequest(requestBodyStr);
                logger.debug("Response status: {}", response.statusCode());
                logger.debug("Response body: {}", response.body());

                if (response.statusCode() != 200) {
                    handleErrorResponse(response);
                    throw new RuntimeException("API request failed with status code: " + response.statusCode());
                }

                return processResponse(response.body());
            } catch (Exception e) {
                logger.error("Error while processing message", e);
                throw new RuntimeException("Failed to process message", e);
            }
        });
    }

    private ObjectNode createRequestBody() {
        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", model);
        
        ArrayNode messages = requestBody.putArray("messages");
        
        // Add system message
        ObjectNode systemMessage = messages.addObject();
        systemMessage.put("role", "system");
        systemMessage.put("content", promptGenerator.generatePrompt(functions, functionSchemas));
        
        // Add conversation history
        for (Map<String, Object> msg : conversationHistory.getMessages()) {
            ObjectNode messageNode = messages.addObject();
            messageNode.put("role", (String) msg.get("role"));
            
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
                function.put("description", getFunctionDescription(entry.getKey(), entry.getValue()));
                
                ObjectNode schema = functionSchemas.getOrDefault(entry.getKey(), 
                    schemaGenerator.generateDefaultSchema(entry.getKey()));
                function.setAll(schema);
            }
        }

        return requestBody;
    }

    private HttpResponse<String> sendRequest(String requestBodyStr) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(OPENAI_API_URL))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + apiKey)
            .POST(HttpRequest.BodyPublishers.ofString(requestBodyStr))
            .build();

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private void handleErrorResponse(HttpResponse<String> response) {
        JsonNode lastToolCalls = findLastToolCalls();
        if (lastToolCalls != null && lastToolCalls.isArray()) {
            for (JsonNode toolCall : lastToolCalls) {
                String toolCallId = toolCall.path("id").asText();
                conversationHistory.addToolResponse(toolCallId, "Error: " + response.body());
            }
        }
    }

    private JsonNode findLastToolCalls() {
        for (int i = conversationHistory.getMessages().size() - 1; i >= 0; i--) {
            Map<String, Object> msg = conversationHistory.getMessages().get(i);
            if (msg.containsKey("tool_calls")) {
                return (JsonNode) msg.get("tool_calls");
            }
        }
        return null;
    }

    private String processResponse(String responseBody) throws Exception {
        ObjectNode responseJson = (ObjectNode) objectMapper.readTree(responseBody);
        ObjectNode responseMessage = (ObjectNode) responseJson.path("choices").get(0).path("message");
        JsonNode toolCalls = responseMessage.path("tool_calls");
        String content = responseMessage.path("content").isNull() ? null : responseMessage.path("content").asText();

        StringBuilder finalResponse = new StringBuilder();
        
        // Add the assistant's text response if present
        if (content != null && !content.trim().isEmpty()) {
            finalResponse.append(content);
        }

        // Handle tool calls if present
        if (!toolCalls.isMissingNode() && toolCalls.isArray() && toolCalls.size() > 0) {
            // Add the assistant's message with both content and tool calls to history
            conversationHistory.addAssistantMessage(content, toolCalls);

            // Execute each tool call and append results
            for (JsonNode toolCall : toolCalls) {
                String result = executeToolCall(toolCall);
                if (finalResponse.length() > 0 && result != null && !result.trim().isEmpty()) {
                    finalResponse.append("\n");
                }
                if (result != null && !result.trim().isEmpty()) {
                    finalResponse.append(result);
                }
            }
        } else {
            // If no tool calls, just add the content to history
            conversationHistory.addAssistantMessage(content, null);
        }

        return finalResponse.toString();
    }

    private String executeToolCall(JsonNode toolCall) throws Exception {
        String functionName = toolCall.path("function").path("name").asText();
        String arguments = toolCall.path("function").path("arguments").asText();
        String toolCallId = toolCall.path("id").asText();
        
        logger.debug("Function call: {} with arguments: {}", functionName, arguments);

        AgentFunction function = functions.get(functionName);
        if (function == null) {
            String errorMsg = "Function not found: " + functionName;
            conversationHistory.addToolResponse(toolCallId, errorMsg);
            throw new RuntimeException(errorMsg);
        }

        try {
            Map<String, Object> args = objectMapper.readValue(arguments, Map.class);
            Object result = function.execute(args);
            String resultStr = String.valueOf(result);
            conversationHistory.addToolResponse(toolCallId, resultStr);
            return resultStr;
        } catch (Exception e) {
            String errorMsg = "Error calling method: " + functionName + " - " + e.getMessage();
            conversationHistory.addToolResponse(toolCallId, errorMsg);
            throw new RuntimeException(errorMsg, e);
        }
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

    private String getFunctionDescription(String functionName, AgentFunction function) {
        if (function instanceof MethodFunction) {
            Method method = ((MethodFunction) function).getMethod();
            StringBuilder description = new StringBuilder();
            
            description.append("Returns ").append(method.getReturnType().getSimpleName()).append(". ");
            
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
                String paramName = "arg" + i;
                Class<?> paramType = methodParams[i].getType();
                Object value = parameters.get(paramName);
                
                if (value != null) {
                    try {
                        args[i] = TypeConverter.convert(value, paramType);
                    } catch (Exception e) {
                        throw new IllegalArgumentException("Failed to convert parameter " + paramName + 
                            " to type " + paramType.getSimpleName() + ": " + e.getMessage());
                    }
                } else {
                    throw new IllegalArgumentException("Missing required parameter: " + paramName);
                }
            }
            
            return method.invoke(instance, args);
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
        for (Method method : instance.getClass().getDeclaredMethods()) {
            if (!method.getName().equals("main")) {
                ObjectNode schema = schemaGenerator.generateSchema(method);
                registerFunction(method.getName(), new MethodFunction(method, instance), schema);
            }
        }
    }
} 