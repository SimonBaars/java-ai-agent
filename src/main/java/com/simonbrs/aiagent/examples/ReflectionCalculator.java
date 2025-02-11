package com.simonbrs.aiagent.examples;

import com.simonbrs.aiagent.OpenAIAgent;

import java.util.concurrent.ExecutionException;

/**
 * A more complex example demonstrating how to use the AI agent with reflection-based function calling.
 */
public class ReflectionCalculator {
    private double memory = 0.0;

    public double add(double a, double b) {
        return a + b;
    }

    public double subtract(double a, double b) {
        return a - b;
    }

    public double multiply(double a, double b) {
        return a * b;
    }

    public double divide(double a, double b) {
        if (b == 0) {
            throw new IllegalArgumentException("Cannot divide by zero");
        }
        return a / b;
    }

    public double getMemory() {
        return memory;
    }

    public void setMemory(double value) {
        this.memory = value;
    }

    public static void main(String[] args) throws ExecutionException, InterruptedException {
        // Get API key from environment variable
        String apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey == null || apiKey.trim().isEmpty()) {
            System.err.println("Please set the OPENAI_API_KEY environment variable");
            System.exit(1);
        }

        // Create calculator instance and agent
        ReflectionCalculator calculator = new ReflectionCalculator();
        OpenAIAgent agent = new OpenAIAgent(apiKey, "gpt-4o");

        // Register all calculator methods
        agent.registerMethods(calculator);

        // Example conversation with the agent
        String[] questions = {
            "What is 15 divided by 3?",
            "Can you subtract 7 from 20?",
            "Store the number 42 in memory",
            "What number is currently stored in memory?",
            "Take the number in memory and multiply it by 2",
            "What is 1 divided by 0?"
        };

        for (String question : questions) {
            try {
                System.out.println("\nQuestion: " + question);
                String response = agent.sendMessage(question).get();
                System.out.println("Answer: " + response);
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
            }
        }
    }
} 