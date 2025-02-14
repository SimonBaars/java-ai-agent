package com.simonbrs.aiagent.examples;

import com.simonbrs.aiagent.OpenAIAgent;
import java.util.concurrent.ExecutionException;

/**
 * Example demonstrating how the AI agent can use multiple tool calls and mixed responses.
 * This calculator supports complex operations that require multiple steps and tool calls.
 */
public class MultiToolCalculator {
    private double memory = 0.0;
    private double lastResult = 0.0;

    public double add(double a, double b) {
        System.out.println("Tool called: add(" + a + ", " + b + ")");
        return a + b;
    }

    public double multiply(double a, double b) {
        System.out.println("Tool called: multiply(" + a + ", " + b + ")");
        return a * b;
    }

    public double getMemory() {
        System.out.println("Tool called: getMemory()");
        return memory;
    }

    public void setMemory(double value) {
        System.out.println("Tool called: setMemory(" + value + ")");
        memory = value;
    }

    public double getLastResult() {
        System.out.println("Tool called: getLastResult()");
        return lastResult;
    }

    public void setLastResult(double value) {
        System.out.println("Tool called: setLastResult(" + value + ")");
        lastResult = value;
    }

    public double power(double base, double exponent) {
        System.out.println("Tool called: power(" + base + ", " + exponent + ")");
        return Math.pow(base, exponent);
    }

    public static void main(String[] args) throws ExecutionException, InterruptedException {
        // Get API key from environment variable
        String apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey == null || apiKey.trim().isEmpty()) {
            System.err.println("Please set the OPENAI_API_KEY environment variable");
            System.exit(1);
        }

        // Create calculator instance and agent
        MultiToolCalculator calculator = new MultiToolCalculator();
        OpenAIAgent agent = new OpenAIAgent(apiKey, "gpt-4o");

        // Register all calculator methods
        agent.registerMethods(calculator);

        // Example questions that require multiple tool calls
        String[] questions = {
            "Calculate (2 + 3) * 4 and store the result in memory",
            "What's the current value in memory raised to the power of 2?",
            "Add 5 to the last result and multiply it by 3",
            "Can you explain step by step how you calculated the previous result?",
            "Calculate the following: take 10, add it to what's in memory, multiply by 2, then raise to the power of 2"
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