package com.ai.agent;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Interface defining the core functionality of an AI agent.
 */
public interface Agent {
    /**
     * Sends a message to the agent and receives a response.
     *
     * @param message The message to send to the agent
     * @return A CompletableFuture containing the agent's response
     */
    CompletableFuture<String> sendMessage(String message);

    /**
     * Sends a message with additional context to the agent and receives a response.
     *
     * @param message The message to send to the agent
     * @param context Additional context as key-value pairs
     * @return A CompletableFuture containing the agent's response
     */
    CompletableFuture<String> sendMessage(String message, Map<String, Object> context);

    /**
     * Registers a function that can be called by the agent.
     *
     * @param functionName The name of the function to register
     * @param function The function to register
     */
    void registerFunction(String functionName, AgentFunction function);
} 