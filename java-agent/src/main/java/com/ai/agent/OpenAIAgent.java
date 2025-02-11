package com.ai.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.completion.chat.ChatMessageRole;
import com.theokanning.openai.service.OpenAiService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of the Agent interface using OpenAI's API.
 */
public class OpenAIAgent implements Agent {
    private static final Logger logger = LoggerFactory.getLogger(OpenAIAgent.class);
    private final OpenAiService openAiService;
    private final String model;
    private final Map<String, AgentFunction> functions;
    private final ObjectMapper objectMapper;
    private final List<ChatMessage> conversationHistory;

    /**
     * Creates a new OpenAIAgent with the specified API key and model.
     *
     * @param apiKey The OpenAI API key
     * @param model The model to use (e.g., "gpt-3.5-turbo")
     * @throws IllegalArgumentException if apiKey is null or empty
     */
    public OpenAIAgent(String apiKey, String model) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalArgumentException("API key cannot be null or empty");
        }
        if (model == null || model.trim().isEmpty()) {
            throw new IllegalArgumentException("Model name cannot be null or empty");
        }
        
        this.openAiService = new OpenAiService(apiKey);
        this.model = model;
        this.functions = new ConcurrentHashMap<>();
        this.objectMapper = new ObjectMapper();
        this.conversationHistory = new ArrayList<>();
    }

    @Override
    public CompletableFuture<String> sendMessage(String message) {
        return sendMessage(message, new HashMap<>());
    }

    @Override
    public CompletableFuture<String> sendMessage(String message, Map<String, Object> context) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Add user message to conversation history
                conversationHistory.add(new ChatMessage(ChatMessageRole.USER.value(), message));

                // Create chat completion request
                ChatCompletionRequest request = ChatCompletionRequest.builder()
                        .model(model)
                        .messages(new ArrayList<>(conversationHistory))
                        .build();

                // Get response from OpenAI
                ChatMessage response = openAiService.createChatCompletion(request)
                        .getChoices().get(0).getMessage();

                // Add assistant's response to conversation history
                conversationHistory.add(response);

                return response.getContent();
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