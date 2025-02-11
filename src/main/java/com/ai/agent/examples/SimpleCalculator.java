package com.ai.agent.examples;

import com.ai.agent.Agent;
import com.ai.agent.OpenAIAgent;

import java.util.concurrent.ExecutionException;

/**
 * A simple example demonstrating how to use the AI agent with function calling.
 */
public class SimpleCalculator {
    public static void main(String[] args) throws ExecutionException, InterruptedException {
        // Get API key from environment variable
        String apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey == null || apiKey.trim().isEmpty()) {
            System.err.println("Please set the OPENAI_API_KEY environment variable");
            System.exit(1);
        }

        // Create an agent instance
        Agent agent = new OpenAIAgent(apiKey, "gpt-3.5-turbo");

        // Register a function for addition
        agent.registerFunction("add", params -> {
            double a = ((Number) params.get("a")).doubleValue();
            double b = ((Number) params.get("b")).doubleValue();
            return a + b;
        });

        // Register a function for multiplication
        agent.registerFunction("multiply", params -> {
            double a = ((Number) params.get("a")).doubleValue();
            double b = ((Number) params.get("b")).doubleValue();
            return a * b;
        });

        // Example conversation with the agent
        String[] questions = {
            "What is 5 plus 3?",
            "What is 7 times 6?",
            "If I have 4 apples and get 3 more, how many do I have?",
            "What is the product of 8 and 9?"
        };

        for (String question : questions) {
            System.out.println("\nQuestion: " + question);
            String response = agent.sendMessage(question).get();
            System.out.println("Answer: " + response);
        }
    }
} 