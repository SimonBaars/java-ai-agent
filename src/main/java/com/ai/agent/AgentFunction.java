package com.ai.agent;

import java.util.Map;

/**
 * Interface defining a function that can be called by an AI agent.
 */
@FunctionalInterface
public interface AgentFunction {
    /**
     * Executes the function with the given parameters.
     *
     * @param parameters The parameters passed to the function
     * @return The result of the function execution
     * @throws Exception If an error occurs during execution
     */
    Object execute(Map<String, Object> parameters) throws Exception;
} 