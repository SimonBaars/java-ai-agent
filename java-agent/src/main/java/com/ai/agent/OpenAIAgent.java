package com.ai.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

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
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final List<Map<String, String>> conversationHistory;

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
                Map<String, String> messageMap = new HashMap<>();
                messageMap.put("role", "user");
                messageMap.put("content", userMessage);
                conversationHistory.add(messageMap);

                // Create request body
                ObjectNode requestBody = objectMapper.createObjectNode();
                requestBody.put("model", model);
                
                // Add messages
                ArrayNode messages = requestBody.putArray("messages");
                for (Map<String, String> msg : conversationHistory) {
                    ObjectNode messageNode = messages.addObject();
                    messageNode.put("role", msg.get("role"));
                    messageNode.put("content", msg.get("content"));
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
                        
                        ObjectNode parameters = function.putObject("parameters");
                        parameters.put("type", "object");
                        ObjectNode properties = parameters.putObject("properties");
                        // Add generic parameters for now - this could be enhanced with proper schema
                        properties.putObject("args").put("type", "object");
                        parameters.putArray("required").add("args");
                        parameters.put("additionalProperties", false);
                        
                        function.put("strict", true);
                    }
                }

                // Create HTTP request
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(OPENAI_API_URL))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
                    .build();

                // Send request and get response
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                
                if (response.statusCode() != 200) {
                    throw new RuntimeException("API request failed with status code: " + response.statusCode());
                }

                // Parse response
                ObjectNode responseJson = (ObjectNode) objectMapper.readTree(response.body());
                ObjectNode choice = (ObjectNode) responseJson.get("choices").get(0);
                ObjectNode responseMessage = (ObjectNode) choice.get("message");

                // Check for function calls
                if (responseMessage.has("tool_calls")) {
                    ArrayNode toolCalls = (ArrayNode) responseMessage.get("tool_calls");
                    for (int i = 0; i < toolCalls.size(); i++) {
                        ObjectNode toolCall = (ObjectNode) toolCalls.get(i);
                        String functionName = toolCall.get("function").get("name").asText();
                        String arguments = toolCall.get("function").get("arguments").asText();
                        
                        // Execute function
                        AgentFunction function = functions.get(functionName);
                        if (function != null) {
                            Map<String, Object> params = objectMapper.readValue(arguments, Map.class);
                            Object result = function.execute(params);
                            
                            // Add function result to conversation
                            Map<String, String> toolMessage = new HashMap<>();
                            toolMessage.put("role", "tool");
                            toolMessage.put("tool_call_id", toolCall.get("id").asText());
                            toolMessage.put("content", String.valueOf(result));
                            conversationHistory.add(toolMessage);
                        }
                    }
                    
                    // Make another request with function results
                    return sendMessage("Continue with the function results", context).get();
                }

                // Add assistant's response to conversation history
                Map<String, String> assistantMessage = new HashMap<>();
                assistantMessage.put("role", "assistant");
                assistantMessage.put("content", responseMessage.get("content").asText());
                conversationHistory.add(assistantMessage);

                return responseMessage.get("content").asText();
            } catch (Exception e) {
                logger.error("Error while processing message", e);
                throw new RuntimeException("Failed to process message", e);
            }
        });
    }

    @Override
    public void registerFunction(String functionName, AgentFunction function) {
        if (functionName == null || functionName.trim().isEmpty()) {
            throw new IllegalArgumentException("Function name cannot be null or empty");
        }
        if (function == null) {
            throw new IllegalArgumentException("Function cannot be null");
        }
        functions.put(functionName, function);
    }

    /**
     * Clears the conversation history.
     */
    public void clearConversationHistory() {
        conversationHistory.clear();
    }
} 